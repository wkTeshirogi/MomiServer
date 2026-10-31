package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonsLangSecurityRegressionTest {

    @Test
    @Timeout(15)
    void longUnresolvableNameDoesNotOverflowTheStack() throws Exception {
        ClassLoader rejectingLoader = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };
        FutureTask<Class<?>> lookup = new FutureTask<>(
                () -> ClassUtils.getClass(rejectingLoader, "missing.".repeat(4096) + "Type", false));

        // スタック容量に余裕がある環境でも旧版の再帰を検出できるようにする。
        Thread worker = new Thread(null, lookup, "commons-lang-regression", 256 * 1024);
        worker.start();
        try {
            assertThatThrownBy(() -> lookup.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ClassNotFoundException.class);
        } finally {
            lookup.cancel(true);
            worker.join(1000);
        }
    }

    @Test
    void ordinaryPrimitiveArrayAndInnerClassNamesStillResolve() throws Exception {
        assertThat(ClassUtils.getClass("int")).isEqualTo(int.class);
        assertThat(ClassUtils.getClass("java.lang.String[]")).isEqualTo(String[].class);
        assertThat(ClassUtils.getClass("java.util.Map.Entry")).isEqualTo(java.util.Map.Entry.class);
    }
}
