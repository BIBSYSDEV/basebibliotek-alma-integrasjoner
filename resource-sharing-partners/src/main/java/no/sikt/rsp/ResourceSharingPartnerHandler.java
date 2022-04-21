package no.sikt.rsp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

public class ResourceSharingPartnerHandler implements RequestHandler<Map<String, Object>, String> {
        //skeleton

    @Override
    public String handleRequest(Map<String, Object> s3Event, Context context) {
        return "";
    }
}