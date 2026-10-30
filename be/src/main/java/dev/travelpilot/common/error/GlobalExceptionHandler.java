package dev.travelpilot.common.error;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every error to an RFC 9457 ProblemDetail with extra {@code code} and {@code requestId} properties. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException e) {
        if (e.code().status().is5xxServerError()) {
            log.warn("{}: {}", e.code(), e.getMessage(), e.getCause());
        }
        return problem(e.code(), e.getMessage());
    }

    @ExceptionHandler(ValidationFailedException.class)
    ProblemDetail handleBusinessValidation(ValidationFailedException e) {
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        pd.setProperty("errors", e.errors());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        pd.setProperty("errors", errors);
        return pd;
    }

    /** Method validation (e.g. @Valid body + constrained header) reports field errors here in Spring 7. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(HandlerMethodValidationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bean) {
                bean.getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(err -> errors.putIfAbsent(
                        name == null ? "param" : name, err.getDefaultMessage()));
            }
        }
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ProblemDetail handleBadRequest(Exception e) {
        return problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ProblemDetail handleNotFound(Exception e) {
        return problem(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage());
    }

    static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setTitle(code.name());
        pd.setProperty("code", code.name());
        String requestId = MDC.get("requestId");   // set by RequestIdFilter (T7.02)
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }
}
