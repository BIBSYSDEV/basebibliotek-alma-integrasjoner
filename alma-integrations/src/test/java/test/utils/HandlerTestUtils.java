package test.utils;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;

public class HandlerTestUtils {

    public static final RequestParametersEntity EMPTY_REQUEST_PARAMETERS = null;
    public static final ResponseElementsEntity EMPTY_RESPONSE_ELEMENTS = null;
    public static final UserIdentityEntity EMPTY_USER_IDENTITY = null;
    public static final long SOME_FILE_SIZE = 100L;

    public static S3Event prepareBaseBibliotekFromXml(final Map<String, String> bibNrToXmlMap, S3Driver s3Driver)
        throws IOException {
        return prepareBaseBibliotekFromXml(null, bibNrToXmlMap, s3Driver);
    }


    public static S3Event prepareBaseBibliotekFromXml(final UnixPath s3Path, final Map<String, String> bibNrToXmlMap,
                                                      S3Driver s3Driver)
        throws IOException {

        final String fileContent = String.join("\n", bibNrToXmlMap.keySet());

        // prepare mocks:
        bibNrToXmlMap.forEach((key, value) -> WireMocker.mockBasebibliotekXml(value, key));

        var path = (s3Path == null) ? HandlerTestUtils.randomS3Path() : s3Path;
        var uri = s3Driver.insertFile(path, fileContent);

        return createS3Event(uri);
    }

    public static UnixPath randomS3Path() {
        return UnixPath.of(randomString());
    }


    public static S3Event createS3Event(URI uri) {
        return createS3Event(UriWrapper.fromUri(uri).toS3bucketPath().toString());
    }

    public static S3Event createS3Event(String expectedObjectKey) {
        var eventNotification = new S3EventNotificationRecord(randomString(), randomString(), randomString(),
                                                              randomDate(), randomString(), EMPTY_REQUEST_PARAMETERS,
                                                              EMPTY_RESPONSE_ELEMENTS,
                                                              createS3Entity(expectedObjectKey), EMPTY_USER_IDENTITY);
        return new S3Event(List.of(eventNotification));
    }

    private static String randomDate() {
        return Instant.now().toString();
    }

    private static S3Entity createS3Entity(String expectedObjectKey) {
        var bucket = new S3BucketEntity(randomString(), EMPTY_USER_IDENTITY, randomString());
        var object = new S3ObjectEntity(expectedObjectKey, SOME_FILE_SIZE, randomString(), randomString(),
                                        randomString());
        var schemaVersion = randomString();
        return new S3Entity(randomString(), bucket, object, schemaVersion);
    }

}
