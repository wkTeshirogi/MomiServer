package jp.xoki.web.zunda.smarthomewebserverbackend.common.time;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.stream.Stream;

/** 引数なしの日時取得・環境依存タイムゾーン・個別Clock生成の再追加を防ぐ。 */
class ApplicationTimePolicyTest {
    @Test
    void currentTimeAndCronUseExplicitClockAndZone() throws Exception {
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        Set<String> dateTypes = Set.of("LocalDateTime", "LocalDate", "LocalTime", "OffsetDateTime", "OffsetTime",
                "ZonedDateTime", "Instant", "Year", "YearMonth", "MonthDay");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                CompilationUnit unit = parser.parse(file).getResult().orElseThrow();
                checkCalls(file, unit, dateTypes, violations);
                checkCron(file, unit, violations);
            }
        }
        assertThat(violations).as("Time acquisition must use the shared injected Clock; elapsed timing may use nanoTime").isEmpty();
    }

    private void checkCalls(Path file, CompilationUnit unit, Set<String> dateTypes, List<String> violations) {
        String filename = file.getFileName().toString();
        boolean factory = filename.equals("ApplicationClockFactory.java");
        boolean factoryConsumer = factory || filename.equals("ApplicationTimeConfiguration.java")
                || filename.equals("DesignDocGenerator.java");
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            if (usesImplicitTime(call, dateTypes) || createsIndependentClock(call, factory, factoryConsumer)) {
                violations.add(file + ": " + call);
            }
        }
    }

    private boolean usesImplicitTime(MethodCallExpr call, Set<String> dateTypes) {
        String scope = call.getScope().map(Object::toString).orElse("");
        String name = call.getNameAsString();
        return dateTypes.contains(scope) && name.equals("now") && call.getArguments().isEmpty()
                || scope.equals("ZoneId") && name.equals("systemDefault")
                || scope.equals("System") && name.equals("currentTimeMillis");
    }

    private boolean createsIndependentClock(MethodCallExpr call, boolean factory, boolean factoryConsumer) {
        String scope = call.getScope().map(Object::toString).orElse("");
        return scope.equals("Clock") && call.getNameAsString().startsWith("system") && !factory
                || scope.equals("ApplicationClockFactory") && call.getNameAsString().equals("systemClock") && !factoryConsumer;
    }

    private void checkCron(Path file, CompilationUnit unit, List<String> violations) {
        for (NormalAnnotationExpr annotation : unit.findAll(NormalAnnotationExpr.class)) {
            if (annotation.getNameAsString().equals("Scheduled")
                    && annotation.getPairs().stream().anyMatch(pair -> pair.getNameAsString().equals("cron"))
                    && annotation.getPairs().stream().noneMatch(pair -> pair.getNameAsString().equals("zone"))) {
                violations.add(file + ": Cron timezone missing");
            }
        }
    }
}
