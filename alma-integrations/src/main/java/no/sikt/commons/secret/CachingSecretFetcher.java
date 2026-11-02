package no.sikt.commons.secret;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Caches the result of a {@link SecretFetcher} for a fixed time-to-live.
 *
 * <p><b>Not thread-safe.</b> Call only from the Lambda handler thread. Fetch the secret
 * before starting worker threads and pass the value to them as a parameter.
 */
public class CachingSecretFetcher<T> implements SecretFetcher<T> {

    private final SecretFetcher<T> delegate;
    private final Duration ttl;
    private final Clock clock;
    private T cached;
    private Instant expires = Instant.EPOCH;

    public CachingSecretFetcher(SecretFetcher<T> delegate, Duration ttl) {
        this(delegate, ttl, Clock.systemUTC());
    }

    public CachingSecretFetcher(SecretFetcher<T> delegate, Duration ttl, Clock clock) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public T fetchSecret() {
        var now = clock.instant();
        if (now.isAfter(expires)) {
            cached = delegate.fetchSecret();
            expires = now.plus(ttl);
        }
        return cached;
    }

    /**
     * Marks the cached value as expired, so the next call to {@link #fetchSecret()} fetches it again.
     **/
    public void invalidate() {
        expires = Instant.EPOCH;
    }
}
