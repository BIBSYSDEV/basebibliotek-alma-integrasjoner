package no.sikt.clients.alma;

import no.sikt.alma.partners.generated.Partner;

@FunctionalInterface
public interface AlmaPartnerUpserter {

    boolean upsertPartner(Partner partner);
}
