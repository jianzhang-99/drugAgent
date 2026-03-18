package com.liang.drugagent.controller;

import com.liang.drugagent.domain.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 兜底异常处理，避免异常只返回给前端而服务端没有统一日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Business validation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(Result.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Request validation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(Map.of(
                "error", "请求参数校验失败",
                "message", ex.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("未知校验错误")
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnhandledException(Exception ex) {
        log.error("Unhandled server exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("服务器内部异常，请稍后重试"));
    }
}
