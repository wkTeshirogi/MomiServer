package jp.xoki.web.zunda.smarthomewebserverbackend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.RegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.response.AuthResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.AuthService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.UserNotificationPreferenceService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.UserNotificationRouteSettingService;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.response.UserMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.service.UserMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ユーザーマスタの登録と参照APIを提供する管理者向けコントローラー。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/user")
@RequiredArgsConstructor
@Tag(name = "MasterUserController", description = "マスターユーザー関連のAPI")
public class MasterUserController {
	private final UserMasterService userMasterService;
	private final AuthService authService;
	private final UserNotificationPreferenceService userNotificationPreferenceService;
	private final UserNotificationRouteSettingService userNotificationRouteSettingService;

	@Operation(summary = "ユーザー登録", description = "メールアドレスとパスワードを登録し、登録後に認証トークンを返します。", operationId = "registerMasterUser")
	@PostMapping("/register")

	/**
	 * ユーザーを登録し、認証トークンを返す。
	 *
	 * @param request ユーザー登録リクエスト
	 * @return 登録後に発行した認証トークン情報
	 */
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.ok(authService.register(request));
	}

	@Operation(summary = "ユーザー一覧", description = "登録されているユーザー一覧を取得します。", operationId = "getMasterUserList")
	@PostMapping("/list")

	/**
	 * 登録されているユーザー一覧を取得する。
	 *
	 * @return ユーザー詳細一覧
	 */
	public ResponseEntity<List<UserMasterDetailDto>> list() {
		return ResponseEntity.ok(userMasterService.getUserMasterList());
	}

	@Operation(summary = "ユーザー詳細", description = "ユーザーの詳細情報を取得します。", operationId = "getMasterUserDetail")
	@PostMapping("/detail")

	/**
	 * 指定されたIDのユーザー詳細を取得する。
	 *
	 * @param id ユーザーID
	 * @return ユーザー詳細
	 */
	public ResponseEntity<UserMasterDetailDto> detail(Long id) {
		return ResponseEntity.ok(userMasterService.getUserMaster(id));
	}

	/**
	 * 指定ユーザーの通知受信設定を取得する。
	 *
	 * @param userId ユーザーID
	 * @return 通知受信設定一覧
	 */
	@Operation(summary = "ユーザー別通知設定取得", description = "指定ユーザーの通知受信設定を取得します。", operationId = "getUserNotificationPreferences")
	@GetMapping("/{userId}/notification-preferences")
	public ResponseEntity<List<UserNotificationPreferenceDetailDto>> getNotificationPreferences(
			@PathVariable Long userId
	) {
		return ResponseEntity.ok(userNotificationPreferenceService.list(userId));
	}

	/**
	 * 指定ユーザーの通知受信設定を保存する。
	 *
	 * @param userId ユーザーID
	 * @param request 保存内容
	 * @return 保存後の通知受信設定一覧
	 */
	@Operation(summary = "ユーザー別通知設定保存", description = "指定ユーザーの通知受信設定を保存します。", operationId = "saveUserNotificationPreferences")
	@PostMapping("/{userId}/notification-preferences")
	public ResponseEntity<List<UserNotificationPreferenceDetailDto>> saveNotificationPreferences(
			@PathVariable Long userId,
			@Valid @RequestBody UserNotificationPreferenceSaveRequest request
	) {
		return ResponseEntity.ok(userNotificationPreferenceService.save(userId, request.preferences()));
	}

	/**
	 * 指定ユーザーを直接宛先にしている通知ルート設定を取得する。
	 *
	 * @param userId ユーザーID
	 * @return 個別ユーザー宛て通知ルート設定一覧
	 */
	@Operation(summary = "ユーザー直接通知ルート設定取得", description = "指定ユーザーを直接宛先にしている通知ルート設定を取得します。", operationId = "getUserNotificationRouteSettings")
	@GetMapping("/{userId}/notification-route-settings")
	public ResponseEntity<List<UserNotificationRouteSettingDetailDto>> getNotificationRouteSettings(
			@PathVariable Long userId
	) {
		return ResponseEntity.ok(userNotificationRouteSettingService.listByTargetUserId(userId));
	}
}
