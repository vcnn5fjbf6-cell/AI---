package com.company.procurement.modules.platform.importlog.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.platform.importlog.dto.ImportFileLogPageQueryDTO;
import com.company.procurement.modules.platform.importlog.vo.ImportFileLogVO;
import org.springframework.web.multipart.MultipartFile;

public interface ImportFileLogService {

    Long recordWebImport(String importType, String importTypeName, MultipartFile file);

    Long recordFeishuImport(
        String importType,
        String importTypeName,
        String fileName,
        byte[] content,
        String sourceUserKey,
        String messageId,
        Long messageLogId,
        AuthUser user
    );

    void markSuccess(Long id, Integer rowCount, Integer successCount, Integer failureCount, String summary);

    void markFailed(Long id, Throwable error);

    PageResult<ImportFileLogVO> page(ImportFileLogPageQueryDTO queryDTO);

    ImportFileLogVO detail(Long id);

    int cleanupExpiredLogs();
}
