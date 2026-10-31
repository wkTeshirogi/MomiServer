package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

/** 管理者向けの非機密設定だけを列挙した参照応答。設定Bean全体は公開しない。 */
public record SecuritySettingDetailDto(long accessTokenSeconds, long refreshTokenSeconds,
        boolean iotRateLimitEnabled, int iotPerMinute, int externalInventoryHourlyLimit,
        int loginFailureTolerance, int loginLockMinutes) { }
