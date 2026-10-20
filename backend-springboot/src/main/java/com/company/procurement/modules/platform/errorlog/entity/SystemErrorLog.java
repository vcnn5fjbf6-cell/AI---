package com.company.procurement.modules.platform.errorlog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_error_log")
public class SystemErrorLog extends BaseEntity {

    @TableField("module_code")
    private String moduleCode;

    @TableField("function_name")
    private String functionName;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("exception_class")
    private String exceptionClass;

    @TableField("stack_trace")
    private String stackTrace;

    @TableField("request_method")
    private String requestMethod;

    @TableField("request_uri")
    private String requestUri;

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("client_ip")
    private String clientIp;

    @TableField("status")
    private String status;

    @TableField("fix_remark")
    private String fixRemark;

    @TableField("fixed_by")
    private Long fixedBy;

    @TableField("fixed_at")
    private LocalDateTime fixedAt;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
