package no.sikt.lum.secret;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
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
        var keyPairs = secretsReader.fetchClassSecret(ALMA_API_KEYS_ID, AlmaCodeAlmaApiKeyPair[].class);
        var keyPairMap = Arrays.stream(keyPairs)
                             .collect(Collectors.toMap(a -> a.almaCode,
                                                       a -> a.almaApikey));
        logger.info(KEYS_FOR_ALMA_FOUND_MESSAGE, keyPairMap.size());
        return keyPairMap;
    }

    private static final class AlmaCodeAlmaApiKeyPair {

        @JsonProperty("almaCode")
        private transient String almaCode;
        @JsonProperty("almaApiKey")
        private transient String almaApikey;
    }

}
