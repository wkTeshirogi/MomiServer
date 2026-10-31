package jp.xoki.web.zunda.smarthomewebserverbackend.device;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.repository.DeviceRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.service.DeviceMasterService;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.service.ServiceMasterService;
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
class DeviceMasterIntegrationTest {
    @Autowired DeviceMasterService devices;
    @Autowired ServiceMasterService services;
    @Autowired DeviceRepository deviceRepository;

    @Test
    void deviceCreationUpdateAndListsPreserveAllFields() {
        DeviceMasterRegistRequest request = deviceRequest();
        DeviceMasterDetailDto created = devices.save(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertDevice(created, request);
        assertThat(devices.getDeviceById(created.getId())).usingRecursiveComparison().isEqualTo(created);
        assertThat(devices.getAllDevices()).extracting(DeviceMasterDetailDto::getId).contains(created.getId());

        request.setId(created.getId());
        request.setDeviceName("updated-device");
        request.setAddress("192.0.2.11");
        request.setLocation("rack-b");
        request.setMqttClientId("updated-client");
        request.setNote("updated note");
        request.setStatus("0");
        DeviceMasterDetailDto updated = devices.save(request);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertDevice(updated, request);
        assertThat(devices.getDeviceById(created.getId())).usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void missingDeviceCannotBeRetrievedOrUpdated() {
        DeviceMasterRegistRequest request = deviceRequest();
        request.setId(Long.MAX_VALUE);
        assertThatThrownBy(() -> devices.getDeviceById(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> devices.save(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void directDeviceRegistrationAndUpdatePreserveIdentityAndClearOptionalFields() {
        long id = deviceRepository.save("direct-device", "192.0.2.12", "rack-a", "direct-client", "note", "1");
        DeviceMasterDetailDto created = devices.getDeviceById(id);
        assertThat(created.getDevicename()).isEqualTo("direct-device");
        assertThat(created.getAddress()).isEqualTo("192.0.2.12");
        assertThat(created.getLocation()).isEqualTo("rack-a");
        assertThat(created.getMqttClientId()).isEqualTo("direct-client");
        assertThat(created.getNote()).isEqualTo("note");
        assertThat(created.getStatus()).isEqualTo("1");

        assertThat(deviceRepository.update(id, "updated-direct-device", "192.0.2.13", null, null, null, "0"))
                .isEqualTo(1);
        DeviceMasterDetailDto updated = devices.getDeviceById(id);
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getDevicename()).isEqualTo("updated-direct-device");
        assertThat(updated.getAddress()).isEqualTo("192.0.2.13");
        assertThat(updated.getLocation()).isNull();
        assertThat(updated.getMqttClientId()).isNull();
        assertThat(updated.getNote()).isNull();
        assertThat(updated.getStatus()).isEqualTo("0");
        assertThat(deviceRepository.update(Long.MAX_VALUE, "missing", "192.0.2.14", null, null, null, "1"))
                .isZero();
    }

    @Test
    void serviceCreationUsesDefaultNoticeLevelAndUpdatePreservesControls() {
        long deviceId = devices.save(deviceRequest()).getId();
        ServiceMasterRegistRequest request = serviceRequest(deviceId);
        ServiceMasterDetailDto created = services.save(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getNoticeLevel()).isEqualTo("none");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertService(created, request);
        assertThat(services.getAllServices()).extracting(ServiceMasterDetailDto::getId).contains(created.getId());
        assertThat(services.getServiceById(created.getId())).usingRecursiveComparison().isEqualTo(created);

        request.setId(created.getId());
        request.setServiceName("updated.service");
        request.setViewName("Updated service");
        request.setDescription("updated description");
        request.setStatus("0");
        request.setLogPath("updated.log");
        request.setAllowStart(false);
        request.setAllowStop(false);
        request.setAllowRestart(false);
        request.setAutoRestart(false);
        request.setUseJournalctl(false);
        request.setNoticeLevel("warning");
        ServiceMasterDetailDto updated = services.save(request);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(updated.getNoticeLevel()).isEqualTo("warning");
        assertService(updated, request);
        assertThat(services.getServiceById(created.getId())).usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void missingServiceCannotBeRetrievedOrUpdated() {
        ServiceMasterRegistRequest request = serviceRequest(devices.save(deviceRequest()).getId());
        request.setId(Long.MAX_VALUE);
        assertThatThrownBy(() -> services.getServiceById(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> services.save(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    private DeviceMasterRegistRequest deviceRequest() {
        return DeviceMasterRegistRequest.builder().deviceName("master-test-device")
                .address("192.0.2.10").status("1").location("rack-a")
                .mqttClientId("master-test-client").note("initial note").build();
    }

    private ServiceMasterRegistRequest serviceRequest(long deviceId) {
        return ServiceMasterRegistRequest.builder().serviceName("master-test.service")
                .viewName("Master test service").targetMachine(deviceId).status("1")
                .logPath("service.log").description("initial description")
                .allowStart(true).allowStop(true).allowRestart(true).autoRestart(true)
                .useJournalctl(true).build();
    }

    private void assertDevice(DeviceMasterDetailDto actual, DeviceMasterRegistRequest expected) {
        assertThat(actual.getDevicename()).isEqualTo(expected.getDeviceName());
        assertThat(actual).usingRecursiveComparison()
                .comparingOnlyFields("address", "status", "location", "mqttClientId", "note")
                .isEqualTo(expected);
    }

    private void assertService(ServiceMasterDetailDto actual, ServiceMasterRegistRequest expected) {
        assertThat(actual).usingRecursiveComparison().comparingOnlyFields(
                "serviceName", "viewName", "targetMachine", "status", "logPath", "description",
                "allowStart", "allowStop", "allowRestart", "autoRestart", "useJournalctl")
                .isEqualTo(expected);
    }
}
