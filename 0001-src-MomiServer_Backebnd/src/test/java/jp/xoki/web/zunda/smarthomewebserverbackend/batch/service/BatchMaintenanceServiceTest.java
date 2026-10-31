package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchMaintenanceServiceTest {

	@TempDir
	Path directory;

	@Test
	void restartKeepsPauseUntilExplicitResume() {
		Path marker = directory.resolve("state/maintenance.flag");
		BatchMaintenanceService service = new BatchMaintenanceService(marker.toString());
		assertThat(service.isPaused()).isFalse();
		service.setPaused(true);
		service.setPaused(true);
		BatchMaintenanceService restarted = new BatchMaintenanceService(marker.toString());
		assertThat(restarted.isPaused()).isTrue();
		restarted.setPaused(false);
		assertThat(new BatchMaintenanceService(marker.toString()).isPaused()).isFalse();
	}

	@Test
	void corruptOrUnreadableStateNeverDefaultsToActive() throws Exception {
		Path marker = directory.resolve("invalid.flag");
		Files.writeString(marker, "broken");
		String markerPath = marker.toString();
		String directoryPath = directory.toString();
		assertThatThrownBy(() -> new BatchMaintenanceService(markerPath)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new BatchMaintenanceService(directoryPath)).isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void failedPersistenceLeavesGateActive() throws Exception {
		Path parent = directory.resolve("not-a-directory");
		BatchMaintenanceService service = new BatchMaintenanceService(parent.resolve("state.flag").toString());
		Files.writeString(parent, "obstacle");
		assertThatThrownBy(() -> service.setPaused(true)).isInstanceOf(UncheckedIOException.class);
		assertThat(service.isPaused()).isFalse();
	}

	@Test
	void pauseWaitsForAdmittedWorkAndRejectsFollowingWork() throws Exception {
		BatchMaintenanceService service = new BatchMaintenanceService(directory.resolve("state.flag").toString());
		CountDownLatch admitted = new CountDownLatch(1);
		CountDownLatch finish = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			Future<Boolean> active = pool.submit(() -> service.withStartGate(() -> {
				admitted.countDown();
				await(finish);
				return service.isPaused();
			}));
			assertThat(admitted.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> pause = pool.submit(() -> service.setPaused(true));
			try {
				assertThatThrownBy(() -> pause.get(100, TimeUnit.MILLISECONDS))
						.isInstanceOf(java.util.concurrent.TimeoutException.class);
			} finally {
				finish.countDown();
			}
			assertThat(active.get(5, TimeUnit.SECONDS)).isFalse();
			pause.get(5, TimeUnit.SECONDS);
			assertThat(service.withStartGate(service::isPaused)).isTrue();
		}
	}

	@Test
	void outerTransactionKeepsGateUntilCompletion() throws Exception {
		BatchMaintenanceService service = new BatchMaintenanceService(directory.resolve("state.flag").toString());
		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
			service.withStartGate(() -> null);
			List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
			Future<?> pause = pool.submit(() -> service.setPaused(true));
			try {
				assertThatThrownBy(() -> pause.get(100, TimeUnit.MILLISECONDS))
						.isInstanceOf(java.util.concurrent.TimeoutException.class);
			} finally {
				callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
			}
			pause.get(5, TimeUnit.SECONDS);
		} finally {
			TransactionSynchronizationManager.clear();
		}
		assertThat(service.isPaused()).isTrue();
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test synchronization timed out");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
