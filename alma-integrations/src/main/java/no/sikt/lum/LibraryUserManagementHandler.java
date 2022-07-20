package no.sikt.lum;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import no.sikt.alma.user.generated.User;
import no.sikt.clients.BaseBibliotekApi;
import no.sikt.clients.basebibliotek.HttpUrlConnectionBaseBibliotekApi;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import com.google.gson.Gson;

public class LibraryUserManagementHandler implements RequestHandler<S3Event, Integer> {

    public static final String SHARED_CONFIG_BUCKET_NAME_ENV_NAME = "SHARED_CONFIG_BUCKET";
    public static final String REPORT_BUCKET_ENVIRONMENT_NAME = "REPORT_BUCKET";
    public static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY =
        "LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH";
    private static final Logger logger = LoggerFactory.getLogger(LibraryUserManagementHandler.class);
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_REST_URL";
    private static final String ALMA_API_KEY_ENV_KEY = "ALMA_APIKEY";
    private static final String EVENT = "event";
    private final transient S3Client s3Client;
    private final transient Environment environment;
    private final transient String reportS3BucketName;
    private final transient Gson gson = new Gson();

    private final transient List<User> users;
    private final transient BaseBibliotekApi baseBibliotekApi;

    @JacocoGenerated
    public LibraryUserManagementHandler() {
        this(S3Driver.defaultS3Client().build(), new Environment());
    }

    public LibraryUserManagementHandler(S3Client s3Client, Environment environment) {
        this.s3Client = s3Client;
        this.environment = environment;

        final String almaApiKey = environment.readEnv(ALMA_API_KEY_ENV_KEY);

        final URI basebibliotekUri =
            UriWrapper.fromUri(environment.readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri();
        this.baseBibliotekApi = new HttpUrlConnectionBaseBibliotekApi(basebibliotekUri);
        this.users = new ArrayList<>();
        this.reportS3BucketName = environment.readEnv(REPORT_BUCKET_ENVIRONMENT_NAME);
    }

    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));
        String sharedConfigBucketName = environment.readEnv(SHARED_CONFIG_BUCKET_NAME_ENV_NAME);
//        S3Driver driver = new S3Driver(s3Client, sharedConfigBucketName);
//        String libCodeToAlmaCodeMappingFilePath = environment.readEnv(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY);
        logger.info("done setting up drivers and reading environment");
        logger.info(sharedConfigBucketName);

        return 1;
    }
}
