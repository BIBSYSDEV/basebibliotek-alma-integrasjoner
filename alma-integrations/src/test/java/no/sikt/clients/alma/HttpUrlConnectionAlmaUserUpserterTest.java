package no.sikt.clients.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import no.sikt.lum.SensitiveXmlDataRedacter;
import no.sikt.lum.serialize.SerializedUser;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.Test;

class HttpUrlConnectionAlmaUserUpserterTest {

    private static final String API_KEY = "api-key";
    private static final String PROBLEMS_COMMUNICATING_WITH_ALMA = "Problems communicating with Alma!";

    @Test
    void shouldLogErrorWhenCommunicationWithAlmaFailsOnFetchingUser() throws Exception {
        var httpClient = mock(HttpClient.class);
        doThrow(IOException.class).when(httpClient).send(any(), any());

        var upserter = new HttpUrlConnectionAlmaUserUpserter(httpClient, getHost(), new SensitiveXmlDataRedacter());

        var appender = LogUtils.getTestingAppender(HttpUrlConnectionAlmaUserUpserter.class);

        assertThat(upserter.upsertUser(getSerializedUser(), API_KEY), equalTo(false));
        assertThat(appender.getMessages(), containsString(PROBLEMS_COMMUNICATING_WITH_ALMA));
    }

    @Test
    void shouldLogErrorWhenCommunicationWithAlmaFailsOnCreatingNewUser() throws Exception {
        var almaNotFoundResponse = IoUtils.stringFromResources(Path.of("almaUserNotFound.json"));

        var httpClient = mock(HttpClient.class);
        var httpResponse = mock(HttpResponse.class);
        doReturn(almaNotFoundResponse).when(httpResponse).body();
        doReturn(400).when(httpResponse).statusCode();
        doReturn(httpResponse).doThrow(IOException.class).when(httpClient).send(any(), any());

        var upserter = new HttpUrlConnectionAlmaUserUpserter(httpClient, getHost(), new SensitiveXmlDataRedacter());

        var appender = LogUtils.getTestingAppender(HttpUrlConnectionAlmaUserUpserter.class);

        assertThat(upserter.upsertUser(getSerializedUser(), API_KEY), equalTo(false));
        assertThat(appender.getMessages(), containsString(PROBLEMS_COMMUNICATING_WITH_ALMA));
    }

    @Test
    void shouldLogErrorWhenCommunicationWithAlmaFailsOnUpdatingExistingUser() throws Exception {
        var almaResponse = IoUtils.stringFromResources(Path.of("lum_0030100.json"));

        var httpClient = mock(HttpClient.class);
        var httpResponse = mock(HttpResponse.class);
        doReturn(almaResponse).when(httpResponse).body();
        doReturn(200).when(httpResponse).statusCode();
        doReturn(httpResponse).doThrow(IOException.class).when(httpClient).send(any(), any());

        var upserter = new HttpUrlConnectionAlmaUserUpserter(httpClient, getHost(), new SensitiveXmlDataRedacter());

        var appender = LogUtils.getTestingAppender(HttpUrlConnectionAlmaUserUpserter.class);

        assertThat(upserter.upsertUser(getSerializedUser(), API_KEY), equalTo(false));
        assertThat(appender.getMessages(), containsString(PROBLEMS_COMMUNICATING_WITH_ALMA));
    }

    private SerializedUser getSerializedUser() {
        return new SerializedUser("1234", "<xml>hello</xml>");
    }

    private URI getHost() {
        return URI.create("http://localhost");
    }

}