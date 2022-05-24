package no.sikt.rsp;

import static java.lang.Math.toIntExact;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.google.gson.Gson;
import jakarta.xml.bind.JAXB;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
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
    public static final String INST_REG_CONFIG_FILE_PATH = "/inst-reg.json";

    private final S3Client s3Client;
    private final AlmaConnection almaConnection;

    private List<Partner> partners;

    private final Environment environment;

    @JacocoGenerated
    public ResourceSharingPartnerHandler() {
        this(S3Driver.defaultS3Client().build(), new Environment(), HttpClient.newHttpClient());
    }

    public ResourceSharingPartnerHandler(S3Client s3Client, Environment environment, HttpClient httpClient) {
        this.s3Client = s3Client;
        this.environment = environment;
        this.almaConnection = new AlmaConnection(httpClient,
                UriWrapper.fromUri(environment.readEnv(ALMA_API_HOST)).getUri());
    }

    @Override
    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));

        String illServer = environment.readEnv(ILL_SERVER_ENV_NAME);
        String sharedConfigBucketName = environment.readEnv(SHARED_CONFIG_BUCKET_NAME_ENV_NAME);
        S3Driver driver = new S3Driver(s3Client, sharedConfigBucketName);

        try {
            var file = readFile(s3event);
            var baseibliotek = parseXmlFile(file);
            final String instRegAsJson = driver.getFile(UnixPath.of(INST_REG_CONFIG_FILE_PATH));
            AlmaCodeProvider almaCodeProvider = new AlmaCodeProvider(instRegAsJson);
            PartnerConverter partnerConverter = new PartnerConverter(almaCodeProvider, illServer, baseibliotek);
            partners = partnerConverter.toPartners();
            return toIntExact(partners.stream()
                                  .filter(this::sendToAlma)
                                  .count());
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    private boolean sendToAlma(Partner partner) {
        try {
            HttpResponse<String> httpResponse = almaConnection.sendGet(partner.getPartnerDetails().getCode());
            logger.info(String.format("Read partner %s successfully.", partner.getPartnerDetails().getCode()));
            if (httpResponse.statusCode() <= HttpURLConnection.HTTP_MULT_CHOICE) {
                almaConnection.sendPut(partner);
                logger.info(String.format("Updated partner %s successfully.", partner.getPartnerDetails().getCode()));
            } else {
                almaConnection.sendPost(partner);
                logger.info(String.format("Created partner %s successfully.", partner.getPartnerDetails().getCode()));
            }
        } catch (IOException | InterruptedException e) {
            logger.error(e.getMessage());
            return false;
        }
        return true;
    }

    public List<Partner> getPartners() {
        return partners;
    }

    private BaseBibliotek parseXmlFile(String file) {
        return JAXB.unmarshal(new StringReader(file), BaseBibliotek.class);
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