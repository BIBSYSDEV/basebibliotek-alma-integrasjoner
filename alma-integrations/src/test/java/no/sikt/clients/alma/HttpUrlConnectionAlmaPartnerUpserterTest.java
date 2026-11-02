package no.sikt.clients.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import no.sikt.alma.partners.generated.Partner;
import no.sikt.alma.partners.generated.PartnerDetails;
import no.sikt.commons.secret.CachingSecretFetcher;
import no.sikt.rsp.secret.AlmaApiKeyFetcher;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

class HttpUrlConnectionAlmaPartnerUpserterTest {

    private transient CachingSecretFetcher<String> cachedAlmaApiKeyFetcher;
    private static final String ALMA_APIKEY_DUMMY = "{ \"ALMA_APIKEY\": \"abcd1234\" }";

    @BeforeEach
    void init() {
        var secretsManagerClient = mock(SecretsManagerClient.class);
        var apiKeyFetcher = new AlmaApiKeyFetcher(secretsManagerClient);
        cachedAlmaApiKeyFetcher = new CachingSecretFetcher<>(apiKeyFetcher, Duration.ofMinutes(15));
        var getSecretValueResponse = mock(GetSecretValueResponse.class);
        when(getSecretValueResponse.secretString())
            .thenReturn(ALMA_APIKEY_DUMMY);
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(getSecretValueResponse);
    }

    @Test
    void shouldLogErrorWhenCommunicationWithAlmaFailsOnCreatingNewPartner() throws Exception {
        var almaNotFoundResponse = IoUtils.stringFromResources(Path.of("almaPartnerNotFound.json"));

        var httpClient = mock(HttpClient.class);
        var httpResponse = mock(HttpResponse.class);
        doReturn(almaNotFoundResponse).when(httpResponse).body();
        doReturn(400).when(httpResponse).statusCode();
        doReturn(httpResponse).doThrow(IOException.class).when(httpClient).send(any(), any());

        var partner = new Partner();
        var partnerDetails = new PartnerDetails();
        partner.setPartnerDetails(partnerDetails);

        var host = URI.create("http://localhost");

        var upserter = new HttpUrlConnectionAlmaPartnerUpserter(httpClient, cachedAlmaApiKeyFetcher, host);

        var logRecorder = LogRecorder.forClass(HttpUrlConnectionAlmaPartnerUpserter.class);

        assertThat(upserter.upsertPartner(partner), equalTo(false));
        assertThat(logRecorder.asString(), containsString("Problems communicating with Alma!"));
    }

    @Test
    void shouldLogErrorWhenCommunicationWithAlmaFailsOnUpdatingExistingPartner() throws Exception {
        var almaResponse = IoUtils.stringFromResources(Path.of("rsp_0030100.json"));

        var httpClient = mock(HttpClient.class);
        var httpResponse = mock(HttpResponse.class);
        doReturn(almaResponse).when(httpResponse).body();
        doReturn(200).when(httpResponse).statusCode();
        doReturn(httpResponse).doThrow(IOException.class).when(httpClient).send(any(), any());

        var partner = new Partner();
        var partnerDetails = new PartnerDetails();
        partner.setPartnerDetails(partnerDetails);

        var host = URI.create("http://localhost");

        var upserter = new HttpUrlConnectionAlmaPartnerUpserter(httpClient, cachedAlmaApiKeyFetcher, host);

        var logRecorder = LogRecorder.forClass(HttpUrlConnectionAlmaPartnerUpserter.class);

        assertThat(upserter.upsertPartner(partner), equalTo(false));
        assertThat(logRecorder.asString(), containsString("Problems communicating with Alma!"));
    }

}