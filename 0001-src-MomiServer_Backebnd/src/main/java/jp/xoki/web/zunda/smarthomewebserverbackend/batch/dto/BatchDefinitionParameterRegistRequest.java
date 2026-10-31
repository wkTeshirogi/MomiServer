package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** バッチ定義パラメータマスタの登録または更新で受け取るリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDefinitionParameterRegistRequest {
	private Long id;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_BATCH_DEFINITION_ID_REQUIRED)
	@JsonProperty("batch_definition_id")
	private Long batchDefinitionId;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_PARAMETER_NAME_REQUIRED)
	@Size(max = 255, message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_PARAMETER_NAME_SIZE)
	@JsonProperty("parameter_name")
	private String parameterName;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_PARAMETER_TYPE_REQUIRED)
	@Size(max = 50, message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_PARAMETER_TYPE_SIZE)
	@JsonProperty("parameter_type")
	private String parameterType;

	@JsonProperty("fixed_value")
	private String fixedValue;

	@Size(max = 50, message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_DYNAMIC_VALUE_TYPE_SIZE)
	@JsonProperty("dynamic_value_type")
	private String dynamicValueType;

	@Size(max = 100, message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_VALUE_FORMAT_SIZE)
	@JsonProperty("value_format")
	private String valueFormat;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_DISPLAY_ORDER_REQUIRED)
	@Min(value = 0, message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_DISPLAY_ORDER_MIN)
	@JsonProperty("display_order")
	private Integer displayOrder;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_PARAMETER_ENABLED_REQUIRED)
	private Boolean enabled;
}
