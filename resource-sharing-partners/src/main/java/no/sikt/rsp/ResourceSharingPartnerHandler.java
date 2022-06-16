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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
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
    private final transient S3Client s3Client;
    private final transient AlmaConnection connection;

    private final transient List<Partner> partners;

    private final transient Environment environment;
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_REST_URL";

    private final transient BasebibliotekConnection basebibliotekConnection;

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
                                                                   UriWrapper.fromUri(
                                                                           environment.readEnv(
                                                                               BASEBIBLIOTEK_URI_ENVIRONMENT_NAME))
                                                                       .getUri());
        this.partners = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));

        String illServer = environment.readEnv(ILL_SERVER_ENV_NAME);
        String sharedConfigBucketName = environment.readEnv(SHARED_CONFIG_BUCKET_NAME_ENV_NAME);
        S3Driver driver = new S3Driver(s3Client, sharedConfigBucketName);
        String libCodeToAlmaCodeMappingFilePath = environment.readEnv(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY);
        logger.info("done setting up drivers and reading environment");
        logger.info(sharedConfigBucketName);
        try {
            var bibNrFile = readFile(s3event);
            logger.info("done collecting bibNrFile");

            final String instRegAsJson = driver.getFile(UnixPath.of(libCodeToAlmaCodeMappingFilePath));
            logger.info("done collecting instreg");
            AlmaCodeProvider almaCodeProvider = new AlmaCodeProvider(instRegAsJson);

            var basebiblioteks = new ArrayList<BaseBibliotek>();
            var bibnrList = getBibNrList(bibNrFile);
            for (String bibnr : bibnrList) {
                try {
                    basebiblioteks.add(basebibliotekConnection.getBasebibliotek(bibnr));
                } catch (Exception e) {
                    logger.info("Could not fetch basebibliotek", e);
                }
            }
            for (BaseBibliotek baseBibliotek : basebiblioteks) {
                try {
                    partners.addAll(new PartnerConverter(almaCodeProvider, illServer, baseBibliotek).toPartners());
                } catch (Exception e) {
                    logger.info("Could not convert to partner", e);
                }
            }
            var counter = 0;
            for (Partner partner : partners) {
                try {
                    if (sendToAlma(partner)) {
                        counter++;
                    }
                } catch (Exception e) {
                    logger.info("Could not contact Alma", e);
                }
            }

            return counter;
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