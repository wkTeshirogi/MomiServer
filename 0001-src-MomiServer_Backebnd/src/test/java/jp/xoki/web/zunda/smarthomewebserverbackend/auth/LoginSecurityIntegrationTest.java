package jp.xoki.web.zunda.smarthomewebserverbackend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.SecuritySettingProperties;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 実DBのコミット境界、ロック永続化、管理者通知の宛先を確認する。 */
@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false", "app.vehicle-inspection-notification.enabled=false", "gateway.enabled=false",
        "app.security.external-inventory-hourly-limit=37"})
@AutoConfigureMockMvc
class LoginSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired DSLContext dsl;
    @Autowired Clock clock;
    @Autowired SecuritySettingProperties settings;
    @Autowired jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalApiKeyService apiKeys;
    private String email;
    private Long id;

    @BeforeEach
    void createAccount() {
        email = UUID.randomUUID() + "@example.invalid";
        id = jdbc.queryForObject("insert into m_users(email,password,role) values (?,?,'USER') returning id",
                Long.class, email, encoder.encode("valid-test-password"));
    }

    @Test
    void sixthAttemptRejectsCorrectPasswordAndPersistsFixedLockWithOneAdminOnlyNotice() throws Exception {
        for (int i = 0; i < 5; i++) login("wrong-password", 401);
        assertThat(count()).isEqualTo(5);
        assertThat(lockedUntil()).isNull();
        login("valid-test-password", 401);
        LocalDateTime until = lockedUntil();
        assertThat(until).isAfter(LocalDateTime.now(clock).plusMinutes(14));

        // Repositoryを作り直しても、メモリではなくDBのロック状態を読み出せる。
        assertThat(new UserRepository(dsl, clock).findByEmail(email).orElseThrow().getLoginLockedUntil()).isEqualTo(until);
        assertThat(noticeCount()).isEqualTo(1);
        for (int i = 0; i < 3; i++) login("valid-test-password", 401);
        assertThat(lockedUntil()).isEqualTo(until);
        assertThat(noticeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from t_user_notifications un "
                + "inner join t_notifications n on un.notification_id=n.notification_id "
                + "inner join m_users u on u.id=un.user_id "
                + "where n.source_type='security_event' and n.source_id=? and u.role<>'ADMIN'",
                Integer.class, id.toString())).isZero();
        assertThat(jdbc.queryForObject("select role from m_users where id=?", String.class, id)).isEqualTo("USER");
    }

    @Test
    void successAndExpiredLockResetFailures() throws Exception {
        login("wrong-password", 401);
        login("valid-test-password", 200);
        assertThat(count()).isZero();
        jdbc.update("update m_users set login_failure_count=5,login_locked_until=? where id=?",
                LocalDateTime.now(clock).minusSeconds(1), id);
        login("wrong-password", 401);
        assertThat(count()).isEqualTo(1);
        assertThat(lockedUntil()).isNull();
        login("valid-test-password", 200);
        assertThat(count()).isZero();
    }

    @Test
    void persistedLockIsHonouredWithoutAnyPreviousRequestInThisProcess() throws Exception {
        jdbc.update("update m_users set login_failure_count=5,login_locked_until=? where id=?",
                LocalDateTime.now(clock).plusMinutes(15), id);
        login("valid-test-password", 401);
        assertThat(noticeCount()).isZero();
        assertThat(count()).isEqualTo(5);
    }

    @Test
    void unknownAccountDoesNotCreateStateAndUsesSamePublicFailure() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", "missing-" + email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from m_users where email=?", Integer.class, "missing-" + email)).isZero();
    }

    @Test
    void settingApiExposesOnlyNonSecretsAndIsAdminReadOnly() throws Exception {
        mvc.perform(get("/api/master/common-settings/security").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loginLockMinutes").value(15))
                .andExpect(jsonPath("$.externalInventoryHourlyLimit").value(37))
                .andExpect(jsonPath("$.accessTokenSeconds").value(86400))
                .andExpect(jsonPath("$.secret").doesNotExist()).andExpect(jsonPath("$.iotKeys").doesNotExist());
        for (String role : new String[]{"USER", "OPERATOR", "AGENT", "EXTERNAL", "BAN_USER"}) {
            mvc.perform(get("/api/master/common-settings/security").with(user(email).roles(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/master/common-settings/security")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/master/common-settings/security").with(user(email).roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
        assertThat(settings.getExternalInventoryHourlyLimit()).isEqualTo(37);
    }

    @Test
    void configuredExternalLimitIsEnforcedByServiceAndFilterHeaders() throws Exception {
        jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalApiKeyService.IssuedKey issued =
                apiKeys.issue(new jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyIssueRequest(
                        "settings-limit", java.util.Set.of("inventory:read"), null), email);
        for (int i = 1; i < 37; i++) assertThat(apiKeys.consume(issued.key())).contains(i);
        mvc.perform(get("/api/v1/external/inventory/stocks").header("X-API-Key", issued.secret()))
                .andExpect(status().isOk()).andExpect(header().string("X-RateLimit-Limit", "37"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
        mvc.perform(get("/api/v1/external/inventory/stocks").header("X-API-Key", issued.secret()))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("X-RateLimit-Limit", "37"));
    }

    private void login(String password, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus));
    }

    private int count() { return jdbc.queryForObject("select login_failure_count from m_users where id=?", Integer.class, id); }
    private LocalDateTime lockedUntil() { return jdbc.queryForObject("select login_locked_until from m_users where id=?", LocalDateTime.class, id); }
    private int noticeCount() { return jdbc.queryForObject("select count(*) from t_notifications where source_type='security_event' and source_id=?", Integer.class, id.toString()); }
}
