package jp.xoki.web.zunda.smarthomewebserverbackend.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.constants.DeviceMessageConstants;

/** デバイスマスタの登録または更新に利用するリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMasterRegistRequest {

	/** 更新時に指定するデバイスID。新規登録時は未指定。 */
	private Long id;

	/** デバイス名。 */
	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_DEVICE_NAME_REQUIRED)
	private String deviceName;

	/** デバイスのIPアドレス。 */
	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_ADDRESS_REQUIRED)
	@Size(min = 7, max = 15)
	private String address;

	/** デバイス状態。 */
	@NotBlank(message = DeviceMessageConstants.DEVICE_MASTER_STATUS_REQUIRED)
	private String status;

	/** 設置場所。 */
	private String location;

	/** MQTT連携時に利用するクライアントID。 */
	@JsonProperty("mqtt_client_id")
	private String mqttClientId;

	/** 補足メモ。 */
	private String note;

}
