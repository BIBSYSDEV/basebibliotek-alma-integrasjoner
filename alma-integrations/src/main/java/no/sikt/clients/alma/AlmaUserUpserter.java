package no.sikt.clients.alma;

import no.sikt.alma.user.generated.User;

public interface AlmaUserUpserter {

    boolean upsertUser(User user, String almaApikey);
}
