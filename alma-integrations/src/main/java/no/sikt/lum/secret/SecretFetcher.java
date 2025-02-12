package no.sikt.lum.secret;

public interface SecretFetcher<T> {

    T fetchSecret();

}
