package com.workshop.loanservice.exception;

import com.workshop.loanservice.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates exceptions into consistent JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Spring MVC exceptions (missing parameter, unsupported method, unknown route, ...)
     * already carry an appropriate status; preserve it and only wrap the body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            String detail = springError.getBody().getDetail();
            return build(status, detail != null ? detail : ex.getMessage(), request);
        }
        log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String message, HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
        ApiError body = new ApiError(status.value(), reason, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
