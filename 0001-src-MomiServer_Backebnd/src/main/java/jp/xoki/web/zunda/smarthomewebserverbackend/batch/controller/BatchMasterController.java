package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchDefinitionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** バッチ定義マスタの参照、登録、更新を提供する管理者向けController。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/batch")
@RequiredArgsConstructor
@Tag(name = "BatchMasterController", description = "バッチマスタ関連のAPI")
public class BatchMasterController {

	/** バッチ定義の業務処理Service。 */
	private final BatchDefinitionService batchDefinitionService;

	/**
	 * バッチ定義IDを指定して詳細を取得する。
	 *
	 * @param id バッチ定義ID
	 * @return バッチ定義詳細レスポンス
	 */
	@Operation(summary = "バッチ定義詳細を取得", description = "指定されたIDのバッチ定義詳細を取得します", operationId = "getBatchDefinitionDetail")
	@PostMapping("/detail")
	public ResponseEntity<BatchDefinitionDetailDto> detail(Long id) {
		return ResponseEntity.ok(batchDefinitionService.getBatchDefinitionDetailDto(id));
	}

	/**
	 * バッチ定義を一覧取得する。
	 *
	 * @return バッチ定義一覧レスポンス
	 */
	@Operation(summary = "バッチ定義一覧を取得", description = "すべてのバッチ定義一覧を取得します", operationId = "getAllBatchDefinitions")
	@PostMapping("/list")
	public ResponseEntity<List<BatchDefinitionDetailDto>> list() {
		return ResponseEntity.ok(batchDefinitionService.getAllBatchDefinitions());
	}


	/**
	 * バッチ定義を登録または更新する。
	 *
	 * @param batchDefinitionRegistRequest 登録または更新内容
	 * @return 保存後のバッチ定義詳細レスポンス
	 */
	@Operation(summary = "バッチ定義を登録または更新", description = "バッチ定義を登録または更新します", operationId = "registerBatchDefinition")
	@PostMapping("/register")
	public ResponseEntity<BatchDefinitionDetailDto> register(@Valid @RequestBody BatchDefinitionRegistRequest batchDefinitionRegistRequest) {
		BatchDefinitionDetailDto registeredBatchDefinition = batchDefinitionService.save(batchDefinitionRegistRequest);
		return ResponseEntity.ok(registeredBatchDefinition);
	}
}
