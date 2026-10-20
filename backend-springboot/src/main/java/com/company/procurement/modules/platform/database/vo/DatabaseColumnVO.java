package com.company.procurement.modules.platform.database.vo;

import lombok.Data;

@Data
public class DatabaseColumnVO {

    private String columnName;
    private String dataType;
    private String columnType;
    private String nullable;
    private String columnKey;
    private String defaultValue;
    private String extra;
    private String comment;
    private Integer ordinalPosition;
}
