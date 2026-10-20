package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EhsKnowledgeSaveDTO {
    @NotBlank(message = "知识标题不能为空")
    private String title;
    private String category;
    private String fileName;
    private String fileType;
    @NotBlank(message = "知识正文不能为空")
    private String content;
}
