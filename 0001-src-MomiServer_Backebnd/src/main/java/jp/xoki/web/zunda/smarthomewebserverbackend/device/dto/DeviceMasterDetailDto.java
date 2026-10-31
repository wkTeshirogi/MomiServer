package jp.xoki.web.zunda.smarthomewebserverbackend.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.constants.DeviceMessageConstants;

/** デバイスマスタ詳細をAPIレスポンスとして返すためのDTO。 */
@Getter
@Builder
public class DeviceMasterDetailDto {
	private Long id;

	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_DETAIL_DEVICENAME_REQUIRED)
	private String devicename;

	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_DETAIL_ADDRESS_REQUIRED)
	private String address;

	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_DETAIL_LOCATION_REQUIRED)
	private String location;

	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_DETAIL_MQTT_CLIENT_ID_REQUIRED)
	@JsonProperty("mqtt_client_id")
	private String mqttClientId;
	private String note;

	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_DETAIL_STATUS_REQUIRED)
	private String status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
