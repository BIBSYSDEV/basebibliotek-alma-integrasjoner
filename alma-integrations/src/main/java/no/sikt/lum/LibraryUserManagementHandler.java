package no.sikt.lum;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    public static final String ALMA_API_KEYS_ENV_KEY = "ALMA_API_KEYS";
    private static final String EVENT = "event";
    public static final String HANDLER_NAME = "lum";
    private final transient S3Client s3Client;
    private final transient Environment environment;
    private final transient String reportS3BucketName;
    private final transient Gson gson = new Gson();

    private final transient BaseBibliotekApi baseBibliotekApi;
    private final transient AlmaUserUpserter almaUserUpserter;
    private final transient Map<String, String> almaApiKeyMap;

    @JacocoGenerated
    public LibraryUserManagementHandler() {
        this(S3Driver.defaultS3Client().build(), new Environment());
    }

    public LibraryUserManagementHandler(S3Client s3Client, Environment environment) {
        this.s3Client = s3Client;
        this.environment = environment;
        final String almaApiKeys = environment.readEnv(ALMA_API_KEYS_ENV_KEY);
        final URI almaUri = UriWrapper.fromUri(environment.readEnv(ALMA_API_HOST)).getUri();
        almaApiKeyMap = readAlmaApiKeys(almaApiKeys);
        this.almaUserUpserter = new HttpUrlConnectionAlmaUserUpserter(almaApiKeyMap, almaUri);
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
            int counter = sendBaseBibliotekToAlma(almaCodeProvider, reportStringBuilder, baseBibliotekList);
            HandlerUtils.reportToS3Bucket(reportStringBuilder, s3event, s3Client, reportS3BucketName, HANDLER_NAME);
            return counter;
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    private int sendBaseBibliotekToAlma(AlmaCodeProvider almaCodeProvider, StringBuilder reportStringBuilder,
                                        List<BaseBibliotek> baseBibliotekList) {
        int counter = 0;
        for (String almaCode : almaApiKeyMap.keySet()) {
            counter += sendToAlmaAndCountSuccess(
                generateUsers(almaCodeProvider, baseBibliotekList, reportStringBuilder, almaCode),
                almaApiKeyMap.get(almaCode), reportStringBuilder);
        }
        return counter;
    }

    private List<User> generateUsers(AlmaCodeProvider almaCodeProvider, List<BaseBibliotek> baseBibliotekList,
                                     StringBuilder reportStringBuilder,
                                     String targetAlmaCode) {
        var users = new ArrayList<User>();
        for (BaseBibliotek baseBibliotek : baseBibliotekList) {
            users.addAll(new UserConverter(almaCodeProvider, baseBibliotek, targetAlmaCode).toUsers(reportStringBuilder));
        }
        return users;
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    private int sendToAlmaAndCountSuccess(List<User> users, String almaApikey,
                                          StringBuilder reportStringBuilder) {
        var counter = 0;
        for (User user : users) {
            var primaryId = user.getPrimaryId();
            if (sendToAlma(user, almaApikey)) {
                counter++;
                reportStringBuilder
                    .append(primaryId)
                    .append(StringUtils.SPACE)
                    .append(almaApikey)
                    .append(StringUtils.SPACE)
                    .append(OK_REPORT_MESSAGE);
            } else {
                reportStringBuilder
                    .append(primaryId)
                    .append(COULD_NOT_CONTACT_ALMA_REPORT_MESSAGE)
                    .append(StringUtils.SPACE)
                    .append(almaApikey);
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

    private Map<String, String> readAlmaApiKeys(String almaApiKeys) {
        try {
            AlmaCodeAlmaApiKeyPair[] almaCodeAlmaApiKeyPairs = new ObjectMapper()
                .readValue(almaApiKeys, AlmaCodeAlmaApiKeyPair[].class);
            return Arrays.stream(almaCodeAlmaApiKeyPairs).collect(Collectors.toMap(a -> a.almaCode, a -> a.almaApikey));
        } catch (JsonProcessingException e) {
            throw logErrorAndThrowException(e);
        }
    }

    private static class AlmaCodeAlmaApiKeyPair {

        @JsonProperty("almaCode")
        private transient String almaCode;
        @JsonProperty("almaApiKey")
        private transient String almaApikey;
    }
}
