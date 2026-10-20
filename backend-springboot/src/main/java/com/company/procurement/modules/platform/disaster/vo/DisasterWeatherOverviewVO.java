package com.company.procurement.modules.platform.disaster.vo;

import lombok.Data;

@Data
public class DisasterWeatherOverviewVO {

    private DisasterWeatherSettingsVO settings;

    private DisasterWeatherRecordVO latestRecord;
}
