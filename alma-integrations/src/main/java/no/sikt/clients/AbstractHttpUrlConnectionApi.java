package no.sikt.clients;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.util.Optional;
import org.slf4j.Logger;

public abstract class AbstractHttpUrlConnectionApi {

    public static final String LOG_MESSAGE_COMMUNICATION_PROBLEM = "Problem communicating with external API.";
    protected final transient HttpClient httpClient;

    protected AbstractHttpUrlConnectionApi(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    protected abstract Logger getLogger();

    protected <T> Optional<T> doRequest(final HttpRequest request, final BodyHandler<T> bodyHandler) {
        try {
            final HttpResponse<T> response = httpClient.send(request, bodyHandler);
            if (successfulResponse(response)) {
                return Optional.of(response.body());
            } else {
                getLogger().warn("Unexpected response from external API. Status code {} with body '{}'!",
                                 response.statusCode(), response.body());
                return Optional.empty();
            }
        } catch (IOException | InterruptedException e) {
            getLogger().warn(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
            return Optional.empty();
        }
    }

    protected boolean successfulResponse(final HttpResponse<?> response) {
        return response.statusCode() >= HttpURLConnection.HTTP_OK
               && response.statusCode() < HttpURLConnection.HTTP_MULT_CHOICE;
    }
}
