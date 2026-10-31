package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.AgentBatchCompleteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionParameterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionParameterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileRegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionHistoryDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionHistoryRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestCreateRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockHistoryDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoJsonContractTest {

	private static final Map<Class<?>, Integer> RENAMED_PROPERTY_COUNTS = Map.ofEntries(
			Map.entry(AgentBatchCompleteRequest.class, 6),
			Map.entry(BatchDefinitionDetailDto.class, 15),
			Map.entry(BatchDefinitionParameterDetailDto.class, 7),
			Map.entry(BatchDefinitionParameterRegistRequest.class, 7),
			Map.entry(BatchDefinitionRegistRequest.class, 15),
			Map.entry(BatchExecutionFileDetailDto.class, 5),
			Map.entry(BatchExecutionFileRegisterRequest.class, 3),
			Map.entry(BatchExecutionHistoryDetailDto.class, 7),
			Map.entry(BatchExecutionHistoryRegistRequest.class, 7),
			Map.entry(BatchExecutionRequestCreateRequest.class, 2),
			Map.entry(BatchExecutionRequestDetailDto.class, 38),
			Map.entry(BatchScheduleDetailDto.class, 6),
			Map.entry(BatchScheduleRegistRequest.class, 6),
			Map.entry(BatchGlobalSettingDetailDto.class, 13),
			Map.entry(BrowserNotificationCreateDto.class, 6),
			Map.entry(BrowserNotificationDetailDto.class, 7),
			Map.entry(NotificationGlobalSettingDetailDto.class, 9),
			Map.entry(NotificationRouteSettingDetailDto.class, 9),
			Map.entry(UserBrowserNotificationDetailDto.class, 13),
			Map.entry(UserNotificationPreferenceDetailDto.class, 5),
			Map.entry(UserNotificationRouteSettingDetailDto.class, 7),
			Map.entry(DeviceMasterDetailDto.class, 1),
			Map.entry(DeviceMasterRegistRequest.class, 1),
			Map.entry(ServiceMasterDetailDto.class, 10),
			Map.entry(ServiceMasterRegistRequest.class, 10),
			Map.entry(ItemMasterDetailDto.class, 5),
			Map.entry(ItemMasterRegistRequest.class, 5),
			Map.entry(ItemStockDetailDto.class, 5),
			Map.entry(ItemStockHistoryDetailDto.class, 7),
			Map.entry(ItemStockOperationRequest.class, 2),
			Map.entry(ItemStockRegistRequest.class, 5));

	private final ObjectMapper mapper = JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build();

	@Test
	void allRenamedFieldsUseCamelCaseAndPreserveTheirSnakeCaseJsonNames() {
		RENAMED_PROPERTY_COUNTS.forEach((dtoType, expectedCount) -> {
			List<Field> fields = List.of(dtoType.getDeclaredFields());
			long annotatedCount = fields.stream()
					.filter(field -> field.getAnnotation(JsonProperty.class) != null)
					.count();

			assertThat(annotatedCount)
					.as("%s renamed property count", dtoType.getSimpleName())
					.isEqualTo(expectedCount.longValue());

			fields.forEach(field -> {
				assertThat(field.getName())
						.as("%s.%s", dtoType.getSimpleName(), field.getName())
						.matches("^[a-z][a-zA-Z0-9]*$");

				JsonProperty property = field.getAnnotation(JsonProperty.class);
				if (property != null) {
					assertThat(property.value())
							.as("%s.%s JSON name", dtoType.getSimpleName(), field.getName())
							.isEqualTo(toSnakeCase(field.getName()));
				}
			});
		});
	}

	@Test
	void agentCompletionRequestPreservesFlagsAndExecutionStatus() throws Exception {
		String json = """
				{
				  "stdout": "out",
				  "stderr": "err",
				  "output_truncated": true,
				  "stop_confirmed": true,
				  "not_started": false,
				  "execution_status": "success",
				  "exit_code": 0,
				  "log_path": "/var/log/batch.log",
				  "message": "completed"
				}
				""";

		AgentBatchCompleteRequest request = mapper.readValue(json, AgentBatchCompleteRequest.class);

		assertThat(request.getOutputTruncated()).isTrue();
		assertThat(request.getStopConfirmed()).isTrue();
		assertThat(request.getNotStarted()).isFalse();
		assertThat(request.getExecutionStatus()).isEqualTo(BatchExecutionStatus.SUCCESS);
		assertThat(mapper.readTree(mapper.writeValueAsString(request)))
				.isEqualTo(mapper.readTree(json));
	}

	@Test
	void requestDtosDeserializeRenamedDeviceAndItemProperties() throws Exception {
		String deviceJson = """
				{
				  "id": null,
				  "deviceName": "sensor-01",
				  "address": "192.168.1.10",
				  "status": "active",
				  "location": "office",
				  "mqtt_client_id": "mqtt-sensor-01",
				  "note": "test"
				}
				""";
		DeviceMasterRegistRequest device = mapper.readValue(deviceJson, DeviceMasterRegistRequest.class);
		assertThat(device.getMqttClientId()).isEqualTo("mqtt-sensor-01");
		assertThat(mapper.readTree(mapper.writeValueAsString(device)))
				.isEqualTo(mapper.readTree(deviceJson));

		String itemJson = """
				{
				  "id": null,
				  "inventory_item_id": 12,
				  "storage_location": "cabinet",
				  "quantity": 4,
				  "replenish_quantity": 2,
				  "inventory_status": "low",
				  "expiration_date": "2027-01-31",
				  "memo": "check monthly"
				}
				""";
		ItemStockRegistRequest item = mapper.readValue(itemJson, ItemStockRegistRequest.class);
		assertThat(item.getInventoryItemId()).isEqualTo(12L);
		assertThat(item.getInventoryStatus()).isEqualTo(InventoryStatus.LOW);
		assertThat(item.getExpirationDate()).isEqualTo(LocalDate.of(2027, 1, 31));
		assertThat(mapper.readTree(mapper.writeValueAsString(item)))
				.isEqualTo(mapper.readTree(itemJson));
	}

	@Test
	void representativeDtosKeepSnakeCasePropertiesAndCamelCaseTimestamps() throws Exception {
		LocalDateTime timestamp = LocalDateTime.of(2026, 10, 20, 12, 30);

		JsonNode batch = mapper.readTree(mapper.writeValueAsString(BatchExecutionRequestDetailDto.builder()
				.executionId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
				.startReceivedAt(timestamp)
				.createdAt(timestamp)
				.updatedAt(timestamp)
				.build()));
		assertThat(batch.get("execution_id").asText())
				.isEqualTo("11111111-1111-1111-1111-111111111111");
		assertThat(batch.get("start_received_at").asText()).isEqualTo("2026-10-20T12:30:00");
		assertThat(batch.get("createdAt").asText()).isEqualTo("2026-10-20T12:30:00");
		assertThat(batch.get("updatedAt").asText()).isEqualTo("2026-10-20T12:30:00");
		assertThat(batch.has("executionId")).isFalse();
		assertThat(batch.has("startReceivedAt")).isFalse();

		JsonNode device = mapper.readTree(mapper.writeValueAsString(DeviceMasterDetailDto.builder()
				.id(1L)
				.devicename("sensor-01")
				.address("192.168.1.10")
				.location("office")
				.mqttClientId("mqtt-sensor-01")
				.status("active")
				.createdAt(timestamp)
				.updatedAt(timestamp)
				.build()));
		assertThat(device.get("mqtt_client_id").asText()).isEqualTo("mqtt-sensor-01");
		assertThat(device.get("createdAt").asText()).isEqualTo("2026-10-20T12:30:00");
		assertThat(device.has("mqttClientId")).isFalse();

		JsonNode item = mapper.readTree(mapper.writeValueAsString(ItemStockDetailDto.builder()
				.id(2L)
				.inventoryItemId(12L)
				.storageLocation("cabinet")
				.quantity(4)
				.replenishQuantity(2)
				.inventoryStatus(InventoryStatus.LOW)
				.expirationDate(LocalDate.of(2027, 1, 31))
				.createdAt(timestamp)
				.updatedAt(timestamp)
				.build()));
		assertThat(item.get("inventory_item_id").asLong()).isEqualTo(12L);
		assertThat(item.get("inventory_status").asText()).isEqualTo("low");
		assertThat(item.get("expiration_date").asText()).isEqualTo("2027-01-31");
		assertThat(item.get("updatedAt").asText()).isEqualTo("2026-10-20T12:30:00");
		assertThat(item.has("inventoryItemId")).isFalse();

		JsonNode notification = mapper.readTree(mapper.writeValueAsString(BrowserNotificationDetailDto.builder()
				.id(3L)
				.notificationId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
				.notificationType(NotificationType.BATCH_COMPLETED)
				.level(NotificationLevel.INFO)
				.title("Batch completed")
				.message("The batch completed.")
				.sourceType(NotificationSourceType.BATCH_EXECUTION)
				.sourceId("execution-1")
				.linkPath("/batch/executions/1")
				.metadataJson("{}")
				.expiresAt(timestamp.plusDays(1))
				.createdAt(timestamp)
				.updatedAt(timestamp)
				.build()));
		assertThat(notification.get("notification_id").asText())
				.isEqualTo("22222222-2222-2222-2222-222222222222");
		assertThat(notification.get("notification_type").asText()).isEqualTo("batch_completed");
		assertThat(notification.get("source_type").asText()).isEqualTo("batch_execution");
		assertThat(notification.get("expires_at").asText()).isEqualTo("2026-10-21T12:30:00");
		assertThat(notification.get("createdAt").asText()).isEqualTo("2026-10-20T12:30:00");
		assertThat(notification.has("notificationId")).isFalse();
	}

	private static String toSnakeCase(String fieldName) {
		return fieldName.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
				.toLowerCase(Locale.ROOT);
	}
}
