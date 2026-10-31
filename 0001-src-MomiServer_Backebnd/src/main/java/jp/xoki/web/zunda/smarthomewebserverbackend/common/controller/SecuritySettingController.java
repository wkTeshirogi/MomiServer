package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.GatewayProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.SecuritySettingProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.SecuritySettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** セキュリティ共通設定の参照API。保存APIや秘密情報の応答は提供しない。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/common-settings/security")
public class SecuritySettingController {
    private final SecuritySettingDetailDto setting;

    public SecuritySettingController(SecuritySettingProperties security, GatewayProperties gateway,
            @Value("${jwt.expiration.access-token}") long accessSeconds,
            @Value("${jwt.expiration.refresh-token}") long refreshSeconds) {
        this.setting = new SecuritySettingDetailDto(accessSeconds, refreshSeconds,
                gateway.getRateLimit().isEnabled(), gateway.getRateLimit().getPerMinute(),
                security.getExternalInventoryHourlyLimit(), security.getLoginFailureTolerance(),
                security.getLoginLockMinutes());
    }

    /** 有効な非機密設定を取得する。
     *
     * @return 管理者向け参照設定
     */
    @GetMapping
    public SecuritySettingDetailDto detail() {
        return setting;
    }
}
