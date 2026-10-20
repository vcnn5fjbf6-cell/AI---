package com.company.procurement.modules.platform.permission.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlatformUnitVO {

    private Long id;

    private String unitCode;

    private String unitName;

    private Long parentId;

    private Integer status;

    private String remark;

    private List<String> appCodes;

    private LocalDateTime updatedAt;
}
