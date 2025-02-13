package no.sikt.lum;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.sikt.alma.user.generated.User;
import no.sikt.clients.BaseBibliotekApi;
import no.sikt.clients.alma.AlmaUserUpserter;
import no.sikt.clients.alma.HttpUrlConnectionAlmaUserUpserter;
import no.sikt.clients.basebibliotek.HttpUrlConnectionBaseBibliotekApi;
import no.sikt.commons.HandlerUtils;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

public class LibraryUserManagementHandler implements RequestHandler<S3Event, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(LibraryUserManagementHandler.class);

    public static final String REPORT_BUCKET_ENVIRONMENT_NAME = "REPORT_BUCKET";
    public static final String OK_REPORT_MESSAGE = "OK";
    public static final String FAILURE_WHEN_UPDATING_ALMA = "Failure when updating Alma";
    public static final String COULD_NOT_CONVERT_TO_USER_REPORT_MESSAGE = " could not convert to user\n";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_REST_URL";
    public static final String HANDLER_NAME = "lum";
    private static final String EVENT = "event";
    public static final String LINE_BREAK = "\n";
    public static final String TAB = "\t";
    public static final String ALMA_ID_TEXT = "Alma ID: ";

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
        this(S3Driver.defaultS3Client().build(), new Environment(), SecretsManagerClient.builder()
                                                                        .region(Region.EU_WEST_1)
                                                                        .build());
    }

    public LibraryUserManagementHandler(S3Client s3Client, Environment environment,
                                        SecretsManagerClient secretsManagerClient) {
        this.s3Client = s3Client;
        final String almaApiKeys = getAlmaApiKeyFromSecretsManager(secretsManagerClient);
        final URI almaUri = UriWrapper.fromUri(environment.readEnv(ALMA_API_HOST)).getUri();
        almaApiKeyMap = readAlmaApiKeys(almaApiKeys);
        this.almaUserUpserter = new HttpUrlConnectionAlmaUserUpserter(almaUri);
        final URI basebibliotekUri =
            UriWrapper.fromUri(environment.readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri();
        this.baseBibliotekApi = new HttpUrlConnectionBaseBibliotekApi(basebibliotekUri);
        this.reportS3BucketName = environment.readEnv(REPORT_BUCKET_ENVIRONMENT_NAME);
    }

    @Override
    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));
        try {
            var bibNrFile = HandlerUtils.readFile(s3event, s3Client);
            logger.info("done collecting bibNrFile");
            var bibnrList = HandlerUtils.getBibNrList(bibNrFile);
            var reportStringBuilder = new StringBuilder();
            var baseBibliotekList =
                HandlerUtils.generateBasebibliotek(bibnrList, reportStringBuilder, baseBibliotekApi);
            int counter = sendBaseBibliotekToAlma(reportStringBuilder, baseBibliotekList);
            HandlerUtils.reportToS3Bucket(reportStringBuilder, s3event, s3Client, reportS3BucketName, HANDLER_NAME);
            return counter;
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    public Map<String, List<User>> getUsers() {
        return usersPerAlmaInstanceMap;
    }

    private static String getAlmaApiKeyFromSecretsManager(SecretsManagerClient secretsManagerClient) {

        String secretName = "alma_api_keys";

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                                                          .secretId(secretName)
                                                          .build();
        try {
            var getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            // Decrypts secret using the associated KMS key.
            // Depending on whether the secret is a string or binary, one of these fields will be populated.
            return Objects.nonNull(getSecretValueResponse.secretString())
                       ?
                       getSecretValueResponse.secretString()
                       : new String(
                           Base64.getDecoder().decode(getSecretValueResponse.secretBinary().asByteBuffer()).array());
        } catch (SecretsManagerException e) {
            throw new RuntimeException(e);
        }
    }

    private int sendBaseBibliotekToAlma(StringBuilder reportStringBuilder,
                                        List<BaseBibliotek> baseBibliotekList) {
        int counter = 0;
        for (String almaCode : almaApiKeyMap.keySet()) {
            List<User> users = generateUsers(baseBibliotekList, reportStringBuilder, almaCode);
            counter += sendToAlmaAndCountSuccess(users,
                                                 almaCode,
                                                 almaApiKeyMap.get(almaCode),
                                                 reportStringBuilder);
            usersPerAlmaInstanceMap.put(almaCode, users);
        }
        return counter;
    }

    private List<User> generateUsers(List<BaseBibliotek> baseBibliotekList,
                                     StringBuilder reportStringBuilder,
                                     String targetAlmaCode) {
        var users = new ArrayList<User>();
        for (BaseBibliotek baseBibliotek : baseBibliotekList) {
            users.addAll(new UserConverter(baseBibliotek, targetAlmaCode).toUsers(reportStringBuilder));
        }
        return users;
    }

    private int sendToAlmaAndCountSuccess(List<User> users,
                                          String almaId,
                                          String almaApikey,
                                          StringBuilder reportStringBuilder) {
        var counter = 0;
        for (User user : users) {
            var primaryId = user.getPrimaryId();
            if (sendToAlma(user, almaApikey)) {
                counter++;
                reportStringBuilder
                    .append(primaryId)
                    .append(TAB + TAB)
                    .append(OK_REPORT_MESSAGE)
                    .append(TAB + TAB)
                    .append(ALMA_ID_TEXT)
                    .append(almaId)
                    .append(LINE_BREAK);
            } else {
                reportStringBuilder
                    .append(primaryId)
                    .append(TAB + TAB)
                    .append(FAILURE_WHEN_UPDATING_ALMA)
                    .append(TAB + TAB)
                    .append(ALMA_ID_TEXT)
                    .append(almaId)
                    .append(LINE_BREAK);
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
                                                                   .readValue(almaApiKeys,
                                                                              AlmaCodeAlmaApiKeyPair[].class);
            return Arrays.stream(almaCodeAlmaApiKeyPairs).collect(Collectors.toMap(a -> a.almaCode, a -> a.almaApikey));
        } catch (JsonProcessingException e) {
            throw logErrorAndThrowException(e);
        }
    }

    private static final class AlmaCodeAlmaApiKeyPair {

        @JsonProperty("almaCode")
        private transient String almaCode;
        @JsonProperty("almaApiKey")
        private transient String almaApikey;
    }
}
