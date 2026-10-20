package com.company.procurement.modules.platform.database.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DatabaseTableVO {

    private String tableName;
    private String tableComment;
    private String engine;
    private String tableCollation;
    private Long tableRows;
    private Long dataLength;
    private Long indexLength;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
