package com.company.procurement.modules.procurement.settings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProcurementDepartmentLimitVO {

    private Long id;
    private String deptCode;
    private String deptName;
    private BigDecimal singleRequestLimit;
    private BigDecimal monthlyLimit;
    private Integer status;
    private String remark;
    private LocalDateTime updatedAt;
}
