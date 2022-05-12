package no.sikt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Arrays;
import no.unit.nva.stubs.WiremockHttpClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class BaseBibliotekFetchHandlerTest {

    public static final String BASEBIBLIOTEK_REDACTED_INCREMENTAL_1_XML = "basebibliotek_redacted_incremental_1.xml";
    public static final String BASEBIBLIOTEK_REDACTED_INCREMENTAL_2_XML = "basebibliotek_redacted_incremental_2.xml";
    private static final String S3_BUCKET_NAME = "s3BucketName";
    public static final String BIBLIOTEK_EKSPORT_BIBLEV_PATH = "/bibliotek/eksport/biblev";
    private static final String BASEBIBLIOTEK_BB_2022_04_27_XML = "bb-2022-04-27.xml";
    private static final String BASEBIBLIOTEK_BB_2022_05_04_XML = "bb-2022-05-04.xml";
    private static final String BASEBIBLIOTEK_BB_FULL_XML = "bb-full.xml";
    private WireMockServer httpServer;

    private BasebibliotekFetchHandler baseBibliotekFetchHandler;
    public static final Context CONTEXT = mock(Context.class);

    private S3Client s3Client;

    private TestAppender appender;

    @BeforeEach
    public void init() {
        appender = LogUtils.getTestingAppenderForRootLogger();
        s3Client = mock(S3Client.class);
        startWiremockServer();
        HttpClient httpClient = WiremockHttpClient.create();
        Environment environment = mock(Environment.class);
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            "http://localhost:"
            + httpServer.port()
            + "/bibliotek/eksport/biblev");
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME)).thenReturn(
            "ignored");
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME)).thenReturn(
            "ignored");
        when(environment.readEnv(BasebibliotekFetchHandler.S3_BUCKET_ENVIRONMENT_NAME)).thenReturn(S3_BUCKET_NAME);
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

        var basebibliotekUrlsAsHtml = IoUtils.stringFromResources(
            Path.of("basebibliotek-url.html"));
        mockedGetRequestThatReturnsSpecifiedResponse(basebibliotekUrlsAsHtml);

        var basebibliotekXML1 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_1_XML));
        var basebibliotekXML2 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_2_XML));
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_04_27_XML, basebibliotekXML1);
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML, basebibliotekXML2);

        var scheduledEvent = new ScheduledEvent();
        baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT);

        //Verify that basebibliotek has been contacted.
        WireMock.verify(getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH)));
        WireMock.verify(
            getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_04_27_XML)));
        WireMock.verify(
            getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML)));
        WireMock.verify(0,
                        getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_FULL_XML)));

        //verify that the s3client has been called to putobjects:
        Mockito
            .verify(this.s3Client)
            .putObject(eq(generatePutObjectRequest(BASEBIBLIOTEK_BB_2022_04_27_XML)),
                       argThat(new RequestBodyMatches(RequestBody.fromString(basebibliotekXML1))));
        Mockito
            .verify(this.s3Client).putObject(eq(generatePutObjectRequest(BASEBIBLIOTEK_BB_2022_05_04_XML)),
                                             argThat(
                                                 new RequestBodyMatches(RequestBody.fromString(basebibliotekXML2))));
        Mockito
            .verify(this.s3Client, never()).putObject(eq(generatePutObjectRequest(BASEBIBLIOTEK_BB_FULL_XML)),
                                                      any(RequestBody.class));
    }

    @Test
    public void shouldHandleS3Exceptions() {
        var basebibliotekUrlsAsHtml = IoUtils.stringFromResources(
            Path.of("basebibliotek-url.html"));
        mockedGetRequestThatReturnsSpecifiedResponse(basebibliotekUrlsAsHtml);

        var basebibliotekXML1 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_1_XML));
        var basebibliotekXML2 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_2_XML));
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_04_27_XML, basebibliotekXML1);
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML, basebibliotekXML2);

        var scheduledEvent = new ScheduledEvent();
        baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT);
        when(this.s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(new RuntimeException());
        var expectedMessage = "Could not upload file to s3";
        assertThrows(RuntimeException.class, () -> baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT));
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    private PutObjectRequest generatePutObjectRequest(String key) {
        return PutObjectRequest.builder()
                   .bucket(S3_BUCKET_NAME)
                   .key(key)
                   .build();
    }

    private void startWiremockServer() {
        httpServer = new WireMockServer(options().dynamicHttpsPort());
        httpServer.start();
    }

    private void mockedGetRequestThatReturnsSpecifiedResponse(String response) {
        stubFor(get(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "text/html")
                                    .withStatus(HttpURLConnection.HTTP_OK)
                                    .withBody(response)));
    }

    private void mockedGetRequestWithSpecifiedStatusCode(int statusCode) {
        stubFor(get(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "text/html")
                                    .withStatus(statusCode)
                                    .withBody("fdgfg")));
    }

    private void mockedWiremockStubFor(String urlEqualTo, String body) {
        stubFor(get(urlEqualTo(urlEqualTo))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "application/xml")
                                    .withStatus(HttpURLConnection.HTTP_OK)
                                    .withBody(body)));
    }

    class RequestBodyMatches implements ArgumentMatcher<RequestBody> {

        private final RequestBody left;

        public RequestBodyMatches(RequestBody left) {
            this.left = left;
        }

        @Override
        public boolean matches(RequestBody right) {
            try (var rightStream = right.contentStreamProvider().newStream();
                var leftStream = left.contentStreamProvider().newStream()) {
                var rightContent = Arrays.toString(rightStream.readAllBytes());
                var leftContent = Arrays.toString(leftStream.readAllBytes());
                return rightContent.equals(leftContent);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
