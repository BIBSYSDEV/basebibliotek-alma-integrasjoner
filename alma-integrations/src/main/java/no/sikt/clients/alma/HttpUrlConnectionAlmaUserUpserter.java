package no.sikt.clients.alma;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXB;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import no.sikt.alma.user.generated.User;
import no.sikt.clients.AbstractHttpUrlConnectionApi;
import no.sikt.commons.Redacter;
import no.sikt.lum.SensitiveXmlDataRedacter;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUrlConnectionAlmaUserUpserter extends AbstractHttpUrlConnectionApi implements AlmaUserUpserter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUrlConnectionAlmaUserUpserter.class);

    public static final String LOG_MESSAGE_COMMUNICATION_PROBLEM = "Problems communicating with Alma!";
    public static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_XML = "application/xml";
    public static final String USERS_URL_PATH = "users";
    public static final String UNEXPECTED_RESPONSE_UPDATING_USER_LOG_MESSAGE_PREFIX = "Unexpected response "
                                                                                      + "updating user";
    public static final String UNEXPECTED_RESPONSE_CREATING_USER_LOG_MESSAGE_PREFIX = "Unexpected response "
                                                                                      + "creating user";
    public static final String UNEXPECTED_RESPONSE_FETCHING_USER_LOG_MESSAGE_PREFIX = "Unexpected response "
                                                                                      + "fetching user";

    private static final String ALMA_ERROR_CODE_USER_NOT_FOUND = "401861";
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String ACCEPT_HEADER_NAME = "Accept";
    private static final String APIKEY_KEY = "apikey";
    private static final String SPACE_KEY = " ";
    private static final String UNEXPECTED_RESPONSE_UPDATING_USER_MESSAGE_FORMAT =
        UNEXPECTED_RESPONSE_UPDATING_USER_LOG_MESSAGE_PREFIX + " '%s' in Alma:\n%s\nStatus code: "
        + "%d\nResponse body: %s";
    private static final String UNEXPECTED_RESPONSE_CREATING_USER_MESSAGE_FORMAT =
        UNEXPECTED_RESPONSE_CREATING_USER_LOG_MESSAGE_PREFIX + " '%s' in Alma.\n%s\nStatus code: "
        + "%d\nResponse body: %s";
    private static final String UNEXPECTED_RESPONSE_FETCHING_USER_MESSAGE_FORMAT =
        UNEXPECTED_RESPONSE_FETCHING_USER_LOG_MESSAGE_PREFIX + " '%s' from Alma.\nStatus code: "
        + "%d\nResponse body: %s";

    private final transient URI almaApiHost;
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                   false);
    private final Redacter redacter;

    public HttpUrlConnectionAlmaUserUpserter(final URI almaApiHost) {
        this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build(),
             almaApiHost,
             new SensitiveXmlDataRedacter());
    }

    public HttpUrlConnectionAlmaUserUpserter(final HttpClient httpClient,
                                             final URI almaApiHost,
                                             Redacter redacter) {
        super(httpClient);
        this.almaApiHost = almaApiHost;
        this.redacter = redacter;
    }

    private Optional<String> fetchUser(final String userID, String almaApikey) {
        final HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(UriWrapper.fromUri(almaApiHost)
                     .addChild(USERS_URL_PATH)
                     .addChild(userID).getUri())
            .setHeader(AUTHORIZATION_HEADER_NAME, APIKEY_KEY + SPACE_KEY + almaApikey)
            .setHeader(ACCEPT_HEADER_NAME, APPLICATION_JSON)
            .build();
        try {
            final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                return Optional.of(response.body());
            } else if (HttpURLConnection.HTTP_BAD_REQUEST == response.statusCode()) {
                final AlmaErrorResponse almaErrorResponse = objectMapper.readValue(response.body(),
                                                                                   AlmaErrorResponse.class);
                if (userNotFound(almaErrorResponse)) {
                    return Optional.empty();
                } else {
                    final String message = String.format(
                        UNEXPECTED_RESPONSE_FETCHING_USER_MESSAGE_FORMAT,
                        userID,
                        response.statusCode(),
                        response.body());
                    throw new RuntimeException(message);
                }
            } else {
                final String message = String.format(
                    UNEXPECTED_RESPONSE_FETCHING_USER_MESSAGE_FORMAT,
                    userID,
                    response.statusCode(),
                    response.body());
                throw new RuntimeException(message);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
        }
    }

    private boolean userNotFound(final AlmaErrorResponse almaErrorResponse) {
        return almaErrorResponse.isErrorsExist() && almaErrorResponse.getErrorList().getError().stream()
            .anyMatch(almaError -> ALMA_ERROR_CODE_USER_NOT_FOUND.equals(
                almaError.getErrorCode()));
    }

    private void updateUser(final User user, String almaApikey) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXB.marshal(user, outputStream);
            String userAsString = outputStream.toString(StandardCharsets.UTF_8);

            final HttpRequest request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofString(userAsString))
                .uri(UriWrapper.fromUri(almaApiHost)
                         .addChild(USERS_URL_PATH)
                         .addChild(user.getPrimaryId()).getUri())
                .setHeader(AUTHORIZATION_HEADER_NAME, APIKEY_KEY + SPACE_KEY + almaApikey)
                .header(CONTENT_TYPE_HEADER_NAME, APPLICATION_XML)
                .build();

            final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (!successfulResponse(response)) {
                final String message = String.format(
                    UNEXPECTED_RESPONSE_UPDATING_USER_MESSAGE_FORMAT,
                    user.getPrimaryId(),
                    redacter.redact(userAsString),
                    response.statusCode(),
                    response.body());
                throw new RuntimeException(message);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
        }
    }

    private void createUser(final User user, String almaApikey) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXB.marshal(user, outputStream);
            String userAsString = outputStream.toString(StandardCharsets.UTF_8);

            final HttpRequest request = HttpRequest.newBuilder()
                .POST(
                    HttpRequest.BodyPublishers.ofString(userAsString))
                .uri(UriWrapper.fromUri(almaApiHost)
                         .addChild(USERS_URL_PATH)
                         .getUri())
                .setHeader(AUTHORIZATION_HEADER_NAME, APIKEY_KEY + SPACE_KEY + almaApikey)
                .header(CONTENT_TYPE_HEADER_NAME, APPLICATION_XML)
                .build();

            final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                final String message = String.format(
                    UNEXPECTED_RESPONSE_CREATING_USER_MESSAGE_FORMAT,
                    user.getPrimaryId(),
                    redacter.redact(userAsString),
                    response.statusCode(),
                    response.body());
                throw new RuntimeException(message);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
        }
    }

    @Override
    public boolean upsertUser(final User user, String almaApikey) {
        try {
            final Optional<String> almaUser = fetchUser(user.getPrimaryId(), almaApikey);
            almaUser.ifPresentOrElse(currentPartner -> updateUser(user, almaApikey),
                                     () -> createUser(user, almaApikey));
            return true;
        } catch (Exception e) {
            LOGGER.warn(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
            return false;
        }
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
