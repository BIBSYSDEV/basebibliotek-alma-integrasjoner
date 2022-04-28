package no.sikt.rsp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import no.unit.nva.s3.S3Driver;

public class ResourceSharingPartnerHandler implements RequestHandler<S3Event, String> {

    private static final Logger log = LoggerFactory.getLogger(ResourceSharingPartnerHandler.class);
    private static final String EVENT = "event";
    private final transient Gson gson = new Gson();

    @Override
    public String handleRequest(S3Event s3event, Context context) {
        log.info(EVENT + gson.toJson(s3event));
        return "done handling request";
    }
}