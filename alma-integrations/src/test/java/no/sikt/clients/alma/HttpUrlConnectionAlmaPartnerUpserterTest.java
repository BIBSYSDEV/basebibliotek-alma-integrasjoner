package no.sikt.clients.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import no.sikt.alma.partners.generated.Partner;
import no.sikt.alma.partners.generated.PartnerDetails;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogRecorder;
import org.junit.jupiter.api.Test;

class HttpUrlConnectionAlmaPartnerUpserterTest {

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

        var upserter = new HttpUrlConnectionAlmaPartnerUpserter(httpClient, "api-key", host);

        var appender = LogRecorder.forClass(HttpUrlConnectionAlmaPartnerUpserter.class);

        assertThat(upserter.upsertPartner(partner), equalTo(false));
        assertThat(appender.asString(), containsString("Problems communicating with Alma!"));
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

        var upserter = new HttpUrlConnectionAlmaPartnerUpserter(httpClient, "api-key", host);

        var appender = LogRecorder.forClass(HttpUrlConnectionAlmaPartnerUpserter.class);

        assertThat(upserter.upsertPartner(partner), equalTo(false));
        assertThat(appender.asString(), containsString("Problems communicating with Alma!"));
    }

}