package no.sikt.rsp.clients;

import no.sikt.alma.generated.Partner;

public interface AlmaPartnerUpserter {

    boolean upsertPartner(Partner partner);
}
