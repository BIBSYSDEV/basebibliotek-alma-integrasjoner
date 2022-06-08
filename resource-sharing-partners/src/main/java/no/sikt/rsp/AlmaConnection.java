package no.sikt.rsp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import no.sikt.alma.generated.Partner;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class AlmaConnection {

    private static final  String AUTHORIZATION_KEY = "Authorization";
    private static final  String APIKEY_KEY = "apikey";
    private static final  String SPACE_KEY = " ";
    public static final String CONTENT_TYPE_KEY = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";
    public static final String ALMA_APIKEY = "ALMA_APIKEY";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    public static final String PARTNERS_URL_PATH = "partners";

    private transient final HttpClient httpClient;
    private transient final URI almaApiHost;
    private transient final ObjectMapper objectMapper = new ObjectMapper();

    @JacocoGenerated
    public AlmaConnection() {
        this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build(),
             UriWrapper.fromUri(new Environment().readEnv(ALMA_API_HOST)).getUri());
    }

    public AlmaConnection(HttpClient httpClient, URI almaApiHost) {
        this.httpClient = httpClient;
        this.almaApiHost = almaApiHost;
    }

    /**
     * Sends a get request to the Alma api.
     * @param code the code of the partner you want to retrieve
     * @return the http-response in the shape of a String
     * @throws IOException When something goes wrong.
     * @throws InterruptedException When something goes wrong.
     */
    public HttpResponse<String> sendGet(String code)
        throws IOException,  InterruptedException {
        String almaApikey = new Environment().readEnv(ALMA_APIKEY);
        HttpRequest request = HttpRequest.newBuilder()
                                  .GET()
                                  .uri(UriWrapper.fromUri(almaApiHost)
                                           .addChild(PARTNERS_URL_PATH)
                                           .addChild(code).getUri())
                                  .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + almaApikey)
                                  .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a put request to the Alma api.
     * @param partner the partner to update
     * @return the Http-response in the form of a String
     * @throws IOException When something goes wrong.
     * @throws InterruptedException When something goes wrong.
     */
    public HttpResponse<String> sendPut(Partner partner) throws IOException, InterruptedException {
        String almaApikey = new Environment().readEnv(ALMA_APIKEY);
        HttpRequest request = HttpRequest.newBuilder()
                                  .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(partner)))
                                  .uri(UriWrapper.fromUri(almaApiHost)
                                           .addChild(PARTNERS_URL_PATH)
                                           .addChild(partner.getPartnerDetails().getCode()).getUri())
                                  .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + almaApikey)
                                  .header(CONTENT_TYPE_KEY, APPLICATION_JSON)
                                  .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a post request to the Alma api.
     * @param partner the partner to create
     * @return the Http-response in the form of a String
     * @throws IOException When something goes wrong.
     * @throws InterruptedException When something goes wrong.
     */
    public HttpResponse<String> sendPost(Partner partner) throws IOException, InterruptedException {
        String almaApikey = new Environment().readEnv(ALMA_APIKEY);
        HttpRequest request = HttpRequest.newBuilder()
                                  .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(partner)))
                                  .uri(UriWrapper.fromUri(almaApiHost)
                                           .addChild(PARTNERS_URL_PATH)
                                           .addChild(partner.getPartnerDetails().getCode()).getUri())
                                  .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + almaApikey)
                                  .header(CONTENT_TYPE_KEY, APPLICATION_JSON)
                                  .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

}