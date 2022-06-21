package no.sikt.rsp.clients.basebibliotek;

import jakarta.xml.bind.DataBindingException;
import jakarta.xml.bind.JAXB;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.sikt.rsp.clients.BaseBibliotekApi;
import no.sikt.rsp.clients.AbstractHttpUrlConnectionApi;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUrlConnectionBaseBibliotekApi extends AbstractHttpUrlConnectionApi implements BaseBibliotekApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUrlConnectionBaseBibliotekApi.class);

    private final transient URI host;

    public HttpUrlConnectionBaseBibliotekApi(final URI host) {
        super(HttpClient.newHttpClient());
        this.host = host;
    }

    @Override
    public Optional<BaseBibliotek> getBasebibliotek(String bibNr) {
        HttpRequest request = HttpRequest.newBuilder()
                                  .GET()
                                  .uri(UriWrapper.fromUri(host).addChild(bibNr).getUri())
                                  .build();

        final Optional<String> response = doRequest(request, BodyHandlers.ofString());
        try {
            return response.map(this::parseBasebibliotekXml);
        } catch (DataBindingException e) {
            LOGGER.warn(String.format("Unable to unmarshal XML from BaseBibliotek: %s", response.get()), e);
            return Optional.empty();
        }
    }

    private BaseBibliotek parseBasebibliotekXml(String xml) {
        return JAXB.unmarshal(new StringReader(xml), BaseBibliotek.class);
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
