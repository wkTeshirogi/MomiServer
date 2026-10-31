package jp.xoki.web.zunda.smarthomewebserverbackend.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** アプリケーション全体で注入可能な時計を提供する。 */
@Configuration
public class ApplicationTimeConfiguration {

    /** 現在日時取得用のClockを登録する。
     *
     * テストでは同じ依存をClock.fixedへ置き換える。
     * @return 業務時計
     */
    @Bean
    public Clock applicationClock() {
        return ApplicationClockFactory.systemClock();
    }
}
