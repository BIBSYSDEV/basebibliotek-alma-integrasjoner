package no.sikt.lum;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import nva.commons.core.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import com.google.gson.Gson;

public class LibraryUserManagementHandler {

    private static final Logger logger = LoggerFactory.getLogger(LibraryUserManagementHandler.class);
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    private static final String ALMA_API_KEY_ENV_KEY = "ALMA_APIKEY";
    private static final String EVENT = "event";
    private final transient S3Client s3Client;
    private final transient Environment environment;
    private final transient Gson gson = new Gson();

    public LibraryUserManagementHandler(S3Client s3Client, Environment environment) {
        this.s3Client = s3Client;
        this.environment = environment;

        final String almaApiKey = environment.readEnv(ALMA_API_KEY_ENV_KEY);

    }

    public Integer handleRequest(S3Event s3event, Context context) {

        logger.info(EVENT + gson.toJson(s3event));
        return null;
    }
}
