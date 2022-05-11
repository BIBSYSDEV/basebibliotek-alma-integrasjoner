package no.sikt;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.lambda.runtime.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasebibliotekFetchHandler implements RequestHandler<ScheduledEvent, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(BasebibliotekFetchHandler.class);

    @Override
    public Integer handleRequest(ScheduledEvent scheduledEvent, Context context) {
        logger.info("started");
        return 0;
    }

}
