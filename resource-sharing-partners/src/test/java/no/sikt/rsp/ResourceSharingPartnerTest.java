package no.sikt.rsp;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
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
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
import no.sikt.alma.generated.Phones;
import no.sikt.alma.generated.ProfileType;
import no.sikt.alma.generated.Status;
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

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ResourceSharingPartnerTest {

    public static final RequestParametersEntity EMPTY_REQUEST_PARAMETERS = null;
    public static final ResponseElementsEntity EMPTY_RESPONSE_ELEMENTS = null;
    public static final UserIdentityEntity EMPTY_USER_IDENTITY = null;
    public static final long SOME_FILE_SIZE = 100L;
    private static final String BASEBIBLIOTEK_0030100_XML = "bb_0030100.xml";
    private static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH = "/libCodeToAlmaCodeMapping.json";
    private static final String NO_0030100_ID = "NO-0030100";
    private static final String INVALID_BASEBIBLIOTEK_XML_STRING = "invalid";
    public static final String ALMA = "alma";
    public static final String BIBSYS = "bibsys";
    public static final String TIDEMANN = "Tidemann";
    public static final String OTHER = "other";
    private static final String SHARED_CONFIG_BUCKET_NAME_ENV_VALUE = "SharedConfigBucket";

    private static final String ILL_SERVER_ENVIRONMENT_VALUE = "eu01.alma.exlibrisgroup.com";
    public static final int ILL_SERVER_PORT = 9001;

    private static final String NNCIP_SERVER = "https://nncipuri.org";
    private static final String EMAIL_ADR = "adr@example.com";
    private static final String EMAIL_BEST = "best@example.com";
    private transient ResourceSharingPartnerHandler resourceSharingPartnerHandler;
    public static final String BIBLIOTEK_REST_PATH = "/basebibliotek/rest/bibnr/";
    public static final Context CONTEXT = mock(Context.class);
    private static final String BIBNR_RESOLVABLE_TO_ALMA_CODE = "0030100";
    private static final String RESOLVED_ALMA_CODE = "NB";
    private transient FakeS3Client s3Client;
    private transient S3Driver s3Driver;

    private static final Environment mockedEnvironment = mock(Environment.class);

    @BeforeEach
    public void init() throws IOException {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        WireMocker.startWiremockServer();
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ALMA_API_HOST))
            .thenReturn(UriWrapper.fromUri(WireMocker.serverUri).toString());
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME))
            .thenReturn(UriWrapper.fromUri(WireMocker.serverUri).addChild(BIBLIOTEK_REST_PATH).toString());
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.SHARED_CONFIG_BUCKET_NAME_ENV_NAME))
            .thenReturn(SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY))
            .thenReturn(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);

        final String fullLibCodeToAlmaCodeMapping = IoUtils.stringFromResources(
            Path.of("fullLibCodeToAlmaCodeMapping.json"));

        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH),
                            fullLibCodeToAlmaCodeMapping);

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment,
                                                                          WireMocker.httpClient);
    }

    @AfterEach
    public void tearDown() {
        WireMocker.stopWiremockServer();
    }

    @Test
    public void shouldBeAbleToReadAndPostRecordToAlma() throws IOException {
        var baseBibliotek0030100 = IoUtils.stringFromResources(Path.of(BASEBIBLIOTEK_0030100_XML));
        WireMocker.mockBasebibliotekXml(baseBibliotek0030100, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);
        WireMocker.mockAlmaGetResponseNotFound();
        WireMocker.mockAlmaPostResponse();
        Integer response = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        verify(getRequestedFor(urlPathMatching(WireMocker.URL_PATH_PARTNER + NO_0030100_ID)));
        verify(postRequestedFor(urlPathMatching(WireMocker.URL_PATH_PARTNER + NO_0030100_ID)));
        assertThat(response, is(notNullValue()));
        assertThat(response, is(1));
    }

    @Test
    public void shouldBeAbleToReadAndPutRecordToAlma() throws IOException {
        var baseBibliotek0030100 = IoUtils.stringFromResources(Path.of(BASEBIBLIOTEK_0030100_XML));
        WireMocker.mockBasebibliotekXml(baseBibliotek0030100, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);
        WireMocker.mockAlmaGetResponse();
        WireMocker.mockAlmaPutResponse();
        Integer response = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        verify(getRequestedFor(urlPathMatching(WireMocker.URL_PATH_PARTNER + NO_0030100_ID)));
        verify(putRequestedFor(urlPathMatching(WireMocker.URL_PATH_PARTNER + NO_0030100_ID)));
        assertThat(response, is(notNullValue()));
        assertThat(response, is(1));
    }

    @Test
    public void shouldLogExceptionWhenS3ClientFails() {
        var s3Event = createS3Event(randomString());
        var expectedMessage = randomString();
        s3Client = new FakeS3ClientThrowingException(expectedMessage);
        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment,
                                                                          WireMocker.httpClient);
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldSkipWhenCannotBeConvertedToBaseBibliotek() throws IOException {
        WireMocker.mockBasebibliotekXml(INVALID_BASEBIBLIOTEK_XML_STRING, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);
        var expectedSuccessfulConversion = 0;
        var numberOfSuccessfulConversion = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
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
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        assertContactInfo(partners.get(0).getContactInfo(), basebibliotek.getRecord().get(0));
    }

    @ParameterizedTest
    @MethodSource("provideIsilBibNRAndLandkodeSpecification")
    public void shouldRecordHandleIsilBibNrAndLandKode(RecordSpecification recordSpecification,
                                                       int expectedSize,
                                                       boolean yieldsError,
                                                       boolean withIsil) throws IOException {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var expectedLogMessage = "Could not convert record, missing landkode, record";
        var basebibliotekGenerator = new BasebibliotekGenerator(recordSpecification);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var bibNr = basebibliotek.getRecord().get(0).getBibnr();
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();

        assertThat(partners, hasSize(expectedSize));
        if (yieldsError) {
            assertThat(appender.getMessages(), containsString(expectedLogMessage));
        } else {
            var recordWithoutIsilButContainingBibNrAndLandKode = basebibliotek.getRecord().get(0);
            var expectedCraftedPartnerCode =
                recordWithoutIsilButContainingBibNrAndLandKode.getLandkode().toUpperCase(Locale.ROOT)
                + "-"
                + recordWithoutIsilButContainingBibNrAndLandKode.getBibnr();
            assertThat(partners.get(0).getPartnerDetails().getCode(),
                       is(equalTo(expectedCraftedPartnerCode)));
        }
    }

    private static Stream<Arguments> provideIsilBibNRAndLandkodeSpecification() {
        var withLandkode = true;
        var withIsil = true;
        var yieldsError = true;
        var bibNr = randomString();
        return Stream.of(
            Arguments.of(new RecordSpecification(bibNr,
                                                 !withLandkode,
                                                 null,
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 !withIsil,
                                                 randomString()), 0, yieldsError, !withIsil),
            Arguments.of(new RecordSpecification(bibNr,
                                                 withLandkode,
                                                 null,
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 withIsil,
                                                 randomString()), 1, !yieldsError, withIsil),
            Arguments.of(new RecordSpecification(bibNr,
                                                 withLandkode,
                                                 null,
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 randomBoolean(),
                                                 !withIsil,
                                                 randomString()), 1, !yieldsError, !withIsil));
    }

    @Test
    void shouldExtractBasicPartnerDetailsCorrectly() throws IOException {
        var specifiation = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                   true,
                                                   null, randomBoolean(),
                                                   randomBoolean(),
                                                   randomBoolean(),
                                                   randomBoolean(),
                                                   randomBoolean(),
                                                   randomString());
        var basebibliotekGenerator = new BasebibliotekGenerator(specifiation);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();

        var expectedName = Objects.nonNull(basebibliotek.getRecord().get(0).getInst())
                               ? basebibliotek.getRecord().get(0).getInst().replaceAll("\n", " - ")
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

    @ParameterizedTest(name = "Should handle katsys codes differently")
    @ValueSource(strings = {ALMA, BIBSYS, TIDEMANN})
    public void shouldExtractCertainDataIfAlmaOrBibsysLibrary(String katsys) throws IOException {
        var withLandkode = true;
        var specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                    withLandkode,
                                                    NNCIP_SERVER,
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    katsys);
        var basebibliotekGenerator = new BasebibliotekGenerator(specification);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        var isAlmaOrBibsys = BIBSYS.equals(katsys) || ALMA.equals(katsys);

        assertThat("Expected one mapped partner for katsyst " + katsys, partners, hasSize(1));

        var expectedHoldingCode = isAlmaOrBibsys
                                      ? basebibliotek.getRecord().get(0).getLandkode().toUpperCase(Locale.ROOT)
                                        + BIBNR_RESOLVABLE_TO_ALMA_CODE
                                      : null;
        assertThat(partners.get(0).getPartnerDetails().getHoldingCode(), is(equalTo(expectedHoldingCode)));

        var expectedSystemTypeValueValue = isAlmaOrBibsys
                                               ? ALMA.toUpperCase(Locale.ROOT) : OTHER.toUpperCase(Locale.ROOT);
        assertThat(partners.get(0).getPartnerDetails().getSystemType().getValue(),
                   is(equalTo(expectedSystemTypeValueValue)));

        var expectedSystemTypeValueDesc = isAlmaOrBibsys ? ALMA : OTHER;
        assertThat(partners.get(0).getPartnerDetails().getSystemType().getDesc(),
                   is(equalTo(expectedSystemTypeValueDesc)));
        //TODO:LocateProfile in partnerDetails should be set when isAlmaOrBibsys, otherwise null. SMILE-1573
    }

    @ParameterizedTest(name = "Should handle katsys codes differently")
    @ValueSource(strings = {ALMA, BIBSYS})
    public void shouldExtractPartnerDetailsProfileDataIsoCorrectly(final String katsys) throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), katsys)
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode("1")
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var bibNr = record.getBibnr();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        var s3Event = createS3Event(uri);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertIsoProfileDetailsPopulatedCorrectly(partner, record.getBibnr());
    }

    @Test
    public void shouldExtractPartnerDetailsProfileDataNncipCorrectlyPreferringEmailBest() throws IOException {

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode("1")
                                  .withEpostBest(EMAIL_BEST)
                                  .withEpostAdr(EMAIL_ADR)
                                  .withEressurser(
                                      new EressurserBuilder().withNncipUri(NNCIP_SERVER)
                                          .build())
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);

        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var bibNr = record.getBibnr();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        var s3Event = createS3Event(uri);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertNncipProfileDetailsPopulatedCorrectly(partner, record.getBibnr(), EMAIL_BEST);
    }

    @Test
    public void shouldExtractPartnerDetailsProfileDataNncipCorrectlyFallingBackToEpostAdrIfBestIsMissing()
        throws IOException {
        final String emailAdr = "adr@example.com";

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode("1")
                                  .withEpostAdr(emailAdr)
                                  .withEressurser(
                                      new EressurserBuilder().withNncipUri(NNCIP_SERVER)
                                          .build())
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);

        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var bibNr = record.getBibnr();
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        var s3Event = createS3Event(uri);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertNncipProfileDetailsPopulatedCorrectly(partner, record.getBibnr(), emailAdr);
    }

    @Test
    public void shouldExtractPartnerDetailsProfileDataNncipCorrectlyIgnoringInvalidEmailAddresses() throws IOException {
        final String email = "invalid";

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode("1")
                                  .withEpostAdr(email)
                                  .withEpostBest(email)
                                  .withEressurser(
                                      new EressurserBuilder().withNncipUri(NNCIP_SERVER)
                                          .build())
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var bibNr = record.getBibnr();
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        var s3Event = createS3Event(uri);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertNncipProfileDetailsPopulatedCorrectly(partner, record.getBibnr(), null);
    }

    @Test
    public void shouldExtractProfileDetailsEmailPreferringEmailBest() throws IOException {

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode("1")
                                  .withEpostBest(EMAIL_BEST)
                                  .withEpostAdr(EMAIL_ADR)
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var bibNr = record.getBibnr();
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        var s3Event = createS3Event(uri);

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

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode("1")
                                  .withEpostAdr(email)
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var bibNr = record.getBibnr();
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        var s3Event = createS3Event(uri);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();
        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertEmailProfileDetailsPopulatedCorrectly(partner, email);
    }

    @Test
    public void shouldExtractProfileDetailsEmailCorrectlyIgnoringInvalidEmailAddresses() throws IOException {
        final String email = "invalid";

        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), TIDEMANN)
                                  .withBibnr("1")
                                  .withLandkode("1")
                                  .withEpostBest(email)
                                  .withEpostAdr(email)
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var bibNr = record.getBibnr();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        var s3Event = createS3Event(uri);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, bibNr);
        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME)).thenReturn(
            ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();
        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        assertEmailProfileDetailsPopulatedCorrectly(partner, "");
    }

    @ParameterizedTest
    @MethodSource("provideStengtArguments")
    public void shouldCalculateStengtStatusCorrectly(String stengt, boolean withStengtFra, boolean withStengTil,
                                                     Status expectedStatus) throws IOException {
        var specification = new RecordSpecification(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                    true,
                                                    null,
                                                    withStengtFra,
                                                    withStengTil,
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    randomBoolean(),
                                                    randomString(),
                                                    Collections.emptyList(),
                                                    stengt);
        var basebibliotekGenerator = new BasebibliotekGenerator(specification);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        assertThat(partners.get(0).getPartnerDetails().getStatus(), is(equalTo(expectedStatus)));
    }

    private static Stream<Arguments> provideStengtArguments() {
        return Stream.of(
            Arguments.of("U", false, false, Status.INACTIVE),
            Arguments.of("X", false, false, Status.INACTIVE),
            Arguments.of(null, true, false, Status.INACTIVE),
            Arguments.of(null, false, true, Status.INACTIVE),
            Arguments.of(null, true, true, Status.INACTIVE),
            Arguments.of(null, false, false, Status.ACTIVE)
        );
    }

    @Test
    public void shouldSkipWhenBasebibliotekFails() throws IOException {
        var bibNr = randomString();
        var uri = s3Driver.insertFile(randomS3Path(), bibNr);
        var s3Event = createS3Event(uri);
        WireMocker.mockBassebibliotekFailure(bibNr);
        var expectedNumberOfSuccessfulConversion = 0;
        var numberOfSuccessFulConversion = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        assertThat(numberOfSuccessFulConversion, is(equalTo(expectedNumberOfSuccessfulConversion)));
    }

    @ParameterizedTest(name = "Should handle katsys codes differently")
    @ValueSource(strings = {ALMA, BIBSYS, TIDEMANN})
    public void shouldExtractAlmaCodeInPartnerDetailsCorrectly(final String katsys) throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), katsys)
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode("1")
                                  .build();

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), BIBNR_RESOLVABLE_TO_ALMA_CODE);
        var s3Event = createS3Event(uri);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME))
            .thenReturn(ILL_SERVER_ENVIRONMENT_VALUE);
        WireMocker.mockBasebibliotekXml(basebibliotekXml, BIBNR_RESOLVABLE_TO_ALMA_CODE);

        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);

        var partners = resourceSharingPartnerHandler.getPartners();

        // we should have only ony partner from the one record we have:
        Partner partner = partners.get(0);

        final String expectedInstitutionCode;
        if (ALMA.equals(katsys) || BIBSYS.equals(katsys)) {
            expectedInstitutionCode = RESOLVED_ALMA_CODE;
        } else {
            expectedInstitutionCode = "";
        }
        assertThat(partner.getPartnerDetails().getInstitutionCode(), is(expectedInstitutionCode));
    }

    @Test
    public void shouldLogAndThrowExceptionWhenAlmaCodeLookupTableIsMissing() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), ALMA)
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode("1")
                                  .build();

        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
        var s3Event = createS3Event(uri);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME))
            .thenReturn(ILL_SERVER_ENVIRONMENT_VALUE);

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment,
                                                                          WireMocker.httpClient);

        TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));

        assertThat(appender.getMessages(), containsString(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH));
    }

    @Test
    public void shouldLogAndThrowExceptionWhenAlmaCodeLookupTableIsEmpty() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), ALMA)
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode("1")
                                  .build();

        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME))
            .thenReturn(ILL_SERVER_ENVIRONMENT_VALUE);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY))
            .thenReturn(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);

        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH),
                            IoUtils.stringFromResources(Path.of("emptyLibCodeToAlmaCodeMapping.json")));

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
        var s3Event = createS3Event(uri);

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment,
                                                                          WireMocker.httpClient);

        TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));

        assertThat(appender.getMessages(), containsString(AlmaCodeProvider.EMPTY_MAPPING_TABLE_MESSAGE));
    }

    @Test
    public void shouldLogAndThrowExceptionWhenAlmaCodeLookupTableIsInvalidJson() throws IOException {
        final Record record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), ALMA)
                                  .withBibnr(BIBNR_RESOLVABLE_TO_ALMA_CODE)
                                  .withLandkode("1")
                                  .build();

        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.ILL_SERVER_ENV_NAME))
            .thenReturn(ILL_SERVER_ENVIRONMENT_VALUE);

        when(mockedEnvironment.readEnv(ResourceSharingPartnerHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY))
            .thenReturn(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);

        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH),
                            IoUtils.stringFromResources(Path.of("invalidLibCodeToAlmaCodeMapping.json")));

        var basebibliotekGenerator = new BasebibliotekGenerator(record);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
        var s3Event = createS3Event(uri);

        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, mockedEnvironment,
                                                                          WireMocker.httpClient);

        TestAppender appender = LogUtils.getTestingAppenderForRootLogger();

        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));

        assertThat(appender.getMessages(), containsString(LibCodeToAlmaCodeEntry.FIELD_IS_NULL_OR_EMPTY_MESSAGE));
    }

    private void assertIsoProfileDetailsPopulatedCorrectly(final Partner partner, final String bibnr) {

        IsoDetails details = partner.getPartnerDetails().getProfileDetails().getIsoDetails();

        assertThat(details.getIllPort(), is(ILL_SERVER_PORT));
        assertThat(details.getIsoSymbol(), is(bibnr));
        assertThat(details.getIllServer(), is(ILL_SERVER_ENVIRONMENT_VALUE));
        assertThat(details.isSharedBarcodes(), is(true));
    }

    private void assertNncipProfileDetailsPopulatedCorrectly(Partner partner, String bibnr, String expectedEmail) {
        final ProfileType profileType = partner.getPartnerDetails().getProfileDetails().getProfileType();

        assertThat(profileType, is(ProfileType.NCIP_P_2_P));

        final NcipP2PDetails details = partner.getPartnerDetails().getProfileDetails().getNcipP2PDetails();

        assertThat(details.getRequestExpiryType().getDesc(), is("No expiry"));
        assertThat(details.getRequestExpiryType().getValue(), is("NO_EXPIRY"));
        assertThat(details.getIllServer(), is(NNCIP_SERVER));
        assertThat(details.getPartnerSymbol(), is(bibnr));
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
                            .filter(email -> hasEmailAddressCorresponding(email,
                                                                          record.getEpostBest()))
                            .findFirst()
                            .orElse(null);
        var emailRegular = emails.getEmail()
                               .stream()
                               .filter(email -> hasEmailAddressCorresponding(email,
                                                                             record.getEpostAdr()))
                               .findFirst()
                               .orElse(null);
        if (emailBestShouldExist) {
            assertEmail(emailBest, record.getEpostBest(), true);
        }

        if (emailRegularShouldExist) {
            assertEmail(emailRegular, record.getEpostAdr(), !emailBestShouldExist);
        }
    }

    public void assertEmail(Email email, String emailAddress, boolean shouldBePreferred) {
        assertThat(email, is(IsNull.notNullValue()));
        var expectedEmailTypes = List.of("claimMail", "orderMail", "paymentMail", "queries", "returnsMail");
        assertThat(email.getEmailTypes().getEmailType(), hasSize(expectedEmailTypes.size()));
        expectedEmailTypes.forEach(emailType -> assertThat(email.getEmailTypes().getEmailType(),
                                                           hasItem(containsString(emailType))));
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
        var postAddress =
            addresses.stream()
                .filter(address -> hasLine1CorrespondingToRecord(address, record.getPadr()))
                .findFirst()
                .orElse(null);
        var visitationAddress =
            addresses.stream()
                .filter(address -> hasLine1CorrespondingToRecord(address, record.getVadr()))
                .findFirst()
                .orElse(null);
        if (postAddressShouldExist) {
            assertAddress(postAddress, record.getPadr(), record.getBibnr(), record.getPpoststed(), record.getPpostnr(),
                          record.getLandkode(), true);
        }
        if (visitationAddressShouldExist) {
            assertAddress(visitationAddress,
                          record.getVadr(),
                          record.getBibnr(),
                          record.getVpoststed(),
                          record.getVpostnr(),
                          record.getLandkode(),
                          !postAddressShouldExist);
        }
    }

    private void assertAddress(Address address, String expectedLine1, String expectedLine5,
                               String expectedCity, String expectedPostalCode, String expectedCountry,
                               boolean expectedPreferred) {
        assertThat(address, is(IsNull.notNullValue()));
        assertThat(address.getLine1(), is(equalTo(expectedLine1)));
        assertThat(address.getLine5(), is(equalTo(expectedLine5)));
        assertThat(address.isPreferred(), is(expectedPreferred));
        assertThat(address.getCity(), is(equalTo(expectedCity)));
        assertThat(address.getPostalCode(), is(equalTo(expectedPostalCode)));
        assertThat(address.getCountry().getValue(), is(equalTo(expectedCountry.toUpperCase(Locale.getDefault()))));
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
        var eventNotification = new S3EventNotificationRecord(randomString(),
                                                              randomString(),
                                                              randomString(),
                                                              randomDate(),
                                                              randomString(),
                                                              EMPTY_REQUEST_PARAMETERS,
                                                              EMPTY_RESPONSE_ELEMENTS,
                                                              createS3Entity(expectedObjectKey),
                                                              EMPTY_USER_IDENTITY);
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
