package jp.xoki.web.zunda.smarthomewebserverbackend.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRegistrationStatusTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@ParameterizedTest
	@CsvSource({"PENDING,pending", "REGISTERED,registered", "DISABLED,disabled"})
	void storedCodeAndJsonRemainCompatible(AgentRegistrationStatus status, String storedName) throws Exception {
		assertThat(status.getStoredName()).isEqualTo(storedName);
		assertThat(AgentRegistrationStatus.fromStoredName(storedName)).isEqualTo(status);
		String json = mapper.writeValueAsString(status);
		assertThat(json).isEqualTo("\"" + storedName + "\"");
		assertThat(mapper.readValue(json, AgentRegistrationStatus.class)).isEqualTo(status);
	}

	@Test
	void missingStatusRemainsUnset() {
		assertThat(AgentRegistrationStatus.fromStoredName(null)).isNull();
	}

	@Test
	void unknownStatusIsRejected() {
		assertThatThrownBy(() -> AgentRegistrationStatus.fromStoredName("unregistered"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
