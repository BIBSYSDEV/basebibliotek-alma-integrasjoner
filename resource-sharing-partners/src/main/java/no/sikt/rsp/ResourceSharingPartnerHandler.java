package no.sikt.rsp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceSharingPartnerHandler implements RequestHandler<Map<String, Object>, String> {

    private static final Logger log = LoggerFactory.getLogger(ResourceSharingPartnerHandler.class);
    private static final String EVENT = "event";
    private final transient Gson gson = new Gson();

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        log.info(EVENT + gson.toJson(event));
        return "done handling request";
    }
}