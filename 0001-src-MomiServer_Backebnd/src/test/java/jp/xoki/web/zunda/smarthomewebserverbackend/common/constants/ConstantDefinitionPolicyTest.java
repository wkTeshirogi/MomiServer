package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithPrivateModifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import jp.xoki.web.zunda.smarthomewebserverbackend.tools.constants.DesignDocGeneratorConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.lang.reflect.Field;
import java.util.stream.Stream;

class ConstantDefinitionPolicyTest {

	private static final Path SOURCE_ROOT = Path.of("src/main/java");
	private static final Path PACKAGE_ROOT = SOURCE_ROOT.resolve("jp/xoki/web/zunda/smarthomewebserverbackend");
	private static final Set<String> FIXED_TYPES = Set.of(
			"String", "int", "long", "short", "byte", "boolean", "double", "float", "char",
			"Pattern", "Set", "ZoneId", "String[][]");

	/** 定数クラスの物理配置と宣言パッケージがカテゴリ直下のconstantsに一致することを確認する。 */
	@Test
	void constantHoldersUseCategoryConstantsPackages() throws Exception {
		JavaParser parser = parser();
		try (Stream<Path> files = Files.walk(PACKAGE_ROOT)) {
			for (Path file : files.filter(this::isConstantHolder).toList()) {
				Path relative = PACKAGE_ROOT.relativize(file);
				assertThat(relative.getNameCount()).as(file.toString()).isEqualTo(3);
				assertThat(relative.getName(1)).as(file.toString()).hasToString("constants");
				CompilationUnit unit = parser.parse(file).getResult().orElseThrow();
				assertThat(unit.getPackageDeclaration().orElseThrow().getNameAsString())
						.as(file.toString()).isEqualTo(
								"jp.xoki.web.zunda.smarthomewebserverbackend." + relative.getName(0) + ".constants");
			}
		}
	}

	@Test
	void fixedDefinitionsUseDedicatedFiles() throws Exception {
		JavaParser parser = parser();
		List<String> misplaced = new ArrayList<>();
		try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String name = file.getFileName().toString();
				if (name.endsWith("Constants.java") || name.equals("ApiAccessLogAttributes.java")) {
					continue;
				}
				CompilationUnit unit = parser.parse(file).getResult().orElseThrow();
				for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
					String type = field.getCommonType().asString().replaceAll("<.*>", "");
					if (field.isStatic() && field.isFinal()
							&& FIXED_TYPES.contains(type)) {
						misplaced.add(file + ": " + field.getVariables());
					}
				}
			}
		}
		assertThat(misplaced).as("Fixed definitions outside dedicated files").isEmpty();
	}

	@Test
	void constantHoldersCannotBeInstantiatedOrExtended() throws Exception {
		JavaParser parser = parser();
		try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
			for (Path file : files.filter(this::isConstantHolder).toList()) {
				String name = file.getFileName().toString().replace(".java", "");
				CompilationUnit unit = parser.parse(file).getResult().orElseThrow();
				ClassOrInterfaceDeclaration holder = unit.getClassByName(name).orElseThrow();
				assertThat(holder.isFinal()).as(name + " must be final").isTrue();
				assertThat(holder.isPublic()).as(name + " is referenced from other packages").isTrue();
				assertThat(holder.getConstructors()).as(name + " requires a private constructor")
						.hasSize(1).allMatch(NodeWithPrivateModifier::isPrivate);
			}
		}
	}

	/** 別パッケージへ公開する値が書換え可能な配列・コレクションではないことを確認する。 */
	@Test
	void publicConstantsCannotBeMutated() throws Exception {
		try (Stream<Path> files = Files.walk(PACKAGE_ROOT)) {
			for (Path file : files.filter(this::isConstantHolder).toList()) {
				String className = PACKAGE_ROOT.relativize(file).toString()
						.replace('\\', '.').replace('/', '.').replace(".java", "");
				Class<?> holder = Class.forName("jp.xoki.web.zunda.smarthomewebserverbackend." + className);
				for (Field field : holder.getFields()) {
					assertThat(java.lang.reflect.Modifier.isStatic(field.getModifiers())).as(field.toString()).isTrue();
					assertThat(java.lang.reflect.Modifier.isFinal(field.getModifiers())).as(field.toString()).isTrue();
					assertThat(field.getType().isArray()).as(field.toString()).isFalse();
					if (field.get(null) instanceof Collection<?> values) {
						assertThatThrownBy(values::clear).as(field.toString()).isInstanceOf(UnsupportedOperationException.class);
					}
				}
			}
		}
		for (List<String> layer : DesignDocGeneratorConstants.LAYERS) {
			assertThatThrownBy(layer::clear).isInstanceOf(UnsupportedOperationException.class);
		}
	}

	/** 配置変更でログ連携の既存属性名や設計書生成の対象・順序を変更しない。 */
	@Test
	void movedConstantsPreserveExistingContracts() {
		assertThat(ApiAccessLogAttributes.REQUEST_ID).isEqualTo(
				"jp.xoki.web.zunda.smarthomewebserverbackend.common.logging.ApiAccessLogAttributes.requestId");
		assertThat(ApiAccessLogAttributes.ERROR_CODE).isEqualTo(
				"jp.xoki.web.zunda.smarthomewebserverbackend.common.logging.ApiAccessLogAttributes.errorCode");
		assertThat(DesignDocGeneratorConstants.LAYERS).containsExactly(
				List.of("auth", "auth"), List.of("batch", "batch"), List.of("common", "common"),
				List.of("dashboard", "dashboard"), List.of("device", "device"), List.of("user", "user"));
	}

	private boolean isConstantHolder(Path path) {
		String name = path.getFileName().toString();
		return name.endsWith("Constants.java") || name.equals("ApiAccessLogAttributes.java");
	}

	private JavaParser parser() {
		return new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
	}
}
