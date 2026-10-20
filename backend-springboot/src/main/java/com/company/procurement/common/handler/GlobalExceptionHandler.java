package com.company.procurement.common.handler;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.errorlog.service.SystemErrorLogService;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDate;
import java.util.Set;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final SystemErrorLogService errorLogService;
    private static final Set<String> NON_ALERT_BIZ_CODES = Set.of(
        "UNAUTHORIZED",
        "LOGIN_FAILED",
        "LOGIN_LOCKED",
        "PASSWORD_EXPIRED",
        "OLD_PASSWORD_INVALID",
        "FORBIDDEN",
        "PROCUREMENT_WINDOW_CLOSED",
        "DEMAND_POOL_PUBLISH_DATE_INVALID",
        "QUOTE_COUNT_NOT_ENOUGH",
        "QUOTE_ITEM_MISSING",
        "FLOW_TEST_REQUEST_NOT_FOUND",
        "SUPPLIER_HAS_LINKED_USERS",
        "SUPPLIER_TAX_NO_EXISTS",
        "SUPPLIER_NOT_FOUND",
        "ITEM_DECISION_INVALID",
        "PURCHASE_REQUEST_STATUS_INVALID",
        "FEISHU_USER_NOT_BOUND",
        "USER_EMAIL_CLEAR_CONFIRM_REQUIRED",
        "VALIDATION_ERROR",
        "BIND_ERROR",
        "NOT_FOUND"
    );

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex, HttpServletRequest request) {
        recordError(ex.getCode(), ex.getMessage(), ex, request);
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError == null ? "Validation failed" : fieldError.getDefaultMessage();
        recordError("VALIDATION_ERROR", message, ex, request);
        return Result.fail("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex, HttpServletRequest request) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError == null ? "Parameter binding failed" : fieldError.getDefaultMessage();
        recordError("BIND_ERROR", message, ex, request);
        return Result.fail("BIND_ERROR", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        recordError("VALIDATION_ERROR", ex.getMessage(), ex, request);
        return Result.fail("VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                              HttpServletRequest request) {
        String message = "缺少必填参数：" + ex.getParameterName();
        recordError("VALIDATION_ERROR", message, ex, request);
        return Result.fail("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = requestBodyMessage(ex);
        recordError("VALIDATION_ERROR", message, ex, request);
        return Result.fail("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        recordError("NOT_FOUND", ex.getMessage(), ex, request);
        return Result.fail("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex, HttpServletRequest request) {
        recordError("SYSTEM_ERROR", ex.getMessage(), ex, request);
        return Result.fail("SYSTEM_ERROR", "System error, please contact administrator");
    }

    private void recordError(String code, String message, Throwable throwable, HttpServletRequest request) {
        if (NON_ALERT_BIZ_CODES.contains(code)) {
            return;
        }
        try {
            errorLogService.recordException(code, message, throwable, request);
        } catch (Exception ignored) {
            // Avoid masking the original business response when error logging itself fails.
        }
    }

    private String requestBodyMessage(HttpMessageNotReadableException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof InvalidFormatException invalidFormat
                && LocalDate.class.equals(invalidFormat.getTargetType())) {
                return "日期格式不正确，请使用 yyyy-MM-dd，例如 2026-07-10";
            }
            cause = cause.getCause();
        }
        return "请求内容格式不正确，请检查表单输入后重试";
    }
}
