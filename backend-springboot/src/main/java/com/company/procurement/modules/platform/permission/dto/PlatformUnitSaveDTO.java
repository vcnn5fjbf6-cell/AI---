package com.company.procurement.modules.platform.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PlatformUnitSaveDTO {

    private Long id;

    @NotBlank(message = "单元编码不能为空")
    private String unitCode;

    @NotBlank(message = "单元名称不能为空")
    private String unitName;

    private Long parentId;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private String remark;

    private List<String> appCodes;
}
