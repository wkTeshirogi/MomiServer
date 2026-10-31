package jp.xoki.web.zunda.smarthomewebserverbackend.tools;

import static jp.xoki.web.zunda.smarthomewebserverbackend.tools.constants.DesignDocGeneratorConstants.TABLE_CODE_CELL_END;


import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static jp.xoki.web.zunda.smarthomewebserverbackend.tools.constants.DesignDocGeneratorConstants.BASE_PACKAGE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.tools.constants.DesignDocGeneratorConstants.LAYERS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.tools.constants.DesignDocGeneratorConstants.OUTPUT_ROOT;
import static jp.xoki.web.zunda.smarthomewebserverbackend.tools.constants.DesignDocGeneratorConstants.SOURCE_ROOT;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

/** JavaソースからMarkdown形式の詳細設計書を生成する開発支援ツール。 */
public class DesignDocGenerator {
	private static final Logger LOGGER = LoggerFactory.getLogger(DesignDocGenerator.class);

	// ────────────────────────────────────────────────
	// 設定


	// ────────────────────────────────────────────────
	// エントリーポイント
	// ────────────────────────────────────────────────
	/**
	 * 対象レイヤーのJavaファイルを解析し、クラス別設計書と索引を生成する。
	 *
	 * @throws IOException ファイル読み書きに失敗した場合
	 */
	public static void main() throws IOException {
		StaticJavaParser.getParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

		Path sourceRoot = Paths.get(SOURCE_ROOT);
		Path outputRoot = Paths.get(OUTPUT_ROOT);
		Files.createDirectories(outputRoot);

		List<String> indexEntries = new ArrayList<>();
		String today = LocalDate.now(ApplicationClockFactory.systemClock()).toString();

		for (List<String> layer : LAYERS) {
			String layerName  = layer.get(0);
			String outputDir  = layer.get(1);
			Path layerPath    = sourceRoot.resolve(BASE_PACKAGE).resolve(layerName);

			if (!Files.exists(layerPath)) continue;

			Path outputLayerDir = outputRoot.resolve(outputDir);
			Files.createDirectories(outputLayerDir);

			try (Stream<Path> files = Files.walk(layerPath)) {
				files.filter(path -> path.toString().endsWith(".java"))
						.forEach(path -> generateSourceDoc(path, outputLayerDir, outputDir, layerName, today, indexEntries));
			}
		}

		// index.md 生成
		generateIndex(outputRoot, indexEntries, today);
		LOGGER.info("[complete] docs/design/ generated");
	}

	private static void generateSourceDoc(Path source, Path outputLayerDir, String outputDir,
			String layerName, String today, List<String> indexEntries) {
		try {
			CompilationUnit unit = StaticJavaParser.parse(source);
			Optional<ClassOrInterfaceDeclaration> declaration = unit.findFirst(ClassOrInterfaceDeclaration.class);
			if (declaration.isEmpty()) {
				return;
			}
			ClassOrInterfaceDeclaration clazz = declaration.get();
			String fileName = clazz.getNameAsString() + ".md";
			Path outPath = outputLayerDir.resolve(fileName);
			Files.writeString(outPath, generateClassDoc(clazz, unit, today));
			LOGGER.info("[generate] {}", outPath);
			indexEntries.add(String.format("- [%s](%s/%s) _%s_",
					clazz.getNameAsString(), outputDir, fileName, layerName));
		} catch (IOException exception) {
			LOGGER.warn("[skip] {}: {}", source, exception.getMessage());
		}
	}

	// ────────────────────────────────────────────────
	// クラス単位のドキュメント生成
	// ────────────────────────────────────────────────
	/**
	 * JavaParserで解析したクラス情報からクラス単位のMarkdown本文を生成する。
	 *
	 * @param clazz 解析対象クラス
	 * @param cu コンパイル単位
	 * @param today 生成日
	 * @return クラス設計書のMarkdown本文
	 */
	private static String generateClassDoc(
			ClassOrInterfaceDeclaration clazz,
			CompilationUnit cu,
			String today) {

		StringBuilder sb = new StringBuilder();
		String className = clazz.getNameAsString();

		// ── ヘッダー ──
		sb.append("# ").append(className).append("\n\n");
		sb.append("> 自動生成日: ").append(today).append("\n\n");

		// ── Javadoc ──
		clazz.getJavadocComment().ifPresent(doc ->
				sb.append(cleanJavadoc(doc.getContent())).append("\n\n")
		);

		// ── クラス概要テーブル ──
		sb.append("## クラス概要\n\n");
		sb.append("| 項目 | 内容 |\n");
		sb.append("|---|---|\n");

		// パッケージ
		cu.getPackageDeclaration().ifPresent(pkg ->
				sb.append("| パッケージ | `").append(pkg.getNameAsString()).append(TABLE_CODE_CELL_END)
		);

		// クラス種別
		sb.append("| 種別 | ").append(clazz.isInterface() ? "Interface" : "Class").append(" |\n");

		// アノテーション
		List<AnnotationExpr> annotations = clazz.getAnnotations();
		if (!annotations.isEmpty()) {
			sb.append("| アノテーション | ");
			annotations.forEach(a -> sb.append("`@").append(a.getNameAsString()).append("` "));
			sb.append("|\n");
		}

		// Spring Web: @RequestMapping のベースパス
		getAnnotationValue(clazz.getAnnotations(), "RequestMapping").ifPresent(path ->
				sb.append("| ベースパス | `").append(path).append(TABLE_CODE_CELL_END)
		);

		// Spring Security: @PreAuthorize
		getAnnotationValue(clazz.getAnnotations(), "PreAuthorize").ifPresent(auth ->
				sb.append("| 権限 | `").append(auth).append(TABLE_CODE_CELL_END)
		);

		sb.append("\n");

		// ── フィールド（依存関係） ──
		List<FieldDeclaration> fields = clazz.getFields();
		if (!fields.isEmpty()) {
			sb.append("## 依存関係\n\n");
			sb.append("| フィールド名 | 型 | 説明 |\n");
			sb.append("|---|---|---|\n");
			fields.forEach(field -> {
				String type    = field.getElementType().asString();
				String name    = field.getVariables().get(0).getNameAsString();
				String comment = field.getJavadocComment()
						.map(d -> cleanJavadoc(d.getContent()).trim())
						.orElse("");
				sb.append("| `").append(name).append("` | `").append(type).append("` | ")
						.append(comment).append(" |\n");
			});
			sb.append("\n");
		}

		// ── メソッド一覧 ──
		List<MethodDeclaration> methods = clazz.getMethods();
		if (!methods.isEmpty()) {
			sb.append("## メソッド一覧\n\n");
			methods.forEach(method -> generateMethodDoc(sb, method));
		}

		return sb.toString();
	}

	// ────────────────────────────────────────────────
	// メソッド単位のドキュメント生成
	// ────────────────────────────────────────────────
	/**
	 * メソッド情報をMarkdown本文へ追記する。
	 *
	 * @param sb 出力先のMarkdownバッファ
	 * @param method 解析対象メソッド
	 */
	private static void generateMethodDoc(StringBuilder sb, MethodDeclaration method) {
		String methodName = method.getNameAsString();
		sb.append("### ").append(methodName).append("()\n\n");

		// Javadoc
		method.getJavadocComment().ifPresent(doc ->
				sb.append(cleanJavadoc(doc.getContent())).append("\n\n")
		);

		sb.append("| 項目 | 内容 |\n");
		sb.append("|---|---|\n");

		// HTTP メソッド・パス（Controller層）
		List<AnnotationExpr> annotations = method.getAnnotations();
		for (String httpMethod : List.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping")) {
			if (hasAnnotation(annotations, httpMethod)) {
				sb.append("| HTTPメソッド | `").append(httpMethod.replace("Mapping", "").toUpperCase()).append(TABLE_CODE_CELL_END);
				getAnnotationValue(annotations, httpMethod).ifPresent(path ->
						sb.append("| パス | `").append(path).append(TABLE_CODE_CELL_END)
				);
				break;
			}
		}

		// 戻り値
		sb.append("| 戻り値 | `").append(method.getType().asString()).append(TABLE_CODE_CELL_END);

		// アクセス修飾子
		sb.append("| アクセス修飾子 | `").append(method.getAccessSpecifier().asString()).append(TABLE_CODE_CELL_END);

		// 引数
		if (!method.getParameters().isEmpty()) {
			sb.append("| 引数 | ");
			method.getParameters().forEach(p ->
					sb.append("`").append(p.getType().asString()).append(" ").append(p.getNameAsString()).append("` ")
			);
			sb.append("|\n");
		}

		// @PreAuthorize
		getAnnotationValue(annotations, "PreAuthorize").ifPresent(auth ->
				sb.append("| 権限 | `").append(auth).append(TABLE_CODE_CELL_END)
		);

		// @Scheduled
		getAnnotationValue(annotations, "Scheduled").ifPresent(cron ->
				sb.append("| スケジュール | `").append(cron).append(TABLE_CODE_CELL_END)
		);

		sb.append("\n");
	}

	// ────────────────────────────────────────────────
	// index.md 生成
	// ────────────────────────────────────────────────
	/**
	 * 生成した設計書へのリンクをまとめた索引ファイルを出力する。
	 *
	 * @param outputRoot 出力ルートディレクトリ
	 * @param entries 索引に載せるリンク一覧
	 * @param today 生成日
	 * @throws IOException ファイル書き込みに失敗した場合
	 */
	private static void generateIndex(
			Path outputRoot,
			List<String> entries,
			String today) throws IOException {

		StringBuilder sb = new StringBuilder();
		sb.append("# 詳細設計書\n\n");
		sb.append("> 自動生成日: ").append(today).append("\n\n");
		sb.append("## クラス一覧\n\n");
		entries.forEach(e -> sb.append(e).append("\n"));

		Files.writeString(outputRoot.resolve("index.md"), sb.toString());
		LOGGER.info("[生成] {}", outputRoot.resolve("index.md"));
	}

	// ────────────────────────────────────────────────
	// ユーティリティ
	// ────────────────────────────────────────────────

	/**
	 * アノテーションの単一値またはvalue属性を文字列として取得する。
	 *
	 * @param annotations 検索対象のアノテーション一覧
	 * @param name アノテーション名
	 * @return アノテーション値
	 */
	private static Optional<String> getAnnotationValue(
			List<AnnotationExpr> annotations, String annotationName) {

		for (AnnotationExpr ann : annotations) {
			if (!ann.getNameAsString().equals(annotationName)) continue;

			if (ann instanceof SingleMemberAnnotationExpr sma) {
				return Optional.of(sma.getMemberValue().toString().replace("\"", ""));
			}
			if (ann instanceof NormalAnnotationExpr na) {
				return na.getPairs().stream()
						.filter(p -> p.getNameAsString().equals("value")
								|| p.getNameAsString().equals("cron"))
						.findFirst()
						.map(MemberValuePair::getValue)
						.map(v -> v.toString().replace("\"", ""));
			}
		}
		return Optional.empty();
	}

	/**
	 * 指定名のアノテーションが付与されているか判定する。
	 *
	 * @param annotations 検索対象のアノテーション一覧
	 * @param name アノテーション名
	 * @return 指定アノテーションが存在する場合はtrue
	 */
	private static boolean hasAnnotation(List<AnnotationExpr> annotations, String name) {
		return annotations.stream().anyMatch(a -> a.getNameAsString().equals(name));
	}

	/**
	 * Javadoc本文の余分な空白とアスタリスクをMarkdown向けに整形する。
	 *
	 * @param content Javadoc本文
	 * @return 整形後の文字列
	 */
	private static String cleanJavadoc(String content) {
		return content.lines()
				.map(line -> line.replaceAll("^\\s*\\*\\s?", "").trim())
				.filter(line -> !line.startsWith("@"))
				.filter(line -> !line.isBlank())
				.reduce("", (a, b) -> a + (a.isEmpty() ? "" : " ") + b);
	}
}
