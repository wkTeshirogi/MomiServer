package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogAttributes;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.lang.reflect.Field;
import java.util.stream.Stream;
import java.util.List;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** Controller層まで伝播した例外を共通のAPIエラーレスポンスへ変換するハンドラー。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private final Clock clock;

	/** エラー応答のタイムスタンプ取得用の時計を受け取る。
	 *
	 * @param clock 共通時計
	 */
	public GlobalExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	/**
	 * 楽観的ロック競合を409 Conflictへ変換する。
	 *
	 * @param e 発生した競合例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	@ExceptionHandler(OptimisticLockConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleOptimisticLockConflict(
			OptimisticLockConflictException e, HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", e.getMessage(), request);
	}

	@ExceptionHandler(AgentAuthException.class)
	public ResponseEntity<ApiErrorResponse> handleAgentAuth(
			AgentAuthException exception,
			HttpServletRequest request) {
		return response(HttpStatus.valueOf(exception.status()), exception.code(), exception.getMessage(), request);
	}

	/** メールアドレスが既に登録されている場合に409を返す。 */
	@ExceptionHandler(DuplicateEmailException.class)

	/**
	 * メールアドレス重複エラーを409 Conflictへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(DuplicateEmailException e,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", e.getMessage(), request);
	}

	/** リソースが存在しない場合に404を返す。 */
	@ExceptionHandler(IllegalArgumentException.class)

	/**
	 * 不正な引数エラーを400 Bad Requestへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e,
			HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)

	/**
	 * リソース未存在エラーを404 Not Foundへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException e,
			HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", e.getMessage(), request);
	}

	/** 未定義経路・静的リソース未存在
	 *
	 * Spring MVCの未存在エラーを共通の404レスポンスへ変換する。
	 *
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	public ResponseEntity<ApiErrorResponse> handleMissingRoute(HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
				CommonMessageConstants.ROUTE_NOT_FOUND, request);
	}

	/** {@code @Valid} による入力検証失敗を項目別エラー付きの400へ変換する。 */
	@ExceptionHandler(MethodArgumentNotValidException.class)

	/**
	 * Bean Validationの入力エラーを項目別エラー付き400 Bad Requestへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e,
			HttpServletRequest request) {
		List<ApiErrorResponse.FieldErrorDetail> errors = e.getBindingResult().getFieldErrors()
				.stream().map(error -> toFieldError(error, e.getBindingResult().getTarget())).toList();
		return validationResponse(request, errors);
	}

	/**
	 * Controllerメソッドの引数制約違反を項目別エラー付き400 Bad Requestへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodValidation(
			HandlerMethodValidationException e,
			HttpServletRequest request) {
		Stream<ApiErrorResponse.FieldErrorDetail> parameterErrors = e.getParameterValidationResults().stream().flatMap(result -> {
			String field = result.getMethodParameter().getParameterName();
			return result.getResolvableErrors().stream().map(error -> new ApiErrorResponse.FieldErrorDetail(
					field == null ? "request" : field, error.getDefaultMessage()));
		});
		Stream<ApiErrorResponse.FieldErrorDetail> crossParameterErrors = e.getCrossParameterValidationResults().stream()
				.map(error -> new ApiErrorResponse.FieldErrorDetail("request", error.getDefaultMessage()));
		List<ApiErrorResponse.FieldErrorDetail> errors = Stream.concat(parameterErrors, crossParameterErrors).toList();
		return validationResponse(request, errors);
	}

	/**
	 * サービス層などで発生したBean Validation違反を項目別エラー付き400 Bad Requestへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException e,
			HttpServletRequest request) {
		List<ApiErrorResponse.FieldErrorDetail> errors = e.getConstraintViolations().stream()
				.map(violation -> new ApiErrorResponse.FieldErrorDetail(
						violation.getPropertyPath().toString(), violation.getMessage()))
				.toList();
		return validationResponse(request, errors);
	}

	/** 必須パラメータの欠落や型不一致を400へ変換する。 */
	@ExceptionHandler({MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class})

	/**
	 * リクエストパラメータの欠落や型不一致を400 Bad Requestへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleParameter(Exception e, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
				CommonMessageConstants.REQUEST_PARAMETER_INVALID, request);
	}

	/** 不正なJSONや変換不能なリクエストボディに400を返す。 */
	@ExceptionHandler(HttpMessageNotReadableException.class)

	/**
	 * JSON読み取り失敗を400 Bad Requestへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
			HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
				CommonMessageConstants.REQUEST_BODY_UNREADABLE, request);
	}

	/** ログイン情報が一致しない場合に、詳細を伏せた401を返す。 */
	@ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})

	/**
	 * 認証失敗を401 Unauthorizedへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleAuthentication(RuntimeException e,
			HttpServletRequest request) {
		return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
				CommonMessageConstants.AUTHENTICATION_FAILED, request);
	}

	/** 無効または期限切れのJWTに401を返す。 */
	@ExceptionHandler({InvalidTokenException.class, JwtException.class})

	/**
	 * JWT検証失敗を401 Unauthorizedへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleToken(RuntimeException e,
			HttpServletRequest request) {
		return response(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
				CommonMessageConstants.TOKEN_INVALID, request);
	}

	/** 認証済みユーザーに必要な権限がない場合に403を返す。 */
	@ExceptionHandler(AccessDeniedException.class)

	/**
	 * 権限不足を403 Forbiddenへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e,
			HttpServletRequest request) {
		return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
				CommonMessageConstants.ACCESS_DENIED, request);
	}

	/** DBアクセス中の例外を記録し、内部情報を伏せた500を返す。 */
	@ExceptionHandler(DataAccessException.class)

	/**
	 * DBアクセス例外をログ出力し、500 Internal Server Errorへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleDatabase(DataAccessException e,
			HttpServletRequest request) {
		log.error("Database error: {} {}", request.getMethod(), request.getRequestURI(), e);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR",
				CommonMessageConstants.DATABASE_ERROR, request);
	}

	/** 未提供の保存操作等はサーバー障害ではなく、HTTPメソッド不許可として返す。 */
	@ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsupportedMethod(
			org.springframework.web.HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
		ResponseEntity<ApiErrorResponse> error = response(
				HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", request);
		java.util.Set<org.springframework.http.HttpMethod> allowed = exception.getSupportedHttpMethods();
		if (allowed == null) return error;
		return ResponseEntity.status(error.getStatusCode()).allow(allowed.toArray(org.springframework.http.HttpMethod[]::new))
				.body(error.getBody());
	}

	@ExceptionHandler(Exception.class)

	/**
	 * 個別に定義していない例外をログ出力し、500 Internal Server Errorへ変換する。
	 *
	 * @param e 発生した例外
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e,
			HttpServletRequest request) {
		log.error("Unexpected error: {} {}", request.getMethod(), request.getRequestURI(), e);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
				CommonMessageConstants.UNEXPECTED_ERROR, request);
	}

	/**
	 * SpringのフィールドエラーをAPIレスポンス用の項目別エラーへ変換する。
	 *
	 * @param error Springのフィールドエラー
	 * @param target 検証対象のリクエストDTO
	 * @return APIレスポンス用フィールドエラー
	 */
	private ApiErrorResponse.FieldErrorDetail toFieldError(FieldError error, Object target) {
		Field field = target == null ? null : ReflectionUtils.findField(target.getClass(), error.getField());
		JsonProperty property = field == null ? null : field.getAnnotation(JsonProperty.class);
		String name = property == null || property.value().isEmpty() ? error.getField() : property.value();
		return new ApiErrorResponse.FieldErrorDetail(name, error.getDefaultMessage());
	}

	/**
	 * 入力検証エラーの共通レスポンスを生成する。
	 *
	 * @param request HTTPリクエスト
	 * @param fieldErrors 項目別エラー一覧
	 * @return APIエラーレスポンス
	 */
	private ResponseEntity<ApiErrorResponse> validationResponse(HttpServletRequest request,
			List<ApiErrorResponse.FieldErrorDetail> fieldErrors) {
		return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
				CommonMessageConstants.INPUT_INVALID, request, fieldErrors);
	}

	/**
	 * 項目別エラーなしの共通レスポンスを生成する。
	 *
	 * @param status HTTPステータス
	 * @param code アプリケーション固有エラーコード
	 * @param message エラーメッセージ
	 * @param request HTTPリクエスト
	 * @return APIエラーレスポンス
	 */
	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code,
			String message, HttpServletRequest request) {
		return response(status, code, message, request, List.of());
	}

	/**
	 * HTTPステータス、リクエスト情報、項目別エラーから共通レスポンスを生成する。
	 *
	 * @param status HTTPステータス
	 * @param code アプリケーション固有エラーコード
	 * @param message エラーメッセージ
	 * @param request HTTPリクエスト
	 * @param fieldErrors 項目別エラー一覧
	 * @return APIエラーレスポンス
	 */
	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code,
			String message, HttpServletRequest request,
			List<ApiErrorResponse.FieldErrorDetail> fieldErrors) {
		request.setAttribute(ApiAccessLogAttributes.ERROR_CODE, code);
		return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(),
				status.getReasonPhrase(), code, message, request.getRequestURI(), fieldErrors, clock));
	}
}
