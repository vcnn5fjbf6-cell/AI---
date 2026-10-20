package com.company.procurement.modules.procurement.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcurementDepartmentLimitDTO {

    private Long id;

    private String deptCode;

    @NotBlank(message = "部门名称不能为空")
    private String deptName;

    private BigDecimal singleRequestLimit;
    private BigDecimal monthlyLimit;
    private Integer status;
    private String remark;
}
