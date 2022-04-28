package no.sikt.rsp;

import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ResourceSharingPartnerTest {

    private ResourceSharingPartnerHandler resourceSharingPartnerHandler;
    private Context awsContext;

    @BeforeEach
    public void init(){
        resourceSharingPartnerHandler = new ResourceSharingPartnerHandler();
    }

    @Test
    public void shouldHandleFileUploadToS3() {
        //TODO
    }
}
