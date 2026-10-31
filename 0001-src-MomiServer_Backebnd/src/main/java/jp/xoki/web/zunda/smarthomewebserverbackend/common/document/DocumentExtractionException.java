package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

/** 外部OCRまたは文書解析が安全に完了できない場合の例外。 */
public class DocumentExtractionException extends IllegalArgumentException {
	public DocumentExtractionException(String message) { super(message); }
	public DocumentExtractionException(String message, Throwable cause) { super(message, cause); }
}
