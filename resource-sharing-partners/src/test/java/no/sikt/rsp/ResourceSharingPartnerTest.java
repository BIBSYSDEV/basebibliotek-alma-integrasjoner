package no.sikt.rsp;

import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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
import java.net.URI;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.generated.Address;
import no.sikt.alma.generated.ContactInfo;
import no.sikt.alma.generated.Email;
import no.sikt.alma.generated.Emails;
import no.sikt.alma.generated.Phones;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import test.utils.BasebibliotekGenerator;
import test.utils.RecordSpecification;
import test.utils.WireMocker;

public class ResourceSharingPartnerTest {

    public static final RequestParametersEntity EMPTY_REQUEST_PARAMETERS = null;
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    public static final ResponseElementsEntity EMPTY_RESPONSE_ELEMENTS = null;
    public static final UserIdentityEntity EMPTY_USER_IDENTITY = null;
    public static final long SOME_FILE_SIZE = 100L;
    private static final String BASEBIBLIOTEK_XML = "redacted_bb_full.xml";
    private static final String BASEBIBLIOTEK_0030100_XML = "bb_0030100.xml";

    private static final String INVALID_BASEBIBLIOTEK_XML_STRING = "invalid";
    public static final String ALMA = "alma";
    public static final String BIBSYS = "bibsys";
    public static final String TIDEMANN = "Tidemann";
    public static final String OTHER = "other";

    private ResourceSharingPartnerHandler resourceSharingPartnerHandler;
    public static final Context CONTEXT = mock(Context.class);

    private FakeS3Client s3Client;
    private S3Driver s3Driver;

    @BeforeEach
    public void init() {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, "ignoredValue");
        WireMocker.startWiremockServer();
        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, WireMocker.httpClient,
                                                                          WireMocker.serverUri);
    }

    @Test
    public void shouldBeAbleToReadRecordFromAlma() throws IOException {
        var baseBibliotek0030100 = IoUtils.stringFromResources(Path.of(BASEBIBLIOTEK_0030100_XML));
        var uri = s3Driver.insertFile(randomS3Path(), baseBibliotek0030100);
        var s3Event = createS3Event(uri);
        Integer response = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        assertThat(response, is(notNullValue()));
        assertThat(response, is(1));
    }

    @Test
    public void shouldLogExceptionWhenS3ClientFails() {
        var s3Event = createS3Event(randomString());
        var expectedMessage = randomString();
        s3Client = new FakeS3ClientThrowingException(expectedMessage);
        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client, WireMocker.httpClient,
                                                                          WireMocker.serverUri);
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));
        assertThat(appender.getMessages(), containsString(expectedMessage));
    }

    @Test
    public void shouldLogExceptionWhenS3BucketFileCannotBeConvertedToBaseBibliotek() throws IOException {
        var uri = s3Driver.insertFile(randomS3Path(), INVALID_BASEBIBLIOTEK_XML_STRING);
        var s3Event = createS3Event(uri);
        assertThrows(RuntimeException.class, () -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));
    }

    @Test
    public void shouldBeAbleToConvertFullBasebibliotekFile() throws IOException {
        var fullBaseBibliotekFile = IoUtils.stringFromResources(Path.of(BASEBIBLIOTEK_XML));
        var uri = s3Driver.insertFile(randomS3Path(), fullBaseBibliotekFile);
        var s3Event = createS3Event(uri);
        assertDoesNotThrow(() -> resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT));
    }

    @Test
    public void shouldExtractContactDetailsCorrectly() throws IOException {
        var withPaddr = true;
        var withVaddr = true;
        var specifiedList = List.of(
            new RecordSpecification(true,
                                    true,
                                    null, randomBoolean(),
                                    randomBoolean(),
                                    withPaddr,
                                    withVaddr,
                                    randomBoolean(),
                                    randomString()));
        var basebibliotekGenerator = new BasebibliotekGenerator(specifiedList);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        assertContactInfo(partners.get(0).getContactInfo(), basebibliotek.getRecord().get(0));
    }

    @Test
    public void shouldRecordHandleIsilBibNrAndLandKode() throws IOException {
        var withBibNr = true;
        var withLandkode = true;
        var withIsil = true;
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var expectedLogMessage = "Could not convert record to partner, missing landkode bibNr, record";
        var shouldNotBeConvertedToPartner = new RecordSpecification(!withBibNr,
                                                                    !withLandkode,
                                                                    null,
                                                                    randomBoolean(),
                                                                    randomBoolean(),
                                                                    randomBoolean(),
                                                                    randomBoolean(),
                                                                    !withIsil,
                                                                    randomString());
        var shouldUseIsilAsPartnerDetailsCode = new RecordSpecification(withBibNr,
                                                                        withLandkode,
                                                                        null,
                                                                        randomBoolean(),
                                                                        randomBoolean(),
                                                                        randomBoolean(),
                                                                        randomBoolean(),
                                                                        withIsil,
                                                                        randomString());
        var shouldCombineBibNrAndLandKodeAsPartnerDetailsCode = new RecordSpecification(withBibNr,
                                                                                        withLandkode,
                                                                                        null,
                                                                                        randomBoolean(),
                                                                                        randomBoolean(),
                                                                                        randomBoolean(),
                                                                                        randomBoolean(),
                                                                                        !withIsil,
                                                                                        randomString());
        var specifiedList = List.of(
            shouldUseIsilAsPartnerDetailsCode,
            shouldCombineBibNrAndLandKodeAsPartnerDetailsCode,
            shouldNotBeConvertedToPartner);
        var basebibliotekGenerator = new BasebibliotekGenerator(specifiedList);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        var recordWithIsil = basebibliotek.getRecord().get(0);
        assertThat(partners, hasSize(2));
        assertThat(appender.getMessages(), containsString(expectedLogMessage));
        assertThat(partners.get(0).getPartnerDetails().getCode(), is(equalTo(recordWithIsil.getIsil())));

        var recordWithoutIsilButContainingBibNrAndLandKode = basebibliotek.getRecord().get(1);
        var expectedCraftedPartnerCode =
            recordWithoutIsilButContainingBibNrAndLandKode.getLandkode().toUpperCase(Locale.ROOT)
            + "-"
            + recordWithoutIsilButContainingBibNrAndLandKode.getBibnr();
        assertThat(partners.get(1).getPartnerDetails().getCode(),
                   is(equalTo(expectedCraftedPartnerCode)));
    }

    @Test
    void shouldExtractBasicPartnerDetailsCorrectly() throws IOException {
        var specifiedList = List.of(
            new RecordSpecification(true,
                                    true,
                                    null, randomBoolean(),
                                    randomBoolean(),
                                    randomBoolean(),
                                    randomBoolean(),
                                    randomBoolean(),
                                    randomString()));
        var basebibliotekGenerator = new BasebibliotekGenerator(specifiedList);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
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
        var withBibNr = true;
        var withLandkode = true;
        var specifiedList = List.of(new RecordSpecification(withBibNr,
                                                            withLandkode,
                                                            null,
                                                            randomBoolean(),
                                                            randomBoolean(),
                                                            randomBoolean(),
                                                            randomBoolean(),
                                                            randomBoolean(),
                                                            katsys));
        var basebibliotekGenerator = new BasebibliotekGenerator(specifiedList);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
        var s3Event = createS3Event(uri);
        resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        var partners = resourceSharingPartnerHandler.getPartners();
        var isAlmaOrBibsys = BIBSYS.equals(katsys) || ALMA.equals(katsys);
        var expectedHoldingCode = isAlmaOrBibsys
                                      ? basebibliotek.getRecord().get(0).getLandkode().toUpperCase(Locale.ROOT)
                                        + basebibliotek.getRecord().get(0).getBibnr()
                                      : null;
        var expectedSystemTypeValueValue = isAlmaOrBibsys
                                               ? ALMA.toUpperCase(Locale.ROOT) : OTHER.toUpperCase(Locale.ROOT);
        var expectedSystemTypeValueDesc = isAlmaOrBibsys ? ALMA : OTHER;
        assertThat(partners.get(0).getPartnerDetails().getHoldingCode(), is(equalTo(expectedHoldingCode)));
        assertThat(partners.get(0).getPartnerDetails().getSystemType().getValue(),
                   is(equalTo(expectedSystemTypeValueValue)));
        assertThat(partners.get(0).getPartnerDetails().getSystemType().getDesc(),
                   is(equalTo(expectedSystemTypeValueDesc)));
        //TODO:LocateProfile in partnerDetails should be set when isAlmaOrBibsys, otherwise null. SMILE-1573
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
        assertThat(address.getCountry().getValue(), is(equalTo(expectedCountry.toUpperCase())));
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

    private String createDateString(XMLGregorianCalendar xmlGregorianCalendar) {
        synchronized (dateFormat) {
            return dateFormat.format(xmlGregorianCalendar.toGregorianCalendar().getTime());
        }
    }

    private static class FakeS3ClientThrowingException extends FakeS3Client {

        private final String expectedErrorMessage;

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
