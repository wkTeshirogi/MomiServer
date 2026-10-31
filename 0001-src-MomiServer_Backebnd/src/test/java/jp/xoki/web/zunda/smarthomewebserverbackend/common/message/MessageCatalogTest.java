package jp.xoki.web.zunda.smarthomewebserverbackend.common.message;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageCatalogTest {

	private final MessageCatalog messageCatalog = new MessageCatalog();

	@Test
	void getReturnsMessageByCode() {
		assertThat(messageCatalog.get(MessageCode.INVENTORY_LOW_TITLE))
				.isEqualTo("在庫が少なくなっています");
	}

	@Test
	void formatReplacesPlaceholdersByValues() {
		assertThat(messageCatalog.format(MessageCode.INVENTORY_LOW_MESSAGE, Map.of(
				"itemId", 10,
				"quantity", 5)))
				.isEqualTo("在庫品目ID 10 の現在数量は 5 です。");
	}

	@Test
	void fromKeyReturnsMessageCode() {
		assertThat(MessageCode.fromKey("inventory.low.title"))
				.contains(MessageCode.INVENTORY_LOW_TITLE);
	}
}
