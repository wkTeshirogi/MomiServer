package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationUnreadCountDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserBrowserNotificationDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** ログインユーザー向け通知の取得と状態更新を提供するController。 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "NotificationController", description = "User notification API")
public class NotificationController {

	/** 通知業務処理Service。 */
	private final NotificationService notificationService;

	/** 全件の段階取得に使うカーソル方式。既存の配列APIは互換性のため維持する。
	 *
	 * @param limit 最大件数
	 * @param cursor 前回応答の継続位置
	 * @param unreadOnly 未読限定
	 * @param authentication 認証情報
	 * @return 通知ページ
	 */
	@GetMapping("/page")
	public NotificationPageDto page(
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "false") boolean unreadOnly, Authentication authentication) {
		return notificationService.page(authentication, limit, cursor, unreadOnly);
	}

	/**
	 * ログインユーザーの通知一覧を取得する。
	 *
	 * @param limit 最大取得件数
	 * @param offset 読み飛ばす件数
	 * @param authentication 認証情報
	 * @return 通知一覧
	 */
	@GetMapping
	@Operation(summary = "Get current user notifications", operationId = "getNotifications")
	public ResponseEntity<List<UserBrowserNotificationDetailDto>> list(
			@RequestParam(required = false) Integer limit,
			@RequestParam(defaultValue = "0") int offset,
			Authentication authentication
	) {
		return ResponseEntity.ok(notificationService.list(authentication, limit, offset));
	}

	/**
	 * ログインユーザーの未読通知一覧を取得する。
	 *
	 * @param limit 最大取得件数
	 * @param offset 読み飛ばす件数
	 * @param authentication 認証情報
	 * @return 未読通知一覧
	 */
	@GetMapping("/unread")
	@Operation(summary = "Get current user unread notifications", operationId = "getUnreadNotifications")
	public ResponseEntity<List<UserBrowserNotificationDetailDto>> unread(
			@RequestParam(required = false) Integer limit,
			@RequestParam(defaultValue = "0") int offset,
			Authentication authentication
	) {
		return ResponseEntity.ok(notificationService.listUnread(authentication, limit, offset));
	}

	/**
	 * ログインユーザーの未読通知件数を取得する。
	 *
	 * @param authentication 認証情報
	 * @return 未読通知件数
	 */
	@GetMapping("/unread/count")
	@Operation(summary = "Get current user unread notification count", operationId = "countUnreadNotifications")
	public ResponseEntity<NotificationUnreadCountDto> unreadCount(Authentication authentication) {
		return ResponseEntity.ok(notificationService.countUnread(authentication));
	}

	/**
	 * ログインユーザーの通知を配信済みに更新する。
	 *
	 * @param notificationId 通知ID
	 * @param authentication 認証情報
	 * @return 空レスポンス
	 */
	@PostMapping("/{notificationId}/delivered")
	@Operation(summary = "Mark notification delivered", operationId = "markNotificationDelivered")
	public ResponseEntity<Void> delivered(
			@PathVariable UUID notificationId,
			Authentication authentication
	) {
		notificationService.markDelivered(authentication, notificationId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * ログインユーザーの通知を既読に更新する。
	 *
	 * @param notificationId 通知ID
	 * @param authentication 認証情報
	 * @return 空レスポンス
	 */
	@PostMapping("/{notificationId}/read")
	@Operation(summary = "Mark notification read", operationId = "markNotificationRead")
	public ResponseEntity<Void> read(
			@PathVariable UUID notificationId,
			Authentication authentication
	) {
		notificationService.markRead(authentication, notificationId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * ログインユーザーの通知を非表示に更新する。
	 *
	 * @param notificationId 通知ID
	 * @param authentication 認証情報
	 * @return 空レスポンス
	 */
	@PostMapping("/{notificationId}/dismiss")
	@Operation(summary = "Dismiss notification", operationId = "dismissNotification")
	public ResponseEntity<Void> dismiss(
			@PathVariable UUID notificationId,
			Authentication authentication
	) {
		notificationService.dismiss(authentication, notificationId);
		return ResponseEntity.noContent().build();
	}
}
