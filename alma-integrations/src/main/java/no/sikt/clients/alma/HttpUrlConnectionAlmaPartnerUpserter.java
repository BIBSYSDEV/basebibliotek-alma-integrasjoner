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
import no.sikt.alma.partners.generated.Partner;
import no.sikt.clients.AbstractHttpUrlConnectionApi;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUrlConnectionAlmaPartnerUpserter extends AbstractHttpUrlConnectionApi implements AlmaPartnerUpserter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUrlConnectionAlmaPartnerUpserter.class);

    public static final String LOG_MESSAGE_COMMUNICATION_PROBLEM = "Problems communicating with Alma!";
    public static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_XML = "application/xml";
    public static final String PARTNERS_URL_PATH = "partners";
    public static final String UNEXPECTED_RESPONSE_UPDATING_PARTNER_LOG_MESSAGE_PREFIX = "Unexpected response "
                                                                                         + "updating partner";
    public static final String UNEXPECTED_RESPONSE_CREATING_PARTNER_LOG_MESSAGE_PREFIX = "Unexpected response "
                                                                                         + "creating partner";
    public static final String UNEXPECTED_RESPONSE_FETCHING_PARTNER_LOG_MESSAGE_PREFIX = "Unexpected response "
                                                                                         + "fetching partner";

    private static final String ALMA_ERROR_CODE_PARTNER_NOT_FOUND = "402118";
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String ACCEPT_HEADER_NAME = "Accept";
    private static final String APIKEY_KEY = "apikey";
    private static final String SPACE_KEY = " ";
    private static final String UNEXPECTED_RESPONSE_UPDATING_PARTNER_MESSAGE_FORMAT =
        UNEXPECTED_RESPONSE_UPDATING_PARTNER_LOG_MESSAGE_PREFIX + " '%s' in Alma:\n%s\nStatus code: "
        + "%d\nResponse body: %s";
    private static final String UNEXPECTED_RESPONSE_CREATING_PARTNER_MESSAGE_FORMAT =
        UNEXPECTED_RESPONSE_CREATING_PARTNER_LOG_MESSAGE_PREFIX + " '%s' in Alma.\n%s\nStatus code: "
        + "%d\nResponse body: %s";
    private static final String UNEXPECTED_RESPONSE_FETCHING_PARTNER_MESSAGE_FORMAT =
        UNEXPECTED_RESPONSE_FETCHING_PARTNER_LOG_MESSAGE_PREFIX + " '%s' from Alma.\nStatus code: "
        + "%d\nResponse body: %s";

    private final transient String almaApikey;
    private final transient URI almaApiHost;
    private static final ObjectMapper objectMapper = new ObjectMapper()
                                                         .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                                                    false);

    public HttpUrlConnectionAlmaPartnerUpserter(final String almaApiKey, final URI almaApiHost) {
        this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build(), almaApiKey, almaApiHost);
    }

    public HttpUrlConnectionAlmaPartnerUpserter(final HttpClient httpClient, final String almaApiKey,
                                                final URI almaApiHost) {
        super(httpClient);
        this.almaApikey = almaApiKey;
        this.almaApiHost = almaApiHost;
    }

    private Optional<String> fetchPartner(final String code) {
        final HttpRequest request = HttpRequest.newBuilder()
                                        .GET()
                                        .uri(UriWrapper.fromUri(almaApiHost)
                                                 .addChild(PARTNERS_URL_PATH)
                                                 .addChild(code).getUri())
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
                if (partnerNotFound(almaErrorResponse)) {
                    return Optional.empty();
                } else {
                    final String message = String.format(
                        UNEXPECTED_RESPONSE_FETCHING_PARTNER_MESSAGE_FORMAT,
                        code,
                        response.statusCode(),
                        response.body());
                    throw new RuntimeException(message);
                }
            } else {
                final String message = String.format(
                    UNEXPECTED_RESPONSE_FETCHING_PARTNER_MESSAGE_FORMAT,
                    code,
                    response.statusCode(),
                    response.body());
                throw new RuntimeException(message);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
        }
    }

    private boolean partnerNotFound(final AlmaErrorResponse almaErrorResponse) {
        return almaErrorResponse.isErrorsExist() && almaErrorResponse.getErrorList().getError().stream()
                                                        .anyMatch(almaError -> ALMA_ERROR_CODE_PARTNER_NOT_FOUND.equals(
                                                            almaError.getErrorCode()));
    }

    private void updatePartner(final Partner partner) {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXB.marshal(partner, outputStream);
            final String partnerAsString = outputStream.toString(StandardCharsets.UTF_8);

            final HttpRequest request = HttpRequest.newBuilder()
                                            .PUT(HttpRequest.BodyPublishers.ofString(partnerAsString))
                                            .uri(UriWrapper.fromUri(almaApiHost)
                                                     .addChild(PARTNERS_URL_PATH)
                                                     .addChild(partner.getPartnerDetails().getCode()).getUri())
                                            .setHeader(AUTHORIZATION_HEADER_NAME, APIKEY_KEY + SPACE_KEY + almaApikey)
                                            .header(CONTENT_TYPE_HEADER_NAME, APPLICATION_XML)
                                            .build();

            final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (!successfulResponse(response)) {
                final String message = String.format(
                    UNEXPECTED_RESPONSE_UPDATING_PARTNER_MESSAGE_FORMAT,
                    partner.getPartnerDetails().getCode(),
                    partnerAsString,
                    response.statusCode(),
                    response.body());
                throw new RuntimeException(message);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
        }
    }

    private void createPartner(final Partner partner) {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXB.marshal(partner, outputStream);
            final String partnerAsString = outputStream.toString(StandardCharsets.UTF_8);

            final HttpRequest request = HttpRequest.newBuilder()
                                            .POST(
                                                HttpRequest.BodyPublishers.ofString(partnerAsString))
                                            .uri(UriWrapper.fromUri(almaApiHost)
                                                     .addChild(PARTNERS_URL_PATH)
                                                     .getUri())
                                            .setHeader(AUTHORIZATION_HEADER_NAME, APIKEY_KEY + SPACE_KEY + almaApikey)
                                            .header(CONTENT_TYPE_HEADER_NAME, APPLICATION_XML)
                                            .build();

            final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                final String message = String.format(
                    UNEXPECTED_RESPONSE_CREATING_PARTNER_MESSAGE_FORMAT,
                    partner.getPartnerDetails().getCode(),
                    partnerAsString,
                    response.statusCode(),
                    response.body());
                throw new RuntimeException(message);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(LOG_MESSAGE_COMMUNICATION_PROBLEM, e);
        }
    }

    @Override
    public boolean upsertPartner(final Partner partner) {
        try {
            final Optional<String> almaPartner = fetchPartner(partner.getPartnerDetails().getCode());
            almaPartner.ifPresentOrElse(currentPartner -> updatePartner(partner), () -> createPartner(partner));
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
