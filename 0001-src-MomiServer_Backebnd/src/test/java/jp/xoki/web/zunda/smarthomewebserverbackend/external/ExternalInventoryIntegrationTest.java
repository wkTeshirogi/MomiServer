package jp.xoki.web.zunda.smarthomewebserverbackend.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalApiConstants;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyIssueRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalApiKeyService;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalInventoryService;
import org.junit.jupiter.api.Test;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.repository.ExternalInventoryRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.repository.ExternalApiKeyRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** 実DBでキー管理・認証境界・数量操作・重複防止を検証する。専用検証DBで実行する。 */
@SpringBootTest(properties = {"mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.vehicle-inspection-notification.enabled=false", "gateway.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ExternalInventoryIntegrationTest {
    private static final String API = "/api/v1/external/inventory/stocks";
    private static final String MANAGEMENT = "/api/external-api-keys";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ExternalApiKeyService keys;
    @Autowired ExternalInventoryService inventory;
    @Autowired ExternalInventoryRepository inventoryRepository;
    @Autowired ExternalApiKeyRepository keyRepository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;

    @Test
    void nullScopeElementsUseTheExistingValidationFieldErrorContract() throws Exception {
        mvc.perform(post(MANAGEMENT).with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"name\":\"Invalid scopes\",\"scopes\":[null,\"inventory:read\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value(org.hamcrest.Matchers.startsWith("scopes[")));
    }

    @Test
    void issueReturnsSecretOnceAndStoresOnlyHashWithAudit() throws Exception {
        String body = mvc.perform(post(MANAGEMENT).with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{\"name\":\"Warehouse\",\"scopes\":[\"inventory:read\"]}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        JsonNode issued = mapper.readTree(body);
        String secret = issued.path("secret").asText();
        UUID id = UUID.fromString(issued.path("key").path("id").asText());
        assertThat(secret).matches( ExternalApiConstants.API_KEY_PREFIX + "_[A-Za-z0-9_-]{43}");
        String hash = jdbc.queryForObject("select key_hash from t_external_api_keys where id=?", String.class, id);
        assertThat(hash).hasSize(64).doesNotContain(secret);
        assertThat(jdbc.queryForObject("select event_type from t_external_api_key_events where api_key_id=?", String.class, id))
                .isEqualTo("ISSUED");
        String list = mvc.perform(get(MANAGEMENT).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(secret, hash, "secret", "keyHash");
        mvc.perform(get(API).header("X-API-Key", secret)).andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "1000"));
        assertThat(jdbc.queryForObject("select last_used_at is not null from t_external_api_keys where id=?", Boolean.class, id))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "OPERATOR", "BAN_USER"})
    void nonAdminsCannotManageKeys(String role) throws Exception {
        mvc.perform(get(MANAGEMENT).with(user("other").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post(MANAGEMENT).with(user("other").roles(role)).contentType("application/json")
                .content("{\"name\":\"No\",\"scopes\":[\"inventory:read\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(MANAGEMENT + "/" + UUID.randomUUID() + "/revoke").with(user("other").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void credentialsCannotCrossNamespacesAndKeyIsMandatoryEvenWithGatewayDisabled() throws Exception {
        ExternalApiKeyService.IssuedKey key = issue("inventory:read");
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).with(user("admin").roles("ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(get(API).header("X-API-Key", "legacy-iot-key")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").header("X-API-Key", key.secret())).andExpect(status().isUnauthorized());
        mvc.perform(get(MANAGEMENT).header("X-API-Key", key.secret())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/agent/me").header("X-API-Key", key.secret())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/external/inventory/stocks").header("X-API-Key", key.secret()))
                .andExpect(status().isNotFound());
        jdbc.update("update m_api_versions set status='retired' where api_group='external' and version='v1'");
        mvc.perform(get(API).header("X-API-Key", key.secret())).andExpect(status().isGone());
    }

    @Test
    void revokedAndExpiredKeysAreRejectedImmediately() throws Exception {
        ExternalApiKeyService.IssuedKey key = issue("inventory:read");
        mvc.perform(get(API).header("X-API-Key", key.secret())).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(MANAGEMENT + "/" + key.key().id() + "/revoke").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        }
        mvc.perform(get(API).header("X-API-Key", key.secret())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from t_external_api_key_events where api_key_id=? and event_type='REVOKED'",
                Integer.class, key.key().id())).isEqualTo(1);
        ExternalApiKeyService.IssuedKey expired = issue("inventory:read");
        jdbc.update("update t_external_api_keys set expires_at=now()-interval '1 second' where id=?", expired.key().id());
        mvc.perform(get(API).header("X-API-Key", expired.secret())).andExpect(status().isUnauthorized());
    }

    @Test
    void scopesLimitReadAndWriteIndependently() throws Exception {
        long stock = stock(10);
        ExternalApiKeyService.IssuedKey reader = issue("inventory:read");
        ExternalApiKeyService.IssuedKey writer = issue("inventory:write");
        mvc.perform(get(API + "/" + stock).header("X-API-Key", reader.secret()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.minimumQuantity").value(2));
        mvc.perform(get(API).header("X-API-Key", writer.secret())).andExpect(status().isForbidden());
        operation(reader.secret(), stock, UUID.randomUUID(), "inbound", 1, 403);
        operation(writer.secret(), stock, UUID.randomUUID(), "inbound", 1, 200);
    }

    @Test
    void inboundOutboundStocktakeShareExistingHistoryAndNotificationFlow() throws Exception {
        jdbc.update("update t_notification_global_settings set enabled=true,browser_enabled=true,discord_enabled=false where id=1");
        Long userId = jdbc.queryForObject("insert into m_users(email,password,role) values (?,'unused','USER') returning id",
                Long.class, UUID.randomUUID() + "@example.invalid");
        jdbc.update("insert into t_notification_route_settings(notification_type,source_type,target_type,target_user_id,minimum_level,browser_enabled,discord_enabled,enabled) "
                + "values ('inventory_low','inventory','user',?,'warning',true,false,true)", userId);
        long stock = stock(10);
        ExternalApiKeyService.IssuedKey key = issue("inventory:read", "inventory:write");
        operation(key.secret(), stock, UUID.randomUUID(), "inbound", 2, 200);
        operation(key.secret(), stock, UUID.randomUUID(), "outbound", 10, 200);
        assertThat(quantity(stock)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select inventory_status from t_inventory_stocks where id=?", String.class, stock)).isEqualTo("low");
        operation(key.secret(), stock, UUID.randomUUID(), "stocktake", 0, 200);
        assertThat(quantity(stock)).isZero();
        assertThat(historyCount(stock)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from t_notifications where source_type='inventory' and source_id=(select inventory_item_id::text from t_inventory_stocks where id=?)",
                Integer.class, stock)).isEqualTo(2);
    }

    @Test
    void replayReturnsOriginalResponseAndDifferentPayloadOrStockConflicts() throws Exception {
        long stock = stock(10);
        ExternalApiKeyService.IssuedKey key = issue("inventory:write");
        UUID operation = UUID.randomUUID();
        String first = operation(key.secret(), stock, operation, "inbound", 2, 200);
        operation(key.secret(), stock, UUID.randomUUID(), "inbound", 3, 200);
        assertThat(operation(key.secret(), stock, operation, "inbound", 2, 200)).isEqualTo(first);
        operation(key.secret(), stock, operation, "inbound", 9, 409);
        operation(key.secret(), stock(10), operation, "inbound", 2, 409);
        assertThat(quantity(stock)).isEqualTo(15);
        assertThat(historyCount(stock)).isEqualTo(2);
    }

    @Test
    void invalidOperationsAndOverflowNeverUpdateStock() throws Exception {
        long stock = stock(10);
        ExternalApiKeyService.IssuedKey key = issue("inventory:write");
        operation(key.secret(), stock, UUID.randomUUID(), "delete", 0, 400);
        operation(key.secret(), stock, UUID.randomUUID(), "discard", 1, 400);
        operation(key.secret(), stock, UUID.randomUUID(), "inbound", -1, 400);
        operation(key.secret(), stock, UUID.randomUUID(), "inbound", Integer.MAX_VALUE, 400);
        assertThat(quantity(stock)).isEqualTo(10);
        assertThat(historyCount(stock)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedUpdateRollsBackReservationAndCanBeRetried() throws Exception {
        long stock = stock(1);
        ExternalApiKeyService.IssuedKey key = issue("inventory:write");
        UUID id = UUID.randomUUID();
        operation(key.secret(), stock, id, "outbound", 2, 400);
        assertThat(jdbc.queryForObject("select count(*) from t_external_inventory_operations where api_key_id=?", Integer.class, key.key().id())).isZero();
        jdbc.update("update t_inventory_stocks set quantity=5 where id=?", stock);
        operation(key.secret(), stock, id, "outbound", 2, 200);
        assertThat(quantity(stock)).isEqualTo(3);
        assertThat(historyCount(stock)).isEqualTo(1);
        keys.revoke(key.key().id(), "test");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousDuplicateRequestsAreAppliedOnce() throws Exception {
        long stock = stock(10);
        ExternalApiKeyService.IssuedKey key = issue("inventory:write");
        ExternalStockOperationRequest request = new ExternalStockOperationRequest(UUID.randomUUID(), "inbound", 2, "test");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
            CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> action = () -> {
                ready.countDown();
                start.await();
                return inventory.operate(key.key().id(), stock, request).quantity();
            };
            Future<Integer> first = executor.submit(action);
            Future<Integer> second = executor.submit(action);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(12);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(12);
        }
        assertThat(quantity(stock)).isEqualTo(12);
        assertThat(historyCount(stock)).isEqualTo(1);
        keys.revoke(key.key().id(), "test");
    }

    @Test
    void rateLimitIsStoredInDbAndResetsOnNextWindow() throws Exception {
        ExternalApiKeyService.IssuedKey key = issue("inventory:read");
        jdbc.update("update t_external_api_keys set request_window=date_trunc('hour',now()),request_count=999 where id=?", key.key().id());
        mvc.perform(get(API).header("X-API-Key", key.secret())).andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
        mvc.perform(get(API).header("X-API-Key", key.secret())).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        jdbc.update("update t_external_api_keys set request_window=now()-interval '2 hours' where id=?", key.key().id());
        mvc.perform(get(API).header("X-API-Key", key.secret())).andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "999"));
    }

    @Test
    void inactiveAndDeletedStocksAreNotPublishedOrUpdatedAndListIsBounded() throws Exception {
        long stock = stock(10);
        ExternalApiKeyService.IssuedKey key = issue("inventory:read", "inventory:write");
        mvc.perform(get(API).param("limit", "101").header("X-API-Key", key.secret())).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("offset", "-1").header("X-API-Key", key.secret())).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("limit", "1").header("X-API-Key", key.secret())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        jdbc.update("update t_inventory_stocks set inventory_status='deleted' where id=?", stock);
        mvc.perform(get(API + "/" + stock).header("X-API-Key", key.secret())).andExpect(status().isNotFound());
        operation(key.secret(), stock, UUID.randomUUID(), "stocktake", 5, 404);
        long disabled = stock(10);
        jdbc.update("update m_inventory_items set status='disabled' where id=(select inventory_item_id from t_inventory_stocks where id=?)", disabled);
        mvc.perform(get(API + "/" + disabled).header("X-API-Key", key.secret())).andExpect(status().isNotFound());
        operation(key.secret(), disabled, UUID.randomUUID(), "inbound", 1, 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":\"x\",\"scopes\":[\"admin\"]}", "{\"name\":\"x\",\"scopes\":[]}",
            "{\"name\":\" \u0020\",\"scopes\":[\"inventory:read\"]}",
            "{\"name\":\"x\",\"scopes\":[\"inventory:read\"],\"expiresAt\":\"2000-01-01T00:00:00Z\"}"})
    void invalidIssueRequestsAreRejected(String body) throws Exception {
        mvc.perform(post(MANAGEMENT).with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void stockLockIsExclusiveButMasterLockRemainsShared() throws Exception {
        long stock = stock(10);
        Long item = jdbc.queryForObject("select inventory_item_id from t_inventory_stocks where id=?", Long.class, stock);
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(inventoryRepository.lock(stock)).isPresent();
                assertThatThrownBy(() -> probeLock("select id from t_inventory_stocks where id=? for share nowait", stock))
                        .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("55P03");
                try {
                    assertThat(probeLock("select id from m_inventory_items where id=? for share nowait", item)).isTrue();
                } catch (SQLException error) {
                    throw new AssertionError("Master readers must remain concurrent", error);
                }
                assertThatThrownBy(() -> probeLock("select id from m_inventory_items where id=? for update nowait", item))
                        .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("55P03");
            });
        } finally {
            jdbc.update("delete from t_inventory_stocks where id=?", stock);
            jdbc.update("delete from m_inventory_items where id=?", item);
        }
    }

    private boolean probeLock(String sql, long id) throws SQLException {
        // Springのトランザクションとは別の物理接続から、ロック競合を待機なしで確認する。
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousRateConsumptionCannotExceedTheLimit() throws Exception {
        ExternalApiKeyService.IssuedKey key = issue("inventory:read");
        jdbc.update("update t_external_api_keys set request_window=date_trunc('hour',now()),request_count=9 where id=?", key.key().id());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Optional<Integer>> consume = () -> {
                start.await();
                return keyRepository.consume(key.key().id(), 10);
            };
            Future<Optional<Integer>> first = executor.submit(consume);
            Future<Optional<Integer>> second = executor.submit(consume);
            start.countDown();
            assertThat(java.util.stream.Stream.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
                    .flatMap(Optional::stream).toList()).containsExactly(10);
            assertThat(jdbc.queryForObject("select request_count from t_external_api_keys where id=?", Integer.class, key.key().id())).isEqualTo(10);
        } finally {
            keys.revoke(key.key().id(), "test");
        }
    }

    private ExternalApiKeyService.IssuedKey issue(String... scopes) {
        return keys.issue(new ExternalApiKeyIssueRequest("Integration test", Set.of(scopes), OffsetDateTime.now(ApplicationClockFactory.systemClock()).plusDays(1)), "admin");
    }

    private long stock(int quantity) {
        Long item = jdbc.queryForObject("insert into m_inventory_items(item_name,category,unit,min_quantity) values ('External test','test','pcs',2) returning id", Long.class);
        return jdbc.queryForObject("insert into t_inventory_stocks(inventory_item_id,storage_location,quantity) values (?,'Test',?) returning id", Long.class, item, quantity);
    }

    private int quantity(long stock) {
        return jdbc.queryForObject("select quantity from t_inventory_stocks where id=?", Integer.class, stock);
    }

    private int historyCount(long stock) {
        return jdbc.queryForObject("select count(*) from t_inventory_stock_histories where inventory_stock_id=?", Integer.class, stock);
    }

    private String operation(String secret, long stock, UUID operation, String type, int quantity, int status) throws Exception {
        return mvc.perform(post(API + "/" + stock + "/operations").header("X-API-Key", secret)
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("operationId", operation,
                        "changeType", type, "quantity", quantity))))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }
}
