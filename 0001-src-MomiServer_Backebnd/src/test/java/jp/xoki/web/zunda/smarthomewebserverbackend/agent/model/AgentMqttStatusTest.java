package jp.xoki.web.zunda.smarthomewebserverbackend.agent.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AgentMqttStatusTest {

	@Test
	void existingStorageAndPublicStatusCodesArePreserved() {
		assertThat(Arrays.stream(AgentMqttStatus.values())
				.collect(Collectors.toMap(Enum::name, AgentMqttStatus::getStoredName)))
				.containsOnly(entry("PENDING", "pending"), entry("READY", "ready"), entry("REVOKED", "revoked"),
						entry("REVOCATION_PENDING", "revocation_pending"), entry("NOT_CONFIGURED", "not_configured"));
	}
}
