package jp.xoki.web.zunda.smarthomewebserverbackend.common.time;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApplicationTimeConstants;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.JwtService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ApiErrorResponse;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.security.core.userdetails.UserDetails;

class ApplicationClockTest {
    @Test
    void factoryAndBeanUseTheBusinessZoneWithoutCachingTheCurrentDateTime() {
        Instant before = Instant.now(Clock.systemUTC());
        Clock clock = new ApplicationTimeConfiguration().applicationClock();
        assertThat(clock.getZone()).isEqualTo(ApplicationTimeConstants.ZONE);
        assertThat(clock.instant()).isBetween(before, Instant.now(Clock.systemUTC()));
        assertThat(ApplicationClockFactory.systemClock().getZone().getId()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void fixedClockDistinguishesBusinessDateFromUtcWithoutChangingTheInstant() {
        Instant instant = Instant.parse("2026-10-24T15:00:00Z");
        Clock clock = Clock.fixed(instant, ApplicationTimeConstants.ZONE);
        assertThat(LocalDateTime.now(clock)).isEqualTo(LocalDateTime.of(2026, 10, 25, 0, 0));
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(LocalDate.now(clock.withZone(ZoneOffset.UTC))).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(OffsetDateTime.now(clock).toInstant()).isEqualTo(instant);
        assertThat(clock.withZone(ZoneOffset.UTC).instant()).isEqualTo(clock.instant());
    }

    @Test
    void allPersonalSettingWritesBindTheInjectedFixedTimestamp() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ApplicationTimeConstants.ZONE);
        List<List<Object>> bindings = new ArrayList<>();
        try (MockConnection connection = new MockConnection(context -> {
            bindings.add(Arrays.asList(context.bindings()));
            return new MockResult[]{new MockResult(1)};
        })) {
            UserRepository users = new UserRepository(DSL.using(connection, SQLDialect.POSTGRES), clock);
            users.updatePersonalUsername("clock@example.com", "Clock user");
            users.updatePersonalTheme("clock@example.com", "light");
            users.updatePersonalPassword("clock@example.com", "test-hash");
        }
        assertThat(bindings).hasSize(3).allSatisfy(values ->
                assertThat(values).contains(java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 25, 0, 0)), "clock@example.com"));
    }

    @Test
    void errorResponseUsesTheInjectedTimestamp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ApplicationTimeConstants.ZONE);
        ApiErrorResponse response = ApiErrorResponse.of(400, "Bad Request", "INVALID_REQUEST", "Test", "/test", clock);
        assertThat(response.timestamp()).isEqualTo(OffsetDateTime.parse("2026-10-25T00:00:00+09:00"));
    }

    @Test
    void jwtIssuanceAndParsingUseTheSameFixedClockRatherThanTheHostClock() {
        Instant instant = Instant.parse("2000-01-01T00:00:00Z");
        Clock clock = Clock.fixed(instant, ApplicationTimeConstants.ZONE);
        String secret = "clock-test-secret-with-at-least-thirty-two-bytes";
        UserDetails user = User.withUsername("clock@example.com").password("unused").roles("USER").build();
        JwtService service = new JwtService(secret, 300, 600, clock);
        String token = service.generateAccessToken(user, "clock-session");
        assertThat(service.extractExpiration(token)).isEqualTo(instant.plusSeconds(300));
        assertThat(service.isValidAccessToken(token, user)).isTrue();
        JwtService expired = new JwtService(secret, 300, 600, Clock.offset(clock, java.time.Duration.ofSeconds(301)));
        assertThat(expired.isValidAccessToken(token, user)).isFalse();
    }
}
