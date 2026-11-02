package no.sikt.commons.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachingSecretFetcherTest {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    private MutableClock clock;
    private CountingFetcher delegate;
    private CachingSecretFetcher<String> cachingFetcher;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        delegate = new CountingFetcher();
        cachingFetcher = new CachingSecretFetcher<>(delegate, TTL, clock);
    }

    @Test
    void shouldFetchFromDelegateOnFirstCall() {
        assertEquals("secret-1", cachingFetcher.fetchSecret());
        assertEquals(1, delegate.calls);
    }

    @Test
    void shouldReturnCachedValueWithinTtl() {
        cachingFetcher.fetchSecret();
        clock.advance(TTL.minusSeconds(1));

        assertEquals("secret-1", cachingFetcher.fetchSecret());
        assertEquals(1, delegate.calls);
    }

    @Test
    void shouldReturnCachedValueExactlyAtTtl() {
        cachingFetcher.fetchSecret();
        clock.advance(TTL);

        assertEquals("secret-1", cachingFetcher.fetchSecret());
        assertEquals(1, delegate.calls);
    }

    @Test
    void shouldFetchAgainWhenTtlHasExpired() {
        cachingFetcher.fetchSecret();
        clock.advance(TTL.plusSeconds(1));

        assertEquals("secret-2", cachingFetcher.fetchSecret());
        assertEquals(2, delegate.calls);
    }

    @Test
    void shouldStartNewTtlPeriodAfterRefetch() {
        cachingFetcher.fetchSecret();
        clock.advance(TTL.plusSeconds(1));
        cachingFetcher.fetchSecret();
        clock.advance(TTL.minusSeconds(1));

        assertEquals("secret-2", cachingFetcher.fetchSecret());
        assertEquals(2, delegate.calls);
    }

    @Test
    void shouldFetchAgainAfterInvalidate() {
        cachingFetcher.fetchSecret();
        cachingFetcher.invalidate();

        assertEquals("secret-2", cachingFetcher.fetchSecret());
        assertEquals(2, delegate.calls);
    }

    @Test
    void shouldPropagateExceptionAndRetryOnNextCall() {
        delegate.failNext = true;

        assertThrows(IllegalStateException.class, cachingFetcher::fetchSecret);
        assertEquals("secret-2", cachingFetcher.fetchSecret());
        assertEquals(2, delegate.calls);
    }

    @Test
    void shouldCacheWhenUsingSystemClockConstructor() {
        var fetcher = new CachingSecretFetcher<>(delegate, TTL);

        fetcher.fetchSecret();
        fetcher.fetchSecret();

        assertEquals(1, delegate.calls);
    }

    private static final class CountingFetcher implements SecretFetcher<String> {

        private int calls;
        private boolean failNext;

        @Override
        public String fetchSecret() {
            calls++;
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Simulated Secrets Manager failure");
            }
            return "secret-" + calls;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
