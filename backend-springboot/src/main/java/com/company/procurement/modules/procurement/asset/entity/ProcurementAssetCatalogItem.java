package com.company.procurement.modules.procurement.asset.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("procurement_asset_catalog_item")
public class ProcurementAssetCatalogItem extends BaseEntity {

    @TableField("category_code")
    private String categoryCode;

    @TableField("category_name")
    private String categoryName;

    @TableField("asset_name_code")
    private String assetNameCode;

    @TableField("asset_name")
    private String assetName;

    @TableField("depreciation_years")
    private String depreciationYears;

    @TableField("depreciation_months")
    private String depreciationMonths;

    @TableField("unit")
    private String unit;

    @TableField("inventory_method")
    private String inventoryMethod;

    @TableField("physical_number")
    private String physicalNumber;

    @TableField("physical_label")
    private String physicalLabel;

    @TableField("remark")
    private String remark;

    @TableField("change_mark")
    private String changeMark;

    @TableField("enabled")
    private Integer enabled;

    @TableField("source_version")
    private String sourceVersion;

    @TableField("source_row")
    private Integer sourceRow;
}
