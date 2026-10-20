package com.company.procurement.modules.system.user.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FeishuUserSyncResultVO {

    private LocalDateTime syncedAt;

    private int departmentCount;

    private int feishuUserCount;

    private int createdCount;

    private int updatedCount;

    private int skippedCount;

    private List<String> warnings = new ArrayList<>();
}
