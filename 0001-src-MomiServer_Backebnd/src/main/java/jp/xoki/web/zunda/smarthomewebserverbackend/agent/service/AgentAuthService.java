package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentAuthProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.AgentStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.ChallengeResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.CodeResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.MqttSettings;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.RefreshRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.RegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.StatusRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.TokenResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentAuthRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentRuntimeConstants.HEARTBEAT_OFFLINE_MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.DISABLED;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.PENDING;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.REGISTERED;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_AGENT_ID;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_GENERATION;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_HEARTBEAT_AT;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_STATUS;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMessageConstants;

/** 登録・再登録と資格情報更新を行ロック下で処理する。 */
@Service
@RequiredArgsConstructor
public class AgentAuthService {
    private final AgentAuthRepository repository;
    private final AgentAuthProperties properties;
    private final AgentSecrets secrets;
    private final AgentMqttProvisioning mqtt;

    private final Clock clock;

    @Transactional
    public CodeResponse issueCode(long deviceId, String actor) {
        secrets.requireConfigured();
        if (!repository.lockDevice(deviceId)) {
            throw new AgentAuthException(HttpStatus.CONFLICT.value(), "DEVICE_DISABLED", AgentMessageConstants.DEVICE_DISABLED);
        }
        Record current = repository.byDevice(deviceId);
        if (current == null) {
            repository.create(deviceId, UUID.randomUUID());
            current = repository.byDevice(deviceId);
        }
        UUID agentId = current.get("id", UUID.class);
        current = repository.lockAgent(agentId);
        if (!PENDING.getStoredName().equals(current.get(FIELD_STATUS))) {
            throw new AgentAuthException(HttpStatus.CONFLICT.value(), "AGENT_REREGISTRATION_REQUIRED",
                    AgentMessageConstants.REREGISTRATION_REQUIRED);
        }
        String code = AgentSecrets.randomToken("");
        OffsetDateTime expiry = OffsetDateTime.now(clock).plusMinutes(properties.getRegistrationMinutes());
        repository.issueCode(agentId, AgentSecrets.hash(code), expiry);
        repository.audit(agentId, current.get(FIELD_GENERATION, Long.class), "registration_code_issued", actor, null);
        return new CodeResponse(agentId, code, expiry);
    }

    public AgentStatus status(long deviceId) {
        Record row = repository.byDevice(deviceId);
        if (row == null) {
            return new AgentStatus(null, deviceId, 0, "unregistered", null, null, null, null,
                    "not_configured", null, "unknown", false);
        }
        OffsetDateTime heartbeatAt = time(row, FIELD_HEARTBEAT_AT);
        String heartbeatStatus = "unknown";
        if (heartbeatAt != null) {
            heartbeatStatus = heartbeatAt.isAfter(OffsetDateTime.now(clock).minusMinutes(HEARTBEAT_OFFLINE_MINUTES))
                    ? "online" : "offline";
        }
        return new AgentStatus(row.get("id", UUID.class), deviceId, row.get(FIELD_GENERATION, Long.class),
                row.get(FIELD_STATUS, String.class), row.get("mqtt_client_id", String.class),
                PENDING.getStoredName().equals(row.get(FIELD_STATUS)) ? time(row, "code_expires_at") : null,
                time(row, "registered_at"), time(row, "last_seen_at"),
                mqtt.status(row.get("id", UUID.class), row.get(FIELD_GENERATION, Long.class)),
                heartbeatAt, heartbeatStatus,
                Boolean.TRUE.equals(row.get("mqtt_connected", Boolean.class)));
    }

    @Transactional
    public AgentStatus changeStatus(long deviceId, StatusRequest request, String actor) {
        repository.lockDevice(deviceId);
        Record row = repository.byDevice(deviceId);
        if (row == null) {
            throw new AgentAuthException(HttpStatus.NOT_FOUND.value(), "AGENT_NOT_FOUND", AgentMessageConstants.NOT_FOUND);
        }
        UUID id = row.get("id", UUID.class);
        row = repository.lockAgent(id);
        long generation = row.get(FIELD_GENERATION, Long.class);
        if (PENDING.getStoredName().equals(request.status())) {
            generation++;
        }
        repository.changeStatus(id, generation, request.status());
        repository.audit(id, generation, PENDING.getStoredName().equals(request.status())
                ? "reregistration_started" : DISABLED.getStoredName(),
                actor, request.reason());
        return status(deviceId);
    }

    public ChallengeResponse challenge() {
        secrets.requireConfigured();
        String nonce = AgentSecrets.randomToken("");
        OffsetDateTime expiry = OffsetDateTime.now(clock).plusMinutes(2);
        repository.challenge(nonce, expiry);
        return new ChallengeResponse(nonce, expiry);
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        secrets.requireConfigured();
        String codeHash = AgentSecrets.hash(request.registrationCode());
        Record row = repository.byCode(codeHash);
        if (row == null) {
            throw AgentAuthException.unauthorized();
        }
        UUID id = row.get("id", UUID.class);
        row = repository.lockAgent(id);
        if (!AgentSecrets.hashEquals(codeHash, row.get("code_hash", String.class)) || !repository.deviceActive(row)) {
            throw AgentAuthException.unauthorized();
        }
        consumeProof(request.publicKey(), request.proof(), "register", request.registrationRequestId(),
                request.registrationCode());
        if (REGISTERED.getStoredName().equals(row.get(FIELD_STATUS))) {
            if (!request.registrationRequestId().equals(row.get("registration_request_id", UUID.class))
                    || !request.publicKey().equals(row.get("public_key", String.class))) {
                throw AgentAuthException.unauthorized();
            }
            if (!future(row, "recovery_expires_at") || row.get("registration_response") == null) {
                throw new AgentAuthException(HttpStatus.CONFLICT.value(), "AGENT_RECOVERY_UNAVAILABLE",
                        AgentMessageConstants.RECOVERY_UNAVAILABLE);
            }
            TokenResponse cached = secrets.open(row.get("registration_response", String.class), "register:" + id);
            // Expired access tokens can be renewed with the recovered refresh token.
            repository.audit(id, row.get(FIELD_GENERATION, Long.class), "registration_recovered", id.toString(), null);
            return cached;
        }
        if (!PENDING.getStoredName().equals(row.get(FIELD_STATUS)) || !future(row, "code_expires_at")) {
            throw AgentAuthException.unauthorized();
        }
        TokenResponse tokens = tokens(row);
        repository.createSession(UUID.randomUUID(), tokens);
        repository.registered(id, request, secrets.seal(tokens, "register:" + id),
                OffsetDateTime.now(clock).plusHours(properties.getRecoveryHours()));
        repository.audit(id, tokens.generation(), REGISTERED.getStoredName(), id.toString(), null);
        return tokens;
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        secrets.requireConfigured();
        String hash = AgentSecrets.hash(request.refreshToken());
        Record session = repository.sessionByRefresh(hash);
        if (session == null) {
            throw AgentAuthException.unauthorized();
        }
        UUID id = session.get(FIELD_AGENT_ID, UUID.class);
        Record agent = repository.lockAgent(id);
        session = repository.lockSession(session.get("id", UUID.class));
        if (!REGISTERED.getStoredName().equals(agent.get(FIELD_STATUS)) || !repository.deviceActive(agent)
                || session.get("revoked_at") != null || !future(session, "refresh_expires_at")
                || !agent.get(FIELD_GENERATION, Long.class).equals(session.get(FIELD_GENERATION, Long.class))) {
            throw AgentAuthException.unauthorized();
        }
        consumeProof(agent.get("public_key", String.class), request.proof(), "refresh", request.refreshRequestId(),
                request.refreshToken());
        UUID sessionId = session.get("id", UUID.class);
        if (AgentSecrets.hashEquals(hash, session.get("previous_refresh_hash", String.class))) {
            if (!request.refreshRequestId().equals(session.get("refresh_request_id", UUID.class))
                    || !future(session, "recovery_expires_at") || session.get("refresh_response") == null) {
                throw AgentAuthException.unauthorized();
            }
            return secrets.open(session.get("refresh_response", String.class), "refresh:" + sessionId);
        }
        if (!AgentSecrets.hashEquals(hash, session.get("refresh_hash", String.class))) {
            throw AgentAuthException.unauthorized();
        }
        TokenResponse tokens = tokens(agent);
        repository.rotate(sessionId, request.refreshRequestId(), tokens,
                secrets.seal(tokens, "refresh:" + sessionId),
                OffsetDateTime.now(clock).plusMinutes(properties.getRefreshRecoveryMinutes()));
        repository.audit(id, tokens.generation(), "credentials_refreshed", id.toString(), null);
        return tokens;
    }

    private void consumeProof(String publicKey, String proof, String purpose, UUID requestId, String credential) {
        String nonce = secrets.verifyProof(publicKey, proof, purpose, requestId, credential);
        if (!repository.consumeChallenge(nonce)) {
            throw AgentAuthException.unauthorized();
        }
    }

    private TokenResponse tokens(Record agent) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new TokenResponse(agent.get("id", UUID.class), agent.get("device_id", Long.class),
                agent.get(FIELD_GENERATION, Long.class), "Bearer", AgentSecrets.randomToken("ma_"),
                now.plusMinutes(properties.getAccessMinutes()), AgentSecrets.randomToken("mr_"),
                now.plusDays(properties.getRefreshDays()),
                new MqttSettings(false, "not_configured", agent.get("mqtt_client_id", String.class)));
    }

    private static OffsetDateTime time(Record row, String field) {
        return row.get(field, OffsetDateTime.class);
    }

    private boolean future(Record row, String field) {
        OffsetDateTime value = time(row, field);
        return value != null && value.isAfter(OffsetDateTime.now(clock));
    }

    /** 認証関連の期限切れデータの整理。
     *
     * Schedulerから呼び出し、チャレンジ・レート制限・再送用応答の保持期間を適用する。
     */
    public void cleanup() {
        repository.cleanup();
    }
}
