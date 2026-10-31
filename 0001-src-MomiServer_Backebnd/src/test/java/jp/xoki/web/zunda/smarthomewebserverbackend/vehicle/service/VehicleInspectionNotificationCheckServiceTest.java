package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service;

import jp.xoki.web.zunda.jooq.tables.records.TVehiclesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.*;

class VehicleInspectionNotificationCheckServiceTest {
	@Test
	void continuesAfterFailureAndUsesJapaneseDate() {
		VehicleRepository vehicles = mock(VehicleRepository.class);
		VehicleInspectionNotificationService service = mock(VehicleInspectionNotificationService.class);
		TVehiclesRecord first = new TVehiclesRecord(); first.setId(1L);
		TVehiclesRecord second = new TVehiclesRecord(); second.setId(2L);
		when(vehicles.findAllActive()).thenReturn(List.of(first, second));
		LocalDate date = LocalDate.of(2026, 10, 17);
		when(service.notifyIfDue(1L, date)).thenThrow(new IllegalStateException("test"));
		VehicleInspectionNotificationCheckService check = new VehicleInspectionNotificationCheckService(vehicles, service,
				Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneId.of("Asia/Tokyo")));
		check.checkExpiries();
		verify(service).notifyIfDue(2L, date);
	}

	@Test
	void keepsTransactionsSeparateForEachVehicleThroughServiceProxy() {
		VehicleRepository vehicles = mock(VehicleRepository.class);
		VehicleInspectionNotificationService target = mock(VehicleInspectionNotificationService.class);
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		TransactionStatus firstStatus = mock(TransactionStatus.class);
		TransactionStatus secondStatus = mock(TransactionStatus.class);
		when(manager.getTransaction(any())).thenReturn(firstStatus, secondStatus);
		TransactionInterceptor interceptor = new TransactionInterceptor();
		interceptor.setTransactionManager(manager);
		interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
		ProxyFactory factory = new ProxyFactory(target);
		factory.addAdvice(interceptor);
		TVehiclesRecord first = new TVehiclesRecord(); first.setId(1L);
		TVehiclesRecord second = new TVehiclesRecord(); second.setId(2L);
		when(vehicles.findAllActive()).thenReturn(List.of(first, second));
		LocalDate date = LocalDate.of(2026, 10, 17);
		when(target.notifyIfDue(1L, date)).thenThrow(new IllegalStateException("notification failed"));
		VehicleInspectionNotificationCheckService service = new VehicleInspectionNotificationCheckService(vehicles,
				(VehicleInspectionNotificationService) factory.getProxy(),
				Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneId.of("Asia/Tokyo")));

		service.checkExpiries();
		InOrder order = inOrder(manager, target);
		order.verify(manager).getTransaction(any());
		order.verify(target).notifyIfDue(1L, date);
		order.verify(manager).rollback(firstStatus);
		order.verify(manager).getTransaction(any());
		order.verify(target).notifyIfDue(2L, date);
		order.verify(manager).commit(secondStatus);
	}
}
