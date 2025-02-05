package test.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Path;
import java.util.List;
import nva.commons.core.ioutils.IoUtils;

public class WireMocker {

    public static final String URL_PATH_PARTNER = "/partners";
    public static final String URL_PATH_USERS = "/users";
    public static final String URL_PATH_BASEBIBLIOTEK_REST_BIBNR = "/basebibliotek/rest/bibnr/";

    private static final String SLASH = "/";
    public static final String ALMA_USER_NOT_FOUND_ERROR_JSON = "almaUserNotFoundError.json";
    public static final String ALMA_PARTNER_NOT_FOUND_ERROR_JSON = "almaPartnerNotFoundError.json";
    public static final String ALMA_USER_NOT_FOUND_JSON = "almaUserNotFound.json";
    public static final String ALMA_PARTNER_NOT_FOUND_JSON = "almaPartnerNotFound.json";
    public static final String LUM_0030100_JSON = "lum_0030100.json";
    public static final String RSP_0030100_JSON = "rsp_0030100.json";
    public static final String DUMMY = "DUMMY";

    public static void mockAlmaGetResponse(final String almaCode) {
        String responseBodyPartner = IoUtils.stringFromResources(Path.of(EMPTY_STRING, RSP_0030100_JSON));
        stubFor(get(urlPathMatching(URL_PATH_PARTNER + SLASH + almaCode))
                    .willReturn(ok().withBody(responseBodyPartner)));
        String responseBodyUser = IoUtils.stringFromResources(Path.of(EMPTY_STRING, LUM_0030100_JSON));
        stubFor(get(urlPathMatching(URL_PATH_USERS + SLASH + almaCode))
                    .willReturn(ok().withBody(responseBodyUser)));
    }

    public static void mockAlmaGetResponsePartnerNotFound(final String libCode) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of(ALMA_PARTNER_NOT_FOUND_JSON));
        stubFor(get(URL_PATH_PARTNER + SLASH + libCode).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaGetResponseUserNotFound(final String libUserID) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of(ALMA_USER_NOT_FOUND_JSON));
        stubFor(get(URL_PATH_USERS + SLASH + libUserID).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaGetResponseBadRequestNotPartnerNotFound(final String almaCode) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of(ALMA_USER_NOT_FOUND_ERROR_JSON));
        stubFor(get(URL_PATH_PARTNER + SLASH + almaCode).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaGetResponseBadRequestNotUserNotFound(final String almaCode) {
        String almaGetResponseBody = IoUtils.stringFromResources(Path.of(ALMA_USER_NOT_FOUND_ERROR_JSON));
        stubFor(get(URL_PATH_USERS + SLASH + almaCode).willReturn(badRequest().withBody(almaGetResponseBody)));
    }

    public static void mockAlmaPutResponse(final String almaCode) {
        String responseBodyPartner = IoUtils.stringFromResources(Path.of(RSP_0030100_JSON));
        stubFor(put(URL_PATH_PARTNER + SLASH + almaCode).willReturn(ok().withBody(responseBodyPartner)));
        String responseBodyUser = IoUtils.stringFromResources(Path.of(LUM_0030100_JSON));
        stubFor(put(URL_PATH_USERS + SLASH + almaCode).willReturn(ok().withBody(responseBodyUser)));
    }

    public static void mockAlmaPostResponse() {
        String almaPostResponseBody = DUMMY;
        stubFor(post(URL_PATH_PARTNER).willReturn(ok().withBody(almaPostResponseBody)));
        stubFor(post(URL_PATH_USERS).willReturn(ok().withBody(almaPostResponseBody)));
    }

    public static void mockAlmaForbiddenGetResponse(String code) {
        stubFor(get(urlPathEqualTo(URL_PATH_PARTNER + SLASH + code))
                    .willReturn(forbidden()));
        stubFor(get(urlPathEqualTo(URL_PATH_USERS + SLASH + code))
                    .willReturn(forbidden()));
    }

    public static void mockAlmaForbiddenPostResponse(String code) {
        stubFor(post(urlPathEqualTo(URL_PATH_PARTNER + SLASH + code))
                    .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        stubFor(post(urlPathEqualTo(URL_PATH_USERS + SLASH + code))
                    .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    }

    public static void mockAlmaPostResponseBadRequest() {
        String almaGetResponseBodyPartner = IoUtils.stringFromResources(Path.of(ALMA_PARTNER_NOT_FOUND_ERROR_JSON));
        stubFor(post(URL_PATH_PARTNER).willReturn(badRequest().withBody(almaGetResponseBodyPartner)));
        String almaGetResponseBodyUser = IoUtils.stringFromResources(Path.of(ALMA_USER_NOT_FOUND_ERROR_JSON));
        stubFor(post(URL_PATH_USERS).willReturn(badRequest().withBody(almaGetResponseBodyUser)));
    }

    public static void mockAlmaPutResponseBadRequest(String code) {
        String almaGetResponseBodyPartner = IoUtils.stringFromResources(Path.of(ALMA_PARTNER_NOT_FOUND_ERROR_JSON));
        stubFor(put(URL_PATH_PARTNER + SLASH + code).willReturn(badRequest().withBody(almaGetResponseBodyPartner)));
        String almaGetResponseBodyUser = IoUtils.stringFromResources(Path.of(ALMA_USER_NOT_FOUND_ERROR_JSON));
        stubFor(put(URL_PATH_USERS + SLASH + code).willReturn(badRequest().withBody(almaGetResponseBodyUser)));
    }

    public static void mockBasebibliotekXml(String basebibliotek, String bibNr) {
        stubFor(
            get(urlPathMatching(URL_PATH_BASEBIBLIOTEK_REST_BIBNR + bibNr))
                .willReturn(ok().withBody(basebibliotek)));
    }

    public static void mockBassebibliotekFailure(String bibNr) {
        stubFor(get(urlPathMatching(URL_PATH_BASEBIBLIOTEK_REST_BIBNR + bibNr)).willReturn(forbidden()));
    }

    public static List<LoggedRequest> readAlmaPutRequests(String almaCode) {
        return readPutRequests(URL_PATH_USERS + SLASH + almaCode);
    }

    public static List<LoggedRequest> readPutRequests(String urlPath) {
        return findAll(putRequestedFor(urlPathEqualTo(urlPath)));
    }
}
