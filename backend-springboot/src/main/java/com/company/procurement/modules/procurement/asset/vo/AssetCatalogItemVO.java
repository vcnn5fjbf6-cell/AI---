package com.company.procurement.modules.procurement.asset.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetCatalogItemVO {
    private Long id;
    private String categoryCode;
    private String categoryName;
    private String assetNameCode;
    private String assetName;
    private String depreciationYears;
    private String depreciationMonths;
    private String unit;
    private String inventoryMethod;
    private String physicalNumber;
    private String physicalLabel;
    private String remark;
    private String changeMark;
    private Integer enabled;
    private String sourceVersion;
    private Integer sourceRow;
    private LocalDateTime updatedAt;
}
