package no.sikt.rsp;

import static java.lang.Math.toIntExact;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import no.sikt.alma.generated.Partner;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

public class ResourceSharingPartnerHandler implements RequestHandler<S3Event, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ResourceSharingPartnerHandler.class);
    public static final int SINGLE_EXPECTED_RECORD = 0;
    private static final String EVENT = "event";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    private final transient Gson gson = new Gson();

    public static final String S3_URI_TEMPLATE = "s3://%s/%s";
    public static final String ILL_SERVER_ENV_NAME = "ILL_SERVER";
    public static final String SHARED_CONFIG_BUCKET_NAME_ENV_NAME = "SHARED_CONFIG_BUCKET";
    public static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY =
        "LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH";
    private final S3Client s3Client;
    private final AlmaConnection connection;

    private List<Partner> partners;

    private final Environment environment;
    public static final String BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME = "BASEBIBLIOTEK_USERNAME";
    public static final String BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME = "BASEBIBLIOTEK_PASSWORD";
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_URL";

    private final BasebibliotekConnection basebibliotekConnection;

    @JacocoGenerated
    public ResourceSharingPartnerHandler() {
        this(S3Driver.defaultS3Client().build(), new Environment(), HttpClient.newHttpClient());
    }

    public ResourceSharingPartnerHandler(S3Client s3Client, Environment environment, HttpClient httpClient) {
        this.s3Client = s3Client;
        this.environment = environment;
        this.connection = new AlmaConnection(httpClient,
                                             UriWrapper.fromUri(environment.readEnv(ALMA_API_HOST)).getUri());
        this.basebibliotekConnection = new BasebibliotekConnection(httpClient,
                                                                   UriWrapper.fromUri(environment.readEnv(
                                                                       BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri(),
                                                                   environment.readEnv(
                                                                       BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME),
                                                                   environment.readEnv(
                                                                       BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME));
    }

    @Override
    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));

        String illServer = environment.readEnv(ILL_SERVER_ENV_NAME);
        String sharedConfigBucketName = environment.readEnv(SHARED_CONFIG_BUCKET_NAME_ENV_NAME);
        S3Driver driver = new S3Driver(s3Client, sharedConfigBucketName);
        String libCodeToAlmaCodeMappingFilePath = environment.readEnv(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY);

        try {
            var bibNrFile = readFile(s3event);

            final String instRegAsJson = driver.getFile(UnixPath.of(libCodeToAlmaCodeMappingFilePath));
            AlmaCodeProvider almaCodeProvider = new AlmaCodeProvider(instRegAsJson);

            partners =
                getBibNrList(bibNrFile).stream()
                    .map(basebibliotekConnection::getBasebibliotek)
                    .map(baseBibliotek -> new PartnerConverter(almaCodeProvider, illServer, baseBibliotek).toPartners())
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            return toIntExact(partners.stream()
                                  .filter(this::sendToAlma)
                                  .count());
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    private List<String> getBibNrList(String bibNrFile) {
        return Arrays.stream(bibNrFile.split("\n")).map(String::trim).collect(Collectors.toList());
    }

    private boolean sendToAlma(Partner partner) {
        try {
            HttpResponse<String> httpResponse = connection.sendGet(partner.getPartnerDetails().getCode());
            logger.info(String.format("Read partner %s successfully.", partner.getPartnerDetails().getCode()));
            if (httpResponse.statusCode() <= HttpURLConnection.HTTP_MULT_CHOICE) {
                connection.sendPut(partner);
                logger.info(String.format("Updated partner %s successfully.", partner.getPartnerDetails().getCode()));
            } else {
                connection.sendPost(partner);
                logger.info(String.format("Created partner %s successfully.", partner.getPartnerDetails().getCode()));
            }
        } catch (IOException | InterruptedException e) {
            throw logErrorAndThrowException(e);
        }
        return true;
    }

    public List<Partner> getPartners() {
        return partners;
    }

    private RuntimeException logErrorAndThrowException(Exception exception) {
        logger.error(exception.getMessage());
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }

    private String readFile(S3Event event) {
        var s3Driver = new S3Driver(s3Client, extractBucketName(event));
        var fileUri = createS3BucketUri(event);
        return s3Driver.getFile(UriWrapper.fromUri(fileUri).toS3bucketPath());
    }

    private String extractBucketName(S3Event event) {
        return event.getRecords().get(SINGLE_EXPECTED_RECORD).getS3().getBucket().getName();
    }

    private URI createS3BucketUri(S3Event s3Event) {
        return URI.create(String.format(S3_URI_TEMPLATE, extractBucketName(s3Event), extractFilename(s3Event)));
    }

    private String extractFilename(S3Event event) {
        return event.getRecords().get(SINGLE_EXPECTED_RECORD).getS3().getObject().getKey();
    }
}