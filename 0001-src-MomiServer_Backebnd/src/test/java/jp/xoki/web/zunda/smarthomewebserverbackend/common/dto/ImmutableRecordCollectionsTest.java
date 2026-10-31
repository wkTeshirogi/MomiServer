package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentExtractionResult;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ApiErrorResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyIssueRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusWebSocketTicketService;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleDocumentParser;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportDetailDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImmutableRecordCollectionsTest {

	static Stream<Class<? extends Record>> collectionRecords() {
		return Stream.of(AgentStatusDto.TargetResponse.class, AgentStatusDto.SnapshotRequest.class,
				BatchScheduleExecutionLogPageDto.class, DocumentExtractionResult.class,
				ApiAccessLogPageDto.class, UserNotificationPreferenceSaveRequest.class, ApiErrorResponse.class,
				ExternalApiKeyDto.class, ExternalApiKeyIssueRequest.class, StatusDto.MachineStatus.class,
				StatusDto.MachineUpdateMessage.class, StatusDto.ServiceUpdateMessage.class,
				StatusWebSocketTicketService.Ticket.class, VehicleDocumentParser.ParsedCandidate.class,
				VehicleImportDetailDto.class);
	}

	@ParameterizedTest
	@MethodSource("collectionRecords")
	void collectionsAreSnapshotsAndCannotBeModifiedThroughAccessors(Class<? extends Record> type)
			throws ReflectiveOperationException {
		for (RecordComponent component : collectionComponents(type)) {
			Class<?> elementType = (Class<?>) ((ParameterizedType) component.getGenericType())
					.getActualTypeArguments()[0];
			Object element = defaultValue(elementType);
			Collection<Object> original = component.getType() == Set.class
					? new LinkedHashSet<>() : new ArrayList<>();
			original.add(element);
			Record instance = newRecord(type, Map.of(component.getName(), original));
			original.clear();

			Collection<?> exposed = (Collection<?>) component.getAccessor().invoke(instance);
			assertThat(exposed).as(type.getSimpleName() + "." + component.getName()).hasSize(1);
			assertThat(exposed.iterator().next()).isEqualTo(element);
			assertThrows(UnsupportedOperationException.class, exposed::clear);
		}
	}

	@ParameterizedTest
	@MethodSource("collectionRecords")
	void nullCollectionsRemainAvailableToExistingBeanValidation(Class<? extends Record> type)
			throws ReflectiveOperationException {
		for (RecordComponent component : collectionComponents(type)) {
			Map<String, Object> values = new java.util.HashMap<>();
			values.put(component.getName(), null);
			Record instance = newRecord(type, values);
			assertThat(component.getAccessor().invoke(instance)).isNull();
		}
	}

	private List<RecordComponent> collectionComponents(Class<?> type) {
		return Arrays.stream(type.getRecordComponents())
				.filter(component -> Collection.class.isAssignableFrom(component.getType())).toList();
	}

	@ParameterizedTest
	@MethodSource("collectionRecords")
	void nullElementsReachExistingValidationInsteadOfFailingDuringConstruction(Class<? extends Record> type)
			throws ReflectiveOperationException {
		for (RecordComponent component : collectionComponents(type)) {
			Collection<Object> original = component.getType() == Set.class
					? new LinkedHashSet<>() : new ArrayList<>();
			original.add(null);
			Record instance = newRecord(type, Map.of(component.getName(), original));
			original.clear();
			Collection<?> exposed = (Collection<?>) component.getAccessor().invoke(instance);
			assertThat(exposed).hasSize(1);
			assertThat(exposed.iterator().next()).isNull();
			assertThrows(UnsupportedOperationException.class, exposed::clear);
		}
	}

	@ParameterizedTest
	@MethodSource("setRecords")
	void setIterationOrderIsPreserved(Class<? extends Record> type) throws ReflectiveOperationException {
		for (RecordComponent component : collectionComponents(type)) {
			if (component.getType() != Set.class) continue;
			Set<String> original = new LinkedHashSet<>(List.of("second", "first"));
			Record instance = newRecord(type, Map.of(component.getName(), original));
			Collection<?> exposed = (Collection<?>) component.getAccessor().invoke(instance);
			assertThat(new ArrayList<>(exposed)).isEqualTo(new ArrayList<>(original));
		}
	}

	static Stream<Class<? extends Record>> setRecords() {
		return Stream.of(ExternalApiKeyDto.class, ExternalApiKeyIssueRequest.class,
				StatusWebSocketTicketService.Ticket.class);
	}

	private Record newRecord(Class<?> type, Map<String, Object> overrides) throws ReflectiveOperationException {
		RecordComponent[] components = type.getRecordComponents();
		Class<?>[] parameterTypes = new Class<?>[components.length];
		Object[] arguments = new Object[components.length];
		for (int i = 0; i < components.length; i++) {
			RecordComponent component = components[i];
			parameterTypes[i] = component.getType();
			arguments[i] = overrides.containsKey(component.getName())
					? overrides.get(component.getName()) : defaultValue(component.getType());
		}
		return (Record) type.getDeclaredConstructor(parameterTypes).newInstance(arguments);
	}

	private Object defaultValue(Class<?> type) throws ReflectiveOperationException {
		if (type == String.class) return "sample";
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == double.class) return 0.0;
		if (type == boolean.class) return false;
		if (type == List.class) return List.of();
		if (type == Set.class) return Set.of();
		if (type.isEnum()) return type.getEnumConstants()[0];
		if (type.isRecord()) return newRecord(type, Map.of());
		return null;
	}
}
