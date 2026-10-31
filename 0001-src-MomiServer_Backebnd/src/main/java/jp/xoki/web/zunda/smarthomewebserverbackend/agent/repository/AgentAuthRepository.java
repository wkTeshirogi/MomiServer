package jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository;

import static java.util.concurrent.TimeUnit.MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.REGISTERED;
import static jp.xoki.web.zunda.jooq.Tables.M_AGENTS;
import static jp.xoki.web.zunda.jooq.Tables.M_DEVICES;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_AUTH_AUDIT_EVENTS;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_AUTH_CHALLENGES;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_AUTH_RATE_LIMITS;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_SESSIONS;
import static jp.xoki.web.zunda.jooq.Tables.T_BATCH_EXECUTION_REQUESTS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthRepositoryConstants.RATE_RETENTION_MINUTES;
import static org.jooq.impl.DSL.currentOffsetDateTime;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMessageConstants;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.RegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.TokenResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthException;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentSecrets;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

/** Agent資格情報の保存。平文コード・トークンは照合用テーブルへ保存しない。 */
@Repository
@RequiredArgsConstructor
public class AgentAuthRepository {
    private final DSLContext dsl;
    private final Clock clock;

    public boolean lockDevice(long deviceId) {
        Record row = dsl.select(M_DEVICES.STATUS).from(M_DEVICES)
                .where(M_DEVICES.ID.eq(deviceId)).forUpdate().fetchOne();
        if (row == null) {
            throw new AgentAuthException(HttpStatus.NOT_FOUND.value(), "DEVICE_NOT_FOUND", AgentMessageConstants.DEVICE_NOT_FOUND);
        }
        return !"0".equals(row.get(M_DEVICES.STATUS));
    }

    public Record byDevice(long deviceId) {
        return dsl.selectFrom(M_AGENTS).where(M_AGENTS.DEVICE_ID.eq(deviceId)).fetchOne();
    }

    public void create(long deviceId, UUID agentId) {
        dsl.insertInto(M_AGENTS).set(M_AGENTS.ID, agentId).set(M_AGENTS.DEVICE_ID, deviceId)
                .set(M_AGENTS.STATUS, "pending").set(M_AGENTS.MQTT_CLIENT_ID, "ma-" + agentId + "-1").execute();
    }

    public Record lockAgent(UUID agentId) {
        return dsl.selectFrom(M_AGENTS).where(M_AGENTS.ID.eq(agentId)).forUpdate().fetchOne();
    }

    public boolean deviceActive(Record agent) {
        return dsl.selectOne().from(M_DEVICES).where(M_DEVICES.ID.eq(agent.get("device_id", Long.class)))
                .and(M_DEVICES.STATUS.ne("0")).fetchOne() != null;
    }

    public Record byCode(String hash) {
        return dsl.selectFrom(M_AGENTS).where(M_AGENTS.CODE_HASH.eq(hash)).fetchOne();
    }

    public void issueCode(UUID agentId, String hash, OffsetDateTime expiresAt) {
        dsl.update(M_AGENTS).set(M_AGENTS.CODE_HASH, hash).set(M_AGENTS.CODE_EXPIRES_AT, expiresAt)
                .set(M_AGENTS.UPDATED_AT, currentOffsetDateTime()).where(M_AGENTS.ID.eq(agentId)).execute();
    }

    public void registered(UUID agentId, RegisterRequest request, String response, OffsetDateTime recoveryExpiresAt) {
        dsl.update(M_AGENTS).set(M_AGENTS.STATUS, REGISTERED.getStoredName())
                .set(M_AGENTS.REGISTRATION_REQUEST_ID, request.registrationRequestId())
                .set(M_AGENTS.PUBLIC_KEY, request.publicKey()).set(M_AGENTS.HOSTNAME, request.hostname())
                .set(M_AGENTS.OS, request.os()).set(M_AGENTS.AGENT_VERSION, request.agentVersion())
                .set(M_AGENTS.REGISTRATION_RESPONSE, response).set(M_AGENTS.RECOVERY_EXPIRES_AT, recoveryExpiresAt)
                .set(M_AGENTS.REGISTERED_AT, currentOffsetDateTime()).set(M_AGENTS.UPDATED_AT, currentOffsetDateTime())
                .where(M_AGENTS.ID.eq(agentId)).execute();
    }

    public void changeStatus(UUID agentId, long generation, String status) {
        dsl.update(M_AGENTS).set(M_AGENTS.STATUS, status).set(M_AGENTS.GENERATION, generation)
                .set(M_AGENTS.MQTT_CLIENT_ID, "ma-" + agentId + "-" + generation)
                .setNull(M_AGENTS.CODE_HASH).setNull(M_AGENTS.CODE_EXPIRES_AT).setNull(M_AGENTS.PUBLIC_KEY)
                .setNull(M_AGENTS.REGISTRATION_REQUEST_ID).setNull(M_AGENTS.REGISTRATION_RESPONSE)
                .setNull(M_AGENTS.RECOVERY_EXPIRES_AT).setNull(M_AGENTS.REGISTERED_AT)
                .setNull(M_AGENTS.LAST_SEEN_AT).setNull(M_AGENTS.HEARTBEAT_AT)
                .set(M_AGENTS.MQTT_CONNECTED, false).set(M_AGENTS.UPDATED_AT, currentOffsetDateTime())
                .where(M_AGENTS.ID.eq(agentId)).execute();
        dsl.update(T_AGENT_SESSIONS).set(T_AGENT_SESSIONS.REVOKED_AT, currentOffsetDateTime())
                .setNull(T_AGENT_SESSIONS.REFRESH_RESPONSE).setNull(T_AGENT_SESSIONS.PREVIOUS_REFRESH_HASH)
                .where(T_AGENT_SESSIONS.AGENT_ID.eq(agentId)).and(T_AGENT_SESSIONS.REVOKED_AT.isNull()).execute();
    }

    public void createSession(UUID sessionId, TokenResponse response) {
        dsl.insertInto(T_AGENT_SESSIONS).set(T_AGENT_SESSIONS.ID, sessionId)
                .set(T_AGENT_SESSIONS.AGENT_ID, response.agentId()).set(T_AGENT_SESSIONS.GENERATION, response.generation())
                .set(T_AGENT_SESSIONS.ACCESS_HASH, AgentSecrets.hash(response.accessToken()))
                .set(T_AGENT_SESSIONS.REFRESH_HASH, AgentSecrets.hash(response.refreshToken()))
                .set(T_AGENT_SESSIONS.ACCESS_EXPIRES_AT, response.accessExpiresAt())
                .set(T_AGENT_SESSIONS.REFRESH_EXPIRES_AT, response.refreshExpiresAt()).execute();
    }

    public Record sessionByRefresh(String hash) {
        return dsl.selectFrom(T_AGENT_SESSIONS).where(T_AGENT_SESSIONS.REVOKED_AT.isNull())
                .and(T_AGENT_SESSIONS.REFRESH_HASH.eq(hash).or(T_AGENT_SESSIONS.PREVIOUS_REFRESH_HASH.eq(hash))).fetchOne();
    }

    public Record lockSession(UUID id) {
        return dsl.selectFrom(T_AGENT_SESSIONS).where(T_AGENT_SESSIONS.ID.eq(id)).forUpdate().fetchOne();
    }

    public void rotate(UUID id, UUID requestId, TokenResponse tokens, String response, OffsetDateTime recoveryUntil) {
        dsl.update(T_AGENT_SESSIONS)
                .set(T_AGENT_SESSIONS.PREVIOUS_REFRESH_HASH, T_AGENT_SESSIONS.REFRESH_HASH)
                .set(T_AGENT_SESSIONS.REFRESH_REQUEST_ID, requestId)
                .set(T_AGENT_SESSIONS.ACCESS_HASH, AgentSecrets.hash(tokens.accessToken()))
                .set(T_AGENT_SESSIONS.REFRESH_HASH, AgentSecrets.hash(tokens.refreshToken()))
                .set(T_AGENT_SESSIONS.ACCESS_EXPIRES_AT, tokens.accessExpiresAt())
                .set(T_AGENT_SESSIONS.REFRESH_EXPIRES_AT, tokens.refreshExpiresAt())
                .set(T_AGENT_SESSIONS.REFRESH_RESPONSE, response).set(T_AGENT_SESSIONS.RECOVERY_EXPIRES_AT, recoveryUntil)
                .where(T_AGENT_SESSIONS.ID.eq(id)).execute();
        dsl.update(M_AGENTS).setNull(M_AGENTS.REGISTRATION_RESPONSE).setNull(M_AGENTS.RECOVERY_EXPIRES_AT)
                .where(M_AGENTS.ID.eq(tokens.agentId())).execute();
    }

    public AgentPrincipal authenticate(String accessHash) {
        return dsl.select(M_AGENTS.ID, M_AGENTS.DEVICE_ID, M_AGENTS.GENERATION).from(M_AGENTS)
                .innerJoin(T_AGENT_SESSIONS).on(T_AGENT_SESSIONS.AGENT_ID.eq(M_AGENTS.ID))
                .and(T_AGENT_SESSIONS.GENERATION.eq(M_AGENTS.GENERATION))
                .innerJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_AGENTS.DEVICE_ID))
                .where(T_AGENT_SESSIONS.ACCESS_HASH.eq(accessHash)).and(T_AGENT_SESSIONS.REVOKED_AT.isNull())
                .and(T_AGENT_SESSIONS.ACCESS_EXPIRES_AT.gt(currentOffsetDateTime()))
                .and(M_AGENTS.STATUS.eq(REGISTERED.getStoredName())).and(M_DEVICES.STATUS.ne("0"))
                .fetchOne(row -> new AgentPrincipal(row.get(M_AGENTS.ID), row.get(M_AGENTS.DEVICE_ID), row.get(M_AGENTS.GENERATION)));
    }

    /**
     * 同一トランザクション中は再登録・無効化と排他する。
     * @param principal Agent認証主体
     * @param executionId 実行要求ID
     * @return 端末と登録世代が一致し、有効な場合true
     */
    public boolean lockExecutionOwner(AgentPrincipal principal, UUID executionId) {
        Record device = dsl.select(M_DEVICES.ID).from(M_DEVICES).where(M_DEVICES.ID.eq(principal.deviceId()))
                .and(M_DEVICES.STATUS.ne("0")).forShare().fetchOne();
        if (device == null) {
            return false;
        }
        Record agent = dsl.select(M_AGENTS.ID).from(M_AGENTS).where(M_AGENTS.ID.eq(principal.agentId()))
                .and(M_AGENTS.GENERATION.eq(principal.generation())).and(M_AGENTS.STATUS.eq(REGISTERED.getStoredName()))
                .forShare().fetchOne();
        if (agent == null) {
            return false;
        }
        return dsl.select(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID).from(T_BATCH_EXECUTION_REQUESTS)
                .where(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID.eq(executionId))
                .and(T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_ID.eq(principal.deviceId()))
                .and(T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_GENERATION.eq(principal.generation())).fetchOne() != null;
    }

    public void seen(UUID agentId) {
        dsl.update(M_AGENTS).set(M_AGENTS.LAST_SEEN_AT, currentOffsetDateTime())
                .where(M_AGENTS.ID.eq(agentId)).and(M_AGENTS.STATUS.eq(REGISTERED.getStoredName())).execute();
    }

    public void challenge(String nonce, OffsetDateTime expiresAt) {
        dsl.insertInto(T_AGENT_AUTH_CHALLENGES).set(T_AGENT_AUTH_CHALLENGES.NONCE_HASH, AgentSecrets.hash(nonce))
                .set(T_AGENT_AUTH_CHALLENGES.EXPIRES_AT, expiresAt).execute();
    }

    public boolean consumeChallenge(String nonce) {
        return dsl.deleteFrom(T_AGENT_AUTH_CHALLENGES).where(T_AGENT_AUTH_CHALLENGES.NONCE_HASH.eq(AgentSecrets.hash(nonce)))
                .and(T_AGENT_AUTH_CHALLENGES.EXPIRES_AT.gt(currentOffsetDateTime())).execute() == 1;
    }

    public int countRequest(String key, long minute) {

        // 読み出しと加算を分離せず、同時要求も一つのUPSERTで計数する。
        Integer attempts = dsl.insertInto(T_AGENT_AUTH_RATE_LIMITS)
                .set(T_AGENT_AUTH_RATE_LIMITS.BUCKET_KEY, key).set(T_AGENT_AUTH_RATE_LIMITS.WINDOW_MINUTE, minute)
                .set(T_AGENT_AUTH_RATE_LIMITS.ATTEMPTS, 1)
                .onConflict(T_AGENT_AUTH_RATE_LIMITS.BUCKET_KEY, T_AGENT_AUTH_RATE_LIMITS.WINDOW_MINUTE).doUpdate()
                .set(T_AGENT_AUTH_RATE_LIMITS.ATTEMPTS, T_AGENT_AUTH_RATE_LIMITS.ATTEMPTS.add(1))
                .returningResult(T_AGENT_AUTH_RATE_LIMITS.ATTEMPTS).fetchSingle(T_AGENT_AUTH_RATE_LIMITS.ATTEMPTS);
        return Objects.requireNonNull(attempts, "Rate limit update did not return attempts");
    }

    public void audit(UUID agentId, long generation, String action, String actor, String reason) {
        dsl.insertInto(T_AGENT_AUTH_AUDIT_EVENTS).set(T_AGENT_AUTH_AUDIT_EVENTS.AGENT_ID, agentId)
                .set(T_AGENT_AUTH_AUDIT_EVENTS.GENERATION, generation).set(T_AGENT_AUTH_AUDIT_EVENTS.ACTION, action)
                .set(T_AGENT_AUTH_AUDIT_EVENTS.ACTOR, actor).set(T_AGENT_AUTH_AUDIT_EVENTS.REASON, reason).execute();
    }

    public void cleanup() {
        dsl.deleteFrom(T_AGENT_AUTH_CHALLENGES).where(T_AGENT_AUTH_CHALLENGES.EXPIRES_AT.lt(currentOffsetDateTime())).execute();
        dsl.deleteFrom(T_AGENT_AUTH_RATE_LIMITS).where(T_AGENT_AUTH_RATE_LIMITS.WINDOW_MINUTE.lt(
                clock.millis() / MINUTES.toMillis(1) - RATE_RETENTION_MINUTES)).execute();
        dsl.update(M_AGENTS).setNull(M_AGENTS.REGISTRATION_RESPONSE).setNull(M_AGENTS.RECOVERY_EXPIRES_AT)
                .where(M_AGENTS.RECOVERY_EXPIRES_AT.lt(currentOffsetDateTime())).execute();
        dsl.update(T_AGENT_SESSIONS).setNull(T_AGENT_SESSIONS.REFRESH_RESPONSE)
                .setNull(T_AGENT_SESSIONS.PREVIOUS_REFRESH_HASH).setNull(T_AGENT_SESSIONS.RECOVERY_EXPIRES_AT)
                .where(T_AGENT_SESSIONS.RECOVERY_EXPIRES_AT.lt(currentOffsetDateTime())).execute();
    }
}
