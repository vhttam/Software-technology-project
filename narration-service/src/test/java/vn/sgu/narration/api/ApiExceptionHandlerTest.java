package vn.sgu.narration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void appExceptionPreservesContractStatusCodeAndCorrelationId() {
        MockHttpServletRequest request = request();
        AppException exception = new AppException(HttpStatus.CONFLICT, "ACTIVE_JOB_EXISTS", "Conflict",
                java.util.Map.of("jobId", "j-1"));

        var response = handler.handleApp(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorCode()).isEqualTo("ACTIVE_JOB_EXISTS");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-1");
        assertThat(response.getBody().details()).isEqualTo(java.util.Map.of("jobId", "j-1"));
    }

    @Test
    void beanValidationErrorReturnsBadRequestWithFieldDetails() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "contentId", "must not be blank"));
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("validationProbe", String.class), 0);
        var exception = new MethodArgumentNotValidException(parameter, binding);

        var response = handler.handleValidation(exception, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(((java.util.Map<?, ?>) response.getBody().details()).get("contentId"))
                .isEqualTo("must not be blank");
    }

    @Test
    void unexpectedExceptionIsSanitizedAsInternalError() {
        var response = handler.handleUnexpected(new IllegalStateException("secret"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-1");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "corr-1");
        return request;
    }

    private void validationProbe(String value) {}
}
