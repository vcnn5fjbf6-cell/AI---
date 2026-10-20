package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class EhsPublicPermitSaveDTO {

    @NotNull(message = "请选择作业所属部门")
    private Long approvalRouteId;

    @NotBlank(message = "申请人姓名不能为空")
    @Size(max = 128, message = "申请人姓名不能超过128个字符")
    private String applicantName;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^[0-9+() -]{6,32}$", message = "联系电话格式不正确")
    private String applicantPhone;

    @Size(max = 255, message = "申请单位不能超过255个字符")
    private String applicantCompany;

    @NotBlank(message = "作业名称不能为空")
    @Size(max = 255, message = "作业名称不能超过255个字符")
    private String title;
    @Size(max = 255, message = "施工单位不能超过255个字符")
    private String contractorName;
    @Size(max = 128, message = "作业区域不能超过128个字符")
    private String region;
    @NotBlank(message = "作业地点不能为空")
    @Size(max = 255, message = "作业地点不能超过255个字符")
    private String location;
    @NotBlank(message = "作业内容不能为空")
    @Size(max = 5000, message = "作业内容不能超过5000个字符")
    private String workContent;
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime plannedStartAt;
    @NotNull(message = "计划结束时间不能为空")
    private LocalDateTime plannedEndAt;
    @Size(max = 6, message = "专项作业票不能超过6类")
    private List<String> requiredPermitTypes = new ArrayList<>();
    @Size(max = 50, message = "风险与控制措施不能超过50项")
    private List<Map<String, Object>> riskItems = new ArrayList<>();
    private Map<String, Object> safetyNotice;
    @Size(max = 100, message = "作业步骤不能超过100项")
    private List<Map<String, Object>> operationSteps = new ArrayList<>();
    @Size(max = 20, message = "签名记录不能超过20项")
    private List<Map<String, Object>> signatures = new ArrayList<>();
    @Size(max = 20, message = "附件不能超过20项")
    private List<Map<String, Object>> attachments = new ArrayList<>();
    @Size(max = 1000, message = "备注不能超过1000个字符")
    private String remark;

    // Honeypot field. Real users never see or fill it.
    private String website;
}
