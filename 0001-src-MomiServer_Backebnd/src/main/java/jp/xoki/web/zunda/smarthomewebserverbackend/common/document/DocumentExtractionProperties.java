package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 文書抽出とOCR実行に必要な外部設定。 */
@Getter
@Setter
@Component
@ConfigurationProperties("app.document-extraction")
public class DocumentExtractionProperties {

	private String tesseractCommand = "tesseract";
	private String language = "jpn+eng";
	private Duration timeout = Duration.ofSeconds(30);
	private Duration documentTimeout = Duration.ofSeconds(330);
	private int renderedDpi = 300;
	private long maxRenderedPixels = 40_000_000;
	private int maxExtractedCharacters = 500_000;
}
