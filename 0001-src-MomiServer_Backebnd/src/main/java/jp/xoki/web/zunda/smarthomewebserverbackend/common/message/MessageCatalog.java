package jp.xoki.web.zunda.smarthomewebserverbackend.common.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.MessageCatalogConstants.CATALOG_PATH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.MessageCatalogConstants.PLACEHOLDER_PREFIX;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.MessageCatalogConstants.PLACEHOLDER_SUFFIX;

/** バックエンド内で共通メッセージを解決するカタログ。 */
@Component
public final class MessageCatalog {




	/** 共通メッセージ定義。 */
	private final Map<MessageCode, String> messages;

	/**
	 * 共通メッセージカタログをクラスパスから読み込む。
	 */
	public MessageCatalog() {
		this.messages = loadMessages();
	}

	/**
	 * メッセージコードに対応するメッセージを返す。
	 *
	 * @param code メッセージコード
	 * @return メッセージ本文
	 */
	public String get(MessageCode code) {
		return messages.getOrDefault(code, code.getKey());
	}

	/**
	 * メッセージコードに対応するメッセージを取得し、プレースホルダーを置換する。
	 *
	 * @param code メッセージコード
	 * @param values プレースホルダーへ埋め込む値
	 * @return 置換後のメッセージ本文
	 */
	public String format(MessageCode code, Map<String, ?> values) {
		String message = get(code);

		if (values == null || values.isEmpty()) {
			return message;
		}

		for (Map.Entry<String, ?> entry : values.entrySet()) {
			String placeholder = PLACEHOLDER_PREFIX + entry.getKey() + PLACEHOLDER_SUFFIX;
			message = message.replace(placeholder, String.valueOf(entry.getValue()));
		}

		return message;
	}

	/**
	 * JSON形式の共通メッセージカタログを読み込む。
	 *
	 * @return メッセージコードをキーにしたメッセージ定義
	 */
	private Map<MessageCode, String> loadMessages() {
		ObjectMapper objectMapper = new ObjectMapper();
		Map<MessageCode, String> loadedMessages = new EnumMap<>(MessageCode.class);
		ClassPathResource resource = new ClassPathResource(CATALOG_PATH);

		try (InputStream inputStream = resource.getInputStream()) {
			JsonNode messagesNode = objectMapper.readTree(inputStream).path("messages");

			for (Map.Entry<String, JsonNode> field : messagesNode.properties()) {
				MessageCode.fromKey(field.getKey())
						.ifPresent(code -> loadedMessages.put(code, field.getValue().path("text").asText()));
			}

			return Map.copyOf(loadedMessages);
		} catch (IOException exception) {
			throw new UncheckedIOException("Failed to load message catalog: " + CATALOG_PATH, exception);
		}
	}
}
