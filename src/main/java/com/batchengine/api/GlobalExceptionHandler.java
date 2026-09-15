package com.batchengine.api;

import com.batchengine.exception.BatchCapacityException;
import com.batchengine.exception.BatchNotFoundException;
import com.batchengine.exception.InvalidBatchFileException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({InvalidBatchFileException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(BatchNotFoundException.class)
    ResponseEntity<ApiError> notFound(BatchNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(BatchCapacityException.class)
    ResponseEntity<ApiError> unavailable(BatchCapacityException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "EXECUTOR_CAPACITY_EXCEEDED",
                exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                new ApiError(Instant.now(), status.value(), code, message, Map.of()));
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String code,
            String message,
            Map<String, String> details) {
    }
}
