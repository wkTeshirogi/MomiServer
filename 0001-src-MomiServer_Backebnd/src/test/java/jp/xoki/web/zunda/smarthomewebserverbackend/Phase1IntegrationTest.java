package jp.xoki.web.zunda.smarthomewebserverbackend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleInspectionNotificationService;

import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/** 実DB、認証フィルタ、Controller、Serviceを通した回帰確認。検証用DBで実行する。 */
@SpringBootTest(properties = {
        "mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.vehicle-inspection-notification.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class Phase1IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired VehicleInspectionNotificationService vehicleInspectionNotifications;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void failedVehicleNotificationTransactionCanBeRetried() {
        jdbc.update("update t_notification_global_settings set enabled=true,browser_enabled=true,discord_enabled=false where id=1");
        LocalDate expiry = LocalDate.of(2027, 4, 30);
        Long id = jdbc.queryForObject("insert into t_vehicles(display_name,registration_number,chassis_number,vehicle_name,inspection_expiry_date,created_by,updated_by) "
                + "values ('通知再試行','テスト501','RETRY-' || ?,'テスト車',?,'test','test') returning id", Long.class,
                UUID.randomUUID().toString(), expiry);
        TransactionTemplate transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            assertThat(vehicleInspectionNotifications.notifyIfDue(id, expiry.minusMonths(2))).isTrue();
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from t_vehicle_inspection_notifications where vehicle_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from t_notifications where source_type='vehicle' and source_id=?", Integer.class, id.toString())).isZero();
        assertThat(vehicleInspectionNotifications.notifyIfDue(id, expiry.minusMonths(2))).isTrue();
    }

    @Test
    void vehicleExpiryNotificationsAreRoutedDeduplicatedAndRenewed() throws Exception {
        String email = createUser("USER");
        Long userId = jdbc.queryForObject("select id from m_users where email=?", Long.class, email);
        jdbc.update("update t_notification_global_settings set enabled=true,browser_enabled=true,discord_enabled=false where id=1");
        jdbc.update("insert into t_notification_route_settings(notification_type,source_type,target_type,target_user_id,minimum_level,browser_enabled,discord_enabled,enabled) "
                + "values ('vehicle_inspection_expiry','vehicle','user',?,'warning',true,false,true)", userId);
        LocalDate expiry = LocalDate.of(2027, 4, 30);
        Long vehicleId = jdbc.queryForObject("insert into t_vehicles(display_name,registration_number,chassis_number,vehicle_name,inspection_expiry_date,created_by,updated_by) "
                + "values ('期限通知テスト','テスト500','NOTICE-' || ?,'テスト車',?,?,?) returning id", Long.class,
                UUID.randomUUID().toString(), expiry, email, email);

        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 2, 27))).isFalse();
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 2, 28))).isTrue();
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 3, 1))).isFalse();
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 3, 30))).isTrue();
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 4, 23))).isTrue();
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 4, 30))).isTrue();
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 5, 1))).isTrue();
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 5, 2))).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from t_vehicle_inspection_notifications where vehicle_id=?", Integer.class, vehicleId)).isEqualTo(5);
        String bearer = "Bearer " + login(email).get("accessToken").asText();
        mvc.perform(get("/api/notifications").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notification_type").value("vehicle_inspection_expiry"))
                .andExpect(jsonPath("$[0].link_path").value("/vehicles/" + vehicleId));
        mvc.perform(get("/api/notifications/unread/count").header("Authorization", bearer))
                .andExpect(jsonPath("$.unread_count").value(5));

        // 通知本体の保持削除後も通知済み記録は残す。
        jdbc.update("delete from t_notifications where source_type='vehicle' and source_id=?", vehicleId.toString());
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2027, 5, 2))).isFalse();
        LocalDate renewed = LocalDate.of(2029, 4, 30);
        jdbc.update("update t_vehicles set inspection_expiry_date=? where id=?", renewed, vehicleId);
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2029, 2, 28))).isTrue();
        jdbc.update("update t_vehicles set status='ARCHIVED' where id=?", vehicleId);
        assertThat(vehicleInspectionNotifications.notifyIfDue(vehicleId, LocalDate.of(2029, 3, 30))).isFalse();
    }

    private String createUser(String role) {
        String email = UUID.randomUUID() + "@example.invalid";
        jdbc.update("insert into m_users(email,password,role) values (?,?,?)",
                email, encoder.encode("Phase1-test-password"), role);
        return email;
    }

    private JsonNode login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "Phase1-test-password"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void loginRefreshLogoutRevokesSession() throws Exception {
        String email = createUser("USER");
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        JsonNode initial = login(email);
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("refreshToken", initial.get("refreshToken").asText()))))
                .andExpect(status().isOk()).andReturn();
        JsonNode rotated = mapper.readTree(refreshed.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("refreshToken", initial.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        String bearer = "Bearer " + rotated.get("accessToken").asText();
        mvc.perform(get("/api/notifications").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("refreshToken", rotated.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("refreshToken", rotated.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bannedUserCannotLoginRefreshOrUseAnExistingAccessToken() throws Exception {
        String email = createUser("USER");
        JsonNode tokens = login(email);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        String adminBearer = "Bearer " + login(createUser("ADMIN")).get("accessToken").asText();

        mvc.perform(post("/api/auth/settings").header("Authorization", adminBearer)
                .contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "role", "BAN_USER"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notifications").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of(
                        "refreshToken", tokens.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of(
                        "email", email, "password", "Phase1-test-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        assertThat(jdbc.queryForObject(
                "select count(*) from t_auth_sessions where user_email=? and revoked=false",
                Integer.class, email)).isZero();
    }

    @Test
    void notificationsRemainScopedToRecipientAndReadStatePersists() throws Exception {
        String owner = createUser("USER");
        String other = createUser("USER");
        String ownerToken = "Bearer " + login(owner).get("accessToken").asText();
        String otherToken = "Bearer " + login(other).get("accessToken").asText();
        UUID notification = UUID.randomUUID();
        jdbc.update("insert into t_notifications(notification_id,notification_type,level,title,message,source_type) "
                + "values (?,'system_notice','info','Phase1 fixture','Fixture message','system')", notification);
        jdbc.update("insert into t_user_notifications(notification_id,user_id) "
                + "select ?,id from m_users where email=?", notification, owner);
        mvc.perform(get("/api/notifications").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Phase1 fixture"));
        mvc.perform(get("/api/notifications").header("Authorization", otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        for (String action : new String[]{"read", "dismiss", "delivered"}) {
            mvc.perform(post("/api/notifications/" + notification + "/" + action).header("Authorization", otherToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/notifications/unread/count").header("Authorization", ownerToken))
                .andExpect(jsonPath("$.unread_count").value(1));
        mvc.perform(get("/api/notifications/unread").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Phase1 fixture"));
        mvc.perform(post("/api/notifications/" + notification + "/read").header("Authorization", ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread/count").header("Authorization", ownerToken))
                .andExpect(jsonPath("$.unread_count").value(0));
        assertThat(jdbc.queryForObject("select read_status from t_user_notifications where notification_id=?",
                String.class, notification)).isEqualTo("read");
        mvc.perform(post("/api/notifications/" + notification + "/dismiss").header("Authorization", ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", ownerToken))
                .andExpect(content().json("[]"));
    }

    @Test
    void adminSettingsAndAgentBoundaryAreEnforced() throws Exception {
        String user = "Bearer " + login(createUser("USER")).get("accessToken").asText();
        String admin = "Bearer " + login(createUser("ADMIN")).get("accessToken").asText();
        mvc.perform(get("/api/master/common-settings/batch")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/master/common-settings/batch").header("Authorization", user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/master/common-settings/batch").header("Authorization", admin))
                .andExpect(status().isOk());
        for (String path : new String[]{"/api/master/user/list", "/api/master/device/list",
                "/api/master/service/list", "/api/master/batch/list",
                "/api/master/batch/schedule/list"}) {
            mvc.perform(post(path).header("Authorization", user)).andExpect(status().isForbidden());
            mvc.perform(post(path).header("Authorization", admin)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/settings").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/settings").header("Authorization", user)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        String target = createUser("USER");
        mvc.perform(post("/api/auth/settings").header("Authorization", admin).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", target, "role", "OPERATOR"))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select role from m_users where email=?", String.class, target))
                .isEqualTo("OPERATOR");
        for (String path : new String[]{"/api/v1/agent/batch/settings", "/api/v1/agent/batch/executions/"
                + UUID.randomUUID() + "/state"}) {
            mvc.perform(get(path).header("X-API-Key", "not-an-agent-credential"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", admin)).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @CsvSource({"USER,1", "OPERATOR,2", "ADMIN,4"})
    void notificationVisibilityAndStateUseRealDatabase(String role, int visibleCount) throws Exception {
        String owner = createUser(role);
        String bearer = "Bearer " + login(owner).get("accessToken").asText();
        String other = "Bearer " + login(createUser(role)).get("accessToken").asText();
        UUID ordinary = insertNotification(owner, "Visible fixture", "system", "/stock", false);
        insertNotification(owner, "Machine fixture", "system", "/machine/status", false);
        insertNotification(owner, "Master fixture", "system", "/master/users", false);
        insertNotification(owner, "Batch fixture", "batch_execution", null, false);
        insertNotification(owner, "Expired fixture", "system", "/stock", true);
        mvc.perform(get("/api/notifications").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(visibleCount));
        mvc.perform(get("/api/notifications/unread").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(visibleCount));
        mvc.perform(get("/api/notifications/unread/count").header("Authorization", bearer))
                .andExpect(jsonPath("$.unread_count").value(visibleCount));
        mvc.perform(get("/api/notifications").header("Authorization", other))
                .andExpect(content().json("[]"));
        for (String action : new String[]{"read", "dismiss", "delivered"}) {
            mvc.perform(post("/api/notifications/" + ordinary + "/" + action).header("Authorization", other))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/notifications/" + ordinary + "/delivered").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/notifications/" + ordinary + "/read").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread/count").header("Authorization", bearer))
                .andExpect(jsonPath("$.unread_count").value(visibleCount - 1));
        mvc.perform(get("/api/notifications").header("Authorization", bearer))
                .andExpect(jsonPath("$.length()").value(visibleCount));
        mvc.perform(post("/api/notifications/" + ordinary + "/dismiss").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", bearer))
                .andExpect(jsonPath("$.length()").value(visibleCount - 1));
        assertThat(jdbc.queryForObject("select read_status from t_user_notifications where notification_id=?",
                String.class, ordinary)).isEqualTo("dismissed");
    }

    private UUID insertNotification(String owner, String title, String sourceType, String link, boolean expired) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into t_notifications(notification_id,notification_type,level,title,message,source_type,link_path,expires_at) "
                + "values (?,'system_notice','info',?,'Fixture message',?,?,?)", id, title, sourceType, link,
                expired ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(1)) : null);
        jdbc.update("insert into t_user_notifications(notification_id,user_id) "
                + "select ?,id from m_users where email=?", id, owner);
        return id;
    }

    @Test
    void stockAndNotificationsAreAvailableToEveryLoginRole() throws Exception {
        mvc.perform(post("/api/stock/list")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());

        for (String role : new String[]{"USER", "OPERATOR", "ADMIN"}) {
            String bearer = "Bearer " + login(createUser(role)).get("accessToken").asText();
            mvc.perform(post("/api/master/item/list").header("Authorization", bearer))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/stock/list").header("Authorization", bearer))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/notifications").header("Authorization", bearer))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/notifications/unread").header("Authorization", bearer))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/notifications/unread/count").header("Authorization", bearer))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void apiAccessLogsAreRecordedAndReadableByOperatorsAndAdministrators() throws Exception {
        String operatorEmail = createUser("OPERATOR");
        String operator = "Bearer " + login(operatorEmail).get("accessToken").asText();
        String admin = "Bearer " + login(createUser("ADMIN")).get("accessToken").asText();
        String user = "Bearer " + login(createUser("USER")).get("accessToken").asText();

        MockHttpServletResponse response = mvc.perform(get("/api/notifications")
                        .header("Authorization", operator)
                        .header("User-Agent", "phase1-api-log-test"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn().getResponse();
        UUID requestId = UUID.fromString(response.getHeader("X-Request-ID"));

        Map<String, Object> stored = jdbc.queryForMap("""
                select http_method, request_path, status_code::integer as status_code, source_type, principal,
                       user_agent, error_code
                  from t_api_access_logs
                 where request_id = ?
                """, requestId);
        assertThat(stored)
                .containsEntry("http_method", "GET")
                .containsEntry("request_path", "/api/notifications")
                .containsEntry("status_code", 200)
                .containsEntry("source_type", "web")
                .containsEntry("principal", operatorEmail)
                .containsEntry("user_agent", "phase1-api-log-test");

        mvc.perform(get("/api/logs/access").header("Authorization", user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/logs/access").header("Authorization", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs").isArray());
        MockHttpServletResponse detailResponse = mvc.perform(get("/api/logs/access/" + requestId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value(requestId.toString()))
                .andExpect(jsonPath("$.principal").value(operatorEmail))
                .andReturn().getResponse();
        UUID detailAccessRequestId = UUID.fromString(
                detailResponse.getHeader("X-Request-ID"));

        mvc.perform(get("/api/logs/access")
                        .param("request_path", "/api/logs/access/{requestId}")
                        .header("Authorization", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs[0].request_id")
                        .value(detailAccessRequestId.toString()))
                .andExpect(jsonPath("$.logs[0].endpoint_pattern")
                        .value("/api/logs/access/{requestId}"));
    }

	@Test
	void vehicleManagementEnforcesReadOperateAndArchiveRoles() throws Exception {
		String user = "Bearer " + login(createUser("USER")).get("accessToken").asText();
		String operator = "Bearer " + login(createUser("OPERATOR")).get("accessToken").asText();
		String admin = "Bearer " + login(createUser("ADMIN")).get("accessToken").asText();
		Map<String, Object> vehicle = Map.of(
				"display_name", "統合テスト車両",
				"registration_number", "品川 300 あ 12-34",
				"chassis_number", "PHASE1-VEHICLE-001",
				"vehicle_name", "テスト車",
				"inspection_expiry_date", LocalDate.now(ApplicationClockFactory.systemClock()).plusDays(20).toString());

		mvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/vehicles").header("Authorization", user)
				.contentType("application/json").content(mapper.writeValueAsString(vehicle)))
				.andExpect(status().isForbidden());

		MockHttpServletResponse createdResponse = mvc.perform(post("/api/vehicles").header("Authorization", operator)
						.contentType("application/json").content(mapper.writeValueAsString(vehicle)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DUE_SOON"))
				.andReturn().getResponse();
		long vehicleId = mapper.readTree(createdResponse.getContentAsString()).get("id").asLong();

		for (String bearer : new String[]{user, operator, admin}) {
			mvc.perform(get("/api/vehicles").header("Authorization", bearer))
					.andExpect(status().isOk());
			mvc.perform(get("/api/vehicles/" + vehicleId).header("Authorization", bearer))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.display_name").value("統合テスト車両"));
			mvc.perform(get("/api/vehicles/" + vehicleId + "/history").header("Authorization", bearer))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].change_type").value("CREATED"));
		}

		mvc.perform(put("/api/vehicles/" + vehicleId).header("Authorization", user)
				.contentType("application/json").content(mapper.writeValueAsString(vehicle)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/vehicles/" + vehicleId + "/archive").header("Authorization", operator))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/vehicles/" + vehicleId + "/archive").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	@Test
	void vehicleImportRequiresOperateRoleAndRegistersThroughVehicleService() throws Exception {
		String user = "Bearer " + login(createUser("USER")).get("accessToken").asText();
		String operator = "Bearer " + login(createUser("OPERATOR")).get("accessToken").asText();
		MockMultipartFile document = new MockMultipartFile("file", "inspection.pdf", "application/pdf", textPdf());

		mvc.perform(multipart("/api/vehicle-imports").file(document).header("Content-Length", document.getSize() + 1024)
				.header("Authorization", user))
				.andExpect(status().isForbidden());
		MvcResult upload = mvc.perform(multipart("/api/vehicle-imports").file(document).header("Content-Length", document.getSize() + 1024)
				.header("Authorization", operator))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.display_filename").value("uploaded-document.pdf"))
				.andExpect(jsonPath("$.extraction_method").value("PDF_TEXT")).andReturn();
		String importId = mapper.readTree(upload.getResponse().getContentAsString()).get("id").asText();
		Map<String, Object> request = Map.of("display_name", "取込統合テスト車", "registration_number", "品川 500 さ 56-78",
				"chassis_number", "IMPORT-" + UUID.randomUUID(), "vehicle_name", "取込車", "inspection_expiry_date", LocalDate.now(ApplicationClockFactory.systemClock()).plusYears(1).toString());
		mvc.perform(get("/api/vehicle-imports/" + importId).header("Authorization", user)).andExpect(status().isForbidden());
		mvc.perform(get("/api/vehicle-imports/" + importId + "/source").header("Authorization", user)).andExpect(status().isForbidden());
		mvc.perform(post("/api/vehicle-imports/" + importId + "/confirm").header("Authorization", user)
				.contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isForbidden());
		mvc.perform(post("/api/vehicle-imports/" + importId + "/cancel").header("Authorization", user)).andExpect(status().isForbidden());

		mvc.perform(post("/api/vehicle-imports/" + importId + "/confirm").header("Authorization", operator)
				.contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select status from t_vehicle_imports where id = ?", String.class,
				UUID.fromString(importId))).isEqualTo("REGISTERED");
	}

	private byte[] textPdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(72, 720);
				stream.showText("Vehicle inspection import integration document");
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	@Test
	void vehicleManagementRejectsNormalizedDuplicateCandidates() throws Exception {
		String operator = "Bearer " + login(createUser("OPERATOR")).get("accessToken").asText();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		ObjectNode original = mapper.createObjectNode()
				.put("display_name", "重複判定元車両")
				.put("registration_number", "品川 300 あ " + suffix)
				.put("chassis_number", "DUPLICATE-CHASSIS-" + suffix)
				.put("vehicle_identification_code", "DUPLICATE-CODE-" + suffix)
				.put("vehicle_name", "重複判定テスト車")
				.put("inspection_expiry_date", LocalDate.now(ApplicationClockFactory.systemClock()).plusYears(1).toString());

		mvc.perform(post("/api/vehicles").header("Authorization", operator)
				.contentType("application/json").content(mapper.writeValueAsString(original)))
				.andExpect(status().isOk());

		ObjectNode chassisDuplicate = original.deepCopy()
				.put("registration_number", "横浜 300 あ " + suffix)
				.put("chassis_number", "  DUPLICATE-CHASSIS-" + suffix + "  ")
				.put("vehicle_identification_code", "OTHER-CODE-" + suffix);
		assertDuplicateRejected(operator, chassisDuplicate);

		ObjectNode registrationDuplicate = original.deepCopy()
				.put("registration_number", "  品川 300 あ " + suffix + "  ")
				.put("chassis_number", "OTHER-CHASSIS-" + suffix)
				.put("vehicle_identification_code", "OTHER-CODE-2-" + suffix);
		assertDuplicateRejected(operator, registrationDuplicate);

		ObjectNode identificationDuplicate = original.deepCopy()
				.put("registration_number", "練馬 300 あ " + suffix)
				.put("chassis_number", "OTHER-CHASSIS-2-" + suffix)
				.put("vehicle_identification_code", "  DUPLICATE-CODE-" + suffix + "  ");
		assertDuplicateRejected(operator, identificationDuplicate);
	}

	private void assertDuplicateRejected(String bearer, Object request) throws Exception {
		mvc.perform(post("/api/vehicles").header("Authorization", bearer)
				.contentType("application/json").content(mapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("登録済みの車両に一致する可能性があります。"));
	}
}
