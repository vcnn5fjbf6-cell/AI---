package com.company.procurement.modules.procurement.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("procurement_ai_session")
public class ProcurementAiSession extends BaseEntity {

    @TableField("session_no")
    private String sessionNo;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("source_user_key")
    private String sourceUserKey;

    @TableField("source_message_id")
    private String sourceMessageId;

    @TableField("applicant_user_id")
    private Long applicantUserId;

    @TableField("raw_text")
    private String rawText;

    @TableField("image_key")
    private String imageKey;

    @TableField("image_url")
    private String imageUrl;

    @TableField("req_title")
    private String reqTitle;

    @TableField("req_type")
    private String reqType;

    @TableField("plan_month")
    private String planMonth;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("budget_code")
    private String budgetCode;

    @TableField("urgency_level")
    private Integer urgencyLevel;

    @TableField("emergency_flag")
    private Integer emergencyFlag;

    @TableField("need_date")
    private LocalDate needDate;

    @TableField("status")
    private String status;

    @TableField("confirmed_candidate_id")
    private Long confirmedCandidateId;

    @TableField("monthly_demand_id")
    private Long monthlyDemandId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("ai_summary")
    private String aiSummary;

    @TableField("ai_error")
    private String aiError;

    @TableField("dify_request_json")
    private String difyRequestJson;

    @TableField("dify_response_text")
    private String difyResponseText;

    @TableField("dify_parsed_json")
    private String difyParsedJson;

    @TableField("remark")
    private String remark;
}
