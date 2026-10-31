package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestCreateRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.CanExecuteBatch;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.CanReadBatchExecution;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 手動バッチ実行要求の作成と実行要求詳細取得を提供するController。 */
@RestController
@RequestMapping("/api/batch/executions")
@RequiredArgsConstructor
@Tag(name = "BatchExecutionController", description = "Batch execution request API")
public class BatchExecutionController {

	/** バッチ実行要求の業務処理Service。 */
	private final BatchExecutionRequestService batchExecutionRequestService;

	@AdminOnly
	@PostMapping("/{executionId}/cancel")
	@Operation(summary = "Request batch cancellation", operationId = "cancelBatchExecution")
	public ResponseEntity<BatchExecutionRequestDetailDto> cancel(
			@PathVariable UUID executionId, Authentication authentication) {
		return ResponseEntity.ok(batchExecutionRequestService.cancel(executionId, authentication.getName()));
	}

	/**
	 * バッチ定義IDを指定して手動実行要求を作成する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @param request 実行要求内容
	 * @param authentication 認証情報
	 * @return 作成後のバッチ実行要求詳細レスポンス
	 */
	@Operation(summary = "Create manual batch execution request", operationId = "createManualBatchExecution")
	@CanExecuteBatch
	@PostMapping("/definitions/{batchDefinitionId}")
	public ResponseEntity<BatchExecutionRequestDetailDto> createManualExecution(
			@PathVariable Long batchDefinitionId,
			@Valid @RequestBody BatchExecutionRequestCreateRequest request,
			Authentication authentication
	) {
		String requestedBy = authentication != null ? authentication.getName() : null;
		return ResponseEntity.ok(batchExecutionRequestService.createManualRequest(
				batchDefinitionId,
				request,
				requestedBy));
	}

	/**
	 * 外部参照用実行IDを指定してバッチ実行要求詳細を取得する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return バッチ実行要求詳細レスポンス
	 */
	@Operation(summary = "Get batch execution request detail", operationId = "getBatchExecutionRequestDetail")
	@CanReadBatchExecution
	@GetMapping("/{executionId}")
	public ResponseEntity<BatchExecutionRequestDetailDto> detail(@PathVariable UUID executionId) {
		return ResponseEntity.ok(batchExecutionRequestService.getDetail(executionId));
	}
}
