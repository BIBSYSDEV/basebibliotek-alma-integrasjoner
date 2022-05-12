package no.sikt;

import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import no.unit.nva.language.tooling.JacocoGenerated;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class BasebibliotekFetchHandler implements RequestHandler<ScheduledEvent, String> {

    private static final Logger logger = LoggerFactory.getLogger(BasebibliotekFetchHandler.class);
    public static final String FILENAME_REGEX = ".*?\">(bb-.*?.xml)</a>.*";

    private final S3Client s3Client;
    private final HttpClient httpClient;

    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_URL";
    public static final String BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME = "BASEBIBLIOTEK_USERNAME";
    public static final String BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME = "BASEBIBLIOTEK_PASSWORD";
    public static final String S3_BUCKET_ENVIRONMENT_NAME = "BASEBIBLIOTEK_XML_BUCKET";
    public final String basebibliotekUri;
    public final String basebibliotekUsername;
    public final String basebibliotekPassword;
    public final String s3BasebibliotekXmlBucket;
    private static final String IO_EXCEPTION_MESSAGE = "Could not contact basebibliotek";

    private final String BASEBIBLIOTEK_RESPONSE_STATUS_ERROR = "could not connect to basebibliotek, Connection "
                                                               + "responded with status: ";

    private static final String IMPORT_ALL_LIBRARIES = "bb-full.xml";

    private final String basebibliotekAuthorization;

    @JacocoGenerated
    public BasebibliotekFetchHandler() {
        this(S3Driver.defaultS3Client().build(),
             HttpClient.newBuilder().build(), new Environment());
    }

    public BasebibliotekFetchHandler(S3Client s3Client, HttpClient httpClient, Environment environment) {
        this.s3Client = s3Client;
        this.httpClient = httpClient;
        this.basebibliotekUri = environment.readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME);
        this.basebibliotekUsername = environment.readEnv(BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME);
        this.basebibliotekPassword = environment.readEnv(BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME);
        this.s3BasebibliotekXmlBucket = environment.readEnv(S3_BUCKET_ENVIRONMENT_NAME);
        this.basebibliotekAuthorization = createAuthorization();
    }

    private String createAuthorization() {
        String loginPassword = basebibliotekUsername + ":" + basebibliotekPassword;
        return String.format("Basic %s", Base64.getEncoder().encodeToString(loginPassword.getBytes()));
    }

    @Override
    public String handleRequest(ScheduledEvent scheduledEvent, Context context) {
        try {
            var request = createRequest(UriWrapper.fromUri(basebibliotekUri).getUri());
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                logger.info(BASEBIBLIOTEK_RESPONSE_STATUS_ERROR + response.statusCode());
                throw new RuntimeException();
            } else {
                var filenames = snipIncrementalBasebibliotekUrls(response.body());
                var basebibliotekXmls = fetchBasebibliotekXmls(filenames);

                putObjectstoS3(basebibliotekXmls);
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            logger.info(IO_EXCEPTION_MESSAGE, e);
            throw new RuntimeException(e);
        }
    }

    private void putObjectstoS3(List<BasebibliotekXmlAndFileName> basebibliotekXmlAndFileNames) {
        basebibliotekXmlAndFileNames.forEach(this::putObjectToS3);
    }

    private void putObjectToS3(BasebibliotekXmlAndFileName basebibliotekXmlAndFileName) {
        attempt(() -> s3Client.putObject(createPutObjectRequest(basebibliotekXmlAndFileName.getFileName()),
                                         RequestBody.fromString(basebibliotekXmlAndFileName.getBasebibliotekXmlBody())))
            .orElseThrow(fail -> logS3ExpectionAndThrowRuntimeError(fail.getException()))
        ;
    }

    private RuntimeException logS3ExpectionAndThrowRuntimeError(Exception exception) {
        logger.info("Could not upload file to s3");
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }

    private PutObjectRequest createPutObjectRequest(String filename) {
        return PutObjectRequest.builder()
                   .bucket(s3BasebibliotekXmlBucket)
                   .key(filename)
                   .build();
    }

    private List<BasebibliotekXmlAndFileName> fetchBasebibliotekXmls(List<String> filenames) {
        return filenames.stream()
                   .map(this::fetchBasebibliotekXml)
                   .flatMap(Optional::stream)
                   .collect(Collectors.toList());
    }

    private Optional<BasebibliotekXmlAndFileName> fetchBasebibliotekXml(String filename) {
        try {
            var request = createRequest(UriWrapper.fromUri(basebibliotekUri + "/" + filename).getUri());
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                logger.info(BASEBIBLIOTEK_RESPONSE_STATUS_ERROR + response.statusCode());
                return Optional.empty();
            } else {
                return Optional.of(new BasebibliotekXmlAndFileName(filename, response.body()));
            }
        } catch (IOException | InterruptedException e) {
            logger.info(IO_EXCEPTION_MESSAGE, e);
            throw new RuntimeException(e);
        }
    }

    private List<String> snipIncrementalBasebibliotekUrls(String body) {
        Pattern p = Pattern.compile(FILENAME_REGEX);
        return p.matcher(body)
                   .results()
                   .map(matcher -> matcher.group(1))
                   .filter(filename -> !isFullBasebibliotekFileName(filename))
                   .collect(Collectors.toList());
    }

    private boolean isFullBasebibliotekFileName(String filename) {
        return IMPORT_ALL_LIBRARIES.equalsIgnoreCase(filename);
    }

    private HttpRequest createRequest(URI uri) {
        return HttpRequest.newBuilder()
                   .uri(uri)
                   .setHeader("Authorization", basebibliotekAuthorization)
                   .GET()
                   .build();
    }

    /* default */class BasebibliotekXmlAndFileName {

        private final String fileName;
        private final String basebibliotekXmlBody;

        public BasebibliotekXmlAndFileName(String fileName, String basebibliotekXmlBody) {
            this.fileName = fileName;
            this.basebibliotekXmlBody = basebibliotekXmlBody;
        }

        public String getFileName() {
            return fileName;
        }

        public String getBasebibliotekXmlBody() {
            return basebibliotekXmlBody;
        }
    }
}
