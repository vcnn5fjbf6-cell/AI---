package com.company.procurement.modules.ehs.vo;

import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class EhsPermitVO {
    private Long id;
    private String permitNo;
    private Long parentId;
    private String permitType;
    private String title;
    private Long applicantUserId;
    private String applicantName;
    private String applicantPhone;
    private String applicantCompany;
    private String sourceType;
    private Long approvalRouteId;
    private String deptCode;
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
    private Long currentApproverId;
    private String currentApproverName;
    private String currentLevelName;
    private Long approvalInstanceId;
    private Integer submitRound;
    private List<String> requiredPermitTypes = new ArrayList<>();
    private List<Map<String, Object>> riskItems = new ArrayList<>();
    private Map<String, Object> safetyNotice = new LinkedHashMap<>();
    private List<Map<String, Object>> operationSteps = new ArrayList<>();
    private List<Map<String, Object>> signatures = new ArrayList<>();
    private List<Map<String, Object>> attachments = new ArrayList<>();
    private List<Map<String, Object>> samples = new ArrayList<>();
    private Map<String, Object> closure = new LinkedHashMap<>();
    private Long closedBy;
    private LocalDateTime closedAt;
    private String closeOutcome;
    private String closeNote;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canEdit;
    private boolean canSubmit;
    private boolean canApprove;
    private boolean canStart;
    private boolean canClose;
    private List<EhsPermitVO> children = new ArrayList<>();
    private List<ApprovalRecordVO> approvalRecords = new ArrayList<>();
}
