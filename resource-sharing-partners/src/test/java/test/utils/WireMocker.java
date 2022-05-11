package test.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import no.unit.nva.stubs.WiremockHttpClient;
import nva.commons.core.ioutils.IoUtils;

public class WireMocker {

    private static WireMockServer httpServer;
    public static HttpClient httpClient;
    private static URI serverUri;

    public static void startWiremockServer() {
        httpClient = WiremockHttpClient.create();
        httpServer = new WireMockServer(options().dynamicHttpsPort());
        httpServer.start();
        serverUri = URI.create(httpServer.baseUrl());
        mockAlmaGetResponse();
    }

    private static void mockAlmaGetResponse() {
        String responseBody = IoUtils.stringFromResources(Path.of(EMPTY_STRING, "rsp_0030100.json"));
        stubFor(get(urlPathEqualTo("/partners/NO-0030100")).willReturn(ok().withBody(responseBody)));
    }


}
