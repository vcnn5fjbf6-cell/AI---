package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("temporary_supplier_recommendation")
public class TemporarySupplierRecommendation extends BaseEntity {

    @TableField("recommendation_no")
    private String recommendationNo;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("request_no")
    private String requestNo;

    @TableField("request_title")
    private String requestTitle;

    @TableField("region_id")
    private Long regionId;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("supplier_code")
    private String supplierCode;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("contact_email")
    private String contactEmail;

    @TableField("recommended_by")
    private Long recommendedBy;

    @TableField("recommended_by_name")
    private String recommendedByName;

    @TableField("recommend_reason")
    private String recommendReason;

    @TableField("status")
    private String status;

    @TableField("current_role_code")
    private String currentRoleCode;

    @TableField("room_manager_user_id")
    private Long roomManagerUserId;

    @TableField("room_manager_name")
    private String roomManagerName;

    @TableField("room_manager_comment")
    private String roomManagerComment;

    @TableField("room_manager_reviewed_at")
    private LocalDateTime roomManagerReviewedAt;

    @TableField("region_manager_user_id")
    private Long regionManagerUserId;

    @TableField("region_manager_name")
    private String regionManagerName;

    @TableField("region_manager_comment")
    private String regionManagerComment;

    @TableField("region_manager_reviewed_at")
    private LocalDateTime regionManagerReviewedAt;

    @TableField("rejected_by")
    private Long rejectedBy;

    @TableField("rejected_by_name")
    private String rejectedByName;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("rejected_at")
    private LocalDateTime rejectedAt;

    @TableField("remark")
    private String remark;
}
