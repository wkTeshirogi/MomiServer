package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchMaintenanceService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** 更新前に新規開始を停止し、実行中・開始未確認の残件を確認する管理者専用API。 */
@RestController
@AdminOnly
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/master/common-settings/batch/maintenance")
public class BatchMaintenanceController {

	private final BatchMaintenanceService maintenance;
	private final BatchExecutionRequestService requests;

	/** 結果不明を完了とみなさず、停止可否を保守的に返す。 */
	@GetMapping
	@Operation(summary = "Get batch maintenance status", operationId = "getBatchMaintenance")
	public Status status() {
		return maintenance.withStartGate(() -> {
			boolean paused = maintenance.isPaused();
			long unsettled = requests.countUnsettledExecutions();
			return new Status(paused, unsettled, paused && unsettled == 0);
		});
	}

	/** 状態だけを変更し、アプリ停止や実行中バッチの取消は行わない。 */
	@PostMapping
	@Operation(summary = "Set batch maintenance status", operationId = "setBatchMaintenance")
	public Status change(@Valid @RequestBody Change request, Principal principal) {
		maintenance.setPaused(request.paused());
		log.info("Batch maintenance changed. actor={}, paused={}", principal.getName(), request.paused());
		return status();
	}

	public record Change(@NotNull Boolean paused) { }

	public record Status(boolean paused, long unsettled_execution_count, boolean safe_to_stop) { }
}
