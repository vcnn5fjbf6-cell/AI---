package com.company.procurement.modules.ai.governance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_content_revision")
public class AiContentRevision extends BaseEntity {

    private String targetType;
    private Long targetId;
    private String targetCode;
    private Integer revisionNo;
    private String snapshotJson;
    private String changeSource;
    private String changeSummary;
}
