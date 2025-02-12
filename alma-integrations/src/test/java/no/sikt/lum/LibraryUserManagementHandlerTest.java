package no.sikt.lum;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static no.sikt.clients.AbstractHttpUrlConnectionApi.LOG_MESSAGE_COMMUNICATION_PROBLEM;
import static no.sikt.commons.HandlerUtils.COULD_NOT_FETCH_BASEBIBLIOTEK_REPORT_MESSAGE;
import static no.sikt.commons.HandlerUtils.HYPHEN;
import static no.sikt.lum.UserConverter.LIB_USER_PREFIX;
import static no.sikt.lum.UserConverter.USER_IDENTIFIER_REALMS;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.user.generated.Address;
import no.sikt.alma.user.generated.ContactInfo;
import no.sikt.alma.user.generated.Email;
import no.sikt.alma.user.generated.Emails;
import no.sikt.alma.user.generated.Phones;
import no.sikt.alma.user.generated.User;
import no.sikt.clients.alma.HttpUrlConnectionAlmaUserUpserter;
import no.sikt.clients.basebibliotek.BaseBibliotekUtils;
import no.sikt.clients.basebibliotek.HttpUrlConnectionBaseBibliotekApi;
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
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import test.utils.BasebibliotekGenerator;
import test.utils.FakeS3ClientThrowingException;
import test.utils.HandlerTestUtils;
import test.utils.RecordBuilder;
import test.utils.RecordSpecification;
import test.utils.WireMocker;

@WireMockTest
class LibraryUserManagementHandlerTest {

    public static final String BIBLIOTEK_REST_PATH = "/basebibliotek/rest/bibnr/";
    public static final String BASEBIBLIOTEK_REPORT = "basebibliotek-report";
    public static final String FULL_ALMA_CODE_ALMA_APIKEY_MAPPING_JSON = "fullAlmaCodeAlmaApiKeyMapping.json";
    public static final String FULL_LIB_CODE_TO_ALMA_CODE_MAPPING_JSON = "fullLibCodeToAlmaCodeMapping.json";
    public static final String INST = "Inst";
    public static final Context CONTEXT = mock(Context.class);
    public static final String BIBLTYPE = "Bibltype";
    private static final String SHARED_CONFIG_BUCKET_NAME_ENV_VALUE = "SharedConfigBucket";
    private static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH = "/libCodeToAlmaCodeMapping.json";
    private static final String BIBNR_RESOLVABLE_TO_ALMA_CODE = "0030100";
    private static final String BASEBIBLIOTEK_0030100_XML = "bb_0030100.xml";
    private static final String LIB_0030100_ID = "lib0030100";
    private static final String INVALID_BASEBIBLIOTEK_XML_STRING = "invalid";
    private static final String EMAIL_ADR = "adr@example.com";
    private static final String EMAIL_BEST = "best@example.com";

    private static final Environment mockedEnvironment = mock(Environment.class);
    private transient FakeS3Client s3Client;
    private transient S3Driver s3Driver;
    private transient LibraryUserManagementHandler libraryUserManagementHandler;
    private transient int numberOfAlmaInstances;

    private transient SecretsManagerClient secretsManagerClient;

    @BeforeEach
    public void init(final WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        secretsManagerClient = mock(SecretsManagerClient.class);
        var getSecretValueResponse = mock(GetSecretValueResponse.class);

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
        when(getSecretValueResponse.secretString())
            .thenReturn(fullAlmaCodeAlmaApiKeyMapping);
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(getSecretValueResponse);
        numberOfAlmaInstances = StringUtils.countMatches(fullAlmaCodeAlmaApiKeyMapping, "almaCode");
        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH), fullLibCodeToAlmaCodeMapping);
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment,
                                                                        secretsManagerClient);
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
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment,
                                                                        secretsManagerClient);
        var appender = LogUtils.getTestingAppender(LibraryUserManagementHandler.class);
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
    public void shouldExtractContactDetailsCorrectly() throws IOException {
        var withPaddr = true;
        var withVaddr = true;
        var specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                    true,
                                                    null,
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    withPaddr,
                                                    withVaddr,
                                                    randomBoolean(),
                                                    randomString());
        var basebibliotekGenerator = new BasebibliotekGenerator(specification);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        WireMocker.mockAlmaGetResponse(LIB_0030100_ID);
        WireMocker.mockAlmaPutResponse(LIB_0030100_ID);
        var uri = s3Driver.insertFile(HandlerTestUtils.randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = HandlerTestUtils.createS3Event(uri);
        libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        var users = libraryUserManagementHandler.getUsers();
        Entry<String, List<User>> entry = users.entrySet().iterator().next();
        assertContactInfo(entry.getValue().getFirst().getContactInfo(), basebibliotek.getRecord().getFirst());
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
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment,
                                                                        secretsManagerClient);
        final var appender = LogUtils.getTestingAppender(LibraryUserManagementHandler.class);
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
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment,
                                                                        secretsManagerClient);
        final var appender = LogUtils.getTestingAppender(LibraryUserManagementHandler.class);
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
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment,
                                                                        secretsManagerClient);
        final var appender = LogUtils.getTestingAppender(LibraryUserManagementHandler.class);
        assertThrows(RuntimeException.class, () -> libraryUserManagementHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(LibCodeToAlmaCodeEntry.FIELD_IS_NULL_OR_EMPTY_MESSAGE));
    }

    @Test
    public void shouldIgnoreUserAndLogProblemIfBaseBibliotekIsNotAvailable() throws IOException {
        var uri = s3Driver.insertFile(HandlerTestUtils.randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = HandlerTestUtils.createS3Event(uri);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            UriWrapper.fromUri("http://localhost:9999").toString());
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment,
                                                                        secretsManagerClient);
        final var appender = LogUtils.getTestingAppender(HttpUrlConnectionBaseBibliotekApi.class);
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
        final var appender = LogUtils.getTestingAppender(HttpUrlConnectionAlmaUserUpserter.class);
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
        final var appender = LogUtils.getTestingAppender(HttpUrlConnectionAlmaUserUpserter.class);
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
        var s3Event = prepareBaseBibliotekFromRecords(record);
        WireMocker.mockAlmaGetResponse(LIB_0030100_ID);
        WireMocker.mockAlmaPutResponseBadRequest(LIB_0030100_ID);
        final var appender = LogUtils.getTestingAppender(HttpUrlConnectionAlmaUserUpserter.class);
        final Integer count = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        assertThat(count, is(0));
        assertThat(appender.getMessages(), containsString(
            HttpUrlConnectionAlmaUserUpserter.UNEXPECTED_RESPONSE_UPDATING_USER_LOG_MESSAGE_PREFIX));
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

        assertThat(report, containsString("lib1000000 \t ok:83 \t failures:0 \t failed:[]"));
    }

    @Test
    void shouldGenerateReportWhenAlmaContactFailureWithListOfFailures() throws IOException {
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

        assertThat(report, containsString(LIB_USER_PREFIX + bibNr));
        assertThat(report, containsString("ok:0"));
        assertThat(report, containsString("failures:83"));
        assertThat(report, containsString("failed:["));
        assertThat(report, containsString("NTNU"));
        assertThat(report, containsString("MOLDESYK"));

        assertThat(report, not(containsString("failed:[]")));
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

        assertThat(report, containsString("failures:83"));
        assertThat(report, startsWith(bibNr));
        assertThat(report, containsString("Could not convert to user"));
    }

    @Test
    public void shouldHaveAllIdentifiersInAlmaPayload() throws IOException {
        var withPaddr = true;
        var withVaddr = true;
        var specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                    true,
                                                    null,
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    withPaddr,
                                                    withVaddr,
                                                    randomBoolean(),
                                                    randomString());
        var basebibliotekGenerator = new BasebibliotekGenerator(specification);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        WireMocker.mockAlmaGetResponse(LIB_0030100_ID);
        WireMocker.mockAlmaPutResponse(LIB_0030100_ID);
        var uri = s3Driver.insertFile(HandlerTestUtils.randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = HandlerTestUtils.createS3Event(uri);
        libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        var requests = WireMocker.readAlmaPutRequests(LIB_0030100_ID);

        requests.forEach(request -> assertThat(USER_IDENTIFIER_REALMS.stream()
                                                   .allMatch(request.getBodyAsString()::contains),
                                               equalTo(true)));
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

    private void assertContactInfo(ContactInfo contactInfo, Record record) {
        assertAddresses(contactInfo.getAddresses().getAddress(), record);
        assertPhone(contactInfo.getPhones(), record);
        assertEmails(contactInfo.getEmails(), record);
    }

    private void assertEmails(Emails emails, Record record) {
        var emailBestShouldExist = Objects.nonNull(record.getEpostBest());
        var emailRegularShouldExist = Objects.nonNull(record.getEpostAdr());
        var expectedEmailsSize = (emailBestShouldExist ? 1 : 0) + (emailRegularShouldExist ? 1 : 0);
        assertThat(emails.getEmail(), IsCollectionWithSize.hasSize(expectedEmailsSize));
        var emailBest = emails.getEmail()
                            .stream()
                            .filter(email -> hasEmailAddressCorresponding(email, record.getEpostBest()))
                            .findFirst()
                            .orElse(null);
        var emailRegular = emails.getEmail()
                               .stream()
                               .filter(email -> hasEmailAddressCorresponding(email, record.getEpostAdr()))
                               .findFirst()
                               .orElse(null);
        if (emailBestShouldExist) {
            assertEmail(emailBest, record.getEpostBest(), true);
        }
        if (emailRegularShouldExist) {
            assertEmail(emailRegular, record.getEpostAdr(), !emailBestShouldExist);
        }
    }

    private void assertEmail(Email email, String emailAddress, boolean shouldBePreferred) {
        assertThat(email, is(IsNull.notNullValue()));
        assertThat(email.getEmailTypes().getEmailType(), IsCollectionWithSize.hasSize(1));
        var expectedEmailType = ContactInfoConverter.WORK;
        assertThat(email.getEmailTypes().getEmailType().getFirst().getDesc(), is(equalTo(expectedEmailType)));
        var expectedEmailAddress = Objects.nonNull(emailAddress) ? emailAddress : EMPTY_STRING;
        assertThat(email.getEmailAddress(), is(equalTo(expectedEmailAddress)));
        assertThat(email.isPreferred(), is(equalTo(shouldBePreferred)));
    }

    private boolean hasEmailAddressCorresponding(Email email, String recordEmail) {
        return Objects.nonNull(recordEmail) && recordEmail.equals(email.getEmailAddress());
    }

    private void assertPhone(Phones phones, Record record) {
        assertThat(phones.getPhone(), IsCollectionWithSize.hasSize(1));
        var phone = phones.getPhone().getFirst();
        assertThat(phone.isPreferred(), is(equalTo(true)));
        assertThat(phone.getPhoneTypes().getPhoneType(), IsCollectionWithSize.hasSize(1));
        assertThat(phone.getPhoneTypes().getPhoneType().getFirst().getDesc(), is(equalTo(ContactInfoConverter.OFFICE)));
        if (nva.commons.core.StringUtils.isEmpty(record.getTlf())) {
            assertThat(phone.getPhoneNumber(), is(equalTo(EMPTY_STRING)));
        } else {
            assertThat(phone.getPhoneNumber(), is(equalTo(record.getTlf())));
        }
    }

    private void assertAddresses(List<Address> addresses, Record record) {
        var postAddressShouldExist = Objects.nonNull(record.getPadr());
        var visitationAddressShouldExist = Objects.nonNull(record.getVadr());
        var expectedAddressSize = (postAddressShouldExist ? 1 : 0) + (visitationAddressShouldExist ? 1 : 0);
        assertThat(addresses, IsCollectionWithSize.hasSize(expectedAddressSize));
        var postAddress = addresses.stream()
                              .filter(address -> hasLine1CorrespondingToRecord(address, record.getPadr()))
                              .findFirst()
                              .orElse(null);
        var visitationAddress = addresses.stream()
                                    .filter(address -> hasLine1CorrespondingToRecord(address, record.getVadr()))
                                    .findFirst()
                                    .orElse(null);
        if (postAddressShouldExist) {
            final String expectedLine5 = record.getLandkode().toUpperCase(Locale.ROOT) + HYPHEN + record.getBibnr();
            assertAddress(postAddress,
                          record.getPadr(),
                          expectedLine5,
                          record.getPpoststed(),
                          record.getPpostnr(),
                          record.getLandkode(),
                          true);
        }
        if (visitationAddressShouldExist) {
            final String expectedLine5 = record.getLandkode().toUpperCase(Locale.ROOT) + HYPHEN + record.getBibnr();
            assertAddress(visitationAddress,
                          record.getVadr(),
                          expectedLine5,
                          record.getVpoststed(),
                          record.getVpostnr(),
                          record.getLandkode(),
                          !postAddressShouldExist);
        }
    }

    private void assertAddress(Address address, String expectedLine1, String expectedLine5, String expectedCity,
                               String expectedPostalCode, String expectedCountry, boolean expectedPreferred) {
        assertThat(address, is(IsNull.notNullValue()));
        assertThat(address.getLine1(), is(equalTo(expectedLine1)));
        assertThat(address.getLine5(), is(equalTo(expectedLine5)));
        assertThat(address.isPreferred(), is(expectedPreferred));
        assertThat(address.getCity(), is(equalTo(expectedCity)));
        assertThat(address.getPostalCode(), is(equalTo(expectedPostalCode)));
        assertThat(address.getCountry().getValue(), is(equalTo(expectedCountry.toUpperCase(Locale.ROOT))));
        var expectedAddressType = ContactInfoConverter.WORK;
        assertThat(address.getAddressTypes().getAddressType(), IsCollectionWithSize.hasSize(1));
        assertThat(address.getAddressTypes().getAddressType().getFirst().getDesc(), is(equalTo(expectedAddressType)));
    }

    private boolean hasLine1CorrespondingToRecord(Address address, String recordAddr) {
        return recordAddr.equals(address.getLine1());
    }
}