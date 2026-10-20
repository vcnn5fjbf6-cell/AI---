package com.company.procurement.modules.ehs.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class EhsPublicPermitVO {
    private Long id;
    private String permitNo;
    private Long parentId;
    private String permitType;
    private String title;
    private String applicantName;
    private String applicantPhone;
    private String applicantCompany;
    private Long approvalRouteId;
    private String deptName;
    private String contractorName;
    private String region;
    private String location;
    private String workContent;
    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private String status;
    private String approvalStatus;
    private Integer currentLevelNo;
    private String currentLevelName;
    private String currentApproverName;
    private Integer submitRound;
    private List<String> requiredPermitTypes = new ArrayList<>();
    private List<Map<String, Object>> riskItems = new ArrayList<>();
    private Map<String, Object> safetyNotice = new LinkedHashMap<>();
    private List<Map<String, Object>> operationSteps = new ArrayList<>();
    private List<Map<String, Object>> samples = new ArrayList<>();
    private Map<String, Object> closure = new LinkedHashMap<>();
    private String closeOutcome;
    private String closeNote;
    private boolean canEdit;
    private boolean canSubmit;
    private boolean canApprove;
    private boolean canStart;
    private boolean canSample;
    private boolean canClose;
    private List<EhsPublicPermitVO> children = new ArrayList<>();
    private List<EhsPublicApprovalRecordVO> approvalRecords = new ArrayList<>();
}
