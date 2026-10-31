package jp.xoki.web.zunda.smarthomewebserverbackend.item.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemMasterService;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ItemAccessControlTest {

	@Configuration
	@EnableMethodSecurity
	static class MethodSecurityConfiguration {
	}

	private AnnotationConfigApplicationContext context;
	private ItemMasterService masterService;
	private ItemStockService stockService;

	@BeforeEach
	void setUp() {
		masterService = mock(ItemMasterService.class);
		stockService = mock(ItemStockService.class);
		context = new AnnotationConfigApplicationContext();
		context.register(MethodSecurityConfiguration.class);
		context.registerBean(ItemMasterService.class, () -> masterService);
		context.registerBean(ItemStockService.class, () -> stockService);
		context.registerBean(ItemMasterController.class);
		context.registerBean(ItemStockController.class);
		context.refresh();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		context.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "OPERATOR", "ADMIN"})
	void everyRoleCanReadItemMasterAndUseStock(String role) {
		authenticate(role);
		ItemMasterController masters = context.getBean(ItemMasterController.class);
		ItemStockController stock = context.getBean(ItemStockController.class);
		ItemStockRegistRequest saveRequest = new ItemStockRegistRequest();
		ItemStockOperationRequest operationRequest = new ItemStockOperationRequest();
		assertThatCode(() -> {
			masters.list();
			masters.getItemMasterDetail(1L);
			stock.list();
			stock.detail(2L);
			stock.save(saveRequest);
			stock.operate(operationRequest);
			stock.history(2L);
		}).doesNotThrowAnyException();
		verify(masterService).list();
		verify(masterService).detail(1L);
		verify(stockService).list();
		verify(stockService).detail(2L);
		verify(stockService).save(saveRequest);
		verify(stockService).operate(operationRequest);
		verify(stockService).history(2L);
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "OPERATOR"})
	void nonAdminCannotEditItemMaster(String role) {
		authenticate(role);
		ItemMasterController exceptionInput1 = context.getBean(ItemMasterController.class);
		ItemMasterRegistRequest exceptionInput2 = new ItemMasterRegistRequest();
		assertThatThrownBy(() -> exceptionInput1
				.register(exceptionInput2))
				.isInstanceOf(AccessDeniedException.class);
		verify(masterService, never()).save(any());
	}

	@Test
	void adminCanEditItemMaster() {
		authenticate("ADMIN");
		assertThatCode(() -> context.getBean(ItemMasterController.class)
				.register(new ItemMasterRegistRequest())).doesNotThrowAnyException();
		verify(masterService).save(any());
	}

	private void authenticate(String role) {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken(role.toLowerCase(), null, "ROLE_" + role));
	}
}
