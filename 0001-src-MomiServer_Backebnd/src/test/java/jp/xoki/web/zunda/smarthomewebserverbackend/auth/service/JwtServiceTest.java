package jp.xoki.web.zunda.smarthomewebserverbackend.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class JwtServiceTest {

	private final JwtService service = new JwtService("test-signing-secret-that-is-at-least-thirty-two-bytes", 300, 600, ApplicationClockFactory.systemClock());
	private final UserDetails user = User.withUsername("operator@example.com").password("unused").roles("OPERATOR").build();

	@Test
	void accessTokenRetainsNumericDatesIdentityAndRole() throws Exception {
		Instant before = Instant.now(Clock.systemUTC()).truncatedTo(ChronoUnit.SECONDS);
		String token = service.generateAccessToken(user, "session-id");
		Instant after = Instant.now(Clock.systemUTC()).truncatedTo(ChronoUnit.SECONDS);
		JsonNode payload = payload(token);

		assertThat(payload.path("iat").isIntegralNumber()).isTrue();
		assertThat(payload.path("exp").isIntegralNumber()).isTrue();
		assertThat(payload.path("exp").longValue() - payload.path("iat").longValue()).isEqualTo(300);
		assertThat(payload.path("sub").asText()).isEqualTo(user.getUsername());
		assertThat(payload.path("sid").asText()).isEqualTo("session-id");
		assertThat(payload.path("role").asText()).isEqualTo("OPERATOR");
		assertThat(payload.path("type").asText()).isEqualTo("access");
		assertThat(service.extractExpiration(token)).isBetween(before.plusSeconds(300), after.plusSeconds(300));
		assertThat(service.extractUsername(token)).isEqualTo(user.getUsername());
		assertThat(service.extractSessionId(token)).isEqualTo("session-id");
		assertThat(service.isValidAccessToken(token, user)).isTrue();
		assertThat(service.isValidRefreshToken(token, user)).isFalse();
	}

	@Test
	void refreshTokenRetainsItsOwnLifetimeAndCannotBeUsedAsAccessToken() throws Exception {
		String token = service.generateRefreshToken(user, "session-id");
		JsonNode payload = payload(token);

		assertThat(payload.path("exp").longValue() - payload.path("iat").longValue()).isEqualTo(600);
		assertThat(payload.path("type").asText()).isEqualTo("refresh");
		assertThat(payload.has("role")).isFalse();
		assertThat(service.isValidRefreshToken(token, user)).isTrue();
		assertThat(service.isValidAccessToken(token, user)).isFalse();
	}

	@Test
	void expiredMalformedWrongUserAndWrongSignatureTokensAreRejected() {
		JwtService expiredService = new JwtService("test-signing-secret-that-is-at-least-thirty-two-bytes", -120, -120, ApplicationClockFactory.systemClock());
		JwtService otherService = new JwtService("different-signing-secret-at-least-thirty-two-bytes", 300, 600, ApplicationClockFactory.systemClock());
		UserDetails otherUser = User.withUsername("other@example.com").password("unused").roles("USER").build();

		assertThat(service.isValidAccessToken(expiredService.generateAccessToken(user, "session-id"), user)).isFalse();
		assertThat(service.isValidRefreshToken(expiredService.generateRefreshToken(user, "session-id"), user)).isFalse();
		assertThat(service.isValidAccessToken("malformed", user)).isFalse();
		assertThat(service.isValidAccessToken(service.generateAccessToken(otherUser, "session-id"), user)).isFalse();
		assertThat(service.isValidAccessToken(otherService.generateAccessToken(user, "session-id"), user)).isFalse();
	}

	private JsonNode payload(String token) throws Exception {
		return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
	}
}
