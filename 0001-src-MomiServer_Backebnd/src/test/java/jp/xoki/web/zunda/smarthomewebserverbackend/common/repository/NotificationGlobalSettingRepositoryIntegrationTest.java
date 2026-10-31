package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;

@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false", "app.vehicle-inspection-notification.enabled=false"})
@Transactional
class NotificationGlobalSettingRepositoryIntegrationTest {
    @Autowired NotificationGlobalSettingRepository repository;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", " https://example.invalid/webhook "})
    void missingSingletonIsCreatedWithNormalizedWebhookAndAllSettings(String webhook) {
        jdbc.update("delete from t_notification_global_settings where id=1");
        assertThat(repository.find()).isEmpty();
        repository.save(new NotificationGlobalSettingSaveRequest(false, true, true, webhook,
                NotificationLevel.WARNING, false, NotificationLevel.CRITICAL, 90, 15));
        NotificationGlobalSettingDetailDto created = repository.find().orElseThrow();
        String expectedWebhook = webhook == null || webhook.isBlank() ? null : webhook.trim();
        assertThat(created.getEnabled()).isFalse();
        assertThat(created.getBrowserEnabled()).isTrue();
        assertThat(created.getDiscordEnabled()).isTrue();
        assertThat(created.getDiscordWebhookUrl()).isEqualTo(expectedWebhook);
        assertThat(created.getDiscordWebhookConfigured()).isEqualTo(expectedWebhook != null);
        assertThat(created.getDiscordMinimumLevel()).isEqualTo(NotificationLevel.WARNING);
        assertThat(created.getSecurityEventDiscordEnabled()).isFalse();
        assertThat(created.getSecurityEventMinimumLevel()).isEqualTo(NotificationLevel.CRITICAL);
        assertThat(created.getSecurityEventAggregationWindowSeconds()).isEqualTo(90);
        assertThat(created.getRetentionDays()).isEqualTo(15);
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from t_notification_global_settings", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void existingSingletonIsUpdatedWithoutDuplicatingOrLosingSettings() {
        repository.save(new NotificationGlobalSettingSaveRequest(false, true, true,
                "https://example.invalid/webhook", NotificationLevel.WARNING,
                false, NotificationLevel.CRITICAL, 90, 15));
        NotificationGlobalSettingDetailDto created = repository.find().orElseThrow();
        repository.save(new NotificationGlobalSettingSaveRequest(true, false, false, null,
                NotificationLevel.INFO, true, NotificationLevel.WARNING, 120, 30));
        NotificationGlobalSettingDetailDto updated = repository.find().orElseThrow();
        assertThat(updated.getEnabled()).isTrue();
        assertThat(updated.getBrowserEnabled()).isFalse();
        assertThat(updated.getDiscordEnabled()).isFalse();
        assertThat(updated.getDiscordWebhookUrl()).isNull();
        assertThat(updated.getDiscordWebhookConfigured()).isFalse();
        assertThat(updated.getDiscordMinimumLevel()).isEqualTo(NotificationLevel.INFO);
        assertThat(updated.getSecurityEventDiscordEnabled()).isTrue();
        assertThat(updated.getSecurityEventMinimumLevel()).isEqualTo(NotificationLevel.WARNING);
        assertThat(updated.getSecurityEventAggregationWindowSeconds()).isEqualTo(120);
        assertThat(updated.getRetentionDays()).isEqualTo(30);
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(jdbc.queryForObject("select count(*) from t_notification_global_settings", Integer.class))
                .isEqualTo(1);
    }
}
