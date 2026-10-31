package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import org.jooq.conf.RenderOptionalKeyword;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** jOOQが生成するSQLの結合種別を明示する共通設定。 */
@Configuration
public class JooqConfig {

    /**
     * 内部結合の省略表記を無効にする。
     *
     * 結合条件・外部結合や他のSettingsは変更せず、出力SQLのINNERキーワードを表示する。
     *
     * @return Spring BootのjOOQ設定カスタマイザー
     */
    @Bean
    public DefaultConfigurationCustomizer explicitJoinKeywords() {
        return configuration -> configuration.settings().withRenderOptionalInnerKeyword(RenderOptionalKeyword.ON);
    }
}
