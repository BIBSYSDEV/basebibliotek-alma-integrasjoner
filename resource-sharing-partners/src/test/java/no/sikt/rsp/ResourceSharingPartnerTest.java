package no.sikt.rsp;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.time.Instant;
import java.util.List;
import no.nb.basebibliotek.generated.Record;
import no.sikt.basebibliotek.BaseBibliotekBean;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import test.utils.BasebibliotekGenerator;
import test.utils.RecordSpecification;

public class ResourceSharingPartnerTest {

    public static final RequestParametersEntity EMPTY_REQUEST_PARAMETERS = null;
    public static final ResponseElementsEntity EMPTY_RESPONSE_ELEMENTS = null;
    public static final UserIdentityEntity EMPTY_USER_IDENTITY = null;
    public static final long SOME_FILE_SIZE = 100L;
    private static final String BASEBIBLIOTEK_XML = "redacted_bb_full.xml";

    private static final String INVALID_BASEBIBLIOTEK_XML_STRING = "invalid";

    private ResourceSharingPartnerHandler resourceSharingPartnerHandler;
    public static final Context CONTEXT = mock(Context.class);

    private FakeS3Client s3Client;
    private S3Driver s3Driver;

    @BeforeEach
    public void init() {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, "ignoredValue");
        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client);
    }

    @Test
    public void shouldLogExceptionWhenS3ClientFails() {
        var s3Event = createS3Event(randomString());
        var expectedMessage = randomString();
        s3Client = new FakeS3ClientThrowingException(expectedMessage);
        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler(s3Client);
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
    public void basebibliotekMissingLibNrOrLandkodeShouldNotBeConvertedToBasebibliotekBean() throws IOException {

        var specifiedList = List.of(
            new RecordSpecification(true, true),
            new RecordSpecification(false, false),
            new RecordSpecification(true, false),
            new RecordSpecification(false, true));
        var basebibliotekGenerator = new BasebibliotekGenerator(specifiedList);
        var basebibliotek = basebibliotekGenerator.generateBaseBibliotek();
        var basebibliotekXml = BasebibliotekGenerator.toXml(basebibliotek);
        var uri = s3Driver.insertFile(randomS3Path(), basebibliotekXml);
        var s3Event = createS3Event(uri);
        var basebibliotekBeans = resourceSharingPartnerHandler.handleRequest(s3Event, CONTEXT);
        assertThat(basebibliotekBeans, hasSize(1));
        assertBaseBibliotekBeansHasExtractedSimppleMatadataCorrectly(basebibliotekBeans.get(0),
                                                                     basebibliotek.getRecord().get(0));
    }

    private void assertBaseBibliotekBeansHasExtractedSimppleMatadataCorrectly(BaseBibliotekBean baseBibliotekBean,
                                                                              Record record) {
        assertEquals(baseBibliotekBean.getBibNr(), record.getBibnr());
        assertEquals(baseBibliotekBean.getInst(), record.getInst());
        assertEquals(baseBibliotekBean.getKatsyst(), record.getKatsyst());
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
