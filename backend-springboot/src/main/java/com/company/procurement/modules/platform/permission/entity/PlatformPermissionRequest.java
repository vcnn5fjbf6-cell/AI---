package com.company.procurement.modules.platform.permission.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_permission_request")
public class PlatformPermissionRequest extends BaseEntity {

    @TableField("applicant_user_id")
    private Long applicantUserId;

    @TableField("applicant_name")
    private String applicantName;

    @TableField("applicant_email")
    private String applicantEmail;

    @TableField("applicant_mobile")
    private String applicantMobile;

    @TableField("unit_id")
    private Long unitId;

    @TableField("app_code")
    private String appCode;

    @TableField("role_code")
    private String roleCode;

    @TableField("reason")
    private String reason;

    @TableField("status")
    private String status;

    @TableField("reviewer_user_id")
    private Long reviewerUserId;

    @TableField("review_comment")
    private String reviewComment;

    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;
}
