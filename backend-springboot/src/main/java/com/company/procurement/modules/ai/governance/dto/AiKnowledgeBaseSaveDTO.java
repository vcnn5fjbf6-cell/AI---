package com.company.procurement.modules.ai.governance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiKnowledgeBaseSaveDTO {

    @NotBlank(message = "知识库编码不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,63}$", message = "知识库编码只能使用小写字母、数字和中划线")
    private String knowledgeCode;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称不能超过128个字符")
    private String knowledgeName;

    @Size(max = 500, message = "知识库说明不能超过500个字符")
    private String description;

    @NotBlank(message = "知识库正文不能为空")
    @Size(max = 30000, message = "知识库正文不能超过30000个字符")
    private String content;

    @Min(value = 0, message = "状态不合法")
    @Max(value = 1, message = "状态不合法")
    private Integer status = 1;

    @Size(max = 500, message = "变更说明不能超过500个字符")
    private String changeSummary;
}
