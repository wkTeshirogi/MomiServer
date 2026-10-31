package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** エージェント向け共通設定参照。Agent専用Bearer認証を使用する。 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@RequestMapping("/api/v1/agent/batch/settings")
public class AgentBatchSettingController {
	private final BatchGlobalSettingService service;

	@GetMapping
	@Operation(summary = "Get agent batch settings", operationId = "getAgentBatchSettings")
	public BatchGlobalSettingDetailDto detail() {
		return service.detail();
	}
}
