package com.company.procurement.modules.procurement.request.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseRequestVO {

    private Long id;
    private String reqNo;
    private String reqTitle;
    private String reqType;
    private Long applicantUserId;
    private String applicantName;
    private String applicantEmployeeNo;
    private Long purchaserUserId;
    private Long regionId;
    private Long buildingScopeId;
    private Long applicantSubjectId;
    private Long approvalPolicyId;
    private String specialtyCode;
    private Integer professionalReviewRequired;
    private String deptCode;
    private String deptName;
    private String receivingCity;
    private String budgetCode;
    private Long actingUserId;
    private String actingUserName;
    private String actingReason;
    private Boolean actingDelegated;
    private String currency;
    private BigDecimal totalAmount;
    private BigDecimal referenceTotalAmount;
    private String priceSource;
    private Integer urgencyLevel;
    private LocalDate needDate;
    private String reqReason;
    private String aiReviewOpinion;
    private String awardStrategy;
    private Integer submitCount;
    private Integer currentNodeNo;
    private String currentNodeName;
    private String approvalStatus;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private Long selectedQuoteId;
    private String leaderConfirmStatus;
    private String leaderConfirmRemark;
    private Long leaderConfirmedBy;
    private LocalDateTime leaderConfirmedAt;
    private LocalDateTime quoteCountdownStartedAt;
    private LocalDateTime quoteCountdownDueAt;
    private LocalDateTime currentApprovalDueAt;
    private Boolean overPriceReviewRequired;
    private Boolean directAwardApplied;
    private Long directAwardSupplierId;
    private String directAwardSupplierName;
    private BigDecimal directAwardThresholdAmount;
    private String directAwardReason;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Long> copyUserIds;
    private List<PurchaseRequestItemVO> items;
}
