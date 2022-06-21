package no.sikt.rsp.clients;

import no.sikt.alma.generated.Partner;

public interface AlmaPartnerUpdater {

    boolean upsertPartner(Partner partner);
}
