package no.sikt.lum.secret;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.nio.file.Path;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.secrets.ErrorReadingSecretException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

class AlmaKeysFetcherTest {

    private static final String FULL_ALMA_CODE_ALMA_APIKEY_MAPPING_JSON = "fullAlmaCodeAlmaApiKeyMapping.json";

    private SecretsManagerClient secretsManagerClient;
    private GetSecretValueResponse getSecretValueResponse;
    private AlmaKeysFetcher almaKeysFetcher;

    @BeforeEach
    public void init() {
        secretsManagerClient = mock(SecretsManagerClient.class);
        getSecretValueResponse = mock(GetSecretValueResponse.class);
    }

    @Test
    void shouldReadSecretCorrectly() {
        var fullAlmaCodeAlmaApiKeyMapping =
            IoUtils.stringFromResources(Path.of(FULL_ALMA_CODE_ALMA_APIKEY_MAPPING_JSON));
        mockSecret(fullAlmaCodeAlmaApiKeyMapping);

        almaKeysFetcher = new AlmaKeysFetcher(secretsManagerClient);
        var almaApiKeyMap = almaKeysFetcher.fetchSecret();

        assertThat(almaApiKeyMap.size(), equalTo(83));
        assertThat(almaApiKeyMap.containsKey("AASENTUN"), equalTo(true));
        assertThat(almaApiKeyMap.get("AASENTUN"), equalTo("almaApiKey_AASENTUN"));
    }

    @Test
    void shouldThrowExceptionOnInvalidSecret() {
        var invalidAlmaKeyMapping = "Hello";
        mockSecret(invalidAlmaKeyMapping);

        almaKeysFetcher = new AlmaKeysFetcher(secretsManagerClient);

        assertThrows(ErrorReadingSecretException.class, () -> almaKeysFetcher.fetchSecret());
    }

    void mockSecret(String secret) {
        when(getSecretValueResponse.secretString())
            .thenReturn(secret);
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(getSecretValueResponse);
    }

}
