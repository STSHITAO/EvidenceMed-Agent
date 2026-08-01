package com.evidencemed.agent.api;

import com.evidencemed.agent.infrastructure.model.ModelServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ModelServiceException.class)
    public ResponseEntity<ApiError> modelUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("MODEL_UNAVAILABLE", "模型服务暂时不可用，请稍后重试或转人工复核", Instant.now()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "请求处理失败，内部详情已隐藏", Instant.now()));
    }

    public record ApiError(String code, String message, Instant timestamp) {
    }
}
