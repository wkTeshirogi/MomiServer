package jp.xoki.web.zunda.smarthomewebserverbackend.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.constants.DeviceMessageConstants;

/** サービスマスタの登録または更新で受け取るリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceMasterRegistRequest {

	private Long id;

	@NotBlank(message = DeviceMessageConstants.SERVICE_MASTER_SERVICE_NAME_REQUIRED)
	@Size(max = 255, message = DeviceMessageConstants.SERVICE_MASTER_SERVICE_NAME_SIZE)
	@JsonProperty("service_name")
	private String serviceName;

	@NotBlank(message = DeviceMessageConstants.SERVICE_MASTER_VIEW_NAME_REQUIRED)
	@Size(max = 50, message = DeviceMessageConstants.SERVICE_MASTER_VIEW_NAME_SIZE)
	@JsonProperty("view_name")
	private String viewName;

	@NotNull(message = DeviceMessageConstants.SERVICE_MASTER_TARGET_MACHINE_REQUIRED)
	@JsonProperty("target_machine")
	private Long targetMachine;

	@NotBlank(message = DeviceMessageConstants.SERVICE_MASTER_STATUS_REQUIRED)
	@Size(max = 50, message = DeviceMessageConstants.SERVICE_MASTER_STATUS_SIZE)
	private String status;

	@NotBlank(message = DeviceMessageConstants.SERVICE_MASTER_LOG_PATH_REQUIRED)
	@Size(max = 255, message = DeviceMessageConstants.SERVICE_MASTER_LOG_PATH_SIZE)
	@JsonProperty("log_path")
	private String logPath;

	@JsonProperty("use_journalctl")
	private boolean useJournalctl;
	@JsonProperty("allow_start")
	private boolean allowStart;
	@JsonProperty("allow_stop")
	private boolean allowStop;
	@JsonProperty("allow_restart")
	private boolean allowRestart;
	@JsonProperty("auto_restart")
	private boolean autoRestart;

	@Size(max = 50, message = DeviceMessageConstants.SERVICE_MASTER_NOTICE_LEVEL_SIZE)
	@JsonProperty("notice_level")
	private String noticeLevel;

	private String description;
}
