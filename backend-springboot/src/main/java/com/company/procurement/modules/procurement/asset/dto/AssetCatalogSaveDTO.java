package com.company.procurement.modules.procurement.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssetCatalogSaveDTO {

    @NotBlank(message = "资产大类编码不能为空")
    @Size(max = 32, message = "资产大类编码不能超过32个字符")
    private String categoryCode;

    @NotBlank(message = "资产大类名称不能为空")
    @Size(max = 255, message = "资产大类名称不能超过255个字符")
    private String categoryName;

    @NotBlank(message = "资产名称编码不能为空")
    @Size(max = 32, message = "资产名称编码不能超过32个字符")
    private String assetNameCode;

    @NotBlank(message = "资产名称不能为空")
    @Size(max = 255, message = "资产名称不能超过255个字符")
    private String assetName;

    @Size(max = 64, message = "折旧年限不能超过64个字符")
    private String depreciationYears;

    @Size(max = 64, message = "折旧月数不能超过64个字符")
    private String depreciationMonths;

    @Size(max = 64, message = "单位不能超过64个字符")
    private String unit;

    @Size(max = 128, message = "盘点方式不能超过128个字符")
    private String inventoryMethod;

    @Size(max = 64, message = "实物编号不能超过64个字符")
    private String physicalNumber;

    @Size(max = 128, message = "实物标签不能超过128个字符")
    private String physicalLabel;

    @Size(max = 1000, message = "备注不能超过1000个字符")
    private String remark;

    @Size(max = 255, message = "变更标识不能超过255个字符")
    private String changeMark;

    @NotNull(message = "状态不能为空")
    private Integer enabled;
}
