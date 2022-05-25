package no.sikt.rsp;

import static nva.commons.core.attempt.Try.attempt;
import jakarta.xml.bind.JAXB;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import no.nb.basebibliotek.generated.BaseBibliotek;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasebibliotekConnection {

    private static final String AUTHORIZATION = "Authorization";
    public static final String BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME = "BASEBIBLIOTEK_USERNAME";
    public static final String BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME = "BASEBIBLIOTEK_PASSWORD";
    private static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_URL";
    private final String BASEBIBLIOTEK_RESPONSE_STATUS_ERROR = "could not connect to basebibliotek, Connection "
                                                               + "responded with status: ";
    private static final String USERNAME_PASSWORD_DELIMITER = ":";
    private static final String BASIC_AUTHORIZATION = "Basic %s";
    private static final Logger logger = LoggerFactory.getLogger(BasebibliotekConnection.class);
    private final String basebibliotekAuthorization;
    private final URI basebibliotekHost;
    private final HttpClient httpClient;

    @JacocoGenerated
    public BasebibliotekConnection() {
        this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build(),
             UriWrapper.fromUri(new Environment().readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri(),
             new Environment().readEnv(BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME),
             new Environment().readEnv(BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME));
    }

    public BasebibliotekConnection(HttpClient httpClient, URI basebibliotekHost, String basebibliotekPassword,
                                   String basebibliotekUsername) {
        this.httpClient = httpClient;
        this.basebibliotekHost = basebibliotekHost;
        this.basebibliotekAuthorization = createBasebibliotekAuthorization(basebibliotekPassword,
                                                                           basebibliotekUsername);
    }

    private String createBasebibliotekAuthorization(String basebibliotekPassword, String basebibliotekUsername) {
        String loginPassword = basebibliotekUsername + USERNAME_PASSWORD_DELIMITER + basebibliotekPassword;
        return String.format(BASIC_AUTHORIZATION, Base64.getEncoder().encodeToString(loginPassword.getBytes()));
    }

    private String getBodyFromResponse(HttpResponse<String> response) {
        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            logger.info(BASEBIBLIOTEK_RESPONSE_STATUS_ERROR + response.statusCode());
            throw new RuntimeException();
        }
        return response.body();
    }

    private BaseBibliotek parseXmlToBasebibliotek(String basebibliotekXml) {
        return JAXB.unmarshal(new StringReader(basebibliotekXml), BaseBibliotek.class);
    }

    private HttpResponse<String> getBasebibliotekResponse(String bibNr) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                                  .GET()
                                  .uri(UriWrapper.fromUri(basebibliotekHost).addChild(bibNr).getUri())
                                  .setHeader(AUTHORIZATION, basebibliotekAuthorization)
                                  .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private RuntimeException logErrorAndThrowException(Exception exception) {
        logger.error(exception.getMessage());
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }

    public BaseBibliotek getBasebibliotek(String bibNr) {
        return attempt(() -> getBasebibliotekResponse(bibNr))
                   .map(this::getBodyFromResponse)
                   .map(this::parseXmlToBasebibliotek)
                   .orElseThrow(fail -> logErrorAndThrowException(fail.getException()));
    }
}
