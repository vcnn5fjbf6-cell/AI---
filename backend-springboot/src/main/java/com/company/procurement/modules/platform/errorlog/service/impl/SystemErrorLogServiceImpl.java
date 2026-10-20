package com.company.procurement.modules.platform.errorlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.i18n.ErrorMessageLocalizer;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.platform.errorlog.dto.SystemErrorLogPageQueryDTO;
import com.company.procurement.modules.platform.errorlog.dto.SystemErrorLogStatusDTO;
import com.company.procurement.modules.platform.errorlog.entity.SystemErrorLog;
import com.company.procurement.modules.platform.errorlog.mapper.SystemErrorLogMapper;
import com.company.procurement.modules.platform.errorlog.service.SystemErrorLogService;
import com.company.procurement.modules.platform.errorlog.vo.SystemErrorLogVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class SystemErrorLogServiceImpl extends ServiceImpl<SystemErrorLogMapper, SystemErrorLog> implements SystemErrorLogService {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_STACK_LENGTH = 12000;

    @Override
    public void recordException(String errorCode, String errorMessage, Throwable throwable, HttpServletRequest request) {
        SystemErrorLog log = new SystemErrorLog();
        String requestUri = request == null ? "" : request.getRequestURI();
        String requestMethod = request == null ? "" : request.getMethod();
        AuthUser authUser = AuthContextHolder.get();

        log.setModuleCode(resolveModuleCode(requestUri));
        log.setFunctionName(resolveFunctionName(requestMethod, requestUri));
        log.setErrorCode(defaultText(errorCode, "SYSTEM_ERROR"));
        String message = defaultText(errorMessage, throwable == null ? "Unknown error" : throwable.getMessage());
        log.setErrorMessage(truncate(ErrorMessageLocalizer.localize(errorCode, message), MAX_MESSAGE_LENGTH));
        log.setExceptionClass(throwable == null ? null : throwable.getClass().getName());
        log.setStackTrace(truncate(stackTrace(throwable), MAX_STACK_LENGTH));
        log.setRequestMethod(requestMethod);
        log.setRequestUri(requestUri);
        log.setUserId(authUser == null ? null : authUser.getUserId());
        log.setUsername(authUser == null ? null : authUser.getUsername());
        log.setClientIp(resolveClientIp(request));
        log.setStatus("OPEN");
        log.setOccurredAt(LocalDateTime.now());
        save(log);
    }

    @Override
    public PageResult<SystemErrorLogVO> page(SystemErrorLogPageQueryDTO queryDTO) {
        LambdaQueryWrapper<SystemErrorLog> wrapper = new LambdaQueryWrapper<SystemErrorLog>()
            .orderByDesc(SystemErrorLog::getOccurredAt)
            .orderByDesc(SystemErrorLog::getId);
        if (StringUtils.hasText(queryDTO.getModuleCode())) {
            wrapper.eq(SystemErrorLog::getModuleCode, queryDTO.getModuleCode().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(SystemErrorLog::getStatus, queryDTO.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            wrapper.and(scope -> scope
                .like(SystemErrorLog::getFunctionName, keyword)
                .or()
                .like(SystemErrorLog::getErrorCode, keyword)
                .or()
                .like(SystemErrorLog::getErrorMessage, keyword)
                .or()
                .like(SystemErrorLog::getRequestUri, keyword)
                .or()
                .like(SystemErrorLog::getUsername, keyword));
        }
        Page<SystemErrorLog> page = page(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public SystemErrorLogVO detail(Long id) {
        SystemErrorLog log = getById(id);
        if (log == null) {
            throw new BizException("ERROR_LOG_NOT_FOUND", "错误记录不存在");
        }
        return toVO(log);
    }

    @Override
    public void updateStatus(Long id, SystemErrorLogStatusDTO dto) {
        SystemErrorLog log = getById(id);
        if (log == null) {
            throw new BizException("ERROR_LOG_NOT_FOUND", "错误记录不存在");
        }
        String status = normalizeStatus(dto.getStatus());
        log.setStatus(status);
        log.setFixRemark(dto.getFixRemark());
        if ("FIXED".equals(status) || "IGNORED".equals(status)) {
            AuthUser authUser = AuthContextHolder.get();
            log.setFixedBy(authUser == null ? null : authUser.getUserId());
            log.setFixedAt(LocalDateTime.now());
        } else {
            log.setFixedBy(null);
            log.setFixedAt(null);
        }
        updateById(log);
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("OPEN".equals(normalized) || "FIXED".equals(normalized) || "IGNORED".equals(normalized)) {
            return normalized;
        }
        throw new BizException("ERROR_LOG_STATUS_INVALID", "错误处理状态不合法");
    }

    private SystemErrorLogVO toVO(SystemErrorLog log) {
        SystemErrorLogVO vo = new SystemErrorLogVO();
        vo.setId(log.getId());
        vo.setModuleCode(log.getModuleCode());
        vo.setFunctionName(log.getFunctionName());
        vo.setErrorCode(log.getErrorCode());
        vo.setErrorMessage(ErrorMessageLocalizer.localize(log.getErrorCode(), log.getErrorMessage()));
        vo.setExceptionClass(log.getExceptionClass());
        vo.setStackTrace(log.getStackTrace());
        vo.setRequestMethod(log.getRequestMethod());
        vo.setRequestUri(log.getRequestUri());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setClientIp(log.getClientIp());
        vo.setStatus(log.getStatus());
        vo.setFixRemark(log.getFixRemark());
        vo.setFixedBy(log.getFixedBy());
        vo.setFixedAt(log.getFixedAt());
        vo.setOccurredAt(log.getOccurredAt());
        vo.setCreatedAt(log.getCreatedAt());
        vo.setUpdatedAt(log.getUpdatedAt());
        return vo;
    }

    private String resolveModuleCode(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "UNKNOWN";
        }
        if (uri.startsWith("/api/auth")) {
            return "AUTH";
        }
        if (uri.startsWith("/api/admin/platform") || uri.startsWith("/api/platform") || uri.startsWith("/api/admin/error-logs")
            || uri.startsWith("/api/admin/users") || uri.startsWith("/api/admin/roles")) {
            return "PLATFORM";
        }
        if (uri.startsWith("/api/supplier")) {
            return "SUPPLIER";
        }
        if (uri.startsWith("/api/workflow") || uri.startsWith("/api/procurement")
            || uri.startsWith("/api/admin/procurement") || uri.startsWith("/api/integrations/shopping")) {
            return "PROCUREMENT";
        }
        if (uri.startsWith("/api/integrations")) {
            return "INTEGRATION";
        }
        return "UNKNOWN";
    }

    private String resolveFunctionName(String method, String uri) {
        String normalizedMethod = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "REQUEST";
        return normalizedMethod + " " + defaultText(uri, "unknown");
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
