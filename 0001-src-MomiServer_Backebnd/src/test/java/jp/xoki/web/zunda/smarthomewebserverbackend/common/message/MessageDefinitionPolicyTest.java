package jp.xoki.web.zunda.smarthomewebserverbackend.common.message;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

/** APIエラー・通知・入力検証への日本語文言の再追加を検知する。 */
class MessageDefinitionPolicyTest {

	@Test
	void handwrittenUserMessagesUseTheCatalog() throws Exception {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertThat(compiler).isNotNull();
		try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
				Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			assertThat(findInlineMessages(compiler, manager, manager.getJavaFileObjectsFromPaths(
					files.filter(file -> file.toString().endsWith(".java")).toList())))
					.as("Use the catalog for validation, API errors and notification text").isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"class Sample { void run() { throw new IllegalArgumentException(\"直書き\"); } }",
			"class Sample { @NotBlank(message = \"直書き\") String value; }",
			"class Sample { void run() { notification.title(\"直書き\"); } }",
			"class Sample { void run() { notification.message(\"直書き\"); } }",
			"class Sample { void run() { warnings.add(\"直書き\"); } }",
			"class Sample { void run() { response(\"直書き\"); } }"
	})
	void detectsInlineMessageContexts(String source) throws Exception {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Sample.java"), JavaFileObject.Kind.SOURCE) {
			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return source;
			}
		};
		try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
			assertThat(findInlineMessages(compiler, manager, List.of(file))).hasSize(1);
		}
	}

	private List<String> findInlineMessages(JavaCompiler compiler, StandardJavaFileManager manager,
			Iterable<? extends JavaFileObject> sources) throws Exception {
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, sources);
		Trees trees = Trees.instance(task);
		List<String> violations = new ArrayList<>();
		for (CompilationUnitTree unit : task.parse()) {
			new TreePathScanner<Void, Void>() {
				@Override
				public Void visitLiteral(LiteralTree literal, Void unused) {
					if (literal.getValue() instanceof String text && text.codePoints().anyMatch(code ->
							Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN
							|| Character.UnicodeScript.of(code) == Character.UnicodeScript.HIRAGANA
							|| Character.UnicodeScript.of(code) == Character.UnicodeScript.KATAKANA)
							&& isMessageContext(getCurrentPath())) {
						violations.add(unit.getSourceFile().getName() + ":" + unit.getLineMap().getLineNumber(
								trees.getSourcePositions().getStartPosition(unit, literal)));
					}
					return super.visitLiteral(literal, unused);
				}
			}.scan(unit, null);
		}
		assertThat(diagnostics.getDiagnostics()).noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
		return violations;
	}

	private boolean isMessageContext(TreePath path) {
		boolean annotationMessage = false;
		for (TreePath current = path.getParentPath(); current != null; current = current.getParentPath()) {
			if (current.getLeaf() instanceof AssignmentTree assignment && assignment.getVariable().toString().equals("message")) {
				annotationMessage = true;
			}
			if (current.getLeaf() instanceof AnnotationTree) return annotationMessage;
			if (current.getLeaf() instanceof NewClassTree constructor && constructor.getIdentifier().toString().endsWith("Exception")) {
				return true;
			}
			if (current.getLeaf() instanceof MethodInvocationTree call) {
				String method = call.getMethodSelect().toString();
				if (method.endsWith(".title") || method.endsWith(".message") || method.endsWith(".write")
						|| method.equals("warnings.add") || method.equals("response") || method.equals("notifyWarning")) return true;
			}
		}
		return false;
	}
}
