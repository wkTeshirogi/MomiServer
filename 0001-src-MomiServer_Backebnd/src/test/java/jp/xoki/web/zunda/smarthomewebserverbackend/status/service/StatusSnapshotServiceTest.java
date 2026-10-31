package jp.xoki.web.zunda.smarthomewebserverbackend.status.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApplicationTimeConstants;

import static org.assertj.core.api.Assertions.assertThat;

class StatusSnapshotServiceTest {

	@Test
	void machineStatusUsesMasterStateAndHeartbeatFreshness() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ApplicationTimeConstants.ZONE);
		OffsetDateTime now = OffsetDateTime.now(clock);
		assertThat(StatusSnapshotService.machineStatus("1", null, clock)).isEqualTo("unknown");
		assertThat(StatusSnapshotService.machineStatus("1", now, clock)).isEqualTo("online");
		assertThat(StatusSnapshotService.machineStatus("1", now.minusMinutes(4), clock))
				.isEqualTo("offline");
		assertThat(StatusSnapshotService.machineStatus("90", now, clock)).isEqualTo("maintenance");
		assertThat(StatusSnapshotService.machineStatus("99", now, clock)).isEqualTo("offline");
	}

	@Test
	void serviceStatusPrioritizesServiceAndMachineState() {
		assertThat(StatusSnapshotService.serviceStatus("stop", "online")).isEqualTo("maintenance");
		assertThat(StatusSnapshotService.serviceStatus("watch", "offline")).isEqualTo("offline");
		assertThat(StatusSnapshotService.serviceStatus("watch", "online")).isEqualTo("unknown");
		assertThat(StatusSnapshotService.serviceStatus("online", "offline")).isEqualTo("offline");
		assertThat(StatusSnapshotService.serviceStatus("watch", "online", "online")).isEqualTo("online");
		assertThat(StatusSnapshotService.serviceStatus("watch", "online", "offline")).isEqualTo("offline");
	}

	@Test
	void temperaturePreservesUnavailableAndZeroValues() {
		assertThat(StatusSnapshotService.temperature(null, true)).isNull();
		assertThat(StatusSnapshotService.temperature(0, true)).isZero();
		assertThat(StatusSnapshotService.temperature(52.6, true)).isEqualTo(53);
		assertThat(StatusSnapshotService.temperature(52.6, false)).isNull();
	}
}
