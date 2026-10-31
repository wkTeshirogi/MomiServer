package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationGlobalSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通知全体設定APIを提供する管理者向けコントローラー。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/common-settings/notifications")
@RequiredArgsConstructor
@Tag(name = "NotificationGlobalSettingController", description = "通知全体設定関連のAPI")
public class NotificationGlobalSettingController {

	/** 通知全体設定Service。 */
	private final NotificationGlobalSettingService notificationGlobalSettingService;

	/**
	 * 通知全体設定を取得する。
	 *
	 * @return 通知全体設定
	 */
	@Operation(summary = "通知全体設定取得", description = "通知機能全体の設定を取得します。", operationId = "getNotificationGlobalSetting")
	@GetMapping
	public ResponseEntity<NotificationGlobalSettingDetailDto> detail() {
		return ResponseEntity.ok(notificationGlobalSettingService.detail());
	}

	/**
	 * 通知全体設定を保存する。
	 *
	 * @param request 保存内容
	 * @return 保存後の通知全体設定
	 */
	@Operation(summary = "通知全体設定保存", description = "通知機能全体の設定を保存します。", operationId = "saveNotificationGlobalSetting")
	@PostMapping
	public ResponseEntity<NotificationGlobalSettingDetailDto> save(
			@Valid @RequestBody NotificationGlobalSettingSaveRequest request
	) {
		return ResponseEntity.ok(notificationGlobalSettingService.save(request));
	}
}
