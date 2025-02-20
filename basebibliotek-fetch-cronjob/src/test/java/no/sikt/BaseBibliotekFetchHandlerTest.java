package no.sikt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static no.sikt.BasebibliotekFetchHandler.NUMBER_OF_LIBRARIES_THAT_LUM_CAN_HANDLE_AT_ONCE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.unit.nva.stubs.WiremockHttpClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.hamcrest.core.Every;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@WireMockTest
public class BaseBibliotekFetchHandlerTest {

    public static final String BASEBIBLIOTEK_REDACTED_INCREMENTAL_1_XML = "basebibliotek_redacted_incremental_1.xml";
    public static final String BASEBIBLIOTEK_REDACTED_INCREMENTAL_2_XML = "basebibliotek_redacted_incremental_2.xml";
    public static final String BIBLIOTEK_EKSPORT_BIBLEV_PATH = "/bibliotek/eksport/biblev";
    public static final String BASEBIBLIOTEK_URL_HTML = "basebibliotek-url.html";
    public static final Context CONTEXT = mock(Context.class);
    private static final String S3_BUCKET_NAME = "s3BucketName";
    private static final String BASEBIBLIOTEK_BB_2022_04_27_XML = "bb-2022-04-27.xml";
    private static final String BASEBIBLIOTEK_BB_2022_05_04_XML = "bb-2022-05-04.xml";
    private static final String BASEBIBLIOTEK_BB_FULL_XML = "bb-full.xml";
    private transient BasebibliotekFetchHandler baseBibliotekFetchHandler;
    private transient S3Client s3Client;

    private transient TestAppender appender;

    @BeforeEach
    public void init(WireMockRuntimeInfo wireMockInfo) {
        appender = LogUtils.getTestingAppender(BasebibliotekFetchHandler.class);
        s3Client = mock(S3Client.class);
        Environment environment = mock(Environment.class);
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            wireMockInfo.getHttpBaseUrl()
            + BIBLIOTEK_EKSPORT_BIBLEV_PATH);
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLILOTEK_USERNAME_ENVIRONMENT_NAME)).thenReturn(
            "ignored");
        when(environment.readEnv(BasebibliotekFetchHandler.BASEBIBLIOTEK_PASSWORD_ENVIRONMENT_NAME)).thenReturn(
            "ignored");
        when(environment.readEnv(BasebibliotekFetchHandler.S3_BUCKET_ENVIRONMENT_NAME)).thenReturn(S3_BUCKET_NAME);
        HttpClient httpClient = WiremockHttpClient.create();
        baseBibliotekFetchHandler = new BasebibliotekFetchHandler(s3Client, httpClient, environment);
    }

    @Test
    public void shouldLogExceptionsWhenBasebibliotekRefusesConnection() {
        var expectedMessage =
            "could not connect to basebibliotek, Connection responded with status: " + HttpURLConnection.HTTP_FORBIDDEN;
        mockedGetRequestWithSpecifiedStatusCode(HttpURLConnection.HTTP_FORBIDDEN, BIBLIOTEK_EKSPORT_BIBLEV_PATH);
        assertThrows(RuntimeException.class, () -> baseBibliotekFetchHandler
                                                       .handleRequest(new ScheduledEvent(), CONTEXT));
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldLogExceptionWhenBasebibliotekXmlGetRequestFails() {
        var basebibliotekUrlsAsHtml = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_URL_HTML));
        mockedGetRequestThatReturnsSpecifiedResponse(basebibliotekUrlsAsHtml);

        var basebibliotekXML2 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_2_XML));
        mockedGetRequestWithSpecifiedStatusCode(HttpURLConnection.HTTP_FORBIDDEN,
                                                BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/"
                                                + BASEBIBLIOTEK_BB_2022_04_27_XML);
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML,
                              basebibliotekXML2);

        assertThrows(RuntimeException.class, () -> baseBibliotekFetchHandler
                                                       .handleRequest(new ScheduledEvent(), CONTEXT));

        var expectedMessage =
            "could not GET " + BASEBIBLIOTEK_BB_2022_04_27_XML;
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldCollectListOfBibnrAndUploadThemTos3() {

        var basebibliotekUrlsAsHtml = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_URL_HTML));
        mockedGetRequestThatReturnsSpecifiedResponse(basebibliotekUrlsAsHtml);

        var basebibliotekXML1 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_1_XML));
        var basebibliotekXML2 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_2_XML));
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_04_27_XML, basebibliotekXML1);
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML, basebibliotekXML2);

        var scheduledEvent = new ScheduledEvent();
        var expectedBibnrs = Set.of("0030100", "0030101", "7049304", "0030103");
        var listOfBibNr =
            baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT).stream().flatMap(Collection::stream)
                .collect(Collectors.toList());
        assertThat(listOfBibNr, hasSize(expectedBibnrs.size()));
        expectedBibnrs.forEach(expectedBibnr -> assertThat(listOfBibNr, hasItem(expectedBibnr)));

        //Verify that basebibliotek has been contacted.
        WireMock.verify(getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH)));
        WireMock.verify(
            getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_04_27_XML)));
        WireMock.verify(
            getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML)));
        WireMock.verify(0,
                        getRequestedFor(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_FULL_XML)));

        //verify that the s3client has been called to putobjects:
        var expectedUpload = "0030100\n0030101\n7049304\n0030103";
        Mockito
            .verify(this.s3Client, times(2))
            .putObject(any(PutObjectRequest.class),
                       argThat(new RequestBodyMatches(RequestBody.fromString(expectedUpload))));
    }

    @Test
    public void shouldHandleS3Exceptions() {
        var basebibliotekUrlsAsHtml = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_URL_HTML));
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

    @Test
    public void shouldLogRecordsThatAreMissingBibNr() {
        var basebibliotekUrlsAsHtml = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_URL_HTML));
        mockedGetRequestThatReturnsSpecifiedResponse(basebibliotekUrlsAsHtml);

        var basebibliotekXML1 = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_REDACTED_INCREMENTAL_1_XML));
        var basebibliotekXML3 = IoUtils.stringFromResources(
            Path.of("basebibliotek_redacted_incremental_3.xml"));
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_04_27_XML, basebibliotekXML1);
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML, basebibliotekXML3);
        var scheduledEvent = new ScheduledEvent();
        baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT);
        var expectedMessage = "Record with missing bibnr";
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldSplitLargeNumberOfLibrariesIntoSmallerBibNrFiles() {
        var basebibliotekUrlsAsHtml = IoUtils.stringFromResources(
            Path.of(BASEBIBLIOTEK_URL_HTML));
        mockedGetRequestThatReturnsSpecifiedResponse(basebibliotekUrlsAsHtml);

        var basebibliotekXML1 = IoUtils.stringFromResources(
            Path.of("redacted_bb_full.xml"));
        var basebibliotekXML3 = IoUtils.stringFromResources(
            Path.of("basebibliotek_redacted_incremental_3.xml"));
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_04_27_XML, basebibliotekXML1);
        mockedWiremockStubFor(BIBLIOTEK_EKSPORT_BIBLEV_PATH + "/" + BASEBIBLIOTEK_BB_2022_05_04_XML, basebibliotekXML3);
        var scheduledEvent = new ScheduledEvent();
        List<List<String>> listOfBibNr = baseBibliotekFetchHandler.handleRequest(scheduledEvent, CONTEXT);
        //Since this test is specific for checking bibNrs files sizes, it has been made so that the
        // numbersOfLibrariesExpected can change according to new requirements added to basebibliotek conversion.
        var expectedNumbersOfLibrariesFiles =
            (int) Math.ceil(listOfBibNr.stream()
                                .mapToDouble(Collection::size)
                                .sum() / NUMBER_OF_LIBRARIES_THAT_LUM_CAN_HANDLE_AT_ONCE);
        //check that no bibNrs file has more than 100 elements:
        assertThat(listOfBibNr,
                   Every.everyItem(hasSize(lessThanOrEqualTo(NUMBER_OF_LIBRARIES_THAT_LUM_CAN_HANDLE_AT_ONCE))));
        //Check that baseBibliotekFetchHandler has not split bibNrs unnecessary.
        assertThat(listOfBibNr, hasSize(equalTo(expectedNumbersOfLibrariesFiles)));
    }

    private void mockedGetRequestThatReturnsSpecifiedResponse(String response) {
        stubFor(get(urlEqualTo(BIBLIOTEK_EKSPORT_BIBLEV_PATH))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "text/html")
                                    .withStatus(HttpURLConnection.HTTP_OK)
                                    .withBody(response)));
    }

    private void mockedGetRequestWithSpecifiedStatusCode(int statusCode, String path) {
        stubFor(get(urlEqualTo(path))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "text/html")
                                    .withStatus(statusCode)
                                    .withBody("")));
    }

    private void mockedWiremockStubFor(String urlEqualTo, String body) {
        stubFor(get(urlEqualTo(urlEqualTo))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, "application/xml")
                                    .withStatus(HttpURLConnection.HTTP_OK)
                                    .withBody(body)));
    }

    static class RequestBodyMatches implements ArgumentMatcher<RequestBody> {

        private final transient RequestBody left;
        transient String leftContent = "";
        transient String rightContent = "";

        public RequestBodyMatches(RequestBody left) {
            this.left = left;
        }

        @Override
        public boolean matches(RequestBody right) {
            try (var rightStream = right.contentStreamProvider().newStream();
                var leftStream = left.contentStreamProvider().newStream()) {
                rightContent = new String(rightStream.readAllBytes());
                leftContent = new String(leftStream.readAllBytes());
                return rightContent.equals(leftContent);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public String toString() {
            return "Request body matcher. Expected content: " + leftContent + ", received:" + rightContent;
        }
    }
}
