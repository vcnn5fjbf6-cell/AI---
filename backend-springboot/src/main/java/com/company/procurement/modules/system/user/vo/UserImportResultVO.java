package com.company.procurement.modules.system.user.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserImportResultVO {

    private int totalCount;
    private int successCount;
    private int createdCount;
    private int updatedCount;
    private int failedCount;
    private List<String> errors = new ArrayList<>();
}
