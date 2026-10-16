package no.sikt.rsp.secret;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import nva.commons.logutils.LogRecorder;
import nva.commons.secrets.ErrorReadingSecretException;
import nva.commons.secrets.SecretsReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

class AlmaApiKeyFetcherTest {

    private static final String ALMA_APIKEY_SECRET = "{ \"ALMA_APIKEY\": \"abcd1234efgh5678\" }";
    private static final String ALMA_APIKEY_VALUE = "abcd1234efgh5678";
    private static final String SHORT_ALMA_APIKEY_SECRET = "{ \"ALMA_APIKEY\": \"abcd1234\" }";

    private SecretsManagerClient secretsManagerClient;
    private GetSecretValueResponse getSecretValueResponse;
    private AlmaApiKeyFetcher almaApiKeyFetcher;

    @BeforeEach
    public void init() {
        secretsManagerClient = mock(SecretsManagerClient.class);
        getSecretValueResponse = mock(GetSecretValueResponse.class);
    }

    @Test
    void shouldReadSecretCorrectly() {
        mockSecret(ALMA_APIKEY_SECRET);

        almaApiKeyFetcher = new AlmaApiKeyFetcher(secretsManagerClient);
        var almaApiKey = almaApiKeyFetcher.fetchSecret();

        assertThat(ALMA_APIKEY_VALUE, equalTo(almaApiKey));
    }

    @Test
    void shouldLogLastPartOfApiKey() {
        mockSecret(ALMA_APIKEY_SECRET);
        almaApiKeyFetcher = new AlmaApiKeyFetcher(secretsManagerClient);
        var logRecorder = LogRecorder.forClass(AlmaApiKeyFetcher.class);

        almaApiKeyFetcher.fetchSecret();

        assertThat(logRecorder.asString(), containsString("Last part of api key: ****5678"));
    }

    @Test
    void shouldMaskLoggingOfLastPartOfApiKeyWhenKeyIsTooShort() {
        mockSecret(SHORT_ALMA_APIKEY_SECRET);
        almaApiKeyFetcher = new AlmaApiKeyFetcher(secretsManagerClient);
        var logRecorder = LogRecorder.forClass(AlmaApiKeyFetcher.class);

        almaApiKeyFetcher.fetchSecret();

        assertThat(logRecorder.asString(), containsString("Last part of api key: ****"));
    }

    @Test
    void shouldThrowExceptionOnInvalidSecret() {
        var invalidAlmaKeyMapping = "Hello";
        mockSecret(invalidAlmaKeyMapping);

        almaApiKeyFetcher = new AlmaApiKeyFetcher(secretsManagerClient);

        var logRecorder = LogRecorder.forClass(SecretsReader.class);

        assertThrows(ErrorReadingSecretException.class, () -> almaApiKeyFetcher.fetchSecret());
        assertThat(logRecorder.asString(), containsString("Could not read secret: ALMA_APIKEY"));
    }

    void mockSecret(String secret) {
        when(getSecretValueResponse.secretString())
            .thenReturn(secret);
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(getSecretValueResponse);
    }

}