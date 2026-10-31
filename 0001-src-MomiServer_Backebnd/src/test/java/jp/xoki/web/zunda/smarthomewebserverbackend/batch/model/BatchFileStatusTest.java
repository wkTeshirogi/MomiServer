package jp.xoki.web.zunda.smarthomewebserverbackend.batch.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchFileStatusTest {

	@Test
	void fileStatusCodesStayIndependentFromDefinitionLifecycle() {
		assertThat(BatchFileStatus.values()).extracting(BatchFileStatus::getStoredName)
				.containsExactly("active", "deleted");
	}
}
