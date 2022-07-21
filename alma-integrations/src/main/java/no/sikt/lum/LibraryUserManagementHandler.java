package no.sikt.lum;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.sikt.alma.user.generated.User;
import no.sikt.clients.BaseBibliotekApi;
import no.sikt.clients.alma.AlmaUserUpserter;
import no.sikt.clients.alma.HttpUrlConnectionAlmaUserUpserter;
import no.sikt.clients.basebibliotek.HttpUrlConnectionBaseBibliotekApi;
import no.sikt.commons.HandlerUtils;
import no.sikt.rsp.AlmaCodeProvider;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import com.google.gson.Gson;

public class LibraryUserManagementHandler implements RequestHandler<S3Event, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(LibraryUserManagementHandler.class);
    public static final String SHARED_CONFIG_BUCKET_NAME_ENV_NAME = "SHARED_CONFIG_BUCKET";
    public static final String REPORT_BUCKET_ENVIRONMENT_NAME = "REPORT_BUCKET";
    public static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY =
        "LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH";
    public static final String OK_REPORT_MESSAGE = "OK\n";
    public static final String COULD_NOT_CONTACT_ALMA_REPORT_MESSAGE = " could not contact Alma\n";
    public static final String COULD_NOT_CONVERT_TO_USER_ERROR_MESSAGE = " Could not convert to user";
    public static final String COULD_NOT_CONVERT_TO_USER_REPORT_MESSAGE = " could not convert to user\n";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_REST_URL";
    private static final String ALMA_API_KEY_ENV_KEY = "ALMA_APIKEY";
    private static final String EVENT = "event";
    public static final String HANDLER_NAME = "lum";
    private final transient S3Client s3Client;
    private final transient Environment environment;
    private final transient String reportS3BucketName;
    private final transient Gson gson = new Gson();

    private final transient BaseBibliotekApi baseBibliotekApi;
    private final transient AlmaUserUpserter almaUserUpserter;

    @JacocoGenerated
    public LibraryUserManagementHandler() {
        this(S3Driver.defaultS3Client().build(), new Environment());
    }

    public LibraryUserManagementHandler(S3Client s3Client, Environment environment) {
        this.s3Client = s3Client;
        this.environment = environment;

        final String almaApiKey = environment.readEnv(ALMA_API_KEY_ENV_KEY);
        final URI almaUri = UriWrapper.fromUri(environment.readEnv(ALMA_API_HOST)).getUri();
        this.almaUserUpserter = new HttpUrlConnectionAlmaUserUpserter(almaApiKey, almaUri);

        final URI basebibliotekUri =
            UriWrapper.fromUri(environment.readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri();
        this.baseBibliotekApi = new HttpUrlConnectionBaseBibliotekApi(basebibliotekUri);
        this.reportS3BucketName = environment.readEnv(REPORT_BUCKET_ENVIRONMENT_NAME);
    }

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
            int counter = sendUsersToAlma(almaCodeProvider, reportStringBuilder, baseBibliotekList);
            HandlerUtils.reportToS3Bucket(reportStringBuilder, s3event, s3Client, reportS3BucketName, HANDLER_NAME);
            return counter;
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    private int sendUsersToAlma(AlmaCodeProvider almaCodeProvider, StringBuilder reportStringBuilder,
                                List<BaseBibliotek> baseBibliotekList) {
        int counter = 0;
        for (String almaCode : almaCodeProvider.getAvailableAlmaCodes()) {
            counter += sendToAlmaAndCountSuccess(
                generateUsers(baseBibliotekList, reportStringBuilder, almaCode), reportStringBuilder);
        }
        return counter;
    }

    private List<User> generateUsers(List<BaseBibliotek> baseBibliotekList,
                                                     StringBuilder reportStringBuilder,
                                                     String targetAlmaCode) {
        var users = new ArrayList<User>();
        for (BaseBibliotek baseBibliotek : baseBibliotekList) {
            try {
                users.addAll(new UserConverter(baseBibliotek, targetAlmaCode).toUser());
            } catch (Exception e) {
                //Errors in individual libraries should not cause crash in entire execution.
                logger.info(COULD_NOT_CONVERT_TO_USER_ERROR_MESSAGE, e);
                reportStringBuilder
                    .append(baseBibliotek.getRecord().get(0).getBibnr())
                    .append(COULD_NOT_CONVERT_TO_USER_REPORT_MESSAGE);
            }
        }
        return users;
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    private int sendToAlmaAndCountSuccess(List<User> users, StringBuilder reportStringBuilder) {
        var counter = 0;
        for (User user : users) {
            var primaryId = user.getPrimaryId();
            if (sendToAlma(user)) {
                counter++;
                reportStringBuilder
                    .append(primaryId)
                    .append(StringUtils.SPACE)
                    .append(OK_REPORT_MESSAGE);
            } else {
                reportStringBuilder
                    .append(primaryId)
                    .append(COULD_NOT_CONTACT_ALMA_REPORT_MESSAGE);
            }
        }
        return counter;
    }

    private boolean sendToAlma(User user) {
        return almaUserUpserter.upsertUser(user);
    }

    private RuntimeException logErrorAndThrowException(Exception exception) {
        logger.error(exception.getMessage());
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }
}
