package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationRouteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 通知ルート設定マスタAPIを提供する管理者向けコントローラー。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/notification-route-settings")
@RequiredArgsConstructor
@Tag(name = "NotificationRouteSettingController", description = "通知ルート設定マスタ関連のAPI")
public class NotificationRouteSettingController {

	/** 通知ルート設定Service。 */
	private final NotificationRouteSettingService notificationRouteSettingService;

	/**
	 * 通知ルート設定一覧を取得する。
	 *
	 * @return 通知ルート設定一覧
	 */
	@Operation(summary = "通知ルート設定一覧", description = "通知ルート設定マスタの一覧を取得します。", operationId = "getNotificationRouteSettingList")
	@GetMapping
	public ResponseEntity<List<NotificationRouteSettingDetailDto>> list() {
		return ResponseEntity.ok(notificationRouteSettingService.list());
	}

	/**
	 * 通知ルート設定詳細を取得する。
	 *
	 * @param id 通知ルート設定ID
	 * @return 通知ルート設定詳細
	 */
	@Operation(summary = "通知ルート設定詳細", description = "通知ルート設定マスタの詳細を取得します。", operationId = "getNotificationRouteSettingDetail")
	@GetMapping("/{id}")
	public ResponseEntity<NotificationRouteSettingDetailDto> detail(@PathVariable Long id) {
		return ResponseEntity.ok(notificationRouteSettingService.detail(id));
	}

	/**
	 * 通知ルート設定を登録または更新する。
	 *
	 * @param request 保存内容
	 * @return 保存後の通知ルート設定詳細
	 */
	@Operation(summary = "通知ルート設定保存", description = "通知ルート設定マスタを登録または更新します。", operationId = "saveNotificationRouteSetting")
	@PostMapping
	public ResponseEntity<NotificationRouteSettingDetailDto> save(
			@Valid @RequestBody NotificationRouteSettingSaveRequest request
	) {
		return ResponseEntity.ok(notificationRouteSettingService.save(request));
	}
}
