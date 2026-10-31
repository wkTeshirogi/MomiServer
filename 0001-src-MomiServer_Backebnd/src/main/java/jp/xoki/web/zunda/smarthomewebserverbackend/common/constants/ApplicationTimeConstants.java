package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import java.time.ZoneId;

/** DBのローカル日時、業務日付、Cron実行で共用するタイムゾーン。 */
public final class ApplicationTimeConstants {
    public static final String ZONE_ID = "Asia/Tokyo";
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private ApplicationTimeConstants() {
    }
}
