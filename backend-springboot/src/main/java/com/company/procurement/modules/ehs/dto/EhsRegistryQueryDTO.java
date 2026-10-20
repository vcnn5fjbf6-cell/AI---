package com.company.procurement.modules.ehs.dto;

import com.company.procurement.common.dto.PageQuery;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EhsRegistryQueryDTO extends PageQuery {
    @NotBlank(message = "模块类型不能为空")
    private String moduleType;
    private String keyword;
    private String status;
    private String riskLevel;
    private String building;
}
