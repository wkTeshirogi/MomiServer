package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.ServiceSnapshot;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.SnapshotRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.StorageSnapshot;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class AgentStatusServiceTest {

	private final AgentStatusRepository repository = mock(AgentStatusRepository.class);
	private final AgentStatusService service = new AgentStatusService(repository, ApplicationClockFactory.systemClock());
	private final AgentPrincipal principal = new AgentPrincipal(UUID.randomUUID(), 10L, 2L);

	@Test
	void savesSnapshotOnlyForServicesAssignedToAuthenticatedDevice() {
		SnapshotRequest request = request(List.of(new ServiceSnapshot(20L, "online", 123L, 60L, 5, 2)));
		when(repository.lockRegisteredAgent(principal)).thenReturn(true);
		when(repository.findTargetIds(10L)).thenReturn(Set.of(20L));

		service.save(principal, request);

		verify(repository).save(principal, request);
	}

	@Test
	void rejectsServiceAssignedToAnotherDevice() {
		when(repository.lockRegisteredAgent(principal)).thenReturn(true);
		when(repository.findTargetIds(10L)).thenReturn(Set.of());

		SnapshotRequest exceptionInput1 = request(List.of(new ServiceSnapshot(99L, "online", null, 0, 0, 0)));
		assertThatThrownBy(() -> service.save(principal,
				exceptionInput1))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("403 FORBIDDEN");
	}

	private SnapshotRequest request(List<ServiceSnapshot> services) {
		return new SnapshotRequest(OffsetDateTime.now(ApplicationClockFactory.systemClock()), 10, 20, null, null, 100,
				List.of(new StorageSnapshot("C:", "C:", 1000, 500)), services);
	}
}
