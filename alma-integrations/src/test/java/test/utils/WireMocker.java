package test.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import com.github.tomakehurst.wiremock.http.Fault;
import java.nio.file.Path;
import nva.commons.core.ioutils.IoUtils;

public class WireMocker {

    public static final String URL_PATH_PARTNER = "/partners";
    public static final String URL_PATH_USERS = "/users";

    private static final String SLASH = "/";

    public static void mockAlmaGetResponse(final String almaCode) {
        String responseBody = IoUtils.stringFromResources(Path.of(EMPTY_STRING, "rsp_0030100.json"));
        stubFor(get(urlPathMatching(URL_PATH_PARTNER + SLASH + almaCode))
                    .willReturn(ok().withBody(responseBody)));
    }

    public static void mockAlmaGetResponsePartnerNotFound(final String almaCode) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of("almaPartnerNotFound.json"));
        stubFor(get(URL_PATH_PARTNER + "/" + almaCode).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaGetResponseUserNotFound(final String almaCode) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of("almaUserNotFound.json"));
        stubFor(get(URL_PATH_PARTNER + "/" + almaCode).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaGetResponseBadRequestNotPartnerNotFound(final String almaCode) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of("almaUserNotFoundError.json"));
        stubFor(get(URL_PATH_PARTNER + SLASH + almaCode).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaPutResponse(final String almaCode) {
        String responseBody = IoUtils.stringFromResources(Path.of("rsp_0030100.json"));
        stubFor(put(URL_PATH_PARTNER + SLASH + almaCode).willReturn(ok().withBody(responseBody)));
    }

    public static void mockAlmaPostResponse() {
        String almaPostResponseBody = "DUMMY";
        stubFor(post(URL_PATH_PARTNER).willReturn(ok().withBody(almaPostResponseBody)));
        stubFor(post(URL_PATH_USERS).willReturn(ok().withBody(almaPostResponseBody)));
    }

    public static void mockAlmaForbiddenGetResponse(String code) {
        stubFor(get(urlPathEqualTo(URL_PATH_PARTNER + SLASH + code))
                    .willReturn(forbidden()));
    }

    public static void mockAlmaForbiddenPostResponse(String code) {
        stubFor(post(urlPathEqualTo(URL_PATH_PARTNER + SLASH + code))
                    .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    }

    public static void mockAlmaPostResponseBadRequest() {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of("almaUserNotFoundError.json"));
        stubFor(post(URL_PATH_PARTNER).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaPutResponseBadRequest(String code) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of("almaUserNotFoundError.json"));
        stubFor(put(URL_PATH_PARTNER + SLASH + code).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockBasebibliotekXml(String basebibliotek, String bibNr) {
        stubFor(get(urlPathMatching("/basebibliotek/rest/bibnr/" + bibNr)).willReturn(ok().withBody(basebibliotek)));
    }

    public static void mockBassebibliotekFailure(String bibNr) {
        stubFor(get(urlPathMatching("/basebibliotek/rest/bibnr/" + bibNr)).willReturn(forbidden()));
    }

}
