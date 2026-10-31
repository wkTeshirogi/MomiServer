package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** ファイルで管理する非機密のセキュリティ設定。変更は再起動後に反映する。 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecuritySettingProperties {

    /** ロック開始前に許容する連続失敗回数。 */
    @Min(1)
    private int loginFailureTolerance = 5;

    /** ロック開始からの固定期間（分）。 */
    @Min(1)
    private int loginLockMinutes = 15;

    /** 外部在庫APIのキーごとの時間単位上限。 */
    @Min(1)
    private int externalInventoryHourlyLimit = 1000;
}
