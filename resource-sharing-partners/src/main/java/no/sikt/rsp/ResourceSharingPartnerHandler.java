package no.sikt.rsp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceSharingPartnerHandler implements RequestHandler<Map<String, Object>, String> {
    private static final Logger logger = LoggerFactory.getLogger(ResourceSharingPartnerHandler.class);
        //skeleton

    @Override
    public String handleRequest(Map<String, Object> s3Event, Context context) {
        logger.info("hello world");
        return "";
    }
}