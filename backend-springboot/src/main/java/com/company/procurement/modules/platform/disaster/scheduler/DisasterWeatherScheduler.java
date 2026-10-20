package com.company.procurement.modules.platform.disaster.scheduler;

import com.company.procurement.modules.platform.disaster.service.DisasterWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DisasterWeatherScheduler {

    private final DisasterWeatherService disasterWeatherService;

    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Shanghai")
    public void sendDailyForecast() {
        disasterWeatherService.sendDailyForecastIfEnabled();
    }

    @Scheduled(cron = "0 5 9 ? * MON", zone = "Asia/Shanghai")
    public void sendWeeklyForecast() {
        disasterWeatherService.sendWeeklyForecastIfEnabled();
    }

    @Scheduled(cron = "0 * * * * ?", zone = "Asia/Shanghai")
    public void pollWarnings() {
        disasterWeatherService.pollWarningsIfEnabled();
    }

    @Scheduled(cron = "0 10 * * * ?", zone = "Asia/Shanghai")
    public void cleanupWarningRecords() {
        disasterWeatherService.cleanupWarningRecords();
    }
}
