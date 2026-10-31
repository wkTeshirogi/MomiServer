package jp.xoki.web.zunda.smarthomewebserverbackend.common;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

/** 手書きの実装がRepositoryとjOOQ生成定義を使用し続けることを確認する。 */
class JooqDataAccessPolicyTest {
    @Test
    void productionDatabaseAccessUsesGeneratedJooqQueries() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
                Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            assertThat(violations(compiler, manager, manager.getJavaFileObjectsFromPaths(
                    files.filter(path -> path.toString().endsWith(".java")).toList()))).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "package sample.repository; class Sample { void run() { dsl.execute(sql); } }",
            "package sample.repository; class Sample { void run() { DSL.field(name(\"status\"), String.class); } }",
            "package sample.repository; class Sample { void run() { table(name(\"items\")); } }",
            "package sample.repository; import org.springframework.jdbc.core.JdbcTemplate; class Sample {}",
            "package sample.service; import org.jooq.DSLContext; class Sample {}",
            "package sample.repository; class Sample { void run() { query.where(\"id = ?\", id); } }"
    })
    void detectsAlternativeSqlPaths(String source) throws Exception {
        assertThat(inspect(source)).isNotEmpty();
    }

    @Test
    void commentsSqlExamplesAndGeneratedTableFieldsAreNotViolations() throws Exception {
        assertThat(inspect("""
                package sample.repository;
                import org.jooq.DSLContext;
                class Sample {
                    // SQL note: select * from items
                    String note = "select * from items";
                    void run() {
                        String labels = String.join(",", "one", "two");
                        dsl.select(table.field(GENERATED.ID)).from(GENERATED).fetch();
                    }
                }
                """)).isEmpty();
    }

    private List<String> inspect(String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Sample.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            return violations(compiler, manager, List.of(file));
        }
    }

    private List<String> violations(JavaCompiler compiler, StandardJavaFileManager manager,
            Iterable<? extends JavaFileObject> sources) throws Exception {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, sources);
        List<String> violations = new ArrayList<>();
        for (CompilationUnitTree unit : task.parse()) {
            String name = unit.getSourceFile().getName();
            boolean repository = unit.getPackageName().toString().contains(".repository")
                    || name.endsWith("VehicleImportRepository.java");
            new TreePathScanner<Void, Void>() {
                @Override public Void visitImport(ImportTree tree, Void unused) {
                    String imported = tree.getQualifiedIdentifier().toString();
                    if (imported.contains(".jdbc.") || imported.startsWith("java.sql.")
                            || imported.startsWith("jakarta.persistence.")
                            || (!repository && imported.equals("org.jooq.DSLContext"))) {
                        violations.add(name + ": " + imported);
                    }
                    return super.visitImport(tree, unused);
                }

                @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                    String select = call.getMethodSelect().toString();
                    String method = call.getMethodSelect() instanceof MemberSelectTree member
                            ? member.getIdentifier().toString() : select;
                    boolean plainExecution = select.matches("(?:dsl|DSL|org\\.jooq\\.impl\\.DSL)\\."
                            + "(?:execute|query|resultQuery|fetch|fetchOne|fetchValue)");
                    boolean constructedMetadata = method.equals("table")
                            || (method.equals("field") && (select.equals("field") || select.endsWith("DSL.field")));
                    boolean literalSql = !call.getArguments().isEmpty()
                            && !Set.of("String.join", "java.lang.String.join").contains(select)
                            && call.getArguments().getFirst() instanceof LiteralTree literal
                            && literal.getValue() instanceof String
                            && Set.of("from", "join", "where", "and", "or", "condition", "sql").contains(method);
                    if (plainExecution || constructedMetadata || literalSql) violations.add(name + ": " + select);
                    return super.visitMethodInvocation(call, unused);
                }
            }.scan(unit, null);
        }
        assertThat(diagnostics.getDiagnostics()).noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
        return violations;
    }
}
