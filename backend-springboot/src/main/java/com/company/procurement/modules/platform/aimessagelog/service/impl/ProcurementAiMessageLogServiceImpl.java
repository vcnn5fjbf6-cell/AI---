package com.company.procurement.modules.platform.aimessagelog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.aimessagelog.dto.ProcurementAiMessageLogPageQueryDTO;
import com.company.procurement.modules.platform.aimessagelog.entity.ProcurementAiMessageLog;
import com.company.procurement.modules.platform.aimessagelog.mapper.ProcurementAiMessageLogMapper;
import com.company.procurement.modules.platform.aimessagelog.service.ProcurementAiMessageLogService;
import com.company.procurement.modules.platform.aimessagelog.vo.ProcurementAiMessageLogVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ProcurementAiMessageLogServiceImpl
    extends ServiceImpl<ProcurementAiMessageLogMapper, ProcurementAiMessageLog>
    implements ProcurementAiMessageLogService {

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long record(ProcurementAiMessageLog log) {
        if (log == null) {
            return null;
        }
        if (!StringUtils.hasText(log.getProcessStatus())) {
            log.setProcessStatus("RECEIVED");
        }
        if (log.getOccurredAt() == null) {
            log.setOccurredAt(LocalDateTime.now());
        }
        save(log);
        return log.getId();
    }

    @Override
    public boolean existsBySourceAndMessageId(String sourceChannel, String messageId) {
        if (!StringUtils.hasText(sourceChannel) || !StringUtils.hasText(messageId)) {
            return false;
        }
        return count(new LambdaQueryWrapper<ProcurementAiMessageLog>()
            .eq(ProcurementAiMessageLog::getSourceChannel, sourceChannel.trim().toUpperCase(Locale.ROOT))
            .eq(ProcurementAiMessageLog::getMessageId, messageId.trim())) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResult(ProcurementAiMessageLog log) {
        if (log == null || log.getId() == null) {
            return;
        }
        updateById(log);
    }

    @Override
    public PageResult<ProcurementAiMessageLogVO> page(ProcurementAiMessageLogPageQueryDTO queryDTO) {
        LambdaQueryWrapper<ProcurementAiMessageLog> wrapper = new LambdaQueryWrapper<ProcurementAiMessageLog>()
            .orderByDesc(ProcurementAiMessageLog::getOccurredAt)
            .orderByDesc(ProcurementAiMessageLog::getId);
        if (StringUtils.hasText(queryDTO.getSourceChannel())) {
            wrapper.eq(ProcurementAiMessageLog::getSourceChannel, queryDTO.getSourceChannel().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(queryDTO.getProcessStatus())) {
            wrapper.eq(ProcurementAiMessageLog::getProcessStatus, queryDTO.getProcessStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(queryDTO.getIntentCode())) {
            wrapper.eq(ProcurementAiMessageLog::getIntentCode, queryDTO.getIntentCode().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(queryDTO.getMessageType())) {
            wrapper.eq(ProcurementAiMessageLog::getMessageType, queryDTO.getMessageType().trim());
        }
        if (StringUtils.hasText(queryDTO.getSessionNo())) {
            wrapper.like(ProcurementAiMessageLog::getSessionNo, queryDTO.getSessionNo().trim());
        }
        if (StringUtils.hasText(queryDTO.getSourceUserKey())) {
            wrapper.like(ProcurementAiMessageLog::getSourceUserKey, queryDTO.getSourceUserKey().trim());
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            wrapper.and(scope -> scope
                .like(ProcurementAiMessageLog::getRawText, keyword)
                .or()
                .like(ProcurementAiMessageLog::getReplyText, keyword)
                .or()
                .like(ProcurementAiMessageLog::getResultSummary, keyword)
                .or()
                .like(ProcurementAiMessageLog::getDifyParsedJson, keyword)
                .or()
                .like(ProcurementAiMessageLog::getDifyResponseText, keyword)
                .or()
                .like(ProcurementAiMessageLog::getErrorMessage, keyword)
                .or()
                .like(ProcurementAiMessageLog::getUsername, keyword)
                .or()
                .like(ProcurementAiMessageLog::getRealName, keyword)
                .or()
                .like(ProcurementAiMessageLog::getMessageId, keyword)
                .or()
                .like(ProcurementAiMessageLog::getPurchaseRequestNo, keyword)
                .or()
                .like(ProcurementAiMessageLog::getSessionNo, keyword));
        }
        Page<ProcurementAiMessageLog> page = page(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public ProcurementAiMessageLogVO detail(Long id) {
        ProcurementAiMessageLog log = getById(id);
        if (log == null) {
            throw new BizException("PROCUREMENT_AI_MESSAGE_LOG_NOT_FOUND", "AI助手消息日志不存在");
        }
        return toVO(log);
    }

    private ProcurementAiMessageLogVO toVO(ProcurementAiMessageLog log) {
        ProcurementAiMessageLogVO vo = new ProcurementAiMessageLogVO();
        vo.setId(log.getId());
        vo.setSourceChannel(log.getSourceChannel());
        vo.setEventType(log.getEventType());
        vo.setMessageId(log.getMessageId());
        vo.setChatId(log.getChatId());
        vo.setChatType(log.getChatType());
        vo.setMessageType(log.getMessageType());
        vo.setSourceUserKey(log.getSourceUserKey());
        vo.setSenderOpenId(log.getSenderOpenId());
        vo.setSenderUserId(log.getSenderUserId());
        vo.setSenderUnionId(log.getSenderUnionId());
        vo.setApplicantUserId(log.getApplicantUserId());
        vo.setUsername(log.getUsername());
        vo.setRealName(log.getRealName());
        vo.setSessionId(log.getSessionId());
        vo.setSessionNo(log.getSessionNo());
        vo.setCandidateNo(log.getCandidateNo());
        vo.setMonthlyDemandId(log.getMonthlyDemandId());
        vo.setMonthlyDemandNo(log.getMonthlyDemandNo());
        vo.setPurchaseRequestId(log.getPurchaseRequestId());
        vo.setPurchaseRequestNo(log.getPurchaseRequestNo());
        vo.setIntentCode(log.getIntentCode());
        vo.setIntentName(log.getIntentName());
        vo.setProcessStatus(log.getProcessStatus());
        vo.setRawText(log.getRawText());
        vo.setRawContent(log.getRawContent());
        vo.setRawPayload(log.getRawPayload());
        vo.setDifyRequestJson(log.getDifyRequestJson());
        vo.setDifyResponseText(log.getDifyResponseText());
        vo.setDifyParsedJson(log.getDifyParsedJson());
        vo.setReplyText(log.getReplyText());
        vo.setResultSummary(log.getResultSummary());
        vo.setErrorCode(log.getErrorCode());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setDurationMs(log.getDurationMs());
        vo.setOccurredAt(log.getOccurredAt());
        vo.setCreatedAt(log.getCreatedAt());
        vo.setUpdatedAt(log.getUpdatedAt());
        return vo;
    }
}
