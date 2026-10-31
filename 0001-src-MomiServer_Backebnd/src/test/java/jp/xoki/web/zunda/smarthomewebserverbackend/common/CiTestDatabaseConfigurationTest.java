package jp.xoki.web.zunda.smarthomewebserverbackend.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static org.assertj.core.api.Assertions.assertThat;

/** JUnitの接続上限がCIに適用され、製品・他ジョブへ広がらないことを検証する。 */
class CiTestDatabaseConfigurationTest {

    @Test
    void junitJobLimitsEachCachedContextsPoolWithoutChangingOtherJobs() throws Exception {
        Map<?, ?> pipeline = loadYaml(Path.of(".gitlab-ci.yml"));
        Map<?, ?> job = (Map<?, ?>) pipeline.get("unit_test");
        Map<Object, Object> variables = new java.util.LinkedHashMap<>((Map<?, ?>) job.get("variables"));
        assertThat(variables).containsEntry("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "5")
                .containsEntry("SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE", "1");
        assertNoTestPoolOverrides((Map<?, ?>) pipeline.get("variables"));
        assertNoTestPoolOverrides((Map<?, ?>) ((Map<?, ?>) pipeline.get("sonarqube_analysis")).get("variables"));
    }

    @Test
    void productionPoolKeepsItsExistingDefaults() throws Exception {
        Map<?, ?> application = loadYaml(Path.of("src/main/resources/application.yaml"));
        Map<?, ?> spring = (Map<?, ?>) application.get("spring");
        Map<?, ?> datasource = (Map<?, ?>) spring.get("datasource");
        Map<?, ?> hikari = (Map<?, ?>) datasource.get("hikari");
        assertThat(hikari.containsKey("maximum-pool-size")).isFalse();
        assertThat(hikari.containsKey("minimum-idle")).isFalse();
    }

    private void assertNoTestPoolOverrides(Map<?, ?> variables) {
        assertThat(variables.containsKey("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE")).isFalse();
        assertThat(variables.containsKey("SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE")).isFalse();
    }

    private Map<?, ?> loadYaml(Path file) throws Exception {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return yaml.load(Files.readString(file, StandardCharsets.UTF_8));
    }
}
