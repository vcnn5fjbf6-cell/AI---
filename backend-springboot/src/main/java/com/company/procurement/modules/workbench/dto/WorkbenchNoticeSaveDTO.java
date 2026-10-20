package com.company.procurement.modules.workbench.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkbenchNoticeSaveDTO {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题最多100个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 2000, message = "公告内容最多2000个字符")
    private String content;

    private Boolean pinned;

    @Min(value = 0, message = "状态只能是0或1")
    @Max(value = 1, message = "状态只能是0或1")
    private Integer status;

    private Integer sortNo;
}
