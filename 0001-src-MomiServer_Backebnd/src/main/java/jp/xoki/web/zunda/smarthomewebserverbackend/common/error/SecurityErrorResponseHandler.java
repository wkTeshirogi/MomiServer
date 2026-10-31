package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Clock;

/** Spring Securityフィルタ内で発生した401/403を共通JSON形式で返すコンポーネント。 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseHandler {
	private final ObjectMapper objectMapper;

	private final Clock clock;

	/**
	 * HTTPレスポンスへ共通エラーJSONを書き込む。
	 *
	 * @param request エラーが発生したリクエスト
	 * @param response 書き込み対象のHTTPレスポンス
	 * @param status HTTPステータスコード
	 * @param error HTTPステータス理由句
	 * @param code アプリケーション固有エラーコード
	 * @param message クライアント向けメッセージ
	 * @throws IOException レスポンス書き込みに失敗した場合
	 */
	public void write(HttpServletRequest request, HttpServletResponse response,
			int status, String error, String code, String message) throws IOException {
		request.setAttribute(ApiAccessLogAttributes.ERROR_CODE, code);
		response.setStatus(status);
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
				status, error, code, message, request.getRequestURI(), clock));
	}
}
