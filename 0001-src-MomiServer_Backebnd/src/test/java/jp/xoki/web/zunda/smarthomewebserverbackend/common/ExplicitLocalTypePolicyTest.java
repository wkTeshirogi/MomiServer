package jp.xoki.web.zunda.smarthomewebserverbackend.common;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** JDKと同じ構文解析で型推論の再追加を防ぎ、文字列・コメントは対象外にする。 */
class ExplicitLocalTypePolicyTest {

    @Test
    void mainAndTestSourcesDeclareLocalTypesExplicitly() throws Exception {
        List<Path> sources = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                sources.addAll(files.filter(path -> path.toString().endsWith(".java")).toList());
            }
        }
        JavaCompiler compiler = compiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            assertThat(inferredDeclarations(compiler, manager, manager.getJavaFileObjectsFromPaths(sources)))
                    .as("Java local types must be explicit; generated sources are excluded").isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Sample { void run() { var value = \"value\"; } }",
            "class Sample { void run() { for (var value : java.util.List.of(\"value\")) {} } }",
            "class Sample { void run() throws Exception { try (var input = java.nio.file.Files.walk(java.nio.file.Path.of(\".\"))) {} } }",
            "class Sample { java.util.function.Function<String, String> transform = (var value) -> value; }",
            "record Source(String value) {} class Sample { boolean match(Object value) { return value instanceof Source(var text); } }"
    })
    void inferredDeclarationsAreRecognizedInEverySupportedContext(String source) throws Exception {
        assertThat(inferredDeclarations(source)).hasSize(1);
    }

    @Test
    void wordsInCommentsAndStringLiteralsAreNotTypeInference() throws Exception {
        assertThat(inferredDeclarations(
                "class Sample { /* var is not a declaration */ String value = \"var\"; }"))
                .isEmpty();
    }

    private JavaCompiler compiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK is required to verify Java syntax").isNotNull();
        return compiler;
    }

    private List<String> inferredDeclarations(String source) throws Exception {
        JavaCompiler compiler = compiler();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Sample.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            return inferredDeclarations(compiler, manager, List.of(file));
        }
    }

    private List<String> inferredDeclarations(JavaCompiler compiler, StandardJavaFileManager manager,
            Iterable<? extends JavaFileObject> sources) throws Exception {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // 型解決や注釈処理は実行せず、生成コード・外部依存に影響されない構文だけを検査する。
        JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, sources);
        Trees trees = Trees.instance(task);
        List<String> violations = new ArrayList<>();
        for (CompilationUnitTree unit : task.parse()) {
            String text = unit.getSourceFile().getCharContent(true).toString();
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitVariable(VariableTree node, Void unused) {
                    if (node.getType() == null || "var".equals(node.getType().toString())) {
                        long start = trees.getSourcePositions().getStartPosition(unit, node);
                        long end = trees.getSourcePositions().getEndPosition(unit, node);
                        if (start >= 0 && end >= start) {
                            Matcher declaration = Pattern.compile("\\bvar\\b(?=\\s+"
                                    + Pattern.quote(node.getName().toString()) + "\\b)")
                                    .matcher(text.substring((int) start, (int) end));
                            if (declaration.find()) {
                                violations.add(unit.getSourceFile().getName() + ":"
                                        + unit.getLineMap().getLineNumber(start + declaration.start()));
                            }
                        }
                    }
                    return super.visitVariable(node, unused);
                }
            }.scan(unit, null);
        }
        assertThat(diagnostics.getDiagnostics()).as("Source syntax must be valid")
                .noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
        return violations;
    }
}
