package jp.xoki.web.zunda.smarthomewebserverbackend.common.message;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Formattable;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.DuplicateEmailException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedMessageConstantsTest {

	@Test
	void everyGeneratedConstantMatchesItsSingleCatalogDefinitionAndCategory() throws Exception {
		ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
		Map<Class<?>, Set<String>> owners = new HashMap<>();
		try (InputStream input = getClass().getResourceAsStream("/message-catalog/messages.ja.json")) {
			assertThat(input).isNotNull();
			JsonNode messages = mapper.readTree(input).path("messages");
			assertThat(messages.isObject()).isTrue();
			for (Map.Entry<String, JsonNode> entry : messages.properties()) {
				JsonNode definition = entry.getValue();
				assertThat(definition.path("text").isTextual()).as(entry.getKey()).isTrue();
				assertThat(definition.path("visibility").asText()).isIn("public", "private");
				if (!definition.has("java")) continue;
				String category = definition.path("java").path("category").asText();
				String name = definition.path("java").path("name").asText();
				String className = category.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + category.substring(1) + "MessageConstants";
				String ownerName = "jp.xoki.web.zunda.smarthomewebserverbackend." + category + ".constants." + className;
				Class<?> owner = Class.forName(ownerName);
				Field field = owner.getField(name);
				assertThat(field.getType()).isEqualTo(String.class);
				assertThat(Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
						&& Modifier.isFinal(field.getModifiers())).isTrue();
				assertThat(field.get(null)).as(entry.getKey()).isEqualTo(definition.path("text").asText());
				assertThat(owners.computeIfAbsent(owner, ignored -> new HashSet<>()).add(name)).isTrue();
				Path source = Path.of("build/generated/message-catalog", ownerName.replace('.', '/') + ".java");
				assertThat(Files.readString(source)).startsWith("// Generated from message-catalog/messages.ja.json. Do not edit.");
			}
		}
		assertThat(owners).isNotEmpty();
		for (Map.Entry<Class<?>, Set<String>> entry : owners.entrySet()) {
			Class<?> owner = entry.getKey();
			assertThat(Modifier.isFinal(owner.getModifiers())).isTrue();
			assertThat(owner.getDeclaredFields()).hasSize(entry.getValue().size());
			assertThat(owner.getDeclaredConstructors()).hasSize(1);
			assertThat(Modifier.isPrivate(owner.getDeclaredConstructors()[0].getModifiers())).isTrue();
		}
	}

	@Test
	void validationStillUsesTheSameTextWithTheStandardFactory() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new VehicleSaveRequest()))
					.extracting(jakarta.validation.ConstraintViolation::getMessage)
					.contains("表示名は必須です", "登録番号は必須です", "車台番号は必須です", "車検有効期間満了日は必須です");
		}
	}

	@Test
	void allLegacyMessageCodesStillResolve() {
		MessageCatalog catalog = new MessageCatalog();
		for (MessageCode code : MessageCode.values()) {
			assertThat(catalog.get(code)).isNotBlank().isNotEqualTo(code.getKey());
		}
	}

	@Test
	void formattedErrorsPreservePercentSignsNullsAndObjectStringConversion() {
		assertThat(new ResourceNotFoundException("100%ユーザー", null))
				.hasMessage("100%ユーザーが見つかりません。id=null");
		assertThat(new DuplicateEmailException("100%@example.com"))
				.hasMessage("既に登録されているメールアドレスです: 100%@example.com");
		Object id = new Formattable() {
			@Override
			public void formatTo(Formatter formatter, int flags, int width, int precision) {
				throw new AssertionError("Resource IDs must retain their original toString conversion");
			}

			@Override
			public String toString() {
				return "id%value";
			}
		};
		assertThat(new ResourceNotFoundException("Resource", id)).hasMessage("Resourceが見つかりません。id=id%value");
	}
}
