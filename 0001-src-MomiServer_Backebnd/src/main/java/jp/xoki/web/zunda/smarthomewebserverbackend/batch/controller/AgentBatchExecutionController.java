package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import org.springframework.transaction.annotation.Transactional;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.AgentBatchCompleteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.AgentBatchStartRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Agentからのバッチ開始通知と完了通知を受け付けるController。 */
@RestController
@RequestMapping("/api/v1/agent/batch/executions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@Transactional
@Tag(name = "AgentBatchExecutionController", description = "Agent batch execution API")
public class AgentBatchExecutionController {
	private final AgentAuthorization agentAuthorization;

	/** バッチ実行要求の業務処理Service。 */
	private final BatchExecutionRequestService batchExecutionRequestService;

	/**
	 * Agentからの開始通知を受け付け、実行要求を実行中へ更新する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return 更新後のバッチ実行要求詳細レスポンス
	 */
	@Operation(summary = "Notify batch execution start", operationId = "startAgentBatchExecution")
	@PostMapping("/{executionId}/start")
	public ResponseEntity<BatchExecutionRequestDetailDto> start(@PathVariable UUID executionId,
			@Valid @RequestBody(required = false) AgentBatchStartRequest request) {
		agentAuthorization.requireExecution(executionId);
		return ResponseEntity.ok(request == null
				? batchExecutionRequestService.markRunning(executionId)
				: batchExecutionRequestService.markRunning(executionId, request.started_at()));
	}

	/** 再接続時に開始・完了の受理状況とキャンセル要求を照合する。 */
	@GetMapping("/{executionId}/state")
	@Operation(summary = "Get accepted execution state", operationId = "getAgentBatchExecutionState")
	public ResponseEntity<AgentExecutionState> state(@PathVariable UUID executionId) {
		agentAuthorization.requireExecution(executionId);
		BatchExecutionRequestDetailDto current = batchExecutionRequestService.getDetail(executionId);
		return ResponseEntity.ok(new AgentExecutionState(executionId,
				current.getExecutionStatus().getStoredName(), current.getConfirmationStatus(),
				current.getCancelRequestedAt(), current.getCancelGraceSeconds()));
	}

	public record AgentExecutionState(UUID execution_id, String execution_status, String confirmation_status,
			java.time.LocalDateTime cancel_requested_at, Integer cancel_grace_seconds) {
	}

	/**
	 * Agentからの完了通知を受け付け、実行要求と履歴を完了状態へ更新する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param request 完了通知内容
	 * @return 完了後のバッチ実行要求詳細レスポンス
	 */
	@Operation(summary = "Notify batch execution completion", operationId = "completeAgentBatchExecution")
	@PostMapping("/{executionId}/complete")
	public ResponseEntity<BatchExecutionRequestDetailDto> complete(
			@PathVariable UUID executionId,
			@Valid @RequestBody AgentBatchCompleteRequest request
	) {
		agentAuthorization.requireExecution(executionId);
		return ResponseEntity.ok(batchExecutionRequestService.complete(executionId, request));
	}
}
