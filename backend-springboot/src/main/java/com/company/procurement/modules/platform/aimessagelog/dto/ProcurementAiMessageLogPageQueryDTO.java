package com.company.procurement.modules.platform.aimessagelog.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcurementAiMessageLogPageQueryDTO extends PageQuery {

    private String sourceChannel;

    private String processStatus;

    private String intentCode;

    private String messageType;

    private String sessionNo;

    private String sourceUserKey;

    private String keyword;
}
