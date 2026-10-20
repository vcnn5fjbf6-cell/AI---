package com.company.procurement.modules.platform.importlog.scheduler;

import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportFileLogCleanupScheduler {

    private final ImportFileLogService importFileLogService;

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupExpiredImportLogs() {
        int deleted = importFileLogService.cleanupExpiredLogs();
        if (deleted > 0) {
            log.info("Cleaned expired import file logs, deleted={}", deleted);
        }
    }
}
