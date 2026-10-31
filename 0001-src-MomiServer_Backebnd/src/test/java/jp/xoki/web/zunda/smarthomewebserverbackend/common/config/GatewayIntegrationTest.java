package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentSecrets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 実DBと認証フィルターでGatewayの認証境界・公開状態を検証する。 */
@SpringBootTest(properties = {
		"gateway.enabled=true",
		"gateway.api-key.enabled=true",
		"gateway.api-key.iot-keys=boundary-key,version-key,rate-key",
		"gateway.rate-limit.enabled=true",
		"gateway.rate-limit.per-minute=2",
		"gateway.openapi.public-enabled=false",
		"mqtt.enabled=false",
		"notification.discord.enabled=false",
		"notification.discord.security-events.enabled=false",
		"spring.batch.job.enabled=false",
		"app.agent-auth.response-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
@Import(GatewayIntegrationTest.IotProbeController.class)
@Transactional
class GatewayIntegrationTest {

	private static final String IOT_PATH = "/api/v1/iot/gateway-test";
	private static final String AGENT_PATH = "/api/v1/agent/me";
	private static final String WEB_PATH = "/api/notifications";

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private PasswordEncoder encoder;
    @Autowired private org.jooq.DSLContext dsl;
    @Autowired private jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.ApiVersionStatusRepository versions;

    @Test
    void springManagedJooqRendersExplicitInnerJoin() {
        assertThat(dsl.settings().getRenderOptionalInnerKeyword()).isEqualTo(org.jooq.conf.RenderOptionalKeyword.ON);
        String sql = dsl.select(jp.xoki.web.zunda.jooq.Tables.M_AGENTS.ID)
                .from(jp.xoki.web.zunda.jooq.Tables.M_AGENTS)
                .innerJoin(jp.xoki.web.zunda.jooq.Tables.M_DEVICES)
                .on(jp.xoki.web.zunda.jooq.Tables.M_DEVICES.ID.eq(jp.xoki.web.zunda.jooq.Tables.M_AGENTS.DEVICE_ID))
                .getSQL();
        assertThat(sql.toLowerCase(java.util.Locale.ROOT)).contains(" inner join ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01T00:00:00.499", "2026-01-01T00:00:00.500", "2040-01-01T00:00:00.750"})
    void deprecatedEpochPreservesDatabaseRoundingAndTokyoInterpretation(String date) {
        jdbc.execute("set local time zone 'UTC'");
        jdbc.update("update m_api_versions set deprecated_dt=?,status='deprecated' where api_group='iot' and version='v1'",
                LocalDateTime.parse(date));
        Long expected = jdbc.queryForObject("""
                select extract(epoch from (coalesce(deprecated_dt,updated_dt) at time zone 'Asia/Tokyo'))::bigint
                from m_api_versions where api_group='iot' and version='v1'
                """, Long.class);
        assertThat(versions.find("iot", "v1").orElseThrow().deprecatedEpochSeconds()).isEqualTo(expected);
    }

    @Test
    void missingDeprecationDateFallsBackToUpdatedDate() {
        jdbc.update("update m_api_versions set deprecated_dt=null,updated_dt=? where api_group='iot' and version='v1'",
                LocalDateTime.parse("2040-01-01T00:00:00.500"));
        Long expected = jdbc.queryForObject("""
                select extract(epoch from (updated_dt at time zone 'Asia/Tokyo'))::bigint
                from m_api_versions where api_group='iot' and version='v1'
                """, Long.class);
        assertThat(versions.find("iot", "v1").orElseThrow().deprecatedEpochSeconds()).isEqualTo(expected);
        assertThat(versions.find("missing-jooq-test", "v1")).isEmpty();
    }

	@BeforeEach
	void activateImplementedVersions() {
		jdbc.update("""
				INSERT INTO m_api_versions(api_group, version, status) VALUES
				('agent', 'v1', 'active'), ('iot', 'v1', 'active')
				ON CONFLICT (api_group, version) DO UPDATE SET status='active', deprecated_dt=NULL
				""");
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "OPERATOR", "ADMIN"})
	void webJwtCannotReplaceAgentBearerOrIotKey(String role) throws Exception {
		String token = userBearer(role);
		mvc.perform(get(WEB_PATH).header("Authorization", token)).andExpect(status().isOk());
		mvc.perform(get(AGENT_PATH).header("Authorization", token))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mvc.perform(get(IOT_PATH).header("Authorization", token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_GATEWAY_API_KEY"));
	}

	@Test
	void credentialsRemainScopedToTheirOwnNamespace() throws Exception {
		String token = agentBearer();
		mvc.perform(get(AGENT_PATH).header("Authorization", token)).andExpect(status().isOk());
		mvc.perform(get(AGENT_PATH).header("Authorization", token).header("X-API-Key", "wrong"))
				.andExpect(status().isOk());
		mvc.perform(get(AGENT_PATH).header("Authorization", token)).andExpect(status().isOk());
		mvc.perform(get(WEB_PATH).header("Authorization", token)).andExpect(status().isUnauthorized());
		mvc.perform(get(IOT_PATH).header("Authorization", token)).andExpect(status().isUnauthorized());
		mvc.perform(get(IOT_PATH).header("X-API-Key", "boundary-key"))
				.andExpect(status().isOk()).andExpect(content().string("iot-probe"));
		mvc.perform(get(AGENT_PATH).header("X-API-Key", "boundary-key"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get(WEB_PATH).header("X-API-Key", "boundary-key"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/v3/api-docs").header("Authorization", token))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/v3/api-docs").header("X-API-Key", "boundary-key"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get(AGENT_PATH).header("Authorization", "Bearer ma_invalid"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void iotRateLimitRejectsBeforeControllerWithoutLimitingWebOrAgent() throws Exception {
		for (int remaining : new int[]{1, 0}) {
			mvc.perform(get(IOT_PATH).header("X-API-Key", "rate-key"))
					.andExpect(status().isOk()).andExpect(header().string("X-RateLimit-Limit", "2"))
					.andExpect(header().string("X-RateLimit-Remaining", String.valueOf(remaining)));
		}
		mvc.perform(get(IOT_PATH).header("X-API-Key", "rate-key"))
				.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("GATEWAY_RATE_LIMIT_EXCEEDED"));
		mvc.perform(get(AGENT_PATH).header("Authorization", agentBearer()).header("X-API-Key", "rate-key"))
				.andExpect(status().isOk()).andExpect(header().doesNotExist("X-RateLimit-Limit"));
		mvc.perform(get(WEB_PATH).header("Authorization", userBearer("USER")))
				.andExpect(status().isOk()).andExpect(header().doesNotExist("X-RateLimit-Limit"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"agent", "iot"})
	void missingVersionIsRejectedBeforeAuthentication(String group) throws Exception {
		jdbc.update("DELETE FROM m_api_versions WHERE api_group=? AND version='v1'", group);
		mvc.perform(get(path(group))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("API_VERSION_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"agent", "iot"})
	void databaseEntryCannotPublishAnUnimplementedVersion(String group) throws Exception {
		jdbc.update("INSERT INTO m_api_versions(api_group,version,status) VALUES (?,'v2','active')", group);
		mvc.perform(get(path(group).replace("/v1/", "/v2/"))
				.header("Authorization", userBearer("ADMIN")).header("X-API-Key", "version-key"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("API_VERSION_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"agent", "iot"})
	void retiredVersionIsRejectedBeforeAuthentication(String group) throws Exception {
		jdbc.update("UPDATE m_api_versions SET status='retired' WHERE api_group=? AND version='v1'", group);
		mvc.perform(get(path(group))).andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("API_VERSION_RETIRED"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"agent", "iot"})
	void deprecatedVersionPreservesAuthenticationAndReportsExactDate(String group) throws Exception {
		LocalDateTime deprecatedDate = LocalDateTime.parse("2026-01-01T00:00:00");
		jdbc.update("UPDATE m_api_versions SET status='deprecated', deprecated_dt=? "
				+ "WHERE api_group=? AND version='v1'", deprecatedDate, group);
		String expected = "@" + deprecatedDate.atZone(ZoneId.of("Asia/Tokyo")).toEpochSecond();
		mvc.perform(get(path(group))).andExpect(status().isUnauthorized())
				.andExpect(header().string("Deprecation", expected));
		MockHttpServletRequestBuilder request = get(path(group));
		if ("agent".equals(group)) {
			request.header("Authorization", agentBearer());
		} else {
			request.header("X-API-Key", "version-key");
		}
		mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Deprecation", expected));
	}

	@ParameterizedTest
	@ValueSource(strings = {"agent", "iot"})
	void unavailableVersionStorageDoesNotBypassGateway(String group) throws Exception {
		// Transaction rollback restores the table after the real DB read fails.
		jdbc.execute("ALTER TABLE m_api_versions RENAME TO gateway_test_unavailable_versions");
		mvc.perform(get(path(group)).header("X-API-Key", "version-key"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("API_VERSION_UNAVAILABLE"));
	}

	@Test
	void unknownControllerRouteReturns404ForAuthenticatedUser() throws Exception {
		mvc.perform(get("/api/gateway-test-missing").header("Authorization", userBearer("ADMIN")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "OPERATOR", "ADMIN"})
	void privateOpenApiUsesActualJwtRole(String role) throws Exception {
		String token = userBearer(role);
		for (String path : new String[]{"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
				"/swagger-ui/index.html"}) {
			mvc.perform(get(path).header("Authorization", token))
					.andExpect("ADMIN".equals(role) ? status().isOk() : status().isForbidden());
		}
	}

	@Test
	void openApiAuthenticationMatchesTheConfiguredRoutes() throws Exception {
		MockHttpServletResponse response = mvc.perform(get("/v3/api-docs").header("Authorization", userBearer("ADMIN")))
				.andExpect(status().isOk()).andReturn().getResponse();
		JsonNode api = mapper.readTree(response.getContentAsString());
		assertThat(api.at("/paths/~1api~1notifications/get/security/0/userBearer").isArray()).isTrue();
		assertThat(api.at("/paths/~1api~1v1~1agent~1me/get/security/0/agentBearer").isArray()).isTrue();
		assertThat(api.at("/paths/~1api~1v1~1iot~1gateway-test/get/security/0/iotApiKey").isArray()).isTrue();
		for (String path : new String[]{"~1api~1auth~1login", "~1api~1auth~1refresh",
				"~1api~1v1~1agent~1register", "~1api~1v1~1agent~1auth~1challenge",
				"~1api~1v1~1agent~1auth~1refresh"}) {
			JsonNode security = api.at("/paths/" + path + "/post/security");
			assertThat(security.isArray()).as(path).isTrue();
			assertThat(security.size()).as(path).isZero();
		}
		assertThat(api.at("/components/securitySchemes/iotApiKey/name").asText()).isEqualTo("X-API-Key");
	}

	private String path(String group) {
		return "agent".equals(group) ? AGENT_PATH : IOT_PATH;
	}

	private String userBearer(String role) throws Exception {
		String email = UUID.randomUUID() + "@example.invalid";
		String password = "Gateway-test-password";
		jdbc.update("INSERT INTO m_users(email,password,role) VALUES (?,?,?)",
				email, encoder.encode(password), role);
		MockHttpServletResponse response = mvc.perform(post("/api/auth/login").contentType("application/json")
				.content(mapper.writeValueAsBytes(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andReturn().getResponse();
		return "Bearer " + mapper.readTree(response.getContentAsString()).get("accessToken").asText();
	}

	private String agentBearer() {
		UUID id = UUID.randomUUID();
		long device = jdbc.queryForObject("""
				INSERT INTO m_devices(device_name,address,status)
				VALUES (?,'127.0.0.1','1') RETURNING id
				""", Long.class, "gateway-test-" + id);
		jdbc.update("INSERT INTO m_agents(id,device_id,status,mqtt_client_id) VALUES (?,?,'registered',?)",
				id, device, "gateway-test-" + id);
		String token = "ma_" + UUID.randomUUID();
		jdbc.update("""
				INSERT INTO t_agent_sessions(id,agent_id,generation,access_hash,refresh_hash,
				access_expires_at,refresh_expires_at)
				VALUES (?,?,1,?,?,now()+interval '1 hour',now()+interval '1 day')
				""", UUID.randomUUID(), id, AgentSecrets.hash(token),
				AgentSecrets.hash(UUID.randomUUID().toString()));
		return "Bearer " + token;
	}

	/** Test-only IoT endpoint; production currently has no IoT business Controller. */
	@TestComponent
	@RestController
	static class IotProbeController {
		@GetMapping(IOT_PATH)
		String probe() {
			return "iot-probe";
		}
	}
}
