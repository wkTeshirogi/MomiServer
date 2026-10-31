package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** バッチ共通設定を管理する管理者専用API。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/common-settings/batch")
@RequiredArgsConstructor
@Tag(name = "BatchGlobalSettingController", description = "バッチ共通設定関連のAPI")
public class BatchGlobalSettingController {

	private final BatchGlobalSettingService service;

	@Operation(summary = "バッチ共通設定取得", operationId = "getBatchGlobalSetting")
	@GetMapping
	public ResponseEntity<BatchGlobalSettingDetailDto> detail() {
		return ResponseEntity.ok(service.detail());
	}

	@Operation(summary = "バッチ共通設定保存", operationId = "saveBatchGlobalSetting")
	@PostMapping
	public ResponseEntity<BatchGlobalSettingDetailDto> save(
			@Valid @RequestBody BatchGlobalSettingSaveRequest request
	) {
		return ResponseEntity.ok(service.save(request));
	}
}
