package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import static jp.xoki.web.zunda.jooq.Tables.M_AGENTS;
import static jp.xoki.web.zunda.jooq.Tables.M_DEVICES;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderOptionalKeyword;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.Test;

/** SQLの結合種別を明示し、既存の設定や外部結合を変えないことを確認する。 */
class JooqConfigTest {

    @Test
    void customizerPreservesOtherSettings() {
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.set(SQLDialect.POSTGRES);
        configuration.settings().withRenderSchema(false).withRenderFormatted(true);

        new JooqConfig().explicitJoinKeywords().customize(configuration);

        assertThat(configuration.settings().getRenderOptionalInnerKeyword()).isEqualTo(RenderOptionalKeyword.ON);
        assertThat(configuration.settings().isRenderSchema()).isFalse();
        assertThat(configuration.settings().isRenderFormatted()).isTrue();
    }

    @Test
    void rendersInnerJoinWithoutOmittingInnerKeyword() {
        DSLContext dsl = configuredContext();
        String sql = dsl.select(M_AGENTS.ID).from(M_AGENTS)
                .innerJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_AGENTS.DEVICE_ID)).getSQL();

        assertThat(sql.toLowerCase(Locale.ROOT)).contains(" inner join ");
    }

    @Test
    void preservesLeftOuterJoinAndItsCondition() {
        DSLContext dsl = configuredContext();
        String sql = dsl.select(M_DEVICES.ID).from(M_DEVICES)
                .leftJoin(M_AGENTS).on(M_AGENTS.DEVICE_ID.eq(M_DEVICES.ID)).getSQL();

        assertThat(sql.toLowerCase(Locale.ROOT)).containsPattern(" left (outer )?join ").doesNotContain(" inner join ");
        assertThat(sql).contains(M_AGENTS.DEVICE_ID.getName(), M_DEVICES.ID.getName());
    }

    private DSLContext configuredContext() {
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.set(SQLDialect.POSTGRES);
        new JooqConfig().explicitJoinKeywords().customize(configuration);
        return DSL.using(configuration);
    }
}
