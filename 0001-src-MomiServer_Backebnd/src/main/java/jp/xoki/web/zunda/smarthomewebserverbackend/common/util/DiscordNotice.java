package jp.xoki.web.zunda.smarthomewebserverbackend.common.util;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationGlobalSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.DiscordNoticeConstants.DISCORD_COLOR_CRITICAL;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.DiscordNoticeConstants.DISCORD_COLOR_INFO;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.DiscordNoticeConstants.DISCORD_COLOR_WARNING;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.DiscordNoticeConstants.DISCORD_CONTENT_MAX_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.DiscordNoticeConstants.DISCORD_EMBED_DESCRIPTION_MAX_LENGTH;

/** Discord Webhookへ通知を送信する共通ユーティリティ。 */
@Slf4j
@Component
class DiscordNotice {






	/** 通知全体設定Service。 */
	private final NotificationGlobalSettingService notificationGlobalSettingService;

	/** Discord Webhook呼び出し用HTTPクライアント。 */
	private final RestClient restClient;

	/**
	 * Discord Webhook通知ユーティリティを生成する。
	 *
	 * @param notificationProperties 通知設定
	 * @param restClientBuilder RestClientビルダー
	 */
	DiscordNotice(
			NotificationGlobalSettingService notificationGlobalSettingService,
			RestClient.Builder restClientBuilder) {
		this.notificationGlobalSettingService = notificationGlobalSettingService;
		this.restClient = restClientBuilder.build();
	}

	/**
	 * Discord Webhookへ通知を送信する。
	 *
	 * @param title 通知タイトル
	 * @param message 通知本文
	 * @param type 通知レベル
	 * @return 送信した場合はtrue、設定により抑止または失敗した場合はfalse
	 */
	boolean sendNotice(
			@NotNull String title,
			@NotNull String message,
			@NotNull NotificationLevel type) {
		NotificationGlobalSettingDetailDto settings = notificationGlobalSettingService.detail();

		// Discord通知全体が無効な場合は送信しない。
		if (!Boolean.TRUE.equals(settings.getEnabled())
				|| !Boolean.TRUE.equals(settings.getDiscordEnabled())) {
			return false;
		}

		// Webhook URLが未設定の場合は送信しない。
		if (!StringUtils.hasText(settings.getDiscordWebhookUrl())) {
			return false;
		}

		DiscordWebhookPayload payload = new DiscordWebhookPayload(
				toContent(type, title),
				List.of(new DiscordWebhookEmbed(
						trim(title, DISCORD_CONTENT_MAX_LENGTH),
						trim(message, DISCORD_EMBED_DESCRIPTION_MAX_LENGTH),
						toColor(type))));

		try {
			restClient.post()
					.uri(settings.getDiscordWebhookUrl())
					.body(payload)
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (RestClientException exception) {
			log.warn("Failed to send Discord webhook notice. type={}", type, exception);
			return false;
		}
	}

	/**
	 * Discord本文用の短い通知文を作成する。
	 *
	 * @param type 通知レベル
	 * @param title 通知タイトル
	 * @return Discord本文
	 */
	private String toContent(NotificationLevel type, String title) {
		return trim("[" + type.getLevel() + "] " + title, DISCORD_CONTENT_MAX_LENGTH);
	}

	/**
	 * 通知レベルに対応するDiscord Embedカラーを返す。
	 *
	 * @param type 通知レベル
	 * @return Discord Embedカラー
	 */
	private int toColor(NotificationLevel type) {

		// 重要通知は赤色で表示する。
		if (type == NotificationLevel.CRITICAL) {
			return DISCORD_COLOR_CRITICAL;
		}

		// 警告通知は黄色で表示する。
		if (type == NotificationLevel.WARNING) {
			return DISCORD_COLOR_WARNING;
		}

		return DISCORD_COLOR_INFO;
	}

	/**
	 * Discordの文字数上限を超えないように文字列を丸める。
	 *
	 * @param value 対象文字列
	 * @param maxLength 最大文字数
	 * @return 丸めた文字列
	 */
	private String trim(String value, int maxLength) {

		// 文字数上限以内の場合はそのまま返す。
		if (value.length() <= maxLength) {
			return value;
		}

		return value.substring(0, maxLength - 3) + "...";
	}

	/**
	 * Discord Webhookへ送信するJSONペイロード。
	 *
	 * @param content 通知本文
	 * @param embeds 埋め込み表示内容
	 */
	private record DiscordWebhookPayload(
			String content,
			List<DiscordWebhookEmbed> embeds) {
	}

	/**
	 * Discord WebhookのEmbed要素。
	 *
	 * @param title Embedタイトル
	 * @param description Embed本文
	 * @param color Embedカラー
	 */
	private record DiscordWebhookEmbed(
			String title,
			String description,
			int color) {
	}
}
