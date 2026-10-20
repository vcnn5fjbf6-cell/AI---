package com.company.procurement.modules.platform.importlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.platform.importlog.dto.ImportFileLogPageQueryDTO;
import com.company.procurement.modules.platform.importlog.entity.ImportFileLog;
import com.company.procurement.modules.platform.importlog.mapper.ImportFileLogMapper;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.platform.importlog.vo.ImportFileLogVO;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportFileLogServiceImpl
    extends ServiceImpl<ImportFileLogMapper, ImportFileLog>
    implements ImportFileLogService {

    private static final String SETTING_RETENTION_DAYS = "import_file_log_retention_days";

    private final ProcurementSettingMapper settingMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordWebImport(String importType, String importTypeName, MultipartFile file) {
        ImportFileLog log = baseLog("WEB", importType, importTypeName);
        fillFileInfo(log, file);
        fillUser(log, AuthContextHolder.get());
        save(log);
        return log.getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordFeishuImport(
        String importType,
        String importTypeName,
        String fileName,
        byte[] content,
        String sourceUserKey,
        String messageId,
        Long messageLogId,
        AuthUser user
    ) {
        ImportFileLog log = baseLog("FEISHU_BOT", importType, importTypeName);
        log.setFileName(limit(firstText(fileName, "飞书附件"), 255));
        log.setFileExt(extension(fileName));
        log.setFileSize(content == null ? 0L : (long) content.length);
        log.setFileSha256(sha256(content));
        log.setSourceUserKey(limit(sourceUserKey, 128));
        log.setMessageId(limit(messageId, 128));
        log.setMessageLogId(messageLogId);
        fillUser(log, user);
        save(log);
        return log.getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long id, Integer rowCount, Integer successCount, Integer failureCount, String summary) {
        if (id == null) {
            return;
        }
        ImportFileLog update = new ImportFileLog();
        update.setId(id);
        update.setProcessStatus("SUCCESS");
        update.setRowCount(rowCount);
        update.setSuccessCount(successCount);
        update.setFailureCount(failureCount);
        update.setResultSummary(limit(summary, 1000));
        updateById(update);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, Throwable error) {
        if (id == null) {
            return;
        }
        ImportFileLog update = new ImportFileLog();
        update.setId(id);
        update.setProcessStatus("FAILED");
        update.setErrorMessage(limit(error == null ? "导入失败" : firstText(error.getMessage(), error.getClass().getName()), 2000));
        updateById(update);
    }

    @Override
    public PageResult<ImportFileLogVO> page(ImportFileLogPageQueryDTO queryDTO) {
        LambdaQueryWrapper<ImportFileLog> wrapper = new LambdaQueryWrapper<ImportFileLog>()
            .orderByDesc(ImportFileLog::getOccurredAt)
            .orderByDesc(ImportFileLog::getId);
        if (StringUtils.hasText(queryDTO.getSourceChannel())) {
            wrapper.eq(ImportFileLog::getSourceChannel, normalize(queryDTO.getSourceChannel()));
        }
        if (StringUtils.hasText(queryDTO.getImportType())) {
            wrapper.eq(ImportFileLog::getImportType, normalize(queryDTO.getImportType()));
        }
        if (StringUtils.hasText(queryDTO.getProcessStatus())) {
            wrapper.eq(ImportFileLog::getProcessStatus, normalize(queryDTO.getProcessStatus()));
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            wrapper.and(scope -> scope
                .like(ImportFileLog::getFileName, keyword)
                .or()
                .like(ImportFileLog::getImportTypeName, keyword)
                .or()
                .like(ImportFileLog::getUploaderUsername, keyword)
                .or()
                .like(ImportFileLog::getUploaderRealName, keyword)
                .or()
                .like(ImportFileLog::getSourceUserKey, keyword)
                .or()
                .like(ImportFileLog::getMessageId, keyword)
                .or()
                .like(ImportFileLog::getRelatedBizNo, keyword)
                .or()
                .like(ImportFileLog::getResultSummary, keyword)
                .or()
                .like(ImportFileLog::getErrorMessage, keyword));
        }
        Page<ImportFileLog> page = page(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public ImportFileLogVO detail(Long id) {
        ImportFileLog log = getById(id);
        if (log == null) {
            throw new BizException("IMPORT_FILE_LOG_NOT_FOUND", "导入文件记录不存在");
        }
        return toVO(log);
    }

    @Override
    @Transactional
    public int cleanupExpiredLogs() {
        int retentionDays = retentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return baseMapper.physicalDeleteBefore(cutoff);
    }

    private ImportFileLog baseLog(String sourceChannel, String importType, String importTypeName) {
        ImportFileLog log = new ImportFileLog();
        log.setSourceChannel(normalize(sourceChannel));
        log.setImportType(normalize(importType));
        log.setImportTypeName(limit(importTypeName, 128));
        log.setStorageMode("NOT_STORED");
        log.setProcessStatus("RECEIVED");
        log.setOccurredAt(LocalDateTime.now());
        return log;
    }

    private void fillFileInfo(ImportFileLog log, MultipartFile file) {
        if (file == null) {
            return;
        }
        String fileName = file.getOriginalFilename();
        log.setFileName(limit(fileName, 255));
        log.setFileExt(extension(fileName));
        log.setContentType(limit(file.getContentType(), 128));
        log.setFileSize(file.getSize());
        try {
            log.setFileSha256(sha256(file.getBytes()));
        } catch (IOException ex) {
            ImportFileLogServiceImpl.log.debug("Unable to calculate import file hash", ex);
        }
    }

    private void fillUser(ImportFileLog log, AuthUser user) {
        if (user == null) {
            return;
        }
        log.setUploaderUserId(user.getUserId());
        log.setUploaderUsername(limit(user.getUsername(), 64));
        log.setUploaderRealName(limit(user.getRealName(), 128));
    }

    private int retentionDays() {
        try {
            ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
                .eq(ProcurementSetting::getSettingKey, SETTING_RETENTION_DAYS)
                .last("LIMIT 1"));
            return setting == null || !StringUtils.hasText(setting.getSettingValue())
                ? 90
                : Integer.parseInt(setting.getSettingValue().trim());
        } catch (RuntimeException ex) {
            log.warn("Invalid import file log retention days, fallback to 90 days", ex);
            return 90;
        }
    }

    private ImportFileLogVO toVO(ImportFileLog log) {
        ImportFileLogVO vo = new ImportFileLogVO();
        vo.setId(log.getId());
        vo.setSourceChannel(log.getSourceChannel());
        vo.setImportType(log.getImportType());
        vo.setImportTypeName(log.getImportTypeName());
        vo.setFileName(log.getFileName());
        vo.setFileExt(log.getFileExt());
        vo.setContentType(log.getContentType());
        vo.setFileSize(log.getFileSize());
        vo.setFileSha256(log.getFileSha256());
        vo.setStorageMode(log.getStorageMode());
        vo.setStoragePath(log.getStoragePath());
        vo.setRowCount(log.getRowCount());
        vo.setSuccessCount(log.getSuccessCount());
        vo.setFailureCount(log.getFailureCount());
        vo.setProcessStatus(log.getProcessStatus());
        vo.setUploaderUserId(log.getUploaderUserId());
        vo.setUploaderUsername(log.getUploaderUsername());
        vo.setUploaderRealName(log.getUploaderRealName());
        vo.setSourceUserKey(log.getSourceUserKey());
        vo.setMessageId(log.getMessageId());
        vo.setMessageLogId(log.getMessageLogId());
        vo.setRelatedBizType(log.getRelatedBizType());
        vo.setRelatedBizId(log.getRelatedBizId());
        vo.setRelatedBizNo(log.getRelatedBizNo());
        vo.setResultSummary(log.getResultSummary());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setOccurredAt(log.getOccurredAt());
        vo.setCreatedAt(log.getCreatedAt());
        vo.setUpdatedAt(log.getUpdatedAt());
        return vo;
    }

    private String sha256(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception ex) {
            return "";
        }
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        return index < 0 || index == fileName.length() - 1 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
