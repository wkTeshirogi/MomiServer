package jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository;

import org.jooq.DSLContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class AuthSessionRepositoryTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"not-a-session", " ", "0000"})
	void missingOrMalformedSessionIdsCannotReadOrModifySessions(String sessionId) {
		DSLContext dsl = mock(DSLContext.class);
		AuthSessionRepository repository = new AuthSessionRepository(dsl, ApplicationClockFactory.systemClock());
		assertThat(repository.isActive(sessionId, "test@example.com")).isFalse();
		assertThat(repository.isRefreshTokenActive(sessionId, "test@example.com", "hash")).isFalse();
		repository.rotateRefreshToken(sessionId, "hash", LocalDateTime.now(ApplicationClockFactory.systemClock()).plusDays(1));
		repository.revoke(sessionId);
		verifyNoInteractions(dsl);
	}
}
