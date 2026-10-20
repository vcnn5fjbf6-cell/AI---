package com.company.procurement.modules.ehs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ehs_permit")
public class EhsPermit extends BaseEntity {

    @TableField("permit_no")
    private String permitNo;
    @TableField("parent_id")
    private Long parentId;
    @TableField("permit_type")
    private String permitType;
    @TableField("title")
    private String title;
    @TableField("applicant_user_id")
    private Long applicantUserId;
    @TableField("applicant_name")
    private String applicantName;
    @TableField("applicant_phone")
    private String applicantPhone;
    @TableField("applicant_company")
    private String applicantCompany;
    @TableField("source_type")
    private String sourceType;
    @TableField("approval_route_id")
    private Long approvalRouteId;
    @TableField("dept_code")
    private String deptCode;
    @TableField("dept_name")
    private String deptName;
    @TableField("contractor_name")
    private String contractorName;
    @TableField("region")
    private String region;
    @TableField("location")
    private String location;
    @TableField("work_content")
    private String workContent;
    @TableField("planned_start_at")
    private LocalDateTime plannedStartAt;
    @TableField("planned_end_at")
    private LocalDateTime plannedEndAt;
    @TableField("actual_start_at")
    private LocalDateTime actualStartAt;
    @TableField("actual_end_at")
    private LocalDateTime actualEndAt;
    @TableField("status")
    private String status;
    @TableField("approval_status")
    private String approvalStatus;
    @TableField("current_level_no")
    private Integer currentLevelNo;
    @TableField("approver_user_ids_json")
    private String approverUserIdsJson;
    @TableField("special_approver_user_ids_json")
    private String specialApproverUserIdsJson;
    @TableField("approval_snapshot_json")
    private String approvalSnapshotJson;
    @TableField("current_approver_id")
    private Long currentApproverId;
    @TableField("current_approver_name")
    private String currentApproverName;
    @TableField("approval_instance_id")
    private Long approvalInstanceId;
    @TableField("submit_round")
    private Integer submitRound;
    @TableField("required_permit_types_json")
    private String requiredPermitTypesJson;
    @TableField("risk_items_json")
    private String riskItemsJson;
    @TableField("safety_notice_json")
    private String safetyNoticeJson;
    @TableField("operation_steps_json")
    private String operationStepsJson;
    @TableField("signatures_json")
    private String signaturesJson;
    @TableField("attachments_json")
    private String attachmentsJson;
    @TableField("samples_json")
    private String samplesJson;
    @TableField("closure_json")
    private String closureJson;
    @TableField("closed_by")
    private Long closedBy;
    @TableField("closed_at")
    private LocalDateTime closedAt;
    @TableField("close_outcome")
    private String closeOutcome;
    @TableField("close_note")
    private String closeNote;
    @TableField("remark")
    private String remark;
}
