package no.sikt.lum.secret;

@FunctionalInterface
public interface SecretFetcher<T> {

    T fetchSecret();

}
