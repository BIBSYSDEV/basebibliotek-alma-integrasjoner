package no.sikt.rsp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import no.sikt.alma.generated.Partner;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Connection {

    private static final String AUTHORIZATION_KEY = "Authorization";
    private static final String APIKEY_KEY = "apikey";
    private static final String SPACE_KEY = " ";
    public static final String CONTENT_TYPE_KEY = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";
    public static final String ALMA_APIKEY = "ALMA_APIKEY";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";

    private final HttpClient httpClient;
    private final URI almaApiHost;
    private final URI basebibliotekHost;
    public static final String BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME = "BASEBIBLIOTEK_USERNAME";
    public static final String BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME = "BASEBIBLIOTEK_PASSWORD";
    private static final String USERNAME_PASSWORD_DELIMITER = ":";
    private static final String BASIC_AUTHORIZATION = "Basic %s";
    private static final String AUTHORIZATION = "Authorization";
    private final String basebibliotekAuthorization;
    private static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_URL";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @JacocoGenerated
    public Connection() {
        this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build(),
             UriWrapper.fromUri(new Environment().readEnv(ALMA_API_HOST)).getUri(),
             UriWrapper.fromUri(new Environment().readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri(),
             new Environment().readEnv(BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME),
             new Environment().readEnv(BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME));
    }

    public Connection(HttpClient httpClient, URI almaApiHost, URI basebibliotekHost, String basebibliotekPassword,
                      String basebibliotekUsername) {
        this.httpClient = httpClient;
        this.almaApiHost = almaApiHost;
        this.basebibliotekHost = basebibliotekHost;
        this.basebibliotekAuthorization = createBasebibliotekAuthorization(basebibliotekPassword,
                                                                           basebibliotekUsername);
    }

    private String createBasebibliotekAuthorization(String basebibliotekPassword, String basebibliotekUsername) {
        String loginPassword = basebibliotekUsername + USERNAME_PASSWORD_DELIMITER + basebibliotekPassword;
        return String.format(BASIC_AUTHORIZATION, Base64.getEncoder().encodeToString(loginPassword.getBytes()));
    }

    /**
     * Sends a get request to the Alma api.
     *
     * @param code the code of the partner you want to retrieve
     * @return the http-response in the shape of a String
     * @throws IOException          When something goes wrong.
     * @throws InterruptedException When something goes wrong.
     */
    public HttpResponse<String> getAlmaPartner(String code)
        throws IOException, InterruptedException {
        String almaApikey = new Environment().readEnv(ALMA_APIKEY);
        HttpRequest request = HttpRequest.newBuilder()
                                  .GET()
                                  .uri(UriWrapper.fromUri(almaApiHost).addChild(code).getUri())
                                  .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + almaApikey)
                                  .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a put request to the Alma api.
     *
     * @param partner the partner to update
     * @return the Http-response in the form of a String
     * @throws IOException          When something goes wrong.
     * @throws InterruptedException When something goes wrong.
     */
    public HttpResponse<String> putAlmaPartner(Partner partner) throws IOException, InterruptedException {
        String almaApikey = new Environment().readEnv(ALMA_APIKEY);
        HttpRequest request = HttpRequest.newBuilder()
                                  .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(partner)))
                                  .uri(UriWrapper.fromUri(almaApiHost)
                                           .addChild(partner.getPartnerDetails().getCode())
                                           .getUri())
                                  .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + almaApikey)
                                  .header(CONTENT_TYPE_KEY, APPLICATION_JSON)
                                  .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a post request to the Alma api.
     *
     * @param partner the partner to create
     * @return the Http-response in the form of a String
     * @throws IOException          When something goes wrong.
     * @throws InterruptedException When something goes wrong.
     */
    public HttpResponse<String> postAlmaPartner(Partner partner) throws IOException, InterruptedException {
        String almaApikey = new Environment().readEnv(ALMA_APIKEY);
        HttpRequest request = HttpRequest.newBuilder()
                                  .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(partner)))
                                  .uri(UriWrapper.fromUri(almaApiHost)
                                           .addChild(partner.getPartnerDetails().getCode())
                                           .getUri())
                                  .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + almaApikey)
                                  .header(CONTENT_TYPE_KEY, APPLICATION_JSON)
                                  .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> getBasebibliotek(String bibNr) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                                  .GET()
                                  .uri(UriWrapper.fromUri(basebibliotekHost).addChild(bibNr).getUri())
                                  .setHeader(AUTHORIZATION, basebibliotekAuthorization)
                                  .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
