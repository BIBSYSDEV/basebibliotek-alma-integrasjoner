package no.sikt.rsp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
//import javax.ws.rs.core.HttpHeaders;
//import javax.ws.rs.core.MediaType;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public final class AlmaConnection {

    private static final  String AUTHORIZATION_KEY = "Authorization";
    private static final  String APIKEY_KEY = "apikey";
    private static final  String SPACE_KEY = " ";

    private final HttpClient httpClient;
    private final String almaApiHost;

    @JacocoGenerated
    public AlmaConnection() {
        this(new Environment().readEnv("ALMA_API_HOST"), HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build());
    }

    public AlmaConnection(String almaApiHost, HttpClient httpClient){
        this.almaApiHost = almaApiHost;
        this.httpClient = httpClient;
    }

    /**
     * Sends a get request to the Alma api.
     * @param code the code of the partner you want to retrieve
     * @param alma_apikey the apiKey for Alma
     * @return the http-response in the shape of a String
     * @throws IOException When something goes wrong.
     * @throws InterruptedException When something goes wrong.
     */
    @JacocoGenerated
    public HttpResponse<String> sendGet(String code, String alma_apikey)
            throws IOException,  InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(almaApiHost + code))
                .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + alma_apikey)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response;
    }
//
//    /**
//     * Sends a put request to the Alma api.
//     * @param mmsId the mms_id of the bib-post you want to update
//     * @param xml the new xml that should replace the old bib-post
//     * @return the Http-response in the form of a String
//     * @throws IOException When something goes wrong.
//     * @throws InterruptedException When something goes wrong.
//     */
//    @JacocoGenerated
//    public HttpResponse<String> sendPut(String mmsId, String xml)
//            throws IOException, InterruptedException {
//        HttpRequest request = HttpRequest.newBuilder()
//                .PUT(HttpRequest.BodyPublishers.ofString(xml))
//                .uri(URI.create(almaApiHost + mmsId))
//                .setHeader(AUTHORIZATION_KEY, APIKEY_KEY + SPACE_KEY + config.secretKey) // add request header
//                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML)
//                .build();
//        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//        return response;
//    }

}
