package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentMqttProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentMqttProvisioningRepository;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentMqttProvisioningTest {
    private final AgentMqttProvisioningRepository repository = mock(AgentMqttProvisioningRepository.class);
    private final AgentSecrets secrets = mock(AgentSecrets.class);
    private final AgentMqttProperties properties = new AgentMqttProperties();
    private final MosquittoControl control = mock(MosquittoControl.class);
    private final AgentMqttProvisioning provisioning = new AgentMqttProvisioning(repository, secrets, properties,
            control, mock(PlatformTransactionManager.class));

    @Test
    void connectionPreservesCredentialContextIdentityAndTopics() {
        properties.setEnabled(true);
        properties.setAgentBrokerUrl("tcp://127.0.0.1:18884");
        properties.setAllowInsecureLocalhost(true);
        AgentPrincipal principal = new AgentPrincipal(UUID.randomUUID(), 1L, 2L);
        Record row = mock(Record.class);
        when(repository.connection(principal)).thenReturn(row);
        when(row.get("client_id", String.class)).thenReturn("agent-generation-2");
        when(row.get("encrypted_password", String.class)).thenReturn("encrypted-test-value");
        when(secrets.open("encrypted-test-value", "mqtt:agent-generation-2", String.class))
                .thenReturn("test-password");

        AgentMqttProvisioning.Connection connection = provisioning.connection(principal);

        assertThat(connection.enabled()).isTrue();
        assertThat(connection.status()).isEqualTo("ready");
        assertThat(connection.clientId()).isEqualTo("agent-generation-2");
        assertThat(connection.username()).isEqualTo("agent-generation-2");
        assertThat(connection.password()).isEqualTo("test-password");
        assertThat(connection.commandTopic()).isEqualTo("smarthome/agents/agent-generation-2/batch/command");
        assertThat(connection.cancelTopic()).isEqualTo("smarthome/agents/agent-generation-2/batch/cancel");
        verify(secrets).open("encrypted-test-value", "mqtt:agent-generation-2", String.class);
        verifyNoInteractions(control);
    }

    @Test
    void disabledMqttDoesNotResolveCredentialsOrContactBroker() {
        properties.setEnabled(false);

        assertThat(provisioning.status(UUID.randomUUID(), 1L)).isEqualTo("not_configured");

        verifyNoInteractions(repository, secrets, control);
    }

    @ParameterizedTest
    @ValueSource(strings = {"device", "agent", "credentials"})
    void staleReconciliationKeysNeverGrantBrokerAccess(String missing) {
        UUID agentId = UUID.randomUUID();
        Record key = mock(Record.class);
        when(key.get("device_id", Long.class)).thenReturn(1L);
        when(key.get("agent_id", UUID.class)).thenReturn(agentId);
        when(key.get("generation", Long.class)).thenReturn(2L);
        Record present = mock(Record.class);
        when(repository.lockDevice(1L))
                .thenReturn("device".equals(missing) ? null : present);
        when(repository.lockAgent(agentId))
                .thenReturn("agent".equals(missing) ? null : present);
        when(repository.lockCredentials(agentId, 2L))
                .thenReturn("credentials".equals(missing) ? null : present);

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(provisioning, "synchronize", key);

        verifyNoInteractions(control, secrets);
    }
}
