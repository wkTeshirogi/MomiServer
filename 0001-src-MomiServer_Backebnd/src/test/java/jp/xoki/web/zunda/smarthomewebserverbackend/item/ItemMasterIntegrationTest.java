package jp.xoki.web.zunda.smarthomewebserverbackend.item;

import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false", "app.vehicle-inspection-notification.enabled=false"})
@Transactional
class ItemMasterIntegrationTest {
    @Autowired ItemMasterService items;

    @Test
    void creationAndUpdateRoundTripEveryInventoryMasterField() {
        ItemMasterRegistRequest request = ItemMasterRegistRequest.builder().itemName("Test battery").category("supplies")
                .unit("piece").minQuantity(2).manufacturer("Test manufacturer").modelNumber("BAT-1")
                .janCode("4900000000001").purchaseUrl("https://example.invalid/battery")
                .status("active").memo("initial memo").build();
        ItemMasterDetailDto created = items.save(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertItem(created, request);
        assertThat(items.detail(created.getId())).usingRecursiveComparison().isEqualTo(created);
        assertThat(items.list()).extracting(ItemMasterDetailDto::getId).contains(created.getId());

        request.setId(created.getId());
        request.setItemName("Updated battery");
        request.setCategory("spares");
        request.setUnit("pack");
        request.setMinQuantity(0);
        request.setManufacturer("Updated manufacturer");
        request.setModelNumber("BAT-2");
        request.setJanCode("4900000000002");
        request.setPurchaseUrl(null);
        request.setStatus("disabled");
        request.setMemo(null);
        ItemMasterDetailDto updated = items.save(request);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertItem(updated, request);
        assertThat(items.detail(created.getId())).usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void missingItemCannotBeRetrievedOrUpdated() {
        ItemMasterRegistRequest request = ItemMasterRegistRequest.builder().id(Long.MAX_VALUE).itemName("Missing item")
                .category("test").unit("piece").minQuantity(1).status("active").build();
        assertThatThrownBy(() -> items.detail(Long.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Item not found");
        assertThatThrownBy(() -> items.save(request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Item not found");
    }

    private void assertItem(ItemMasterDetailDto actual, ItemMasterRegistRequest expected) {
        assertThat(actual).usingRecursiveComparison().comparingOnlyFields("itemName", "category",
                "unit", "minQuantity", "manufacturer", "modelNumber", "janCode", "purchaseUrl", "status", "memo")
                .isEqualTo(expected);
    }
}
