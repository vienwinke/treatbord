package com.treatbord.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DuplicateKeyException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：统一转成 Result<T>（docs/API_DESIGN.md §1.2）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e, HttpServletRequest req) {
        log.warn("业务异常 code={} msg={} uri={}", e.getCode(), e.getMessage(), req.getRequestURI());
        return ResponseEntity.ok(Result.error(e.getCode(), e.getMessage()));
    }

    /** 参数校验失败（@Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + defaultMessage(f))
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(Result.error(ResultCode.BAD_REQUEST.getCode(), msg));
    }

    private String defaultMessage(FieldError f) {
        return f.getDefaultMessage() == null ? "非法值" : f.getDefaultMessage();
    }

    /** 参数缺失/类型错误/JSON 解析失败 */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleBadParam(Exception e) {
        return ResponseEntity.ok(Result.error(ResultCode.BAD_REQUEST));
    }

    /** 唯一键冲突（DB 层兜底，尽量在服务层提前校验） */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return ResponseEntity.ok(Result.error(ResultCode.CONFLICT));
    }

    /** 资源不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(ResultCode.NOT_FOUND));
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e, HttpServletRequest req) {
        log.error("未处理异常 uri={}", req.getRequestURI(), e);
        return ResponseEntity.ok(Result.error(ResultCode.INTERNAL_ERROR));
    }
}