package com.company.procurement.modules.platform.disaster.dto;

import lombok.Data;

@Data
public class DisasterWeatherTriggerDTO {

    private String recordType;

    private Boolean sendToFeishu;

    private String testCardColor;
}
