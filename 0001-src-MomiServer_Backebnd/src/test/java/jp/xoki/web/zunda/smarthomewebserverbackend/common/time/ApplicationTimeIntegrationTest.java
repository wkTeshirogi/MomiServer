package jp.xoki.web.zunda.smarthomewebserverbackend.common.time;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.Instant;

/** UTCのJVM・DBサーバーでも、JDBCセッションとローカル日時の前提が揃うことを確認する。 */
@SpringBootTest(properties = {"mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.vehicle-inspection-notification.enabled=false", "gateway.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ApplicationTimeIntegrationTest {
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSessionService sessions;

    @Test
    void databaseSessionAndDefaultLocalTimestampMatchTheBusinessClock() {
        assertThat(jdbc.queryForObject("show TimeZone", String.class)).isEqualTo(clock.getZone().getId());
        LocalDateTime before = LocalDateTime.now(clock);
        LocalDateTime databaseTime = jdbc.queryForObject("select localtimestamp", LocalDateTime.class);
        assertThat(databaseTime).isCloseTo(before, within(5, ChronoUnit.SECONDS));
    }

    @Test
    void sessionExpiryConversionAndActiveComparisonUseTheSameZone() {
        String sessionId = UUID.randomUUID().toString();
        String email = UUID.randomUUID() + "@example.com";
        jdbc.update("insert into m_users(email,password,role) values (?,?,?)", email, "test-hash", "USER");
        Instant expiry = clock.instant().plusSeconds(300);
        sessions.create(sessionId, email, "test-refresh-token", expiry);
        LocalDateTime stored = jdbc.queryForObject("select expires_at from t_auth_sessions where session_id=?",
                LocalDateTime.class, UUID.fromString(sessionId));
        assertThat(stored).isCloseTo(LocalDateTime.ofInstant(expiry, clock.getZone()), within(1, ChronoUnit.MILLIS));
        assertThat(sessions.isActive(sessionId, email)).isTrue();
        sessions.revoke(sessionId);
        assertThat(sessions.isActive(sessionId, email)).isFalse();
    }
}
