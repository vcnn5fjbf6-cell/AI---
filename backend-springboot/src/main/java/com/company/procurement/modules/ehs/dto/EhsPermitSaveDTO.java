package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class EhsPermitSaveDTO {
    @NotNull(message = "请选择作业所属部门")
    private Long approvalRouteId;
    @NotBlank(message = "作业名称不能为空")
    private String title;
    private String contractorName;
    private String region;
    @NotBlank(message = "作业地点不能为空")
    private String location;
    @NotBlank(message = "作业内容不能为空")
    private String workContent;
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime plannedStartAt;
    @NotNull(message = "计划结束时间不能为空")
    private LocalDateTime plannedEndAt;
    private List<String> requiredPermitTypes = new ArrayList<>();
    private List<Map<String, Object>> riskItems = new ArrayList<>();
    private Map<String, Object> safetyNotice;
    private List<Map<String, Object>> operationSteps = new ArrayList<>();
    private List<Map<String, Object>> signatures = new ArrayList<>();
    private List<Map<String, Object>> attachments = new ArrayList<>();
    private String remark;
}
