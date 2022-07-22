package no.sikt.clients;

import java.util.Optional;
import no.nb.basebibliotek.generated.BaseBibliotek;

public interface BaseBibliotekApi {

    Optional<BaseBibliotek> fetchBasebibliotek(String bibNr);
}
