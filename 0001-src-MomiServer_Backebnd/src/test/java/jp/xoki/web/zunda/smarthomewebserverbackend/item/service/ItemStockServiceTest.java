package jp.xoki.web.zunda.smarthomewebserverbackend.item.service;

import jp.xoki.web.zunda.jooq.tables.records.TInventoryStockHistoriesRecord;
import jp.xoki.web.zunda.jooq.tables.records.TInventoryStocksRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.message.MessageCatalog;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.util.Notifications;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.repository.ItemStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemStockServiceTest {

	@Mock
	private ItemStockRepository itemStockRepository;

	@Mock
	private Notifications notifications;

	@Spy
	private MessageCatalog messageCatalog;

	@InjectMocks
	private ItemStockService itemStockService;

	@ParameterizedTest
	@CsvSource({
			"0, 2, AVAILABLE, OUT_OF_STOCK, true",
			"2, 2, AVAILABLE, LOW, true",
			"3, 2, LOW, AVAILABLE, false",
			"3, 2, DELETED, DELETED, false"
	})
	void saveDerivesInventoryStatusFromQuantityAndMinimum(
			int quantity,
			int minimumQuantity,
			InventoryStatus requestedStatus,
			InventoryStatus expectedStatus,
			boolean notificationExpected
	) {
		ItemStockRegistRequest request = ItemStockRegistRequest.builder()
				.inventoryItemId(10L)
				.storageLocation("食品棚")
				.quantity(quantity)
				.inventoryStatus(requestedStatus)
				.build();
		TInventoryStocksRecord saved = stockRecord(1L, 10L, quantity, expectedStatus);
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(minimumQuantity);
		when(itemStockRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
		when(itemStockRepository.findById(1L)).thenReturn(Optional.of(saved));

		itemStockService.save(request);

		ArgumentCaptor<TInventoryStocksRecord> databaseRecord = ArgumentCaptor.forClass(TInventoryStocksRecord.class);
		verify(itemStockRepository).save(databaseRecord.capture());
		assertThat(databaseRecord.getValue().getInventoryStatus()).isEqualTo(expectedStatus.getStoredName());
		if (notificationExpected) {
			verify(notifications).sendNotice(org.mockito.ArgumentMatchers.any());
		} else {
			verify(notifications, never()).sendNotice(org.mockito.ArgumentMatchers.any());
		}
	}

	@Test
	void saveUpdateNotifiesWhenStockBecomesOutOfStock() {
		ItemStockRegistRequest request = ItemStockRegistRequest.builder()
				.id(1L)
				.inventoryItemId(10L)
				.storageLocation("食品棚")
				.quantity(0)
				.inventoryStatus(InventoryStatus.AVAILABLE)
				.build();
		TInventoryStocksRecord before = stockRecord(1L, 10L, 3, InventoryStatus.AVAILABLE);
		TInventoryStocksRecord after = stockRecord(1L, 10L, 0, InventoryStatus.OUT_OF_STOCK);
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(2);
		when(itemStockRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
		when(itemStockRepository.update(org.mockito.ArgumentMatchers.any())).thenReturn(1);
		when(itemStockRepository.findById(1L)).thenReturn(Optional.of(after));

		itemStockService.save(request);

		ArgumentCaptor<BrowserNotificationCreateDto> notification =
				ArgumentCaptor.forClass(BrowserNotificationCreateDto.class);
		verify(notifications).sendNotice(notification.capture());
		assertThat(notification.getValue().getNotificationType()).isEqualTo(NotificationType.INVENTORY_LOW);
		assertThat(notification.getValue().getLevel()).isEqualTo(NotificationLevel.CRITICAL);
		assertThat(notification.getValue().getSourceId()).isEqualTo("10");
	}

	@Test
	void saveUpdateDoesNotNotifyWhenWarningStatusIsUnchanged() {
		ItemStockRegistRequest request = ItemStockRegistRequest.builder()
				.id(1L)
				.inventoryItemId(10L)
				.storageLocation("食品棚")
				.quantity(1)
				.inventoryStatus(InventoryStatus.LOW)
				.build();
		TInventoryStocksRecord before = stockRecord(1L, 10L, 2, InventoryStatus.LOW);
		TInventoryStocksRecord after = stockRecord(1L, 10L, 1, InventoryStatus.LOW);
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(2);
		when(itemStockRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
		when(itemStockRepository.update(org.mockito.ArgumentMatchers.any())).thenReturn(1);
		when(itemStockRepository.findById(1L)).thenReturn(Optional.of(after));

		itemStockService.save(request);

		verify(notifications, never()).sendNotice(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void operateInboundIncreasesQuantityAndWritesHistory() {
		TInventoryStocksRecord before = stockRecord(1L, 10L, 2, InventoryStatus.LOW);
		TInventoryStocksRecord after = stockRecord(1L, 10L, 5, InventoryStatus.LOW);
		ItemStockOperationRequest request = operationRequest(1L, InventoryStockChangeType.INBOUND, 3);
		when(itemStockRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(5);
		when(itemStockRepository.updateQuantityAndStatus(1L, 5, InventoryStatus.LOW.getStoredName())).thenReturn(1);
		when(itemStockRepository.findById(1L)).thenReturn(Optional.of(after));

		ItemStockDetailDto result = itemStockService.operate(request);

		assertThat(result.getQuantity()).isEqualTo(5);
		assertThat(result.getInventoryStatus()).isEqualTo(InventoryStatus.LOW);
		ArgumentCaptor<TInventoryStockHistoriesRecord> history = ArgumentCaptor.forClass(TInventoryStockHistoriesRecord.class);
		verify(itemStockRepository).saveHistory(history.capture());
		assertThat(history.getValue().getChangeType()).isEqualTo(InventoryStockChangeType.INBOUND.getStoredName());
		assertThat(history.getValue().getQuantityDelta()).isEqualTo(3);
		assertThat(history.getValue().getQuantityBefore()).isEqualTo(2);
		assertThat(history.getValue().getQuantityAfter()).isEqualTo(5);
	}

	@Test
	void operateOutboundRejectsNegativeQuantity() {
		TInventoryStocksRecord before = stockRecord(1L, 10L, 2, InventoryStatus.LOW);
		ItemStockOperationRequest request = operationRequest(1L, InventoryStockChangeType.OUTBOUND, 3);
		when(itemStockRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(5);

		assertThatThrownBy(() -> itemStockService.operate(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Stock quantity cannot be negative");

		verify(itemStockRepository, never()).updateQuantityAndStatus(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyString());
		verify(itemStockRepository, never()).saveHistory(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void operateStocktakeSetsAbsoluteQuantity() {
		TInventoryStocksRecord before = stockRecord(1L, 10L, 2, InventoryStatus.LOW);
		TInventoryStocksRecord after = stockRecord(1L, 10L, 8, InventoryStatus.AVAILABLE);
		ItemStockOperationRequest request = operationRequest(1L, InventoryStockChangeType.STOCKTAKE, 8);
		when(itemStockRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(5);
		when(itemStockRepository.updateQuantityAndStatus(1L, 8, InventoryStatus.AVAILABLE.getStoredName())).thenReturn(1);
		when(itemStockRepository.findById(1L)).thenReturn(Optional.of(after));

		ItemStockDetailDto result = itemStockService.operate(request);

		assertThat(result.getQuantity()).isEqualTo(8);
		assertThat(result.getInventoryStatus()).isEqualTo(InventoryStatus.AVAILABLE);
	}

	@Test
	void operateNotifiesWhenStockBecomesLow() {
		TInventoryStocksRecord before = stockRecord(1L, 10L, 8, InventoryStatus.AVAILABLE);
		TInventoryStocksRecord after = stockRecord(1L, 10L, 5, InventoryStatus.LOW);
		ItemStockOperationRequest request = operationRequest(1L, InventoryStockChangeType.OUTBOUND, 3);
		when(itemStockRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(5);
		when(itemStockRepository.updateQuantityAndStatus(1L, 5, InventoryStatus.LOW.getStoredName())).thenReturn(1);
		when(itemStockRepository.findById(1L)).thenReturn(Optional.of(after));

		itemStockService.operate(request);

		ArgumentCaptor<BrowserNotificationCreateDto> notification =
				ArgumentCaptor.forClass(BrowserNotificationCreateDto.class);
		verify(notifications).sendNotice(notification.capture());
		assertThat(notification.getValue().getNotificationType()).isEqualTo(NotificationType.INVENTORY_LOW);
		assertThat(notification.getValue().getLevel()).isEqualTo(NotificationLevel.WARNING);
		assertThat(notification.getValue().getTitle()).isEqualTo("在庫が少なくなっています");
		assertThat(notification.getValue().getMessage()).isEqualTo("在庫品目ID 10 の現在数量は 5 です。");
		assertThat(notification.getValue().getSourceId()).isEqualTo("10");
	}

	@Test
	void operateDeleteKeepsQuantityAndMarksDeleted() {
		TInventoryStocksRecord before = stockRecord(1L, 10L, 2, InventoryStatus.LOW);
		TInventoryStocksRecord after = stockRecord(1L, 10L, 2, InventoryStatus.DELETED);
		ItemStockOperationRequest request = operationRequest(1L, InventoryStockChangeType.DELETE, 0);
		when(itemStockRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
		when(itemStockRepository.findMinimumQuantityByItemId(10L)).thenReturn(5);
		when(itemStockRepository.updateQuantityAndStatus(1L, 2, InventoryStatus.DELETED.getStoredName())).thenReturn(1);
		when(itemStockRepository.findById(1L)).thenReturn(Optional.of(after));

		ItemStockDetailDto result = itemStockService.operate(request);

		assertThat(result.getQuantity()).isEqualTo(2);
		assertThat(result.getInventoryStatus()).isEqualTo(InventoryStatus.DELETED);
		ArgumentCaptor<TInventoryStockHistoriesRecord> history = ArgumentCaptor.forClass(TInventoryStockHistoriesRecord.class);
		verify(itemStockRepository).saveHistory(history.capture());
		assertThat(history.getValue().getQuantityDelta()).isZero();
	}

	@Test
	void historyReturnsStockOperationHistories() {
		TInventoryStockHistoriesRecord history = new TInventoryStockHistoriesRecord();
		history.setId(100L);
		history.setInventoryStockId(1L);
		history.setChangeType(InventoryStockChangeType.OUTBOUND.getStoredName());
		history.setQuantityDelta(-2);
		history.setQuantityBefore(5);
		history.setQuantityAfter(3);
		history.setInventoryStatusBefore(InventoryStatus.AVAILABLE.getStoredName());
		history.setInventoryStatusAfter(InventoryStatus.LOW.getStoredName());
		when(itemStockRepository.findHistoriesByStockId(1L)).thenReturn(List.of(history));

		assertThat(itemStockService.history(1L))
				.singleElement()
				.satisfies(result -> {
					assertThat(result.getId()).isEqualTo(100L);
					assertThat(result.getInventoryStockId()).isEqualTo(1L);
					assertThat(result.getChangeType()).isEqualTo(InventoryStockChangeType.OUTBOUND);
					assertThat(result.getQuantityDelta()).isEqualTo(-2);
					assertThat(result.getQuantityBefore()).isEqualTo(5);
					assertThat(result.getQuantityAfter()).isEqualTo(3);
					assertThat(result.getInventoryStatusBefore()).isEqualTo(InventoryStatus.AVAILABLE);
					assertThat(result.getInventoryStatusAfter()).isEqualTo(InventoryStatus.LOW);
				});
	}

	private TInventoryStocksRecord stockRecord(Long id, Long inventoryItemId, Integer quantity, InventoryStatus inventoryStatus) {
		TInventoryStocksRecord databaseRecord = new TInventoryStocksRecord();
		databaseRecord.setId(id);
		databaseRecord.setInventoryItemId(inventoryItemId);
		databaseRecord.setStorageLocation("食品棚");
		databaseRecord.setQuantity(quantity);
		databaseRecord.setInventoryStatus(inventoryStatus.getStoredName());
		return databaseRecord;
	}

	private ItemStockOperationRequest operationRequest(Long stockId, InventoryStockChangeType changeType, Integer quantity) {
		return ItemStockOperationRequest.builder()
				.inventoryStockId(stockId)
				.changeType(changeType)
				.quantity(quantity)
				.reason("テスト")
				.memo("操作テスト")
				.build();
	}
}
