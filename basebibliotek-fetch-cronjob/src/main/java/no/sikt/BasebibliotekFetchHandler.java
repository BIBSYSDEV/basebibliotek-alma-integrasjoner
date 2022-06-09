package no.sikt;

import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import jakarta.xml.bind.JAXB;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.unit.nva.language.tooling.JacocoGenerated;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class BasebibliotekFetchHandler implements RequestHandler<ScheduledEvent, Set<String>> {

    private static final Logger logger = LoggerFactory.getLogger(BasebibliotekFetchHandler.class);
    private static final String FILENAME_REGEX = ".*?\">(bb-.*?.xml)</a>.*";
    private static final String BASIC_AUTHORIZATION = "Basic %s";
    private static final String USERNAME_PASSWORD_DELIMITER = ":";
    private static final String COULD_NOT_UPLOAD_FILE_TO_S_3_ERROR_MESSAGE = "Could not upload file to s3";
    private static final String AUTHORIZATION = "Authorization";
    private static final String DD_MM_YYYY_PATTERN = "dd-MM-yyyy";
    private static final String TXT = ".txt";
    private static final String COULD_NOT_GET_ERROR_MESSAGE = "could not GET ";

    private final transient S3Client s3Client;
    private final transient HttpClient httpClient;

    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_EXPORT_URL";
    public static final String BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME = "BASEBIBLIOTEK_USERNAME";
    public static final String BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME = "BASEBIBLIOTEK_PASSWORD";
    public static final String S3_BUCKET_ENVIRONMENT_NAME = "BASEBIBLIOTEK_XML_BUCKET";
    private final transient String basebibliotekUri;
    private final transient String basebibliotekUsername;
    private final transient String basebibliotekPassword;
    private final transient String s3BasebibliotekXmlBucket;

    private static final String BASEBIBLIOTEK_RESPONSE_ERROR =
        "could not connect to basebibliotek, Connection responded with status: ";

    private static final String IMPORT_ALL_LIBRARIES = "bb-full.xml";

    private final transient String basebibliotekAuthorization;

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
        String loginPassword = basebibliotekUsername + USERNAME_PASSWORD_DELIMITER + basebibliotekPassword;
        return String.format(BASIC_AUTHORIZATION, Base64.getEncoder().encodeToString(loginPassword.getBytes()));
    }

    @Override
    public Set<String> handleRequest(ScheduledEvent scheduledEvent, Context context) {
        return attempt(() -> getBasebibliotekData(UriWrapper.fromUri(basebibliotekUri).getUri()))
                   .map(this::getBodyFromResponse)
                   .map(this::snipIncrementalBasebibliotekUrls)
                   .map(this::fetchBasebibliotekXmls)
                   .map(this::convertXmls)
                   .map(this::collectBibnrFromBaseBibliotek)
                   .map(this::putObjectToS3)
                   .orElseThrow(
                       fail -> logExpectionAndThrowRuntimeError(fail.getException(), fail.getException().getMessage()));
    }

    private Set<String> collectBibnrFromBaseBibliotek(List<BaseBibliotek> baseBiblioteks) {
        return baseBiblioteks.stream()
                   .map(this::getBibNrFromRecords)
                   .flatMap(Collection::stream)
                   .collect(Collectors.toSet());
    }

    private Set<String> getBibNrFromRecords(BaseBibliotek baseBibliotek) {
        return baseBibliotek.getRecord().stream().map(
            this::getBibnrFromRecord).flatMap(Optional::stream).collect(Collectors.toSet());
    }

    private Optional<String> getBibnrFromRecord(Record record) {
        return Objects.nonNull(record.getBibnr()) ? Optional.of(record.getBibnr()) : logRecordWithMissingBibnr(record);
    }

    private Optional<String> logRecordWithMissingBibnr(Record record) {
        logger.info("Record with missing bibnr " + getRecordXmlAsString(record));
        return Optional.empty();
    }

    private String getRecordXmlAsString(Record record) {
        StringWriter xmlWriter = new StringWriter();
        JAXB.marshal(record, xmlWriter);
        return xmlWriter.toString();
    }

    private List<BaseBibliotek> convertXmls(List<String> basebibliotekXmlStrings) {
        return basebibliotekXmlStrings.stream()
                   .map(this::convertXmlToBaseBibliotek)
                   .collect(
                       Collectors.toList());
    }

    private BaseBibliotek convertXmlToBaseBibliotek(String basebibliotekXmlString) {
        return JAXB.unmarshal(new StringReader(basebibliotekXmlString), BaseBibliotek.class);
    }

    private Set<String> putObjectToS3(Set<String> bibNrs) {
        attempt(() -> s3Client.putObject(createPutObjectRequest(),
                                         RequestBody.fromString(craftBibnrString(bibNrs))))
            .orElseThrow(fail -> logExpectionAndThrowRuntimeError(fail.getException(),
                                                                  COULD_NOT_UPLOAD_FILE_TO_S_3_ERROR_MESSAGE));
        return bibNrs;
    }

    private String craftBibnrString(Set<String> bibNrs) {
        return String.join("\n", bibNrs);
    }

    private RuntimeException logExpectionAndThrowRuntimeError(Exception exception, String message) {
        logger.info(message);
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }

    private PutObjectRequest createPutObjectRequest() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat(DD_MM_YYYY_PATTERN, Locale.ROOT);
        String filename = formatter.format(date) + TXT;
        return PutObjectRequest.builder()
                   .bucket(s3BasebibliotekXmlBucket)
                   .key(filename)
                   .build();
    }

    private List<String> fetchBasebibliotekXmls(List<String> filenames) {
        return filenames.stream()
                   .map(this::getBasebibliotekXmlAsString)
                   .collect(Collectors.toList());
    }

    private String getBasebibliotekXmlAsString(String filename) {
        return attempt(
            () -> getBasebibliotekData(UriWrapper.fromUri(basebibliotekUri).addChild(filename).getUri()))
                   .map(this::getBodyFromResponse)
                   .orElseThrow(
                       fail -> logExpectionAndThrowRuntimeError(fail.getException(), COULD_NOT_GET_ERROR_MESSAGE
                                                                                     + filename));
    }

    private String getBodyFromResponse(HttpResponse<String> response) {
        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            logger.info(BASEBIBLIOTEK_RESPONSE_ERROR + response.statusCode());
            throw new RuntimeException();
        }
        return response.body();
    }

    private HttpResponse<String> getBasebibliotekData(URI uri) throws IOException, InterruptedException {
        var request = createRequest(uri);
        return httpClient.send(request, BodyHandlers.ofString());
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
                   .setHeader(AUTHORIZATION, basebibliotekAuthorization)
                   .GET()
                   .build();
    }
}
