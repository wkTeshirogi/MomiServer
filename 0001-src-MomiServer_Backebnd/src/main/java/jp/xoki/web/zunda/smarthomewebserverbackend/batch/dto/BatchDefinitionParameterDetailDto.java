package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** バッチ定義パラメータマスタの検索結果を返すためのDTO。 */
@Getter
@Builder
public class BatchDefinitionParameterDetailDto {
	private Long id;
	@JsonProperty("batch_definition_id")
	private Long batchDefinitionId;
	@JsonProperty("parameter_name")
	private String parameterName;
	@JsonProperty("parameter_type")
	private String parameterType;
	@JsonProperty("fixed_value")
	private String fixedValue;
	@JsonProperty("dynamic_value_type")
	private String dynamicValueType;
	@JsonProperty("value_format")
	private String valueFormat;
	@JsonProperty("display_order")
	private int displayOrder;
	private boolean enabled;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
