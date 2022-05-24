package test.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import no.sikt.rsp.AlmaConnection;
import no.unit.nva.stubs.WiremockHttpClient;
import nva.commons.core.ioutils.IoUtils;

public class WireMocker {

    public static final String URL_PATH_PARTNER = "/" + AlmaConnection.PARTNERS_URL_PATH + "/";
    public static final String LIBCODE_ID_REGEX = "[A-Z]{2}-[0-9]{7}";
    private static WireMockServer httpServer;
    public static HttpClient httpClient;
    public static URI serverUri;

    public static void startWiremockServer() {
        httpClient = WiremockHttpClient.create();
        httpServer = new WireMockServer(options().dynamicHttpsPort());
        httpServer.start();
        serverUri = URI.create(httpServer.baseUrl());
    }

    public static void stopWiremockServer() {
        httpServer.stop();
    }

    public static void mockAlmaGetResponse() {
        String responseBody = IoUtils.stringFromResources(Path.of(EMPTY_STRING, "rsp_0030100.json"));
        stubFor(get(urlPathMatching(URL_PATH_PARTNER + LIBCODE_ID_REGEX))
                    .willReturn(ok().withBody(responseBody)));
    }

    public static void mockAlmaGetResponseNotFound() {
        stubFor(get(urlPathMatching(URL_PATH_PARTNER + LIBCODE_ID_REGEX))
                    .willReturn(notFound()));
    }

    public static void mockAlmaPutResponse() {
        String responseBody = IoUtils.stringFromResources(Path.of(EMPTY_STRING, "rsp_0030100.json"));
        stubFor(put(urlPathMatching(URL_PATH_PARTNER + LIBCODE_ID_REGEX))
                    .willReturn(ok().withBody(responseBody)));
    }

    public static void mockAlmaPostResponse() {
        String responseBody = IoUtils.stringFromResources(Path.of(EMPTY_STRING, "rsp_0030100.json"));
        stubFor(post(urlPathMatching(URL_PATH_PARTNER + LIBCODE_ID_REGEX))
                    .willReturn(ok().withBody(responseBody)));
    }


}
