package com.company.procurement.modules.procurement.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseRequestCreateDTO {

    private String reqNo;

    @NotBlank(message = "Request title must not be blank")
    private String reqTitle;

    @NotBlank(message = "Request type must not be blank")
    private String reqType;

    @NotNull(message = "Applicant user ID must not be null")
    private Long applicantUserId;

    @NotNull(message = "Purchaser user ID must not be null")
    private Long purchaserUserId;

    @NotNull(message = "Region ID must not be null")
    private Long regionId;

    private Long buildingScopeId;
    private Long applicantSubjectId;
    private Long approvalPolicyId;
    private String specialtyCode;
    private Integer professionalReviewRequired;

    private String deptCode;
    private String deptName;
    private String budgetCode;
    private String actingReason;

    @NotBlank(message = "Currency must not be blank")
    private String currency;

    @NotNull(message = "Total amount must not be null")
    private BigDecimal totalAmount;

    @NotNull(message = "Urgency level must not be null")
    private Integer urgencyLevel;

    private LocalDate needDate;
    private String reqReason;
    private String aiReviewOpinion;
    private String awardStrategy;
    private String remark;
    private List<Long> copyUserIds;

    @Valid
    @NotEmpty(message = "Request items must not be empty")
    private List<PurchaseRequestItemDTO> items;
}
