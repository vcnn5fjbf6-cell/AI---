package com.company.procurement.modules.procurement.monthly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MonthlyDemandCreateDTO {

    private String planMonth;

    @NotBlank(message = "申请标题不能为空")
    private String reqTitle;

    @NotBlank(message = "采购类型不能为空")
    private String reqType;

    private String deptCode;
    private String deptName;
    private String budgetCode;
    private Long applicantUserId;
    private String actingReason;

    private Integer urgencyLevel = 1;
    private Boolean emergency = false;

    @NotBlank(message = "商品名称不能为空")
    private String itemName;

    private String specModel;

    @NotBlank(message = "单位不能为空")
    private String unit;

    @NotNull(message = "数量不能为空")
    private BigDecimal qty;

    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    private BigDecimal taxRate = BigDecimal.ZERO;
    private LocalDate needDate;

    @NotBlank(message = "采购原因不能为空")
    private String purchaseReason;

    private String ecommerceUrl;
    private String remark;
}
