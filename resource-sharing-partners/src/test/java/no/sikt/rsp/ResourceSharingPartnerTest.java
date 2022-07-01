package no.sikt.rsp;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static no.sikt.rsp.clients.AbstractHttpUrlConnectionApi.LOG_MESSAGE_COMMUNICATION_PROBLEM;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.RequestParametersEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.ResponseElementsEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3BucketEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3Entity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3ObjectEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.UserIdentityEntity;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.generated.Address;
import no.sikt.alma.generated.ContactInfo;
import no.sikt.alma.generated.Email;
import no.sikt.alma.generated.EmailDetails;
import no.sikt.alma.generated.Emails;
import no.sikt.alma.generated.IsoDetails;
import no.sikt.alma.generated.NcipP2PDetails;
import no.sikt.alma.generated.Partner;
import no.sikt.alma.generated.PartnerDetails.LocateProfile;
import no.sikt.alma.generated.Phones;
import no.sikt.alma.generated.ProfileType;
import no.sikt.alma.generated.Status;
import no.sikt.rsp.clients.alma.HttpUrlConnectionAlmaPartnerUpserter;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import test.utils.BasebibliotekGenerator;
import test.utils.EressurserBuilder;
import test.utils.RecordBuilder;
import test.utils.RecordSpecification;
import test.utils.WireMocker;

@SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "PMD.AvoidDuplicateLiterals"})
@WireMockTest
public class ResourceSharingPartnerTest {

    public static final RequestParametersEntity EMPTY_REQUEST_PARAMETERS = null;
    public static final ResponseElementsEntity EMPTY_RESPONSE_ELEMENTS = null;
    public static final UserIdentityEntity EMPTY_USER_IDENTITY = null;
    public static final long SOME_FILE_SIZE = 100L;
    private static final String BASEBIBLIOTEK_0030100_XML = "bb_0030100.xml";
    private static final String BASEBIBLIOTEK_2062200_XML = "bb_2062200.xml";
    private static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH = "/libCodeToAlmaCodeMapping.json";
    private static final String NO_0030100_ID = "NO-0030100";
    private static final String INVALID_BASEBIBLIOTEK_XML_STRING = "invalid";
    private static final String SHARED_CONFIG_BUCKET_NAME_ENV_VALUE = "SharedConfigBucket";
    private static final String HYPHEN = "-";
    private static final String ILL_SERVER_ENVIRONMENT_VALUE = "eu01.alma.exlibrisgroup.com";
    public static final int ILL_SERVER_PORT = 9001;

    private static final String NNCIP_SERVER = "https://nncipuri.org";
    private static final String EMAIL_ADR = "adr@example.com";
    private static final String EMAIL_BEST = "best@example.com";
    public static final String BASEBIBLIOTEK_REPORT = "basebibliotek-report";
    public static final String BIBLIOTEK_REST_PATH = "/basebibliotek/rest/bibnr/";
    public static final Context CONTEXT = mock(Context.class);
    private static final String BIBNR_RESOLVABLE_TO_ALMA_CODE = "0030100";
    private static final String INSTITUTION_CODE = "47BIBSYS_NB";
    private transient FakeS3Client s3Client;
    private transient S3Driver s3Driver;
    private transient ResourceSharingPartnerHandler resourceSharingPartnerHandler;

    private static final Environment mockedEnvironment = mock(Environment.class);

    @BeforeEach
    public void init(final WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ALMA_API_HOST)).thenReturn(
            UriWrapper.fromUri(wmRuntimeInfo.getHttpBaseUrl()).toString());
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            UriWrapper.fromUri(wmRuntimeInfo.getHttpBaseUrl()).addChild(BIBLIOTEK_REST_PATH).toString());
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.SHARED_CONFIG_BUCKET_NAME_ENV_NAME)).thenReturn(
            SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(
            ResourceSharingPartnerHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY)).thenReturn(
            LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.REPORT_BUCKET_ENVIRONMENT_NAME)).thenReturn(
            BASEBIBLIOTEK_REPORT);

        final String fullLibCodeToAlmaCodeMapping = IoUtils.stringFromResources(
            Path.of("fullLibCodeToAlmaCodeMapping.json"));

        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH), fullLibCodeToAlmaCodeMapping);

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment);
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void shouldBeAbleToReadAndPostRecordToAlma() throws IOException {
        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                                           IoUtils.stringFromResources(
                                                                               Path.of(BASEBIBLIOTEK_0030100_XML)));

        final S3Event s3Event = prepareBaseBibliotekFromXml(bibNrToXmlMap);

        WireMocker.mockAlmaGetResponsePartnerNotFound(NO_0030100_ID);
        WireMocker.mockAlmaPostResponse();
        Integer response = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        verify(getRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_PARTNER + "/" + NO_0030100_ID)));
        verify(postRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_PARTNER)));
        assertThat(response, is(notNullValue()));
        assertThat(response, is(1));
    }

    @Test
    public void shouldBeAbleToReadAndPutRecordToAlma() throws IOException {
        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                                           IoUtils.stringFromResources(
                                                                               Path.of(BASEBIBLIOTEK_0030100_XML)));

        final S3Event s3Event = prepareBaseBibliotekFromXml(bibNrToXmlMap);

        WireMocker.mockAlmaGetResponse(NO_0030100_ID);
        WireMocker.mockAlmaPutResponse(NO_0030100_ID);
        Integer response = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        verify(getRequestedFor(urlEqualTo(WireMocker.URL_PATH_PARTNER + "/" + NO_0030100_ID)));
        verify(putRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_PARTNER + "/" + NO_0030100_ID)));
        assertThat(response, is(notNullValue()));
        assertThat(response, is(1));
    }

    @Test
    public void shouldLogExceptionWhenS3ClientFails() {
        var s3Event = createS3Event(randomString());
        var expectedMessage = randomString();
        s3Client = new FakeS3ClientThrowingException(expectedMessage);
        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment);
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldSkipWhenCannotBeConvertedToBaseBibliotek() throws IOException {
        final String skippedBibBr = "1000000";
        final Map<String, String> bibNrToXmlMap = new HashMap<>();
        bibNrToXmlMap.put(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                          IoUtils.stringFromResources(Path.of(BASEBIBLIOTEK_0030100_XML)));
        bibNrToXmlMap.put(skippedBibBr, INVALID_BASEBIBLIOTEK_XML_STRING);

        final S3Event s3Event = prepareBaseBibliotekFromXml(bibNrToXmlMap);

        WireMocker.mockAlmaGetResponse(NO_0030100_ID);
        WireMocker.mockAlmaPutResponse(NO_0030100_ID);

        var expectedSuccessfulConversion = 1;
        var numberOfSuccessfulConversion = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        assertThat(numberOfSuccessfulConversion, is(equalTo(expectedSuccessfulConversion)));
    }

    @Test
    public void shouldExtractContactDetailsCorrectly() throws IOException {
        var withPaddr = true;
        var withVaddr = true;
        var specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE, true, null, randomBoolean(),
                                                    randomBoolean(), withPaddr, withVaddr, randomBoolean(),
                                                    randomString());
        var basebibliotekGenerator = new BasebibliotekGenerator(specification);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        assertContactInfo(partners.get(0).getContactInfo(), basebibliotek.getRecord().get(0));
    }

    @ParameterizedTest
    @MethodSource("provideIsilBibNRAndLandkodeSpecification")
    public void shouldRecordHandleIsilBibNrAndLandKode(final RecordSpecification recordSpecification,
                                                       final int expectedSize,
                                                       final boolean yieldsError) throws IOException {

        final List<Record> generatedRecords = new ArrayList<>();
        final S3Event s3Event = prepareBaseBibliotekFromRecordSpecifications(generatedRecords, recordSpecification);
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var expectedLogMessage = "Could not convert record, missing landkode, record";

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();

        assertThat(partners, hasSize(expectedSize));
        if (yieldsError) {
            assertThat(appender.getMessages(), containsString(expectedLogMessage));
        } else {
            var recordWithoutIsilButContainingBibNrAndLandKode = generatedRecords.get(0);
            var expectedCraftedPartnerCode = recordWithoutIsilButContainingBibNrAndLandKode.getLandkode()
                                                 .toUpperCase(Locale.ROOT)
                                             + "-"
                                             + recordWithoutIsilButContainingBibNrAndLandKode.getBibnr();
            assertThat(partners.get(0).getPartnerDetails().getCode(), is(equalTo(expectedCraftedPartnerCode)));
        }
    }

    private static Stream<Arguments> provideIsilBibNRAndLandkodeSpecification() {
        var withLandkode = true;
        var withIsil = true;
        var yieldsError = true;
        var bibNr = randomString();
        return Stream.of(Arguments.of(
                             new RecordSpecification(bibNr, !withLandkode, null, randomBoolean(),
                                                     randomBoolean(), randomBoolean(), randomBoolean(), !withIsil,
                                                     randomString()), 0, yieldsError),
                         Arguments.of(
                             new RecordSpecification(bibNr, withLandkode, null, randomBoolean(),
                                                     randomBoolean(), randomBoolean(), randomBoolean(), withIsil,
                                                     randomString()), 1, !yieldsError),
                         Arguments.of(
                             new RecordSpecification(bibNr, withLandkode, null, randomBoolean(),
                                                     randomBoolean(), randomBoolean(), randomBoolean(), !withIsil,
                                                     randomString()), 1, !yieldsError));
    }

    @Test
    void shouldExtractBasicPartnerDetailsCorrectly() throws IOException {
        var specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE, true, null,
                                                    randomBoolean(), randomBoolean(), randomBoolean(), randomBoolean(),
                                                    randomBoolean(), randomString());

        final List<Record> generatedRecords = new ArrayList<>();
        var s3Event = prepareBaseBibliotekFromRecordSpecifications(generatedRecords, specification);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        var expectedName = Objects.nonNull(generatedRecords.get(0).getInst())
                               ? generatedRecords.get(0).getInst().replaceAll("\n", " - ")
                               : StringUtils.EMPTY_STRING;

        assertThat(partners.get(0).getPartnerDetails().getName(), is(equalTo(expectedName)));
        var expectedAvgSupplyTime = 1;
        assertThat(partners.get(0).getPartnerDetails().getAvgSupplyTime(), is(equalTo(expectedAvgSupplyTime)));
        var expectedDeliveryDelay = 0;
        assertThat(partners.get(0).getPartnerDetails().getDeliveryDelay(), is(equalTo(expectedDeliveryDelay)));
        var expectedLendingSupported = true;
        assertThat(partners.get(0).getPartnerDetails().isLendingSupported(), is(equalTo(expectedLendingSupported)));
        var expectedLendingWorkflow = "Lending";
        assertThat(partners.get(0).getPartnerDetails().getLendingWorkflow(), is(equalTo(expectedLendingWorkflow)));
        var expectedBorrowingSupported = true;
        assertThat(partners.get(0).getPartnerDetails().isBorrowingSupported(), is(equalTo(expectedBorrowingSupported)));
        var expectedBorrowingWorkflow = "Borrowing";
        assertThat(partners.get(0).getPartnerDetails().getBorrowingWorkflow(), is(equalTo(expectedBorrowingWorkflow)));
    }

    @ParameterizedTest(name = "Should handle katsys codes differently when generating systemtype")
    @ValueSource(strings = {
        BaseBibliotekUtils.KATSYST_ALMA,
        BaseBibliotekUtils.KATSYST_BIBSYS,
        BaseBibliotekUtils.KATSYST_TIDEMANN
    })
    public void shouldExtractCertainDataIfAlmaOrBibsysLibrary(String katsyst) throws IOException {
        var withLandkode = true;
        var specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE, withLandkode, NNCIP_SERVER,
                                                    randomBoolean(), randomBoolean(), randomBoolean(), randomBoolean(),
                                                    randomBoolean(), katsyst);

        final List<Record> generatedRecords = new ArrayList<>();
        var s3Event = prepareBaseBibliotekFromRecordSpecifications(generatedRecords, specification);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        var isAlmaOrBibsys = BaseBibliotekUtils.isAlmaOrBibsysLibrary(katsyst);

        assertThat("Expected one mapped partner for katsyst " + katsyst, partners, hasSize(1));

        var expectedHoldingCode =
            isAlmaOrBibsys ? BIBNR_RESOLVABLE_TO_ALMA_CODE : null;
        assertThat(partners.get(0).getPartnerDetails().getHoldingCode(), is(equalTo(expectedHoldingCode)));

        var expectedSystemTypeValueValue =
            isAlmaOrBibsys ? PartnerConverter.SYSTEM_TYPE_VALUE_ALMA : PartnerConverter.SYSTEM_TYPE_VALUE_OTHER;
        assertThat(partners.get(0).getPartnerDetails().getSystemType().getValue(),
                   is(equalTo(expectedSystemTypeValueValue)));

        var expectedSystemTypeValueDesc = isAlmaOrBibsys ? PartnerConverter.SYSTEM_TYPE_DESC_ALMA
                                              : PartnerConverter.SYSTEM_TYPE_DESC_OTHER;
        assertThat(partners.get(0).getPartnerDetails().getSystemType().getDesc(),
                   is(equalTo(expectedSystemTypeValueDesc)));
    }

    @ParameterizedTest(name = "Should handle katsys codes differently when generating isoData")
    @ValueSource(strings = {BaseBibliotekUtils.KATSYST_ALMA, BaseBibliotekUtils.KATSYST_BIBSYS})
    public void shouldExtractPartnerDetailsProfileDataIsoCorrectly(final String katsyst) throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), katsyst)
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        final Partner partner = partners.get(0);

        assertIsoProfileDetailsPopulatedCorrectly(partner, record.getLandkode(), record.getBibnr());
    }

    @Test
    public void shouldExtractPartnerDetailsProfileDataNncipCorrectlyPreferringEmailBest() throws IOException {

        final Record record =
            new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                .withBibnr("1000000")
                .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                .withEpostBest(EMAIL_BEST)
                .withEpostAdr(EMAIL_ADR)
                .withEressurser(new EressurserBuilder().withNncipUri(NNCIP_SERVER).build())
                .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        final Partner partner = partners.get(0);

        assertNncipProfileDetailsPopulatedCorrectly(partner, record.getLandkode(), record.getBibnr(), EMAIL_BEST);
    }

    @Test
    public void shouldExtractPartnerDetailsProfileDataNncipCorrectlyFallingBackToEpostAdrIfBestIsMissing()
        throws IOException {

        final String emailAdr = "adr@example.com";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr("1000000")
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .withEpostAdr(emailAdr)
                                  .withEressurser(new EressurserBuilder().withNncipUri(NNCIP_SERVER).build())
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertNncipProfileDetailsPopulatedCorrectly(partner, record.getLandkode(), record.getBibnr(), emailAdr);
    }

    @Test
    public void shouldExtractPartnerDetailsProfileDataNncipCorrectlyIgnoringInvalidEmailAddresses() throws IOException {
        final String email = "invalid";

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr("1000000")
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .withEpostAdr(email)
                                  .withEpostBest(email)
                                  .withEressurser(new EressurserBuilder().withNncipUri(NNCIP_SERVER).build())
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertNncipProfileDetailsPopulatedCorrectly(partner, record.getLandkode(), record.getBibnr(), null);
    }

    @Test
    public void foreignNonAlmaOrBibsysLibrariesWithNncipShouldHavePartnerProfileTypeEmail() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr("1000000")
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_GERMAN)
                                  .withEpostAdr(EMAIL_ADR)
                                  .withEressurser(new EressurserBuilder().withNncipUri(NNCIP_SERVER).build())
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        final Partner partner = partners.get(0);

        assertEmailProfileDetailsPopulatedCorrectly(partner, EMAIL_ADR);
    }

    @ParameterizedTest
    @ValueSource(strings = {BaseBibliotekUtils.KATSYST_ALMA, BaseBibliotekUtils.KATSYST_BIBSYS})
    public void nonNorwegianAlmaOrBibsysLibrariesShouldHavePartnerProfileTypeEmail(final String katsyst)
        throws IOException {
        final String email = "adr@example.com";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), katsyst)
                                  .withBibnr("1000000")
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_GERMAN)
                                  .withEpostAdr(email)
                                  .withEpostBest(email)
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        final Partner partner = partners.get(0);

        assertEmailProfileDetailsPopulatedCorrectly(partner, email);
    }

    @Test
    public void shouldExtractProfileDetailsEmailPreferringEmailBest() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr("10000000")
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .withEpostBest(EMAIL_BEST)
                                  .withEpostAdr(EMAIL_ADR)
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();
        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertEmailProfileDetailsPopulatedCorrectly(partner, EMAIL_BEST);
    }

    @Test
    public void shouldExtractProfileDetailsEmailFallingBackToEpostAdrIfBestIsMissing() throws IOException {
        final String email = "adr@example.com";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .withEpostAdr(email)
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        final Partner partner = partners.get(0);

        assertEmailProfileDetailsPopulatedCorrectly(partner, email);
    }

    @Test
    public void shouldExtractProfileDetailsEmailCorrectlyIgnoringInvalidEmailAddresses() throws IOException {
        final String email = "invalid";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .withEpostBest(email)
                                  .withEpostAdr(email)
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        final Partner partner = partners.get(0);

        assertEmailProfileDetailsPopulatedCorrectly(partner, "");
    }

    @ParameterizedTest
    @MethodSource("provideStengtArguments")
    public void shouldCalculateStengtStatusCorrectly(String stengt, boolean withStengtFra, boolean withStengTil,
                                                     Status expectedStatus) throws IOException {

        final RecordSpecification specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                                          true, null,
                                                                          withStengtFra, withStengTil, randomBoolean(),
                                                                          randomBoolean(), randomBoolean(),
                                                                          randomString(), Collections.emptyList(),
                                                                          stengt);

        final List<Record> generatedRecords = new ArrayList<>();
        final S3Event s3Event = prepareBaseBibliotekFromRecordSpecifications(generatedRecords, specification);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        assertThat(partners.get(0).getPartnerDetails().getStatus(), is(equalTo(expectedStatus)));
    }

    private static Stream<Arguments> provideStengtArguments() {
        return Stream.of(Arguments.of("U", false, false, Status.INACTIVE),
                         Arguments.of("X", false, false, Status.INACTIVE),
                         Arguments.of(null, true, false, Status.INACTIVE),
                         Arguments.of(null, false, true, Status.INACTIVE),
                         Arguments.of(null, true, true, Status.INACTIVE),
                         Arguments.of(null, false, false, Status.ACTIVE));
    }

    @Test
    public void shouldSkipWhenBasebibliotekFails() throws IOException {
        final String bibNrSuccess = "1000000";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr(bibNrSuccess)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .withEpostBest(EMAIL_BEST)
                                  .withEpostAdr(EMAIL_ADR)
                                  .build();

        final String failingBibNr = "1234567";
        final S3Event s3Event = prepareBaseBibliotekFromRecords(failingBibNr, record);

        final String almaCode = record.getLandkode().toUpperCase(Locale.ROOT) + "-" + bibNrSuccess;
        WireMocker.mockAlmaGetResponse(almaCode);
        WireMocker.mockAlmaPutResponse(almaCode);

        WireMocker.mockBassebibliotekFailure(failingBibNr);

        var expectedNumberOfSuccessfulConversions = 1;

        var numberOfSuccessfulConversions = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        assertThat(numberOfSuccessfulConversions, is(equalTo(expectedNumberOfSuccessfulConversions)));
    }

    @ParameterizedTest(name = "Should handle katsys codes differently when generating institution code")
    @ValueSource(strings = {
        BaseBibliotekUtils.KATSYST_ALMA,
        BaseBibliotekUtils.KATSYST_BIBSYS,
        BaseBibliotekUtils.KATSYST_TIDEMANN
    })
    public void shouldExtractAlmaCodeInPartnerDetailsCorrectly(final String katsyst) throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), katsyst).withBibnr(
            BIBNR_RESOLVABLE_TO_ALMA_CODE).withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN).build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        final Partner partner = partners.get(0);

        final String expectedInstitutionCode;
        if (BaseBibliotekUtils.isAlmaOrBibsysLibrary(katsyst)) {
            expectedInstitutionCode = INSTITUTION_CODE;
        } else {
            expectedInstitutionCode = "";
        }
        assertThat(partner.getPartnerDetails().getInstitutionCode(), is(expectedInstitutionCode));
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

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment);

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));

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

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        when(mockedEnvironment.readEnv(
            ResourceSharingPartnerHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY)).thenReturn(
            LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);

        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH),
                            IoUtils.stringFromResources(Path.of("emptyLibCodeToAlmaCodeMapping.json")));

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment);

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));

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

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        when(mockedEnvironment.readEnv(
            ResourceSharingPartnerHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY)).thenReturn(
            LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);

        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH),
                            IoUtils.stringFromResources(Path.of("invalidLibCodeToAlmaCodeMapping.json")));

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment);

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));

        assertThat(appender.getMessages(), containsString(LibCodeToAlmaCodeEntry.FIELD_IS_NULL_OR_EMPTY_MESSAGE));
    }

    @Test
    void shouldReportSuccessfulWhenAllWorksProperly() throws IOException {
        final String bibNr = "1000000";
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                                  .withBibnr(bibNr)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .withEpostBest(EMAIL_BEST)
                                  .withEpostAdr(EMAIL_ADR)
                                  .build();

        final UnixPath s3Path = randomS3Path();
        final S3Event s3Event = prepareBaseBibliotekFromRecords(s3Path, record);

        final String almaCode = record.getLandkode().toUpperCase(Locale.ROOT) + "-" + bibNr;
        WireMocker.mockAlmaGetResponse(almaCode);
        WireMocker.mockAlmaPutResponse(almaCode);

        var numberOfSuccessFulLibraries = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var expectedNumberOfSuccessFulLibraries = 1;
        assertThat(numberOfSuccessFulLibraries, is(equalTo(expectedNumberOfSuccessFulLibraries)));

        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(ResourceSharingPartnerHandler.REPORT_FILE_NAME_PREFIX + s3Path.toString()));
        assertThat(report,
                   containsString(bibNr + StringUtils.SPACE + ResourceSharingPartnerHandler.OK_REPORT_MESSAGE));
    }

    @Test
    void shouldGenerateReportWhenAlmaContactFailure() throws IOException {
        var bibNr = "1234567";
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                         .withBibnr(bibNr)
                         .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                         .withEpostBest(EMAIL_BEST)
                         .withEpostAdr(EMAIL_ADR)
                         .build();

        var s3Path = randomS3Path();
        var s3Event = prepareBaseBibliotekFromRecords(s3Path, record);

        WireMocker.mockAlmaForbiddenGetResponse("NO-" + bibNr);
        WireMocker.mockAlmaForbiddenPostResponse("NO-" + bibNr);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(ResourceSharingPartnerHandler.REPORT_FILE_NAME_PREFIX + s3Path.toString()));

        assertThat(report, containsString(
            bibNr + ResourceSharingPartnerHandler.COULD_NOT_CONTACT_ALMA_REPORT_MESSAGE));
    }

    @Test
    void shouldGenerateReportWhenBasebibliotekFetchFailure() throws IOException {
        var basebibliotekFailureBibnr = "2000000";

        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(basebibliotekFailureBibnr,
                                                                           INVALID_BASEBIBLIOTEK_XML_STRING);

        var s3Path = randomS3Path();
        var s3Event = prepareBaseBibliotekFromXml(s3Path, bibNrToXmlMap);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(ResourceSharingPartnerHandler.REPORT_FILE_NAME_PREFIX + s3Path.toString()));
        assertThat(report, containsString(
            basebibliotekFailureBibnr + ResourceSharingPartnerHandler.COULD_NOT_FETCH_BASEBIBLIOTEK_REPORT_MESSAGE));
    }

    @Test
    void shouldGenerateReportWhenConversionToPartnerFailure() throws IOException {
        var bibNr = "3";
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), BaseBibliotekUtils.KATSYST_TIDEMANN)
                         .withBibnr(bibNr)
                         .withLandkode(null)
                         .withEpostBest(EMAIL_BEST)
                         .withEpostAdr(EMAIL_ADR)
                         .build();

        var s3Path = randomS3Path();
        var s3Event = prepareBaseBibliotekFromRecords(s3Path, record);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var reports3Driver = new S3Driver(s3Client, BASEBIBLIOTEK_REPORT);
        var report = reports3Driver.getFile(
            UnixPath.of(ResourceSharingPartnerHandler.REPORT_FILE_NAME_PREFIX + s3Path.toString()));

        assertThat(report, containsString(
            bibNr + ResourceSharingPartnerHandler.COULD_NOT_CONVERT_TO_PARTNER_REPORT_MESSAGE));
    }

    @ParameterizedTest(name = "Should handle katsys codes differently when generating locateProfile")
    @ValueSource(strings = {
        BaseBibliotekUtils.KATSYST_ALMA,
        BaseBibliotekUtils.KATSYST_BIBSYS,
        BaseBibliotekUtils.KATSYST_TIDEMANN
    })
    public void shouldPopulateLocateProfileInPartnerDetailsCorrectly(final String katsyst) throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), katsyst)
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .build();

        final S3Event s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final List<Partner> partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        final Partner partner = partners.get(0);

        final LocateProfile locateProfile = partner.getPartnerDetails().getLocateProfile();
        if (BaseBibliotekUtils.isAlmaOrBibsysLibrary(katsyst)) {
            assertThat(locateProfile, notNullValue());
            final String expectedLocateProfile = "LOCATE_NB";
            assertThat(locateProfile.getValue(), is(expectedLocateProfile));
        } else {
            assertThat(locateProfile, nullValue());
        }
    }

    @Test
    public void shouldIgnorePartnerAndLogProblemIfBaseBibliotekIsNotAvailable() throws IOException {
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            UriWrapper.fromUri("http://localhost:9999").toString());

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment);

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        final Integer count = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        assertThat(count, is(0));
        assertThat(appender.getMessages(), containsString(LOG_MESSAGE_COMMUNICATION_PROBLEM));
    }

    @Test
    public void shouldIgnorePartnerAndLogProblemIfAlmaIsNotAvailable() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), "BIBSYS")
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .build();

        var s3Event = prepareBaseBibliotekFromRecords(record);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ALMA_API_HOST)).thenReturn(
            UriWrapper.fromUri("http://localhost:9999").toString());

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment);

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        final Integer count = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        assertThat(count, is(0));
        assertThat(appender.getMessages(),
                   containsString(HttpUrlConnectionAlmaPartnerUpserter.LOG_MESSAGE_COMMUNICATION_PROBLEM));
    }

    @Test
    public void shouldIgnorePartnerAndLogProblemIfAlmaGetPartnerReturnsBadRequestWithUnhandledErrorCode()
        throws IOException {

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), "BIBSYS")
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .build();

        var s3Event = prepareBaseBibliotekFromRecords(record);

        WireMocker.mockAlmaGetResponseBadRequestNotPartnerNotFound(NO_0030100_ID);

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        final Integer count = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        assertThat(count, is(0));
        assertThat(appender.getMessages(),
                   containsString(
                       HttpUrlConnectionAlmaPartnerUpserter.UNEXPECTED_RESPONSE_FETCHING_PARTNER_LOG_MESSAGE_PREFIX));
    }

    @Test
    public void shouldIgnorePartnerAndLogProblemIfAlmaCreatePartnerReturnsBadRequestWithUnhandledErrorCode()
        throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), "BIBSYS")
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .build();

        var s3Event = prepareBaseBibliotekFromRecords(record);

        WireMocker.mockAlmaGetResponsePartnerNotFound(NO_0030100_ID);
        WireMocker.mockAlmaPostResponseBadRequest();

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        final Integer count = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        assertThat(count, is(0));
        assertThat(appender.getMessages(),
                   containsString(
                       HttpUrlConnectionAlmaPartnerUpserter.UNEXPECTED_RESPONSE_CREATING_PARTNER_LOG_MESSAGE_PREFIX));
    }

    @Test
    public void shouldIgnorePartnerAndLogProblemIfAlmaUpdatePartnerReturnsBadRequestWithUnhandledErrorCode()
        throws IOException {

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), "BIBSYS")
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode(BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN)
                                  .build();

        var s3Event = prepareBaseBibliotekFromRecords(record);
        WireMocker.mockAlmaGetResponse(NO_0030100_ID);
        WireMocker.mockAlmaPutResponseBadRequest(NO_0030100_ID);

        final TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        final Integer count = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        assertThat(count, is(0));
        assertThat(appender.getMessages(),
                   containsString(
                       HttpUrlConnectionAlmaPartnerUpserter.UNEXPECTED_RESPONSE_UPDATING_PARTNER_LOG_MESSAGE_PREFIX));
    }

    @Test
    public void shouldUseProfileTypeEmailInProfileDetailsWhenNncipUriHasNoValueInEressurser() throws IOException {
        final String bibNr = "2062200";
        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(bibNr,
                                                                           IoUtils.stringFromResources(
                                                                               Path.of(BASEBIBLIOTEK_2062200_XML)));

        final S3Event s3Event = prepareBaseBibliotekFromXml(bibNrToXmlMap);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        final Partner partner = resourceSharingPartnerHandler.getPartners().get(0);

        final String emailIn2062200xml = "biblioteket@krodsherad.kommune.no";
        assertEmailProfileDetailsPopulatedCorrectly(partner, emailIn2062200xml);
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

        final UnixPath path = (s3Path == null) ? randomS3Path() : s3Path;
        var uri = s3Driver.insertFile(path, fileContent);

        return createS3Event(uri);
    }

    private S3Event prepareBaseBibliotekFromRecordSpecifications(final List<Record> generatedRecords,
                                                                 final RecordSpecification... recordSpecifications)
        throws IOException {

        final String fileContent =
            Arrays.stream(recordSpecifications).map(RecordSpecification::getBibNr).collect(Collectors.joining("\n"));

        // prepare mocks:
        Arrays.stream(recordSpecifications).forEach(recordSpecification -> {
            var basebibliotekGenerator = new BasebibliotekGenerator(recordSpecification);
            var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
            var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);

            WireMocker.mockBasebibliotekXml(basebibliotekXml, recordSpecification.getBibNr());

            generatedRecords.addAll(basebibliotek.getRecord());
        });

        var uri = s3Driver.insertFile(randomS3Path(), fileContent);

        return createS3Event(uri);
    }

    private S3Event prepareBaseBibliotekFromXml(final UnixPath s3Path, final Map<String, String> bibNrToXmlMap)
        throws IOException {

        final String fileContent = String.join("\n", bibNrToXmlMap.keySet());

        // prepare mocks:
        bibNrToXmlMap.forEach((key, value) -> WireMocker.mockBasebibliotekXml(value, key));

        var path = (s3Path == null) ? randomS3Path() : s3Path;
        var uri = s3Driver.insertFile(path, fileContent);

        return createS3Event(uri);
    }

    private S3Event prepareBaseBibliotekFromXml(final Map<String, String> bibNrToXmlMap) throws IOException {
        return prepareBaseBibliotekFromXml(null, bibNrToXmlMap);
    }

    private void assertIsoProfileDetailsPopulatedCorrectly(final Partner partner,
                                                           final String landkode,
                                                           final String bibnr) {

        IsoDetails details = partner.getPartnerDetails().getProfileDetails().getIsoDetails();

        final String expectedIsoSymbol = landkode.toUpperCase(Locale.ROOT) + HYPHEN + bibnr;

        assertThat(details.getIllPort(), is(ILL_SERVER_PORT));
        assertThat(details.getIsoSymbol(), is(expectedIsoSymbol));
        assertThat(details.getIllServer(), is(ILL_SERVER_ENVIRONMENT_VALUE));
        assertThat(details.isSharedBarcodes(), is(true));
    }

    private void assertNncipProfileDetailsPopulatedCorrectly(Partner partner,
                                                             String landkode,
                                                             String bibnr,
                                                             String expectedEmail) {

        final ProfileType profileType = partner.getPartnerDetails().getProfileDetails().getProfileType();

        assertThat(profileType, is(ProfileType.NCIP_P_2_P));

        final NcipP2PDetails details = partner.getPartnerDetails().getProfileDetails().getNcipP2PDetails();

        final String expectedPartnerSymbol = landkode.toUpperCase(Locale.ROOT) + HYPHEN + bibnr;

        assertThat(details.getRequestExpiryType().getDesc(), is("No expiry"));
        assertThat(details.getRequestExpiryType().getValue(), is("NO_EXPIRY"));
        assertThat(details.getIllServer(), is(NNCIP_SERVER));
        assertThat(details.getPartnerSymbol(), is(expectedPartnerSymbol));
        assertThat(details.getGeneralUserIdType().getDesc(), is("barcode"));
        assertThat(details.getGeneralUserIdType().getValue(), is("BARCODE"));
        assertThat(details.getEmailAddress(), is(expectedEmail));
        assertThat(details.getResendingOverdueMessageInterval(),
                   is(PartnerConverter.RESENDING_OVERDUE_MESSAGE_INTERVAL));
    }

    private void assertEmailProfileDetailsPopulatedCorrectly(Partner partner, String expectedEmail) {
        final ProfileType profileType = partner.getPartnerDetails().getProfileDetails().getProfileType();

        assertThat(profileType, is(ProfileType.EMAIL));

        final EmailDetails details = partner.getPartnerDetails().getProfileDetails().getEmailDetails();

        assertThat(details.getEmail(), is(expectedEmail));
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
        assertThat(emails.getEmail(), hasSize(expectedEmailsSize));
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
        var expectedEmailTypes = List.of("claimMail", "orderMail", "paymentMail", "queries", "returnsMail");
        assertThat(email.getEmailTypes().getEmailType(), hasSize(expectedEmailTypes.size()));
        expectedEmailTypes.forEach(
            emailType -> assertThat(email.getEmailTypes().getEmailType(), hasItem(containsString(emailType))));
        var expectedEmailAddress = Objects.nonNull(emailAddress) ? emailAddress : StringUtils.EMPTY_STRING;
        assertThat(email.getEmailAddress(), is(equalTo(expectedEmailAddress)));
        assertThat(email.isPreferred(), is(equalTo(shouldBePreferred)));
    }

    private boolean hasEmailAddressCorresponding(Email email, String recordEmail) {
        return Objects.nonNull(recordEmail) && recordEmail.equals(email.getEmailAddress());
    }

    private void assertPhone(Phones phones, Record record) {
        var expectedPhoneTypes = List.of("claimPhone", "orderPhone", "paymentPhone", "returnsPhone");
        assertThat(phones.getPhone(), hasSize(1));
        var phone = phones.getPhone().get(0);
        assertThat(phone.isPreferred(), is(equalTo(true)));
        assertThat(phone.getPhoneTypes().getPhoneType(), hasSize(expectedPhoneTypes.size()));
        expectedPhoneTypes.forEach(expectedPhoneType -> assertThat(phone.getPhoneTypes().getPhoneType(),
                                                                   hasItem(containsString(expectedPhoneType))));
        if (StringUtils.isEmpty(record.getTlf())) {
            assertThat(phone.getPhoneNumber(), is(equalTo(StringUtils.EMPTY_STRING)));
        } else {
            assertThat(phone.getPhoneNumber(), is(equalTo(record.getTlf())));
        }
    }

    private void assertAddresses(List<Address> addresses, Record record) {
        var postAddressShouldExist = Objects.nonNull(record.getPadr());
        var visitationAddressShouldExist = Objects.nonNull(record.getVadr());
        var expectedAddressSize = (postAddressShouldExist ? 1 : 0) + (visitationAddressShouldExist ? 1 : 0);
        assertThat(addresses, hasSize(expectedAddressSize));
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
        var expectedAddressTypes = List.of("billing", "claim", "order", "payment", "returns", "shipping");
        assertThat(address.getAddressTypes().getAddressType(), hasSize(expectedAddressTypes.size()));
        expectedAddressTypes.forEach(expectedAddressType -> assertThat(address.getAddressTypes().getAddressType(),
                                                                       hasItem(containsString(expectedAddressType))));
    }

    private boolean hasLine1CorrespondingToRecord(Address address, String recordAddr) {
        return recordAddr.equals(address.getLine1());
    }

    private UnixPath randomS3Path() {
        return UnixPath.of(randomString());
    }

    private S3Event createS3Event(URI uri) {
        return createS3Event(UriWrapper.fromUri(uri).toS3bucketPath().toString());
    }

    private S3Event createS3Event(String expectedObjectKey) {
        var eventNotification = new S3EventNotificationRecord(randomString(), randomString(), randomString(),
                                                              randomDate(), randomString(), EMPTY_REQUEST_PARAMETERS,
                                                              EMPTY_RESPONSE_ELEMENTS,
                                                              createS3Entity(expectedObjectKey), EMPTY_USER_IDENTITY);
        return new S3Event(List.of(eventNotification));
    }

    private S3Entity createS3Entity(String expectedObjectKey) {
        var bucket = new S3BucketEntity(randomString(), EMPTY_USER_IDENTITY, randomString());
        var object = new S3ObjectEntity(expectedObjectKey, SOME_FILE_SIZE, randomString(), randomString(),
                                        randomString());
        var schemaVersion = randomString();
        return new S3Entity(randomString(), bucket, object, schemaVersion);
    }

    private String randomDate() {
        return Instant.now().toString();
    }

    private static class FakeS3ClientThrowingException extends FakeS3Client {

        private final transient String expectedErrorMessage;

        public FakeS3ClientThrowingException(String expectedErrorMessage) {
            super();
            this.expectedErrorMessage = expectedErrorMessage;
        }

        @Override
        public <ReturnT> ReturnT getObject(GetObjectRequest getObjectRequest,
                                           ResponseTransformer<GetObjectResponse, ReturnT> responseTransformer) {
            throw new RuntimeException(expectedErrorMessage);
        }
    }
}
