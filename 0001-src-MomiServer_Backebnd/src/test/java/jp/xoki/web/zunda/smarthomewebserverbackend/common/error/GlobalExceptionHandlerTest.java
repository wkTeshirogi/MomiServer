package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.core.MethodParameter;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

class GlobalExceptionHandlerTest {

	private GlobalExceptionHandler handler;
	private MockHttpServletRequest request;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionHandler(ApplicationClockFactory.systemClock());
		request = new MockHttpServletRequest("POST", "/api/test");
	}

	@Test
	void vehicleValidationPreservesJsonFieldNames() {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new VehicleSaveRequest(), "vehicleSaveRequest");
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			SpringValidatorAdapter validator = new org.springframework.validation.beanvalidation.SpringValidatorAdapter(factory.getValidator());
			validator.validate(binding.getTarget(), binding);
		}
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		when(exception.getBindingResult()).thenReturn(binding);

		ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().fieldErrors()).extracting(ApiErrorResponse.FieldErrorDetail::field)
				.containsExactlyInAnyOrder("display_name", "registration_number", "chassis_number", "vehicle_name",
						"inspection_expiry_date");
	}

	@Test
	void validationRetainsUnannotatedFieldNames() {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new ValidationTarget(""), "validationTarget");
		binding.addError(new FieldError("validationTarget", "name", "required"));
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		when(exception.getBindingResult()).thenReturn(binding);

		ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception, request);

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().fieldErrors())
				.containsExactly(new ApiErrorResponse.FieldErrorDetail("name", "required"));
	}

	@Test
	void validationWithoutTargetRetainsFieldNames() {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(null, "request");
		binding.addError(new FieldError("request", "unknown", "invalid"));
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
		when(exception.getBindingResult()).thenReturn(binding);

		ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception, request);

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().fieldErrors())
				.containsExactly(new ApiErrorResponse.FieldErrorDetail("unknown", "invalid"));
	}

	@Test
	void handleConstraintViolationReturnsValidationErrorWithFieldDetails() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<ValidationTarget>> violations = validator.validate(new ValidationTarget(""));

			ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(
					new ConstraintViolationException(violations), request);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody()).isNotNull();
			assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
			assertThat(response.getBody().fieldErrors()).singleElement().satisfies(error -> {
				assertThat(error.field()).isEqualTo("name");
				assertThat(error.message()).isNotBlank();
			});
		}
	}

	@Test
	void handleMethodValidationReturnsValidationError() {
		MethodValidationResult validationResult = mock(MethodValidationResult.class);
		when(validationResult.isForReturnValue()).thenReturn(false);
		when(validationResult.getParameterValidationResults()).thenReturn(List.of());
		when(validationResult.getCrossParameterValidationResults()).thenReturn(List.of());

		ResponseEntity<ApiErrorResponse> response = handler.handleMethodValidation(
				new HandlerMethodValidationException(validationResult), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
	}

	@Test
	void handleOptimisticLockConflictReturnsConflict() {
		ResponseEntity<ApiErrorResponse> response = handler.handleOptimisticLockConflict(
				new OptimisticLockConflictException("最新データを再取得してください。"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
	}

	@Test
	void methodValidationPreservesNamedUnnamedAndCrossParameterErrorsInOrder() {
		MethodValidationResult validationResult = mock(MethodValidationResult.class);
		MethodParameter namedParameter = mock(MethodParameter.class);
		MethodParameter unnamedParameter = mock(MethodParameter.class);
		ParameterValidationResult namedResult = mock(ParameterValidationResult.class);
		ParameterValidationResult unnamedResult = mock(ParameterValidationResult.class);
		when(namedParameter.getParameterName()).thenReturn("name");
		when(namedResult.getMethodParameter()).thenReturn(namedParameter);
		when(unnamedResult.getMethodParameter()).thenReturn(unnamedParameter);
		when(namedResult.getResolvableErrors()).thenReturn(List.of(
				new DefaultMessageSourceResolvable(new String[0], "required"),
				new DefaultMessageSourceResolvable(new String[0], "invalid")));
		when(unnamedResult.getResolvableErrors()).thenReturn(List.of(new DefaultMessageSourceResolvable(new String[0], "unnamed")));
		when(validationResult.isForReturnValue()).thenReturn(false);
		when(validationResult.getParameterValidationResults()).thenReturn(List.of(namedResult, unnamedResult));
		when(validationResult.getCrossParameterValidationResults()).thenReturn(List.of(new DefaultMessageSourceResolvable(new String[0], "cross")));

		ResponseEntity<ApiErrorResponse> response = handler.handleMethodValidation(new HandlerMethodValidationException(validationResult), request);

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().fieldErrors()).containsExactly(
				new ApiErrorResponse.FieldErrorDetail("name", "required"),
				new ApiErrorResponse.FieldErrorDetail("name", "invalid"),
				new ApiErrorResponse.FieldErrorDetail("request", "unnamed"),
				new ApiErrorResponse.FieldErrorDetail("request", "cross"));
	}

	private record ValidationTarget(@NotBlank String name) {
	}
}
