package com.company.procurement.modules.ai.governance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiSkillSaveDTO {

    @NotBlank(message = "Skill编码不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,63}$", message = "Skill编码只能使用小写字母、数字和中划线")
    private String skillCode;

    @NotBlank(message = "Skill名称不能为空")
    @Size(max = 128, message = "Skill名称不能超过128个字符")
    private String skillName;

    @Size(max = 500, message = "Skill说明不能超过500个字符")
    private String description;

    @NotBlank(message = "Skill执行指令不能为空")
    @Size(max = 30000, message = "Skill执行指令不能超过30000个字符")
    private String instructions;

    @Size(max = 20, message = "一个Skill最多引用20个知识库")
    private List<@Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,63}$", message = "知识库编码格式不合法") String> knowledgeBaseCodes = new ArrayList<>();

    @Min(value = 0, message = "状态不合法")
    @Max(value = 1, message = "状态不合法")
    private Integer status = 1;

    @Size(max = 500, message = "变更说明不能超过500个字符")
    private String changeSummary;
}
