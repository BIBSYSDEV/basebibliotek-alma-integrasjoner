package no.sikt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.stubs.WiremockHttpClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BaseBibliotekFetchHandlerTest {

    // Sjekker at den ignorerer full basebibliotek-xml url
    // Sjekke at den kontakter de andre url'ene og ender tilslutt opp med basebibliotek-xml
    //Håndtering av feil i connection
    //Skrive til s3
    //Håndtere s3 problemer.

    public static final String FILE_URI_TEMPLATE = "http://localhost:%d/file/%s";
    public static final String BIBLIOTEK_EKSPORT_BIBLEV_BB_2022_04_27_XML = "/bibliotek/eksport/biblev/bb-2022-04-27"
                                                                            + ".xml";

    public static final String BIBLIOTEK_EKSPORT_BIBLEV_BB_2022_05_04_XML = "/bibliotek/eksport/biblev/bb-2022-05-04"
                                                                           + ".xml";
    public static final String BASEBIBLIOTEK_XML_FULL = "/bibliotek/eksport/biblev/bb-full.xml";
    private WireMockServer httpServer;
    private HttpClient httpClient;

    private BasebibliotekFetchHandler baseBibliotekFetchHandler;
    public static final Context CONTEXT = mock(Context.class);

    private FakeS3Client s3Client;
    private S3Driver s3Driver;

    private Environment environment;

    private TestAppender appender;

    @BeforeEach
    public void init() {
        appender = LogUtils.getTestingAppenderForRootLogger();
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, "ignoredValue");
        startWiremockServer();
        httpClient = WiremockHttpClient.create();
        environment = mock(Environment.class);
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            "http://localhost"
            + ":"
            + httpServer.port()
            + "/bibliotek/eksport/biblev");
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME)).thenReturn(
            "ignored");
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME)).thenReturn(
            "ignored");
        when(environment.readEnv(BasebibliotekFetchHandler.S3_BUCKET_ENVIRONMENT_NAME)).thenReturn("ignored");
        baseBibliotekFetchHandler = new BasebibliotekFetchHandler(s3Client, httpClient, environment);
    }

    @AfterEach
    public void tearDown() {
        httpServer.stop();
    }

    @Test
    public void shouldLogExceptionsWhenBasebibliotekRefusesConnection() {
        var scheduledEvent = new ScheduledEvent();

        var expectedMessage =
            "could not connect to basebibliotek, Connection responded with status: " + HttpURLConnection.HTTP_FORBIDDEN;
        mockedGetRequestWithSpecifiedStatusCode(HttpURLConnection.HTTP_FORBIDDEN);
        assertThrows(RuntimeException.class, () -> baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT));
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldConnectToBasebibliotek() {
        var scheduledEvent = new ScheduledEvent();
        var basebibliotekUrlsAsHTML = IoUtils.stringFromResources(
            Path.of("basebibliotek-url.html"));
        mockedGetRequestThatReturnsFile(basebibliotekUrlsAsHTML);
        mockedBasebibliotekXmlResponse();
        var response = baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT);
        verify( getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_BB_2022_04_27_XML)));
        verify( getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_BB_2022_05_04_XML)));
        verify(0, getRequestedFor(urlEqualTo(BASEBIBLIOTEK_XML_FULL)));
        assertThat(response, is(equalTo(basebibliotekUrlsAsHTML)));
    }

    /*
    @Test
    public void shouldContactBasebibliotek() {

    }

     */

    private void startWiremockServer() {
        httpServer = new WireMockServer(options().dynamicHttpsPort());
        httpServer.start();
    }

    private void mockedGetRequestThatReturnsFile(String response) {
        stubFor(get(urlEqualTo("/bibliotek/eksport/biblev"))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "text/html")
                                    .withStatus(HttpURLConnection.HTTP_OK)
                                    .withBody(response)));
    }

    private void mockedGetRequestWithSpecifiedStatusCode(int statusCode) {
        stubFor(get(urlEqualTo("/bibliotek/eksport/biblev"))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "text/html")
                                    .withStatus(statusCode)
                                    .withBody("fdgfg")));
    }

    private void mockedBasebibliotekXmlResponse() {
        var basebibliotekXML = IoUtils.stringFromResources(
            Path.of("basebibliotek_redacted_incremental.xml"));
        stubFor(get(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_BB_2022_04_27_XML))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "application/xml")
                                    .withStatus(HttpURLConnection.HTTP_OK)
                                    .withBody(basebibliotekXML)));

    }
}
