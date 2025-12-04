package no.sikt.clients.alma;

import no.sikt.lum.SerializedUser;

@FunctionalInterface
public interface AlmaUserUpserter {

    boolean upsertUser(SerializedUser serializedUser, String almaApikey);
}
