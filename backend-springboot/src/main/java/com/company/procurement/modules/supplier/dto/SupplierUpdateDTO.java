package com.company.procurement.modules.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SupplierUpdateDTO {

    private String supplierCode;

    @NotBlank(message = "Supplier name must not be blank")
    private String supplierName;

    private String supplierShortName;

    @NotBlank(message = "Tax number must not be blank")
    private String taxNo;

    @NotBlank(message = "Supplier type must not be blank")
    private String supplierType;

    @NotNull(message = "Region ID must not be null")
    private Long regionId;

    @NotBlank(message = "Contact name must not be blank")
    private String contactName;

    @NotBlank(message = "Contact phone must not be blank")
    private String contactPhone;

    private String contactEmail;
    private String address;
    private String bankName;
    private String bankAccount;
    private String invoiceTitle;
    private String defaultPaymentTerm;
    private String ratingLevel;

    @NotBlank(message = "Onboarding status must not be blank")
    private String onboardingStatus;

    @NotBlank(message = "Status must not be blank")
    private String status;

    private Integer blacklistFlag;
    private Integer dataIsolationFlag;
    private String blacklistReason;
    private LocalDate entryDate;
    private String remark;
}
