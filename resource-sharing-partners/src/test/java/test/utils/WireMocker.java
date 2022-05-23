package test.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.unit.nva.stubs.WiremockHttpClient;
import nva.commons.core.ioutils.IoUtils;

public class WireMocker {

    private static WireMockServer httpServer;
    public static HttpClient httpClient;
    public static URI serverUri;

    public static void startWiremockServer() {
        httpClient = WiremockHttpClient.create();
        httpServer = new WireMockServer(options().dynamicHttpsPort());
        httpServer.start();
        serverUri = URI.create(httpServer.baseUrl());
        mockAlmaGetResponse();
    }

    public static void stopWiremockServer() {
        httpServer.stop();
    }

    private static void mockAlmaGetResponse() {
        String responseBody = IoUtils.stringFromResources(Path.of(EMPTY_STRING, "rsp_0030100.json"));
        stubFor(get(urlPathMatching("/[A-Z]{2}-[0-9]{7}")).willReturn(ok().withBody(responseBody)));
    }

    public static void mockBasebibliotekXml(String basebibliotek, String bibNr) {
        stubFor(get(urlPathMatching("/bibliotek/eksport/biblev/" + bibNr)).willReturn(ok().withBody(basebibliotek)));
    }

    public static void mockBassebibliotekFailure(String bibNr) {
        stubFor(get(urlPathMatching("/bibliotek/eksport/biblev/" + bibNr)).willReturn(forbidden()));
    }


}
