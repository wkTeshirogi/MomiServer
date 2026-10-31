package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** 共通文書抽出が返すテキストと抽出方法。 */
public record DocumentExtractionResult(String text, String method, List<String> warnings) {
	public DocumentExtractionResult {
		warnings = warnings == null ? null : Collections.unmodifiableList(new ArrayList<>(warnings));
	}
}
