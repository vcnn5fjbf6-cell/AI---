package com.company.procurement.modules.platform.disaster.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherRecordQueryDTO;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherSettingsDTO;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherTriggerDTO;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherOverviewVO;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherRecordVO;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherSettingsVO;

public interface DisasterWeatherService {

    DisasterWeatherOverviewVO overview();

    DisasterWeatherSettingsVO getSettings();

    void updateSettings(DisasterWeatherSettingsDTO dto);

    PageResult<DisasterWeatherRecordVO> pageRecords(DisasterWeatherRecordQueryDTO queryDTO);

    DisasterWeatherRecordVO trigger(DisasterWeatherTriggerDTO dto);

    void sendDailyForecastIfEnabled();

    void sendWeeklyForecastIfEnabled();

    void pollWarningsIfEnabled();

    void cleanupWarningRecords();
}
