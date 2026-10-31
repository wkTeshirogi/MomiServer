package jp.xoki.web.zunda.smarthomewebserverbackend.common.time;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApplicationTimeConstants;

import java.time.Clock;

/** Springと単独実行ツールで共用する時計の生成元。現在日時の値は保持しない。 */
public final class ApplicationClockFactory {
    private ApplicationClockFactory() {
    }

    /** 業務タイムゾーンを持つシステム時計を生成する。
     *
     * Spring管理クラスでは、この生成処理を直接呼ばずClockをコンストラクタ注入する。
     * @return 日本時間のシステム時計
     */
    public static Clock systemClock() {
        return Clock.system(ApplicationTimeConstants.ZONE);
    }
}
