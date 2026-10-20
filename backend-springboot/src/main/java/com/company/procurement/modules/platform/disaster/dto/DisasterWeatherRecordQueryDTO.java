package com.company.procurement.modules.platform.disaster.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;

@Data
public class DisasterWeatherRecordQueryDTO extends PageQuery {

    private String recordType;

    private String sendStatus;

    private String guardLevel;

    private String keyword;
}
