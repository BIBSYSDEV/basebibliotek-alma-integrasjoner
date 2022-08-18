package no.sikt.clients.alma;

import no.sikt.alma.partners.generated.Partner;

public interface AlmaPartnerUpserter {

    boolean upsertPartner(Partner partner);
}
