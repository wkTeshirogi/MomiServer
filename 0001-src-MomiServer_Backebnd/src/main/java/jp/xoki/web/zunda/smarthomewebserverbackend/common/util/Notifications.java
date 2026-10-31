package jp.xoki.web.zunda.smarthomewebserverbackend.common.util;

import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationGlobalSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.time.Clock;

/** アプリケーション内の通知機能を集約する公開窓口。 */
@Component
@RequiredArgsConstructor
public class Notifications {

	/** Discord Webhook通知の内部実装。 */
	private final DiscordNotice discordNotice;

	/** ブラウザ通知DBアクセス。 */
	private final NotificationRepository notificationRepository;

	/** 通知全体設定Service。 */
	private final NotificationGlobalSettingService notificationGlobalSettingService;

	private final Clock clock;

	/**
	 * システム通知としてブラウザ通知を登録し、必要に応じてDiscordへ通知する。
	 *
	 * @param title 通知タイトル
	 * @param message 通知本文
	 * @param level 通知レベル
	 * @param userIds 通知対象ユーザーID一覧
	 * @return 作成された通知の外部参照用通知ID
	 */
	@Transactional
	public UUID sendNotice(
			@NotNull String title,
			@NotNull String message,
			@NotNull NotificationLevel level,
			@NotNull List<Long> userIds) {
		return sendNoticeToUsers(BrowserNotificationCreateDto.builder()
				.notificationType(NotificationType.SYSTEM_NOTICE)
				.level(level)
				.title(title)
				.message(message)
				.sourceType(NotificationSourceType.SYSTEM)
				.build(), userIds);
	}

	/**
	 * 通知ルート設定から宛先を解決し、ブラウザ通知とDiscord通知を送信する。
	 *
	 * <p>ブラウザ通知の宛先は通知ルート設定とユーザー別受信設定から解決する。
	 * Discord通知はWARNING以上かつDiscord有効な通知ルートが存在する場合のみ送信する。</p>
	 *
	 * @param request 通知作成内容
	 * @return 作成された通知の外部参照用通知ID
	 */
	@Transactional
	public UUID sendNotice(@NotNull BrowserNotificationCreateDto request) {
		NotificationGlobalSettingDetailDto settings = notificationGlobalSettingService.detail();
		BrowserNotificationCreateDto retainedRequest = applyRetention(request, settings);
		List<Long> userIds = isBrowserDeliveryEnabled(settings)
				? notificationRepository.findRoutedBrowserUserIds(retainedRequest)
				: List.of();
		UUID notificationId = registerBrowserNotification(retainedRequest, userIds);

		// WARNING以上で、通知ルート上もDiscord通知が有効な場合のみDiscordへ送信する。
		if (isDiscordDeliveryEnabled(settings, retainedRequest.getLevel())
				&& notificationRepository.hasRoutedDiscordDestination(retainedRequest)) {
			discordNotice.sendNotice(
					retainedRequest.getTitle(),
					retainedRequest.getMessage(),
					retainedRequest.getLevel());
		}

		return notificationId;
	}

	/**
	 * ブラウザ通知を登録し、通知レベルに応じてDiscordへ通知する。
	 *
	 * @param request 通知作成内容
	 * @param userIds 通知対象ユーザーID一覧
	 * @return 作成された通知の外部参照用通知ID
	 */
	@Transactional
	public UUID sendNotice(
			@NotNull BrowserNotificationCreateDto request,
			@NotNull List<Long> userIds) {
		return sendNoticeToUsers(request, userIds);
	}

	private UUID sendNoticeToUsers(BrowserNotificationCreateDto request, List<Long> userIds) {
		NotificationGlobalSettingDetailDto settings = notificationGlobalSettingService.detail();
		BrowserNotificationCreateDto retainedRequest = applyRetention(request, settings);
		UUID notificationId = registerBrowserNotification(
				retainedRequest,
				isBrowserDeliveryEnabled(settings) ? userIds : List.of());

		// WARNING以上の通知はブラウザ通知に加えてDiscordにも送信する。
		if (isDiscordDeliveryEnabled(settings, retainedRequest.getLevel())) {
			discordNotice.sendNotice(
					retainedRequest.getTitle(),
					retainedRequest.getMessage(),
					retainedRequest.getLevel());
		}

		return notificationId;
	}

	/**
	 * ブラウザ通知本体とユーザー別通知状態を登録する。
	 *
	 * @param request 通知作成内容
	 * @param userIds 通知対象ユーザーID一覧
	 * @return 作成された通知の外部参照用通知ID
	 */
	private UUID registerBrowserNotification(BrowserNotificationCreateDto request, List<Long> userIds) {

		// 宛先が空でも発生した通知本体は残し、ユーザー別の受信状態は今回解決できた宛先にだけ作成する。
		UUID notificationId = notificationRepository.createNotification(request);

		// 直接指定とロール等の複数ルートが同じユーザーへ到達しても、受信状態を二重作成しない。
		userIds.stream()
				.distinct()
				.forEach(userId -> notificationRepository.createUserNotification(notificationId, userId));

		return notificationId;
	}

	/**
	 * 通知作成内容に表示期限が未指定の場合、通知全体設定の保持期間を反映する。
	 *
	 * @param request 通知作成内容
	 * @param settings 通知全体設定
	 * @return 表示期限を補完した通知作成内容
	 */
	private BrowserNotificationCreateDto applyRetention(
			BrowserNotificationCreateDto request,
			NotificationGlobalSettingDetailDto settings
	) {
		if (request.getExpiresAt() != null) {
			return request;
		}

		int retentionDays = settings.getRetentionDays() != null
				? Math.max(1, settings.getRetentionDays())
				: 365;

		return BrowserNotificationCreateDto.builder()
				.notificationType(request.getNotificationType())
				.level(request.getLevel())
				.title(request.getTitle())
				.message(request.getMessage())
				.sourceType(request.getSourceType())
				.sourceId(request.getSourceId())
				.linkPath(request.getLinkPath())
				.metadataJson(request.getMetadataJson())
				.expiresAt(LocalDateTime.now(clock).plusDays(retentionDays))
				.build();
	}

	/**
	 * ブラウザ通知を配信可能かどうかを判定する。
	 *
	 * @param settings 通知全体設定
	 * @return ブラウザ通知配信が有効な場合はtrue
	 */
	private boolean isBrowserDeliveryEnabled(NotificationGlobalSettingDetailDto settings) {
		return Boolean.TRUE.equals(settings.getEnabled())
				&& Boolean.TRUE.equals(settings.getBrowserEnabled());
	}

	/**
	 * Discord通知を配信可能かどうかを判定する。
	 *
	 * @param settings 通知全体設定
	 * @param level 通知レベル
	 * @return Discord通知配信が有効な場合はtrue
	 */
	private boolean isDiscordDeliveryEnabled(
			NotificationGlobalSettingDetailDto settings,
			NotificationLevel level
	) {
		return Boolean.TRUE.equals(settings.getEnabled())
				&& Boolean.TRUE.equals(settings.getDiscordEnabled())
				&& allowsLevel(settings.getDiscordMinimumLevel(), level);
	}

	/**
	 * 実際の通知レベルが最低通知レベルを満たしているかを判定する。
	 *
	 * @param minimumLevel 最低通知レベル
	 * @param actualLevel 実際の通知レベル
	 * @return 実際の通知レベルが最低通知レベル以上の場合はtrue
	 */
	private boolean allowsLevel(NotificationLevel minimumLevel, NotificationLevel actualLevel) {
		if (minimumLevel == null || actualLevel == null) {
			return false;
		}

		return actualLevel.ordinal() >= minimumLevel.ordinal();
	}
}
