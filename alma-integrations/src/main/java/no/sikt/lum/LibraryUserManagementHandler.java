package no.sikt.lum;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.google.gson.Gson;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.sikt.alma.user.generated.User;
import no.sikt.clients.BaseBibliotekApi;
import no.sikt.clients.alma.AlmaUserUpserter;
import no.sikt.clients.alma.HttpUrlConnectionAlmaUserUpserter;
import no.sikt.clients.basebibliotek.HttpUrlConnectionBaseBibliotekApi;
import no.sikt.commons.HandlerUtils;
import no.sikt.lum.reporting.AlmaReportBuilder;
import no.sikt.lum.reporting.ReportGenerator;
import no.sikt.lum.reporting.UserReportBuilder;
import no.sikt.lum.secret.AlmaKeysFetcher;
import no.sikt.lum.secret.SecretFetcher;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

public class LibraryUserManagementHandler implements RequestHandler<S3Event, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(LibraryUserManagementHandler.class);

    public static final String REPORT_BUCKET_ENVIRONMENT_NAME = "REPORT_BUCKET";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_REST_URL";
    public static final String HANDLER_NAME = "lum";
    private static final String EVENT = "event";
    private static final String SKIPPING_HANDLING_OF_REQUESTS =
        "No alma api keys found. Skipping handling of requests.";
    private static final String SUCCESSFUL_UPDATES_SENT_TO_ALMA = "{} successful updates sent to Alma";
    private static final String SUCCESSFULLY_OF_TOTAL =
        "{} users updated successfully for alma instance {}, of total {} users";

    private final transient S3Client s3Client;
    private final transient String reportS3BucketName;
    private final transient Gson gson = new Gson();

    private final transient BaseBibliotekApi baseBibliotekApi;
    private final transient AlmaUserUpserter almaUserUpserter;
    private final transient Map<String, String> almaApiKeyMap;
    private final transient Map<String, List<User>> usersPerAlmaInstanceMap = new ConcurrentHashMap<>();

    @JacocoGenerated
    @SuppressWarnings("unused")
    public LibraryUserManagementHandler() {
        this(S3Driver.defaultS3Client().build(),
             new Environment(),
             new AlmaKeysFetcher(SecretsManagerClient.builder()
                                     .region(Region.EU_WEST_1)
                                     .build())
        );
    }

    public LibraryUserManagementHandler(S3Client s3Client,
                                        Environment environment,
                                        SecretFetcher<Map<String,String>> almaKeysFetcher) {
        this.s3Client = s3Client;
        final URI almaUri = UriWrapper.fromUri(environment.readEnv(ALMA_API_HOST)).getUri();
        almaApiKeyMap = almaKeysFetcher.fetchSecret();
        this.almaUserUpserter = new HttpUrlConnectionAlmaUserUpserter(almaUri);
        final URI basebibliotekUri =
            UriWrapper.fromUri(environment.readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri();
        this.baseBibliotekApi = new HttpUrlConnectionBaseBibliotekApi(basebibliotekUri);
        this.reportS3BucketName = environment.readEnv(REPORT_BUCKET_ENVIRONMENT_NAME);
    }

    @Override
    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));
        if (almaApiKeyMap.isEmpty()) {
            logger.info(SKIPPING_HANDLING_OF_REQUESTS);
            return 0;
        }
        try {
            var bibNrFile = HandlerUtils.readFile(s3event, s3Client);
            logger.info("done collecting bibNrFile");
            var bibnrList = HandlerUtils.getBibNrList(bibNrFile);
            var reportStringBuilder = new StringBuilder();
            var baseBibliotekList =
                HandlerUtils.generateBasebibliotek(bibnrList, reportStringBuilder, baseBibliotekApi);
            List<ReportGenerator> reports = new ArrayList<>();
            final int counter = sendBaseBibliotekToAlma(reports, baseBibliotekList);
            reports.forEach(report -> reportStringBuilder.append(report.generateReport()));
            HandlerUtils.reportToS3Bucket(reportStringBuilder, s3event, s3Client, reportS3BucketName, HANDLER_NAME);
            logger.info(SUCCESSFUL_UPDATES_SENT_TO_ALMA, counter);
            logger.info(reportStringBuilder.toString());
            return counter;
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    public Map<String, List<User>> getUsers() {
        return usersPerAlmaInstanceMap;
    }

    private int sendBaseBibliotekToAlma(List<ReportGenerator> reports,
                                        List<BaseBibliotek> baseBibliotekList) {
        var userReportBuilder = new UserReportBuilder();
        var almaReportBuilder = new AlmaReportBuilder();

        var totalCounter = almaApiKeyMap.entrySet().stream()
            .mapToInt(entry -> {
                var almaCode = entry.getKey();
                var apiKey = entry.getValue();

                var users = generateUsers(baseBibliotekList, userReportBuilder, almaCode);
                var successCount = sendToAlmaAndCountSuccess(users, almaCode, apiKey, almaReportBuilder);

                usersPerAlmaInstanceMap.put(almaCode, users);

                return successCount;
            })
            .sum();

        reports.add(userReportBuilder);
        reports.add(almaReportBuilder);

        return totalCounter;
    }

    private List<User> generateUsers(List<BaseBibliotek> baseBibliotekList,
                                     UserReportBuilder userReportBuilder,
                                     String targetAlmaCode) {
        var users = new ArrayList<User>();
        for (BaseBibliotek baseBibliotek : baseBibliotekList) {
            users.addAll(new UserConverter(baseBibliotek, targetAlmaCode).toUsers(userReportBuilder));
        }
        return users;
    }

    private int sendToAlmaAndCountSuccess(List<User> users,
                                          String almaId,
                                          String almaApikey,
                                          AlmaReportBuilder almaReportBuilder) {

        var successes = users.parallelStream()
                            .mapToInt(user -> {
                                var primaryId = user.getPrimaryId();
                                if (sendToAlma(user, almaApikey)) {
                                    almaReportBuilder.addSuccess(primaryId);
                                    return 1;
                                } else {
                                    almaReportBuilder.addFailure(primaryId, almaId);
                                    return 0;
                                }
                            })
                            .sum();

        logger.info(SUCCESSFULLY_OF_TOTAL, successes, almaId, users.size());

        return successes;
    }

    private boolean sendToAlma(User user, String almaApikey) {
        return almaUserUpserter.upsertUser(user, almaApikey);
    }

    private RuntimeException logErrorAndThrowException(Exception exception) {
        logger.error(exception.getMessage());
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }

}
