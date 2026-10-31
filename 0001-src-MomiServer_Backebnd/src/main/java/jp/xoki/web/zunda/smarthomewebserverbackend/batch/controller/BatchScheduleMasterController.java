package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionSummaryDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchScheduleService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** バッチスケジュールマスタの参照、登録、更新を提供する管理者向けController。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/batch/schedule")
@RequiredArgsConstructor
@Tag(name = "BatchScheduleMasterController", description = "バッチスケジュールマスタ関連のAPI")
public class BatchScheduleMasterController {

	/** バッチスケジュールの業務処理Service。 */
	private final BatchScheduleService batchScheduleService;

	/**
	 * 表示期間内のスケジュール実行状態を取得する。
	 *
	 * @param from 開始日
	 * @param to 終了日
	 * @return 日付・時間帯・状態別の実行件数
	 */
	@GetMapping("/executions")
	@Operation(summary = "バッチスケジュール実行状況を取得", operationId = "getBatchScheduleExecutions")
	public ResponseEntity<List<BatchScheduleExecutionSummaryDto>> executions(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ResponseEntity.ok(batchScheduleService.getExecutionSummaries(from, to));
	}

	/** 指定したスケジュール・予定日の完了済み実行ログを取得する。 */
	@GetMapping("/executions/logs")
	@Operation(summary = "バッチスケジュール実行ログを取得", operationId = "getBatchScheduleExecutionLogs")
	public ResponseEntity<BatchScheduleExecutionLogPageDto> executionLogs(
			@RequestParam long scheduleId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Integer hour,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "20") int limit) {
		return ResponseEntity.ok(batchScheduleService.getExecutionLogs(
				scheduleId, date, hour, offset, limit));
	}

	/**
	 * バッチスケジュールIDを指定して詳細を取得する。
	 *
	 * @param id バッチスケジュールID
	 * @return バッチスケジュール詳細レスポンス
	 */
	@Operation(summary = "バッチスケジュール詳細を取得", description = "指定されたIDのバッチスケジュール詳細を取得します", operationId = "getBatchScheduleDetail")
	@PostMapping("/detail")
	public ResponseEntity<BatchScheduleDetailDto> detail(Long id) {
		return ResponseEntity.ok(batchScheduleService.getBatchScheduleDetailDto(id));
	}

	/**
	 * バッチスケジュールを一覧取得する。
	 *
	 * @return バッチスケジュール一覧レスポンス
	 */
	@Operation(summary = "バッチスケジュール一覧を取得", description = "すべてのバッチスケジュール一覧を取得します", operationId = "getAllBatchSchedules")
	@PostMapping("/list")
	public ResponseEntity<List<BatchScheduleDetailDto>> list() {
		return ResponseEntity.ok(batchScheduleService.getAllBatchSchedules());
	}

	/**
	 * バッチスケジュールを登録または更新する。
	 *
	 * @param batchScheduleDetailDto 登録または更新内容
	 * @return 保存後のバッチスケジュール詳細レスポンス
	 */
	@Operation(summary = "バッチスケジュールを登録または更新", description = "バッチスケジュールを登録または更新します", operationId = "registerBatchSchedule")
	@PostMapping("/register")
	public ResponseEntity<BatchScheduleDetailDto> register(@Valid @RequestBody BatchScheduleRegistRequest batchScheduleDetailDto) {
		BatchScheduleDetailDto registeredBatchSchedule = batchScheduleService.save(batchScheduleDetailDto);
		return ResponseEntity.ok(registeredBatchSchedule);
	}
}
