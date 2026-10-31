package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthorization;
import static org.mockito.Mockito.mock;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.response.AuthResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.AuthService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller.AgentBatchExecutionController;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller.BatchExecutionController;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller.BatchExecutionFileController;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller.BatchMasterController;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller.BatchScheduleMasterController;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchDefinitionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionFileService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchScheduleService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.UserNotificationPreferenceService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.UserNotificationRouteSettingService;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.controller.DeviceMasterController;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.controller.ServiceMasterController;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.service.DeviceMasterService;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.service.ServiceMasterService;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.controller.ItemMasterController;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.controller.ItemStockController;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemMasterService;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemStockService;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.controller.MasterUserController;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.response.UserMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.service.UserMasterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ControllerApiContractTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;


	@Mock
	private AuthService authService;

	@Mock
	private UserMasterService userMasterService;

	@Mock
	private UserNotificationPreferenceService userNotificationPreferenceService;

	@Mock
	private UserNotificationRouteSettingService userNotificationRouteSettingService;

	@Mock
	private DeviceMasterService deviceMasterService;

	@Mock
	private ServiceMasterService serviceMasterService;

	@Mock
	private ItemMasterService itemMasterService;

	@Mock
	private ItemStockService itemStockService;

	@Mock
	private BatchDefinitionService batchDefinitionService;

	@Mock
	private BatchScheduleService batchScheduleService;

	@Mock
	private BatchExecutionRequestService batchExecutionRequestService;

	@Mock
	private BatchExecutionFileService batchExecutionFileService;

	@Test
	void authLoginReturnsTokenResponse() throws Exception {
		when(authService.login(any())).thenReturn(AuthResponse.builder()
				.accessToken("access-token")
				.refreshToken("refresh-token")
				.expiresnIn(3600)
				.build());

		mockMvc(new AuthControllerShim(authService).controller())
				.perform(post("/api/auth/login")
						.contentType(JSON)
						.content("""
								{"email":"admin@example.com","password":"password123"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token"))
				.andExpect(jsonPath("$.refreshToken").value("refresh-token"));

		verify(authService).login(any());
	}

	@Test
	void authRefreshReturnsTokenResponse() throws Exception {
		when(authService.refresh(any())).thenReturn(AuthResponse.builder()
				.accessToken("new-access-token")
				.refreshToken("new-refresh-token")
				.expiresnIn(3600)
				.build());

		mockMvc(new AuthControllerShim(authService).controller())
				.perform(post("/api/auth/refresh")
						.contentType(JSON)
						.content("""
								{"refreshToken":"refresh-token"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("new-access-token"));

		verify(authService).refresh(any());
	}

	@Test
	void authLogoutReturnsNoContent() throws Exception {
		mockMvc(new AuthControllerShim(authService).controller())
				.perform(post("/api/auth/logout")
						.header("Authorization", "Bearer access-token")
						.contentType(JSON)
						.content("""
								{"refreshToken":"refresh-token"}
								"""))
				.andExpect(status().isNoContent());

		verify(authService).logout(eq("Bearer access-token"), any());
	}

	@Test
	void masterUserListReturnsUsers() throws Exception {
		when(userMasterService.getUserMasterList()).thenReturn(List.of(UserMasterDetailDto.builder()
				.id(1L)
				.email("admin@example.com")
				.role("ADMIN")
				.build()));

		mockMvc(new MasterUserController(userMasterService, authService,
				userNotificationPreferenceService, userNotificationRouteSettingService))
				.perform(post("/api/master/user/list"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1));

		verify(userMasterService).getUserMasterList();
	}

	@Test
	void deviceMasterListReturnsDevices() throws Exception {
		when(deviceMasterService.getAllDevices()).thenReturn(List.of(DeviceMasterDetailDto.builder()
				.id(10L)
				.devicename("gateway-host")
				.status("ACTIVE")
				.build()));

		mockMvc(new DeviceMasterController(deviceMasterService))
				.perform(post("/api/master/device/list"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(10));

		verify(deviceMasterService).getAllDevices();
	}

	@Test
	void serviceMasterListReturnsServices() throws Exception {
		when(serviceMasterService.getAllServices()).thenReturn(List.of(ServiceMasterDetailDto.builder()
				.id(20L)
				.serviceName("backend")
				.status("ACTIVE")
				.build()));

		mockMvc(new ServiceMasterController(serviceMasterService))
				.perform(post("/api/master/service/list"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].service_name").value("backend"));

		verify(serviceMasterService).getAllServices();
	}

	@Test
	void itemMasterListReturnsItems() throws Exception {
		when(itemMasterService.list()).thenReturn(List.of(ItemMasterDetailDto.builder()
				.id(30L)
				.itemName("battery")
				.category("parts")
				.unit("piece")
				.minQuantity(2)
				.status("ACTIVE")
				.build()));

		mockMvc(new ItemMasterController(itemMasterService))
				.perform(post("/api/master/item/list"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].item_name").value("battery"));

		verify(itemMasterService).list();
	}

	@Test
	void itemStockSaveReturnsSavedStock() throws Exception {
		when(itemStockService.save(any())).thenReturn(ItemStockDetailDto.builder()
				.id(40L)
				.inventoryItemId(30L)
				.storageLocation("shelf-a")
				.quantity(5)
				.inventoryStatus(InventoryStatus.AVAILABLE)
				.build());

		mockMvc(new ItemStockController(itemStockService))
				.perform(post("/api/stock/save")
						.contentType(JSON)
						.content("""
								{
								  "inventory_item_id":30,
								  "storage_location":"shelf-a",
								  "quantity":5,
								  "replenish_quantity":10,
								  "inventory_status":"AVAILABLE"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(40))
				.andExpect(jsonPath("$.inventory_status").value("available"));

		verify(itemStockService).save(any());
	}

	@Test
	void itemStockOperationReturnsUpdatedStock() throws Exception {
		when(itemStockService.operate(any())).thenReturn(ItemStockDetailDto.builder()
				.id(40L)
				.quantity(8)
				.inventoryStatus(InventoryStatus.AVAILABLE)
				.build());

		mockMvc(new ItemStockController(itemStockService))
				.perform(post("/api/stock/operation")
						.contentType(JSON)
						.content("""
								{
								  "inventory_stock_id":40,
								  "change_type":"INBOUND",
								  "quantity":3,
								  "reason":"purchase"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(8));

		verify(itemStockService).operate(any());
	}

	@Test
	void batchDefinitionListReturnsDefinitions() throws Exception {
		when(batchDefinitionService.getAllBatchDefinitions()).thenReturn(List.of(BatchDefinitionDetailDto.builder()
				.id(50L)
				.batchName("daily-job")
				.enabled(true)
				.build()));

		mockMvc(new BatchMasterController(batchDefinitionService))
				.perform(post("/api/master/batch/list"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].batch_name").value("daily-job"));

		verify(batchDefinitionService).getAllBatchDefinitions();
	}

	@Test
	void batchScheduleListReturnsSchedules() throws Exception {
		when(batchScheduleService.getAllBatchSchedules()).thenReturn(List.of(BatchScheduleDetailDto.builder()
				.id(60L)
				.batchDefinitionId(50L)
				.enabled(true)
				.build()));

		mockMvc(new BatchScheduleMasterController(batchScheduleService))
				.perform(post("/api/master/batch/schedule/list"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(60));

		verify(batchScheduleService).getAllBatchSchedules();
	}

	@Test
	void batchExecutionDetailReturnsRequest() throws Exception {
		UUID executionId = UUID.randomUUID();
		when(batchExecutionRequestService.getDetail(executionId)).thenReturn(BatchExecutionRequestDetailDto.builder()
				.id(70L)
				.executionId(executionId)
				.executionStatus(BatchExecutionStatus.QUEUED)
				.build());

		mockMvc(new BatchExecutionController(batchExecutionRequestService))
				.perform(get("/api/batch/executions/{executionId}", executionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.execution_id").value(executionId.toString()));

		verify(batchExecutionRequestService).getDetail(executionId);
	}

	@Test
	void agentBatchStartReturnsUpdatedRequest() throws Exception {
		UUID executionId = UUID.randomUUID();
		when(batchExecutionRequestService.markRunning(executionId)).thenReturn(BatchExecutionRequestDetailDto.builder()
				.id(80L)
				.executionId(executionId)
				.executionStatus(BatchExecutionStatus.RUNNING)
				.build());

		mockMvc(new AgentBatchExecutionController(mock(AgentAuthorization.class), batchExecutionRequestService))
				.perform(post("/api/v1/agent/batch/executions/{executionId}/start", executionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.execution_status").value("running"));

		verify(batchExecutionRequestService).markRunning(executionId);
	}

	@Test
	void agentBatchCompleteReturnsUpdatedRequest() throws Exception {
		UUID executionId = UUID.randomUUID();
		when(batchExecutionRequestService.complete(eq(executionId), any())).thenReturn(BatchExecutionRequestDetailDto.builder()
				.id(80L)
				.executionId(executionId)
				.executionStatus(BatchExecutionStatus.SUCCESS)
				.build());

		mockMvc(new AgentBatchExecutionController(mock(AgentAuthorization.class), batchExecutionRequestService))
				.perform(post("/api/v1/agent/batch/executions/{executionId}/complete", executionId)
						.contentType(JSON)
						.content("""
								{"execution_status":"success","exit_code":0,"message":"done"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.execution_status").value("success"));

		verify(batchExecutionRequestService).complete(eq(executionId), any());
	}

	@Test
	void batchExecutionFilesListReturnsFiles() throws Exception {
		UUID executionId = UUID.randomUUID();
		when(batchExecutionFileService.list(executionId)).thenReturn(List.of(BatchExecutionFileDetailDto.builder()
				.id(90L)
				.fileName("result.log")
				.contentType("text/plain")
				.build()));

		mockMvc(new BatchExecutionFileController(mock(AgentAuthorization.class), batchExecutionFileService))
				.perform(get("/api/v1/agent/batch/executions/{executionId}/files", executionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].file_name").value("result.log"));

		verify(batchExecutionFileService).list(executionId);
	}

	@Test
	void batchExecutionFileDownloadReturnsResourceWithAttachmentHeader() throws Exception {
		UUID executionId = UUID.randomUUID();
		when(batchExecutionFileService.getFile(executionId, 90L)).thenReturn(BatchExecutionFileDetailDto.builder()
				.id(90L)
				.fileName("result.log")
				.contentType("text/plain")
				.build());
		when(batchExecutionFileService.download(executionId, 90L))
				.thenReturn(new ByteArrayResource("ok".getBytes()));

		mockMvc(new BatchExecutionFileController(mock(AgentAuthorization.class), batchExecutionFileService))
				.perform(get("/api/v1/agent/batch/executions/{executionId}/files/{fileId}/download", executionId, 90L))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"result.log\""))
				.andExpect(content().bytes("ok".getBytes()));

		verify(batchExecutionFileService).getFile(executionId, 90L);
		verify(batchExecutionFileService).download(executionId, 90L);
	}

	private MockMvc mockMvc(Object controller) {
		return MockMvcBuilders.standaloneSetup(controller).build();
	}

	private record AuthControllerShim(AuthService authService) {
		jp.xoki.web.zunda.smarthomewebserverbackend.auth.controller.AuthController controller() {
			return new jp.xoki.web.zunda.smarthomewebserverbackend.auth.controller.AuthController(authService);
		}
	}
}
