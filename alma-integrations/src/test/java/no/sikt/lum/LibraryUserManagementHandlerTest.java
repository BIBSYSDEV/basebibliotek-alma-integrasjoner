package no.sikt.lum;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static no.sikt.clients.AbstractHttpUrlConnectionApi.LOG_MESSAGE_COMMUNICATION_PROBLEM;
import static no.sikt.commons.HandlerUtils.COULD_NOT_FETCH_BASEBIBLIOTEK_REPORT_MESSAGE;
import static no.sikt.lum.UserConverter.LIB_USER_PREFIX;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.Record;
import no.sikt.clients.alma.HttpUrlConnectionAlmaUserUpserter;
import no.sikt.clients.basebibliotek.BaseBibliotekUtils;
import no.sikt.commons.HandlerUtils;
import no.sikt.rsp.AlmaCodeProvider;
import no.sikt.rsp.LibCodeToAlmaCodeEntry;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.utils.BasebibliotekGenerator;
import test.utils.FakeS3ClientThrowingException;
import test.utils.HandlerTestUtils;
import test.utils.RecordBuilder;
import test.utils.WireMocker;

@SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "PMD.AvoidDuplicateLiterals"})
@WireMockTest
class LibraryUserManagementHandlerTest {

    private static final String SHARED_CONFIG_BUCKET_NAME_ENV_VALUE = "SharedConfigBucket";
    public static final String BIBLIOTEK_REST_PATH = "/basebibliotek/rest/bibnr/";
    public static final String BASEBIBLIOTEK_REPORT = "basebibliotek-report";
    public static final String FULL_ALMA_CODE_ALMA_APIKEY_MAPPING_JSON = "fullAlmaCodeAlmaApiKeyMapping.json";
    public static final String FULL_LIB_CODE_TO_ALMA_CODE_MAPPING_JSON = "fullLibCodeToAlmaCodeMapping.json";
    private static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH = "/libCodeToAlmaCodeMapping.json";
    private static final String BIBNR_RESOLVABLE_TO_ALMA_CODE = "0030100";
    private static final String BASEBIBLIOTEK_0030100_XML = "bb_0030100.xml";
    private static final String LIB_0030100_ID = "lib0030100";
    private static final String INVALID_BASEBIBLIOTEK_XML_STRING = "invalid";
    private static final String EMAIL_ADR = "adr@example.com";
    private static final String EMAIL_BEST = "best@example.com";
    public static final String INST = "Inst";

    public static final Context CONTEXT = mock(Context.class);
    public static final String BIBLTYPE = "Bibltype";
    private transient FakeS3Client s3Client;
    private transient S3Driver s3Driver;
    private transient LibraryUserManagementHandler libraryUserManagementHandler;
    private static final Environment mockedEnvironment = mock(Environment.class);
    private transient int numberOfAlmaInstances;

    @BeforeEach
    public void init(final WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.ALMA_API_HOST)).thenReturn(
            UriWrapper.fromUri(wmRuntimeInfo.getHttpBaseUrl()).toString());
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            UriWrapper.fromUri(wmRuntimeInfo.getHttpBaseUrl()).addChild(BIBLIOTEK_REST_PATH).toString());
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.SHARED_CONFIG_BUCKET_NAME_ENV_NAME)).thenReturn(
            SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.REPORT_BUCKET_ENVIRONMENT_NAME)).thenReturn(
            BASEBIBLIOTEK_REPORT);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY))
            .thenReturn(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);
        final String fullLibCodeToAlmaCodeMapping = IoUtils.stringFromResources(
            Path.of(FULL_LIB_CODE_TO_ALMA_CODE_MAPPING_JSON));
        final String fullAlmaCodeAlmaApiKeyMapping = IoUtils.stringFromResources(
            Path.of(FULL_ALMA_CODE_ALMA_APIKEY_MAPPING_JSON));
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.ALMA_API_KEYS_ENV_KEY)).thenReturn(
            fullAlmaCodeAlmaApiKeyMapping);
        numberOfAlmaInstances = StringUtils.countMatches(fullAlmaCodeAlmaApiKeyMapping, "almaCode");
        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH), fullLibCodeToAlmaCodeMapping);
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void shouldBeAbleToReadAndPostRecordToAlma() throws IOException {
        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                                           IoUtils.stringFromResources(
                                                                               Path.of(BASEBIBLIOTEK_0030100_XML)));
        final S3Event s3Event = HandlerTestUtils.prepareBaseBibliotekFromXml(bibNrToXmlMap, s3Driver);
        WireMocker.mockAlmaGetResponseUserNotFound(LIB_0030100_ID);
        WireMocker.mockAlmaPostResponse();
        Integer response = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        verify(getRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_USERS + "/" + LIB_0030100_ID)));
        verify(postRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_USERS)));
        assertThat(response, is(notNullValue()));
        assertThat(response, is(numberOfAlmaInstances));
    }

    @Test
    public void shouldBeAbleToReadAndPutRecordToAlma() throws IOException {
        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                                           IoUtils.stringFromResources(
                                                                               Path.of(BASEBIBLIOTEK_0030100_XML)));
        final S3Event s3Event = HandlerTestUtils.prepareBaseBibliotekFromXml(bibNrToXmlMap, s3Driver);
        WireMocker.mockAlmaGetResponse(LIB_0030100_ID);
        WireMocker.mockAlmaPutResponse(LIB_0030100_ID);
        Integer response = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        verify(getRequestedFor(urlEqualTo(WireMocker.URL_PATH_USERS + "/" + LIB_0030100_ID)));
        verify(putRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_USERS + "/" + LIB_0030100_ID)));
        assertThat(response, is(notNullValue()));
        assertThat(response, is(numberOfAlmaInstances));
    }

    @Test
    public void shouldLogExceptionWhenS3ClientFails() {
        var s3Event = HandlerTestUtils.createS3Event(randomString());
        var expectedMessage = randomString();
        s3Client = new FakeS3ClientThrowingException(expectedMessage);
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> libraryUserManagementHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldSkipWhenCannotBeConvertedToBaseBibliotek() throws IOException {
        final String skippedBibBr = "1000000";
        final Map<String, String> bibNrToXmlMap = new HashMap<>();
        bibNrToXmlMap.put(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                          IoUtils.stringFromResources(Path.of(BASEBIBLIOTEK_0030100_XML)));
        bibNrToXmlMap.put(skippedBibBr, INVALID_BASEBIBLIOTEK_XML_STRING);
        final S3Event s3Event = HandlerTestUtils.prepareBaseBibliotekFromXml(bibNrToXmlMap, s3Driver);
        WireMocker.mockAlmaGetResponse(LIB_0030100_ID);
        WireMocker.mockAlmaPutResponse(LIB_0030100_ID);
        var expectedSuccessfulConversion = numberOfAlmaInstances;
        var numberOfSuccessfulConversion = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(numberOfSuccessfulConversion, is(equalTo(expectedSuccessfulConversion)));
    }

    @Test
    public void shouldSkipWhenBasebibliotekFails() throws IOException {
        final String bibNrSuccess = "1000000";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
            .withBibnr(bibNrSuccess)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .withEpostBest(EMAIL_BEST)
            .withEpostAdr(EMAIL_ADR)
            .withInst(INST)
            .withBiblType(BIBLTYPE)
            .build();
        final String failingBibNr = "1234567";
        final S3Event s3Event = prepareBaseBibliotekFromRecords(failingBibNr, record);
        final String almaCode = LIB_USER_PREFIX + bibNrSuccess;
        WireMocker.mockAlmaGetResponse(almaCode);
        WireMocker.mockAlmaPutResponse(almaCode);
        WireMocker.mockBassebibliotekFailure(failingBibNr);
        var expectedNumberOfSuccessfulConversions = numberOfAlmaInstances;
        var numberOfSuccessfulConversions = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(numberOfSuccessfulConversions, is(equalTo(expectedNumberOfSuccessfulConversions)));
    }

    @Test
    public void shouldLogAndThrowExceptionWhenAlmaCodeLookupTableIsMissing() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_ALMA)
            .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .build();
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> libraryUserManagementHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH));
    }

    @Test
    public void shouldLogAndThrowExceptionWhenAlmaCodeLookupTableIsEmpty() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_ALMA)
            .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .build();
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);
        when(mockedEnvironment.readEnv(
            LibraryUserManagementHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY)).thenReturn(
            LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);
        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH),
                            IoUtils.stringFromResources(Path.of("emptyLibCodeToAlmaCodeMapping.json")));
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> libraryUserManagementHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(AlmaCodeProvider.EMPTY_MAPPING_TABLE_MESSAGE));
    }

    @Test
    public void shouldLogAndThrowExceptionWhenAlmaCodeLookupTableIsInvalidJson() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_ALMA)
            .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .build();
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);
        when(mockedEnvironment.readEnv(
            LibraryUserManagementHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY)).thenReturn(
            LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);
        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH),
                            IoUtils.stringFromResources(Path.of("invalidLibCodeToAlmaCodeMapping.json")));
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> libraryUserManagementHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(LibCodeToAlmaCodeEntry.FIELD_IS_NULL_OR_EMPTY_MESSAGE));
    }

    @Test
    void shouldReportSuccessfulWhenAllWorksProperly() throws IOException {
        final String bibNr = "1000000";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
            .withBibnr(bibNr)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_GERMAN)
            .withEpostBest(EMAIL_BEST)
            .withEpostAdr(EMAIL_ADR)
            .withInst(INST)
            .withBiblType(BIBLTYPE)
            .build();
        final UnixPath s3Path = HandlerTestUtils.randomS3Path();
        final S3Event s3Event = prepareBaseBibliotekFromRecords(s3Path, record);
        final String almaCode = LIB_USER_PREFIX + bibNr;
        WireMocker.mockAlmaGetResponse(almaCode);
        WireMocker.mockAlmaPutResponse(almaCode);
        var numberOfSuccessFulLibraries = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(numberOfSuccessFulLibraries, is(equalTo(numberOfSuccessFulLibraries)));
        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(HandlerUtils.extractReportFilename(s3Event, LibraryUserManagementHandler.HANDLER_NAME)));
        assertThat(report, startsWith(LIB_USER_PREFIX + bibNr + nva.commons.core.StringUtils.SPACE));
        assertThat(report,
                   endsWith(nva.commons.core.StringUtils.SPACE + LibraryUserManagementHandler.OK_REPORT_MESSAGE));
    }

    @Test
    void shouldGenerateReportWhenAlmaContactFailure() throws IOException {
        var bibNr = "1234567";
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
            .withBibnr(bibNr)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .withEpostBest(EMAIL_BEST)
            .withEpostAdr(EMAIL_ADR)
            .withInst(INST)
            .withBiblType("VGS")
            .build();
        var s3Path = HandlerTestUtils.randomS3Path();
        var s3Event = prepareBaseBibliotekFromRecords(s3Path, record);
        WireMocker.mockAlmaForbiddenGetResponse(LIB_USER_PREFIX + bibNr);
        WireMocker.mockAlmaForbiddenPostResponse(LIB_USER_PREFIX + bibNr);
        libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(HandlerUtils.extractReportFilename(s3Event, LibraryUserManagementHandler.HANDLER_NAME)));
        assertThat(report, containsString(
            bibNr + LibraryUserManagementHandler.COULD_NOT_CONTACT_ALMA_REPORT_MESSAGE));
    }

    @Test
    void shouldGenerateReportWhenBasebibliotekFetchFailure() throws IOException {
        var basebibliotekFailureBibnr = "2000000";
        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(basebibliotekFailureBibnr,
                                                                           INVALID_BASEBIBLIOTEK_XML_STRING);
        var s3Path = HandlerTestUtils.randomS3Path();
        var s3Event = HandlerTestUtils.prepareBaseBibliotekFromXml(s3Path, bibNrToXmlMap, s3Driver);
        libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(HandlerUtils.extractReportFilename(s3Event, LibraryUserManagementHandler.HANDLER_NAME)));
        assertThat(report, containsString(
            basebibliotekFailureBibnr + COULD_NOT_FETCH_BASEBIBLIOTEK_REPORT_MESSAGE));
    }

    @Test
    void shouldGenerateReportWhenConversionToUserFailure() throws IOException {
        var bibNr = "3";
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
            .withBibnr(bibNr)
            .withInst(null)
            .withEpostBest(EMAIL_BEST)
            .withEpostAdr(EMAIL_ADR)
            .withBiblType(BIBLTYPE)
            .build();
        var s3Path = HandlerTestUtils.randomS3Path();
        var s3Event = prepareBaseBibliotekFromRecords(s3Path, record);
        libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(HandlerUtils.extractReportFilename(s3Event, LibraryUserManagementHandler.HANDLER_NAME)));
        assertThat(report, containsString(
            bibNr + LibraryUserManagementHandler.COULD_NOT_CONVERT_TO_USER_REPORT_MESSAGE));
    }

    @Test
    public void shouldIgnoreUserAndLogProblemIfBaseBibliotekIsNotAvailable() throws IOException {
        var uri = s3Driver.insertFile(HandlerTestUtils.randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = HandlerTestUtils.createS3Event(uri);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            UriWrapper.fromUri("http://localhost:9999").toString());
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();
        final Integer count = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(count, is(0));
        assertThat(appender.getMessages(), containsString(LOG_MESSAGE_COMMUNICATION_PROBLEM));
    }

    @Test
    public void shouldIgnoreUserAndLogProblemIfAlmaGetUserReturnsBadRequestWithUnhandledErrorCode()
        throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), "BIBSYS")
            .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .withInst(INST)
            .withBiblType("UNI")
            .build();
        var s3Event = prepareBaseBibliotekFromRecords(record);
        WireMocker.mockAlmaGetResponseBadRequestNotUserNotFound(LIB_0030100_ID);
        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();
        final Integer count = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(count, is(0));
        assertThat(appender.getMessages(),
                   containsString(
                       HttpUrlConnectionAlmaUserUpserter.UNEXPECTED_RESPONSE_FETCHING_USER_LOG_MESSAGE_PREFIX));
    }

    @Test
    public void shouldIgnoreUserAndLogProblemIfAlmaCreateUserReturnsBadRequestWithUnhandledErrorCode()
        throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), "BIBSYS")
            .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .withInst(INST)
            .withBiblType("ORG")
            .build();
        var s3Event = prepareBaseBibliotekFromRecords(record);
        WireMocker.mockAlmaGetResponseUserNotFound(LIB_0030100_ID);
        WireMocker.mockAlmaPostResponseBadRequest();
        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();
        final Integer count = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(count, is(0));
        assertThat(appender.getMessages(),
                   containsString(
                       HttpUrlConnectionAlmaUserUpserter.UNEXPECTED_RESPONSE_CREATING_USER_LOG_MESSAGE_PREFIX));
    }

    @Test
    public void shouldIgnoreUserAndLogProblemIfAlmaUpdateUserReturnsBadRequestWithUnhandledErrorCode()
        throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), "BIBSYS")
            .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
            .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
            .withInst(INST)
            .withBiblType("FBI")
            .build();
        var
            s3Event = prepareBaseBibliotekFromRecords(record);
        WireMocker.mockAlmaGetResponse(LIB_0030100_ID);
        WireMocker.mockAlmaPutResponseBadRequest(LIB_0030100_ID);
        final TestAppender appender =
            LogUtils.getTestingAppenderForRootLogger();
        final Integer count = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(count, is(0));
        assertThat(appender.getMessages(), containsString(
            HttpUrlConnectionAlmaUserUpserter.UNEXPECTED_RESPONSE_UPDATING_USER_LOG_MESSAGE_PREFIX));
    }

    private S3Event prepareBaseBibliotekFromRecords(final UnixPath s3Path, final Record... records) throws IOException {
        return prepareBaseBibliotekFromRecords(s3Path, null, records);
    }

    private S3Event prepareBaseBibliotekFromRecords(final Record... records) throws IOException {
        return prepareBaseBibliotekFromRecords(null, null, records);
    }

    private S3Event prepareBaseBibliotekFromRecords(final String failingBibNr, final Record... records)
        throws IOException {

        return prepareBaseBibliotekFromRecords(null, failingBibNr, records);
    }

    private S3Event prepareBaseBibliotekFromRecords(final UnixPath s3Path, final String failingBibNr,
                                                    final Record... records) throws IOException {
        String fileContent = Arrays.stream(records).map(Record::getBibnr).collect(Collectors.joining("\n"));
        if (failingBibNr != null) {
            fileContent += ("\n" + failingBibNr);
        }
        // prepare mocks:
        Arrays.stream(records).forEach(record -> {
            var basebibliotekGenerator = new BasebibliotekGenerator(record);
            var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
            var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
            var bibNr = record.getBibnr();

            WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        });
        final UnixPath path = (s3Path == null) ? HandlerTestUtils.randomS3Path() : s3Path;
        var uri = s3Driver.insertFile(path, fileContent);
        return HandlerTestUtils.createS3Event(uri);
    }
}