package no.sikt.rsp.clients;

import java.util.Optional;
import no.nb.basebibliotek.generated.BaseBibliotek;

public interface BaseBibliotekApi {

    Optional<BaseBibliotek> fetchBasebibliotek(String bibNr);
}
