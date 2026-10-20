package com.company.procurement.modules.procurement.assistant.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProcurementAiSessionVO {

    private Long id;
    private String sessionNo;
    private String sourceChannel;
    private Long applicantUserId;
    private String rawText;
    private String imageKey;
    private String imageUrl;
    private String reqTitle;
    private String reqType;
    private String planMonth;
    private String deptCode;
    private String deptName;
    private String budgetCode;
    private Integer urgencyLevel;
    private Boolean emergency;
    private LocalDate needDate;
    private String status;
    private Long confirmedCandidateId;
    private Long monthlyDemandId;
    private Long purchaseRequestId;
    private String aiSummary;
    private String aiError;
    private String difyRequestJson;
    private String difyResponseText;
    private String difyParsedJson;
    private LocalDateTime createdAt;
    private List<ProcurementAiCandidateVO> candidates = new ArrayList<>();
}
