package com.company.procurement.modules.platform.errorlog.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemErrorLogVO {

    private Long id;
    private String moduleCode;
    private String functionName;
    private String errorCode;
    private String errorMessage;
    private String exceptionClass;
    private String stackTrace;
    private String requestMethod;
    private String requestUri;
    private Long userId;
    private String username;
    private String clientIp;
    private String status;
    private String fixRemark;
    private Long fixedBy;
    private LocalDateTime fixedAt;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
