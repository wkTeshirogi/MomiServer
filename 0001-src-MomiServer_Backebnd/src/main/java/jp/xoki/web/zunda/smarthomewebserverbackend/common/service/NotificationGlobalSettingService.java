package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.NotificationProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationGlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知全体設定の参照と保存を提供するService。 */
@Service
@RequiredArgsConstructor
public class NotificationGlobalSettingService {

	/** 通知全体設定DBアクセス。 */
	private final NotificationGlobalSettingRepository notificationGlobalSettingRepository;

	/** application.yaml由来の通知設定。 */
	private final NotificationProperties notificationProperties;

	/**
	 * 通知全体設定を取得する。
	 *
	 * @return 通知全体設定
	 */
	public NotificationGlobalSettingDetailDto detail() {
		return notificationGlobalSettingRepository.find()
				.orElseGet(this::defaultSettings);
	}

	/**
	 * 通知全体設定を保存する。
	 *
	 * @param request 保存内容
	 * @return 保存後の通知全体設定
	 */
	@Transactional
	public NotificationGlobalSettingDetailDto save(NotificationGlobalSettingSaveRequest request) {
		notificationGlobalSettingRepository.save(request);
		return detail();
	}

	/**
	 * DB設定が未作成の場合の既定値を生成する。
	 *
	 * @return application.yamlを初期値にした通知全体設定
	 */
	private NotificationGlobalSettingDetailDto defaultSettings() {
		NotificationProperties.Discord discord = notificationProperties.getDiscord();
		NotificationProperties.SecurityEvents securityEvents = discord.getSecurityEvents();
		String webhookUrl = discord.getWebhookUrl();

		return NotificationGlobalSettingDetailDto.builder()
				.enabled(true)
				.browserEnabled(true)
				.discordEnabled(discord.isEnabled())
				.discordWebhookConfigured(webhookUrl != null && !webhookUrl.isBlank())
				.discordWebhookUrl(webhookUrl)
				.discordMinimumLevel(NotificationLevel.WARNING)
				.securityEventDiscordEnabled(securityEvents.isEnabled())
				.securityEventMinimumLevel(NotificationLevel.from(securityEvents.getMinimumSeverity()))
				.securityEventAggregationWindowSeconds(securityEvents.getAggregationWindowSeconds())
				.retentionDays(365)
				.build();
	}
}
