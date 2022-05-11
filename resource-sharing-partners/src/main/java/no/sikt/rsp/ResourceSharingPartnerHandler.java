package no.sikt.rsp;

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
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

public class ResourceSharingPartnerHandler implements RequestHandler<S3Event, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ResourceSharingPartnerHandler.class);
    public static final int SINGLE_EXPECTED_RECORD = 0;
    private static final String EVENT = "event";
    private final transient Gson gson = new Gson();

    public static final String S3_URI_TEMPLATE = "s3://%s/%s";

    private final Environment environment;
    private final S3Client s3Client;
    private final AlmaConnection almaConnection;

    private List<Partner> partners;

    @JacocoGenerated
    public ResourceSharingPartnerHandler() {
        this(new Environment(), S3Driver.defaultS3Client().build(), HttpClient.newHttpClient());
    }

    public ResourceSharingPartnerHandler(Environment environment, S3Client s3Client, HttpClient httpClient) {
        this.environment = environment;
        this.s3Client = s3Client;
        this.almaConnection = new AlmaConnection(environment.readEnv("ALMA_API_HOST"), httpClient);
    }

    @Override
    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));
        try {
            var file = readFile(s3event);
            var baseBibliotek = parseXmlFile(file);
            partners = PartnerConverter.convertBasebibliotekToPartners(baseBibliotek);
            int numberOfAlmaPartners = 0;
            partners.forEach(partner -> checkInAlma(numberOfAlmaPartners,  partner.getPartnerDetails().getCode()));
            return numberOfAlmaPartners;
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    private void checkInAlma(int numberOfAlmaPartners, String code) {
        try {
            HttpResponse<String> httpResponse = almaConnection
                .sendGet(code, environment.readEnv("ALMA_APIKEY"));
            if (httpResponse.statusCode() <= HttpURLConnection.HTTP_MULT_CHOICE) {
                numberOfAlmaPartners++;
            }
        } catch (IOException | InterruptedException e) {
            logger.error(e.getMessage());
        }
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