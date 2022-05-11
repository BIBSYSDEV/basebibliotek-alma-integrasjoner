package no.sikt;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import no.unit.nva.language.tooling.JacocoGenerated;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nva.commons.core.Environment;
import software.amazon.awssdk.services.s3.S3Client;

public class BasebibliotekFetchHandler implements RequestHandler<ScheduledEvent, String> {

    private static final Logger logger = LoggerFactory.getLogger(BasebibliotekFetchHandler.class);

    private final S3Client s3Client;
    private final HttpClient httpClient;

    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_URL";
    public static final String BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME = "BASEBIBLIOTEK_USERNAME";
    public static final String BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME = "BASEBIBLIOTEK_PASSWORD";
    public static final String S3_BUCKET_ENVIRONMENT_NAME = "BASEBIBLIOTEK_XML_BUCKET";
    public final String BASEBIBLIOTEK_URI;
    public final String BASEBIBLIOTEK_USERNAME;
    public final String BASEBIBLIOTEK_PASSWORD;
    public final String S3_BASEBIBLIOTEK_XML_BUCKET;
    private final String IO_EXCEPTION_MESSAGE = "Could not contact basebibliotek";

    private final String BASEBIBLIOTEK_RESPONSE_STATUS_ERROR = "could not connect to basebibliotek, Connection "
                                                               + "responded with status: ";

    @JacocoGenerated
    public BasebibliotekFetchHandler() {
        this(S3Driver.defaultS3Client().build(),
             HttpClient.newBuilder().build(), new Environment());
    }

    public BasebibliotekFetchHandler(S3Client s3Client, HttpClient httpClient, Environment environment) {
        this.s3Client = s3Client;
        this.httpClient = httpClient;
        this.BASEBIBLIOTEK_URI = environment.readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME);
        this.BASEBIBLIOTEK_USERNAME = environment.readEnv(BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME);
        this.BASEBIBLIOTEK_PASSWORD = environment.readEnv(BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME);
        this.S3_BASEBIBLIOTEK_XML_BUCKET = environment.readEnv(S3_BUCKET_ENVIRONMENT_NAME);
    }

    @Override
    public String handleRequest(ScheduledEvent scheduledEvent, Context context) {
        try {
            var request = createRequest();
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                logger.info(BASEBIBLIOTEK_RESPONSE_STATUS_ERROR  + response.statusCode());
                throw new RuntimeException();

            }else {
                var url = snipIncrementalBasebibliotekUrls(response.body());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            logger.info(IO_EXCEPTION_MESSAGE, e);
            throw new RuntimeException(e);
        }
    }

    private List<String> snipIncrementalBasebibliotekUrls(String body) {

        return null;
    }

    private HttpRequest createRequest() {
        return HttpRequest.newBuilder()
                   .uri(UriWrapper.fromUri(BASEBIBLIOTEK_URI).getUri())
                   .GET()
                   .build();
    }
}
