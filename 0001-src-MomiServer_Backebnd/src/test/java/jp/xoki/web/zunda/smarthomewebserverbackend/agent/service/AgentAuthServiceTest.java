package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentAuthProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.RefreshRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.RegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentAuthRepository;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

/** 検索後のロック取得で資格情報が変わった場合に認証を許可しない。 */
@ExtendWith(MockitoExtension.class)
class AgentAuthServiceTest {
    @Mock private AgentAuthRepository repository;
    @Mock private AgentSecrets secrets;
    @Mock private AgentMqttProvisioning mqtt;
    private AgentAuthService service;

    @BeforeEach
    void setUp() {
        service = new AgentAuthService(repository, new AgentAuthProperties(), secrets, mqtt, ApplicationClockFactory.systemClock());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"stale-code-hash", "malformed"})
    void registrationRejectsChangedOrMissingLockedHashBeforeConsumingProof(String storedHash) {
        UUID agentId = UUID.randomUUID();
        Record found = mock(Record.class);
        Record locked = mock(Record.class);
        when(repository.byCode(AgentSecrets.hash("code"))).thenReturn(found);
        when(found.get("id", UUID.class)).thenReturn(agentId);
        when(repository.lockAgent(agentId)).thenReturn(locked);
        when(locked.get("code_hash", String.class)).thenReturn(storedHash);
        RegisterRequest request = new RegisterRequest("code", UUID.randomUUID(), "key", "proof", "host", "linux", "test");

        AgentAuthException failure = assertThrows(AgentAuthException.class, () -> service.register(request));
        assertThat(failure.status()).isEqualTo(401);
        assertThat(failure.code()).isEqualTo("AGENT_AUTHENTICATION_FAILED");
        verify(repository, never()).consumeChallenge(anyString());
        verify(repository, never()).createSession(any(), any());
    }

    @Test
    void refreshRejectsRotatedLockedHashWithoutRotatingAgain() {
        UUID agentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Record found = mock(Record.class);
        Record agent = mock(Record.class);
        Record session = mock(Record.class);
        when(repository.sessionByRefresh(AgentSecrets.hash("token"))).thenReturn(found);
        when(found.get("agent_id", UUID.class)).thenReturn(agentId);
        when(found.get("id", UUID.class)).thenReturn(sessionId);
        when(repository.lockAgent(agentId)).thenReturn(agent);
        when(repository.lockSession(sessionId)).thenReturn(session);
        when(agent.get("status")).thenReturn("registered");
        when(repository.deviceActive(agent)).thenReturn(true);
        when(session.get("revoked_at")).thenReturn(null);
        when(session.get("refresh_expires_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now(ApplicationClockFactory.systemClock()).plusDays(1));
        when(agent.get("generation", Long.class)).thenReturn(1L);
        when(session.get("generation", Long.class)).thenReturn(1L);
        when(agent.get("public_key", String.class)).thenReturn("key");
        when(secrets.verifyProof("key", "proof", "refresh", requestId, "token")).thenReturn("nonce");
        when(repository.consumeChallenge("nonce")).thenReturn(true);
        when(session.get("id", UUID.class)).thenReturn(sessionId);
        when(session.get("previous_refresh_hash", String.class)).thenReturn(null);
        when(session.get("refresh_hash", String.class)).thenReturn(AgentSecrets.hash("replacement-token"));
        RefreshRequest request = new RefreshRequest("token", requestId, "proof");

        AgentAuthException failure = assertThrows(AgentAuthException.class,
                () -> service.refresh(request));
        assertThat(failure.status()).isEqualTo(401);
        verify(repository, never()).rotate(any(), any(), any(), any(), any());
    }
}
