package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRateLimiterTest {

	@Test
	void consumeAllowsRequestsUntilLimit() {
		GatewayRateLimiter limiter = limiter(2, mutableClock());

		GatewayRateLimiter.RateLimitResult first = limiter.consume("agent:key-1");
		GatewayRateLimiter.RateLimitResult second = limiter.consume("agent:key-1");

		assertThat(first.allowed()).isTrue();
		assertThat(first.remaining()).isEqualTo(1);
		assertThat(second.allowed()).isTrue();
		assertThat(second.remaining()).isZero();
	}

	@Test
	void consumeRejectsWhenLimitExceeded() {
		GatewayRateLimiter limiter = limiter(1, mutableClock());
		limiter.consume("agent:key-1");

		GatewayRateLimiter.RateLimitResult result = limiter.consume("agent:key-1");

		assertThat(result.allowed()).isFalse();
		assertThat(result.limit()).isEqualTo(1);
		assertThat(result.remaining()).isZero();
		assertThat(result.retryAfterSeconds()).isEqualTo(60);
	}

	@Test
	void consumeResetsAfterOneMinuteWindow() {
		MutableClock clock = mutableClock();
		GatewayRateLimiter limiter = limiter(1, clock);
		limiter.consume("agent:key-1");
		clock.advanceMillis(60_000L);

		GatewayRateLimiter.RateLimitResult result = limiter.consume("agent:key-1");

		assertThat(result.allowed()).isTrue();
		assertThat(result.remaining()).isZero();
	}

	private GatewayRateLimiter limiter(int perMinute, Clock clock) {
		GatewayProperties properties = new GatewayProperties();
		properties.getRateLimit().setPerMinute(perMinute);
		return new GatewayRateLimiter(properties, clock);
	}

	private MutableClock mutableClock() {
		return new MutableClock(Instant.parse("2026-08-29T00:00:00Z"));
	}

	private static class MutableClock extends Clock {
		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advanceMillis(long millis) {
			instant = instant.plusMillis(millis);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
