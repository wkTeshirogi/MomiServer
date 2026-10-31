package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

import java.nio.file.Path;

/** 画像から文字列を取得するOCRエンジンの共通契約。 */
public interface OcrEngine {

	/**
	 * 指定画像をOCRする。
	 *
	 * @param image OCR対象画像
	 * @return 認識テキスト
	 */
	String extract(Path image);
}
