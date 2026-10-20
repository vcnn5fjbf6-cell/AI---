package com.company.procurement.modules.workbench.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkbenchNoticeVO {

    private Long id;

    private String title;

    private String content;

    private Boolean pinned;

    private Integer status;

    private Integer sortNo;

    private LocalDateTime updatedAt;
}
