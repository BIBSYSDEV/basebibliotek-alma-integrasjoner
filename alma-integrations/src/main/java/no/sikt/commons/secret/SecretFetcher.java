package no.sikt.commons.secret;

@FunctionalInterface
public interface SecretFetcher<T> {

    T fetchSecret();

}
