package test.utils;

import no.unit.nva.stubs.FakeS3Client;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class FakeS3ClientThrowingException extends FakeS3Client {

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
