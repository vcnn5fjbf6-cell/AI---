package com.company.procurement.modules.procurement.request.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_request")
public class PurchaseRequest extends BaseEntity {

    @TableField("req_no")
    private String reqNo;

    @TableField("req_title")
    private String reqTitle;

    @TableField("req_type")
    private String reqType;

    @TableField("applicant_user_id")
    private Long applicantUserId;

    @TableField("purchaser_user_id")
    private Long purchaserUserId;

    @TableField("region_id")
    private Long regionId;

    @TableField("building_scope_id")
    private Long buildingScopeId;

    @TableField("applicant_subject_id")
    private Long applicantSubjectId;

    @TableField("approval_policy_id")
    private Long approvalPolicyId;

    @TableField("approval_snapshot_json")
    private String approvalSnapshotJson;

    @TableField("specialty_code")
    private String specialtyCode;

    @TableField("professional_review_required")
    private Integer professionalReviewRequired;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("budget_code")
    private String budgetCode;

    @TableField("acting_user_id")
    private Long actingUserId;

    @TableField("acting_user_name")
    private String actingUserName;

    @TableField("acting_reason")
    private String actingReason;

    @TableField("currency")
    private String currency;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("reference_total_amount")
    private BigDecimal referenceTotalAmount;

    @TableField("price_source")
    private String priceSource;

    @TableField("urgency_level")
    private Integer urgencyLevel;

    @TableField("need_date")
    private LocalDate needDate;

    @TableField("req_reason")
    private String reqReason;

    @TableField("ai_review_opinion")
    private String aiReviewOpinion;

    @TableField("award_strategy")
    private String awardStrategy;

    @TableField("submit_count")
    private Integer submitCount;

    @TableField("current_node_no")
    private Integer currentNodeNo;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("status")
    private String status;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("selected_quote_id")
    private Long selectedQuoteId;

    @TableField("leader_confirm_status")
    private String leaderConfirmStatus;

    @TableField("leader_confirm_remark")
    private String leaderConfirmRemark;

    @TableField("leader_confirmed_by")
    private Long leaderConfirmedBy;

    @TableField("leader_confirmed_at")
    private LocalDateTime leaderConfirmedAt;

    @TableField("quote_countdown_started_at")
    private LocalDateTime quoteCountdownStartedAt;

    @TableField("quote_countdown_due_at")
    private LocalDateTime quoteCountdownDueAt;

    @TableField("direct_award_applied")
    private Integer directAwardApplied;

    @TableField("direct_award_supplier_id")
    private Long directAwardSupplierId;

    @TableField("direct_award_supplier_name")
    private String directAwardSupplierName;

    @TableField("direct_award_threshold_amount")
    private BigDecimal directAwardThresholdAmount;

    @TableField("direct_award_reason")
    private String directAwardReason;

    @TableField("returned_at")
    private LocalDateTime returnedAt;

    @TableField("rejected_at")
    private LocalDateTime rejectedAt;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    @TableField("cancel_reason")
    private String cancelReason;

    @TableField("remark")
    private String remark;
}
