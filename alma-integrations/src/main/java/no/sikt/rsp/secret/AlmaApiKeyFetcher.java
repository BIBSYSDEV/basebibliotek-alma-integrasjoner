package no.sikt.rsp.secret;

import no.sikt.commons.secret.SecretFetcher;
import nva.commons.secrets.SecretsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

public class AlmaApiKeyFetcher implements SecretFetcher<String> {

    private static final Logger logger = LoggerFactory.getLogger(AlmaApiKeyFetcher.class);

    public static final String ALMA_APIKEY_SECRET_NAME = "ALMA_APIKEY";
    public static final String ALMA_APIKEY_SECRET_KEY = "ALMA_APIKEY";

    private final SecretsManagerClient secretsManagerClient;

    public AlmaApiKeyFetcher(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    }

    @Override
    public String fetchSecret() {
        var secretsReader = new SecretsReader(secretsManagerClient);
        var apiKey = secretsReader.fetchSecret(ALMA_APIKEY_SECRET_NAME, ALMA_APIKEY_SECRET_KEY);
        logger.info("Fetched Alma api key from secrets manager. Last part of api key: {}", mask(apiKey));
        return apiKey;
    }

    private String mask(String secret) {
        return secret.length() > 12
                   ? "****" + secret.substring(secret.length() - 4)
                   : "****";
    }
}
