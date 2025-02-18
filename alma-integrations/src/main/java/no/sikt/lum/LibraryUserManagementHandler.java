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
import no.sikt.rsp.AlmaCodeProvider;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class LibraryUserManagementHandler implements RequestHandler<S3Event, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(LibraryUserManagementHandler.class);

    public static final String SHARED_CONFIG_BUCKET_NAME_ENV_NAME = "SHARED_CONFIG_BUCKET";
    public static final String REPORT_BUCKET_ENVIRONMENT_NAME = "REPORT_BUCKET";
    public static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY =
        "LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_REST_URL";
    public static final String HANDLER_NAME = "lum";
    private static final String EVENT = "event";

    private final transient S3Client s3Client;
    private final transient Environment environment;
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
        this.environment = environment;
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
        String sharedConfigBucketName = environment.readEnv(SHARED_CONFIG_BUCKET_NAME_ENV_NAME);
        S3Driver driver = new S3Driver(s3Client, sharedConfigBucketName);
        String libCodeToAlmaCodeMappingFilePath = environment.readEnv(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY);
        logger.info("done setting up drivers and reading environment");
        logger.info(sharedConfigBucketName);
        try {
            var bibNrFile = HandlerUtils.readFile(s3event, s3Client);
            logger.info("done collecting bibNrFile");
            final String instRegAsJson = driver.getFile(UnixPath.of(libCodeToAlmaCodeMappingFilePath));
            logger.info("done collecting instreg");
            AlmaCodeProvider almaCodeProvider = new AlmaCodeProvider(instRegAsJson);
            var bibnrList = HandlerUtils.getBibNrList(bibNrFile);
            var reportStringBuilder = new StringBuilder();
            var baseBibliotekList =
                HandlerUtils.generateBasebibliotek(bibnrList, reportStringBuilder, baseBibliotekApi);
            List<ReportGenerator> reports = new ArrayList<>();
            final int counter = sendBaseBibliotekToAlma(almaCodeProvider,
                                                        reports,
                                                        baseBibliotekList);
            reports.forEach(report -> reportStringBuilder.append(report.generateReport()));
            HandlerUtils.reportToS3Bucket(reportStringBuilder, s3event, s3Client, reportS3BucketName, HANDLER_NAME);
            return counter;
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    public Map<String, List<User>> getUsers() {
        return usersPerAlmaInstanceMap;
    }

    private int sendBaseBibliotekToAlma(AlmaCodeProvider almaCodeProvider,
                                        List<ReportGenerator> reports,
                                        List<BaseBibliotek> baseBibliotekList) {
        int counter = 0;
        var userReportBuilder = new UserReportBuilder();
        var almaReportBuilder = new AlmaReportBuilder();

        for (String almaCode : almaApiKeyMap.keySet()) {
            List<User> users = generateUsers(almaCodeProvider,
                                             baseBibliotekList,
                                             userReportBuilder,
                                             almaCode);
            counter += sendToAlmaAndCountSuccess(users,
                                                 almaCode,
                                                 almaApiKeyMap.get(almaCode),
                                                 almaReportBuilder);
            usersPerAlmaInstanceMap.put(almaCode, users);
        }

        reports.add(userReportBuilder);
        reports.add(almaReportBuilder);

        return counter;
    }

    private List<User> generateUsers(AlmaCodeProvider almaCodeProvider,
                                     List<BaseBibliotek> baseBibliotekList,
                                     UserReportBuilder userReportBuilder,
                                     String targetAlmaCode) {
        var users = new ArrayList<User>();
        for (BaseBibliotek baseBibliotek : baseBibliotekList) {
            users.addAll(
                new UserConverter(almaCodeProvider, baseBibliotek, targetAlmaCode).toUsers(userReportBuilder));
        }
        return users;
    }

    private int sendToAlmaAndCountSuccess(List<User> users,
                                          String almaId,
                                          String almaApikey,
                                          AlmaReportBuilder almaReportBuilder) {
        var counter = 0;
        for (User user : users) {
            var primaryId = user.getPrimaryId();
            if (sendToAlma(user, almaApikey)) {
                counter++;
                almaReportBuilder.addSuccess(primaryId);
            } else {
                almaReportBuilder.addFailure(primaryId, almaId);
            }
        }
        return counter;
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
