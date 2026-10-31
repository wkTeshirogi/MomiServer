package jp.xoki.web.zunda.smarthomewebserverbackend.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 専用DBで本人境界、テーマ永続化、実JWTの失効を確認する。 */
@SpringBootTest(properties = {"mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.vehicle-inspection-notification.enabled=false", "gateway.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class PersonalSettingsIntegrationTest {
    private static final String API = "/api/users/me/settings";
    private static final String OLD_PASSWORD = "old-password-123";
    private static final String NEW_PASSWORD = "new-password-456";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    private String email;

    @BeforeEach
    void createUser() {
        email = insertUser("USER");
    }

    @Test
    void legacyUserHasUnsetUsernameAndDarkThemeWithoutSensitiveResponseFields() throws Exception {
        String response = mvc.perform(get(API).with(user(email).roles("USER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark")).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(response).size()).isEqualTo(2);
        assertThat(mapper.readTree(response).path("username").isNull()).isTrue();
        assertThat(response).doesNotContain("password", "role", "email", OLD_PASSWORD, passwordHash(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATOR", "USER"})
    void normalRolesCanChangeOnlyTheirOwnNameAndTheme(String role) throws Exception {
        jdbc.update("update m_users set role=? where email=?", role, email);
        String other = insertUser("USER");
        Map<String, Object> before = jdbc.queryForMap("select * from m_users where email=?", email);
        String malicious = mapper.writeValueAsString(Map.of("username", "  My name  ", "email", other,
                "role", "ADMIN", "id", jdbc.queryForObject("select id from m_users where email=?", Long.class, other)));
        mvc.perform(put(API + "/profile").with(user(email).roles(role)).contentType("application/json").content(malicious))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("My name"));
        mvc.perform(put(API + "/theme").with(user(email).roles(role)).contentType("application/json")
                .content("{\"theme\":\"light\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("My name"));
        mvc.perform(get(API).with(user(email).roles(role))).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("light"));
        Map<String, Object> after = jdbc.queryForMap("select * from m_users where email=?", email);
        for (String field : new String[]{"username", "theme", "updated_dt"}) {
            before.remove(field);
            after.remove(field);
        }
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("select username from m_users where email=?", String.class, other)).isNull();
        assertThat(jdbc.queryForObject("select theme from m_users where email=?", String.class, other)).isEqualTo("dark");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BAN_USER", "AGENT", "EXTERNAL", "UNKNOWN"})
    void nonWebRolesCannotUseAnyPersonalSettingsOperation(String role) throws Exception {
        mvc.perform(get(API).with(user(email).roles(role))).andExpect(status().isForbidden());
        mvc.perform(put(API + "/profile").with(user(email).roles(role)).contentType("application/json")
                .content("{\"username\":\"No\"}")).andExpect(status().isForbidden());
        mvc.perform(put(API + "/theme").with(user(email).roles(role)).contentType("application/json")
                .content("{\"theme\":\"light\"}")).andExpect(status().isForbidden());
        mvc.perform(post(API + "/password").with(user(email).roles(role)).contentType("application/json")
                .content(passwordBody(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void anonymousUsersCannotUseAnyPersonalSettingsOperation() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(put(API + "/profile").contentType("application/json").content("{\"username\":\"No\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(API + "/theme").contentType("application/json").content("{\"theme\":\"light\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/password").contentType("application/json")
                .content(passwordBody(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void blankNamesAreRejected(String name) throws Exception {
        mvc.perform(put(API + "/profile").with(user(email).roles("USER")).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("username", name)))).andExpect(status().isBadRequest());
    }

    @Test
    void nameLengthBoundaryIsEnforced() throws Exception {
        mvc.perform(put(API + "/profile").with(user(email).roles("USER")).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("username", "x".repeat(100)))))
                .andExpect(status().isOk());
        mvc.perform(put(API + "/profile").with(user(email).roles("USER")).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("username", "x".repeat(101)))))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"theme\":null}", "{\"theme\":\"LIGHT\"}", "{\"theme\":\"system\"}"})
    void invalidThemeIsRejected(String input) throws Exception {
        mvc.perform(put(API + "/theme").with(user(email).roles("USER")).contentType("application/json")
                .content(input)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select theme from m_users where email=?", String.class, email)).isEqualTo("dark");
    }

    @Test
    void incorrectCurrentPasswordDoesNotChangePasswordOrRevokeSessions() throws Exception {
        JsonNode tokens = login(email, OLD_PASSWORD);
        String originalHash = passwordHash(email);
        String response = mvc.perform(post(API + "/password").header("Authorization", bearer(tokens))
                .contentType("application/json").content(passwordBody("wrong-password", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("wrong-password", NEW_PASSWORD, originalHash);
        assertThat(passwordHash(email)).isEqualTo(originalHash);
        mvc.perform(get(API).header("Authorization", bearer(tokens))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "same", "mismatch", "bytes", "long", "missing"})
    void invalidPasswordChangePreservesStoredHash(String scenario) throws Exception {
        String originalHash = passwordHash(email);
        String next = switch (scenario) {
            case "short" -> "short";
            case "same" -> OLD_PASSWORD;
            case "bytes" -> "日".repeat(25);
            case "long" -> "x".repeat(73);
            default -> NEW_PASSWORD;
        };
        String input = "missing".equals(scenario) ? "{}"
                : passwordBody(OLD_PASSWORD, next, "mismatch".equals(scenario) ? "different-value" : next);
        mvc.perform(post(API + "/password").with(user(email).roles("USER")).contentType("application/json")
                .content(input)).andExpect(status().isBadRequest());
        assertThat(passwordHash(email)).isEqualTo(originalHash);
    }

    @Test
    void changingPasswordRevokesAllOwnTokensButNotAnotherUsersSession() throws Exception {
        JsonNode first = login(email, OLD_PASSWORD);
        JsonNode second = login(email, OLD_PASSWORD);
        String other = insertUser("USER");
        JsonNode otherTokens = login(other, OLD_PASSWORD);
        String input = passwordBody(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        mvc.perform(post(API + "/password").header("Authorization", bearer(first))
                .contentType("application/json").content(input)).andExpect(status().isNoContent());
        assertThat(encoder.matches(NEW_PASSWORD, passwordHash(email))).isTrue();
        assertThat(passwordHash(email)).isNotEqualTo(NEW_PASSWORD);
        for (JsonNode tokens : new JsonNode[]{first, second}) {
            mvc.perform(get(API).header("Authorization", bearer(tokens))).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/refresh").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("refreshToken", tokens.path("refreshToken").asText()))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get(API).header("Authorization", bearer(otherTokens))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "password", OLD_PASSWORD))))
                .andExpect(status().isUnauthorized());
        assertThat(login(email, NEW_PASSWORD).path("accessToken").asText()).isNotBlank();
    }

    @Test
    void unicodePasswordAtBcryptByteBoundaryCanBeUsedForLogin() throws Exception {
        String next = "日".repeat(24);
        mvc.perform(post(API + "/password").with(user(email).roles("USER")).contentType("application/json")
                .content(passwordBody(OLD_PASSWORD, next, next))).andExpect(status().isNoContent());
        assertThat(login(email, next).path("accessToken").asText()).isNotBlank();
    }

    private String insertUser(String role) {
        String target = UUID.randomUUID() + "@example.com";
        jdbc.update("insert into m_users(email,password,role) values (?,?,?)", target, encoder.encode(OLD_PASSWORD), role);
        return target;
    }

    private String passwordHash(String target) {
        return jdbc.queryForObject("select password from m_users where email=?", String.class, target);
    }

    private String passwordBody(String current, String next, String confirmation) throws Exception {
        return mapper.writeValueAsString(Map.of("current_password", current, "new_password", next,
                "confirm_password", confirmation));
    }

    private JsonNode login(String target, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", target, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private String bearer(JsonNode tokens) {
        return "Bearer " + tokens.path("accessToken").asText();
    }
}
