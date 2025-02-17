package no.sikt.lum.secret;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import nva.commons.secrets.ErrorReadingSecretException;
import nva.commons.secrets.SecretsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

public class AlmaKeysFetcher implements SecretFetcher<Map<String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(AlmaKeysFetcher.class);

    public static final String ALMA_API_KEYS_ID = "alma_api_keys_full";
    public static final String KEYS_FOR_ALMA_FOUND_MESSAGE = "Found {} api keys for alma";

    private final SecretsManagerClient secretsManagerClient;

    /**
     * Fetches alma keys from secrets manager using a client.
     **/
    public AlmaKeysFetcher(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    }

    @Override
    public Map<String, String> fetchSecret() {
        var secretsReader = new SecretsReader(secretsManagerClient);
        var secretString = secretsReader.fetchPlainTextSecret(ALMA_API_KEYS_ID);
        var keyPairs = parseSecretString(secretString);
        var keyPairMap = Arrays.stream(keyPairs)
                             .collect(Collectors.toMap(a -> a.almaCode,
                                                       a -> a.almaApikey));
        logger.info(KEYS_FOR_ALMA_FOUND_MESSAGE, keyPairMap.size());
        return keyPairMap;
    }

    private AlmaCodeAlmaApiKeyPair[] parseSecretString(String secretString) {
        try {
            return new ObjectMapper().readValue(secretString, AlmaCodeAlmaApiKeyPair[].class);
        } catch (JsonProcessingException ex) {
            logger.error("Could not parse secret into data model");
            throw new ErrorReadingSecretException();
        }
    }

    private static final class AlmaCodeAlmaApiKeyPair {

        @JsonProperty("almaCode")
        private transient String almaCode;
        @JsonProperty("almaApiKey")
        private transient String almaApikey;
    }

}
