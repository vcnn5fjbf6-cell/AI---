package com.company.procurement.modules.procurement.labor.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PersonalLaborItemStatusVO {

    private Long recordId;
    private String itemCode;
    private String itemName;
    private String unit;
    private Boolean enabled;
    private Boolean forced;
    private Integer cycleDays;
    private BigDecimal quantityLimit;
    private Integer advanceDays;
    private Integer reminderDays;
    private Boolean emergencyEnabled;
    private Boolean managerApprovalRequired;
    private Boolean dailyUnclaimedReminderEnabled;

    private String specModel;
    private BigDecimal quantity;
    private LocalDate lastReceivedDate;
    private LocalDate nextDueDate;
    private LocalDate earliestApplyDate;
    private Integer daysUntilDue;
    private String status;
    private String statusName;
    private Boolean canApply;
    private Boolean canEmergencyPurchase;
    private Boolean canConfirmReceived;
    private Long purchaseRequestId;
    private String purchaseRequestNo;
}
