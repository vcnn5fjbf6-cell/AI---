package com.company.procurement.modules.procurement.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiAnalysisResult;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiCandidateDraft;
import com.company.procurement.modules.procurement.assistant.client.ProcurementAiAnalyzer;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiConfirmDTO;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.company.procurement.modules.procurement.assistant.entity.ProcurementAiCandidate;
import com.company.procurement.modules.procurement.assistant.entity.ProcurementAiSession;
import com.company.procurement.modules.procurement.assistant.mapper.ProcurementAiCandidateMapper;
import com.company.procurement.modules.procurement.assistant.mapper.ProcurementAiSessionMapper;
import com.company.procurement.modules.procurement.assistant.service.ProcurementAiService;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiCandidateVO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiConfirmResultVO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiSessionVO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestItemDTO;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcurementAiServiceImpl implements ProcurementAiService {

    private static final String STATUS_WAITING_CONFIRM = "WAITING_CONFIRM";
    private static final String STATUS_DRAFTING = "DRAFTING";
    private static final String STATUS_DRAFT_CONVERTED = "DRAFT_CONVERTED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_AI_FAILED = "AI_FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ProcurementAiSessionMapper sessionMapper;
    private final ProcurementAiCandidateMapper candidateMapper;
    private final ProcurementAiAnalyzer procurementAiAnalyzer;
    private final PurchaseRequestService purchaseRequestService;

    @Override
    public ProcurementAiSessionVO startSession(ProcurementAiStartDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return startSession(dto, authUser, "WEB", authUser.getUsername(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiSessionVO startSession(ProcurementAiStartDTO dto, AuthUser applicant, String sourceChannel,
                                               String sourceUserKey, String sourceMessageId) {
        ProcurementAiAnalysisResult analysis = procurementAiAnalyzer.analyze(dto, sourceUserKey);
        return startSession(dto, analysis, applicant, sourceChannel, sourceUserKey, sourceMessageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiSessionVO startSession(ProcurementAiStartDTO dto, ProcurementAiAnalysisResult analysis,
                                               AuthUser applicant, String sourceChannel,
                                               String sourceUserKey, String sourceMessageId) {
        ensureInternalUser(applicant);
        ProcurementAiSession existing = findExistingSourceSession(sourceChannel, sourceUserKey, sourceMessageId);
        if (existing != null) {
            return toSessionVO(existing);
        }
        if (analysis == null) {
            analysis = procurementAiAnalyzer.analyze(dto, sourceUserKey);
        }

        ProcurementAiSession session = new ProcurementAiSession();
        session.setSessionNo(generateSessionNo());
        session.setSourceChannel(firstText(sourceChannel, "WEB"));
        session.setSourceUserKey(limit(sourceUserKey, 128));
        session.setSourceMessageId(limit(sourceMessageId, 128));
        session.setApplicantUserId(applicant.getUserId());
        session.setRawText(limit(dto.getRawText(), 2000));
        session.setImageKey(limit(dto.getImageKey(), 255));
        session.setImageUrl(limit(dto.getImageUrl(), 500));
        session.setReqTitle(limit(firstText(analysis.getReqTitle(), dto.getReqTitle(), dto.getRawText()), 255));
        session.setReqType(normalizeReqType(firstText(analysis.getReqType(), dto.getReqType())));
        session.setPlanMonth(firstText(analysis.getPlanMonth(), dto.getPlanMonth(), LocalDate.now().format(MONTH_FORMATTER)));
        session.setDeptCode(limit(firstText(dto.getDeptCode(), applicant.getDeptCode()), 64));
        session.setDeptName(limit(firstText(dto.getDeptName(), applicant.getDeptName(), applicant.getRealName()), 128));
        session.setBudgetCode(limit(dto.getBudgetCode(), 64));
        session.setUrgencyLevel(analysis.getUrgencyLevel() == null ? 1 : analysis.getUrgencyLevel());
        session.setEmergencyFlag(Boolean.TRUE.equals(analysis.getEmergency()) ? 1 : 0);
        session.setNeedDate(analysis.getNeedDate());
        session.setAiSummary(limit(analysis.getAiSummary(), 1000));
        session.setAiError(limit(analysis.getAiError(), 1000));
        session.setDifyRequestJson(limit(analysis.getDifyRequestJson(), 50000));
        session.setDifyResponseText(limit(analysis.getDifyResponseText(), 50000));
        session.setDifyParsedJson(limit(analysis.getDifyParsedJson(), 50000));
        session.setStatus(StringUtils.hasText(analysis.getAiError()) ? STATUS_AI_FAILED : STATUS_WAITING_CONFIRM);
        session.setRemark(limit(dto.getRemark(), 500));
        sessionMapper.insert(session);

        List<ProcurementAiCandidateDraft> drafts = analysis.getCandidates() == null ? List.of() : analysis.getCandidates();
        int candidateNo = 1;
        int insertedCandidates = 0;
        for (ProcurementAiCandidateDraft draft : drafts) {
            if (!positiveMoney(draft.getUnitPrice())) {
                continue;
            }
            ProcurementAiCandidate candidate = toCandidate(session.getId(), draft, candidateNo++);
            candidateMapper.insert(candidate);
            insertedCandidates++;
        }
        if (insertedCandidates == 0) {
            session.setStatus(STATUS_AI_FAILED);
            session.setAiSummary(limit(firstText(session.getAiSummary(),
                "请补充每项采购明细的参考单价或预算，价格必须大于 0。"), 1000));
            session.setAiError(limit(firstText(session.getAiError(),
                "No valid procurement candidate with unit price greater than 0."), 1000));
            sessionMapper.updateById(session);
        }
        return toSessionVO(session);
    }

    private ProcurementAiSession findExistingSourceSession(String sourceChannel, String sourceUserKey, String sourceMessageId) {
        if (!StringUtils.hasText(sourceMessageId)) {
            return null;
        }
        LambdaQueryWrapper<ProcurementAiSession> wrapper = new LambdaQueryWrapper<ProcurementAiSession>()
            .eq(ProcurementAiSession::getSourceChannel, firstText(sourceChannel, "WEB"))
            .eq(ProcurementAiSession::getSourceMessageId, sourceMessageId.trim())
            .last("LIMIT 1");
        if (StringUtils.hasText(sourceUserKey)) {
            wrapper.eq(ProcurementAiSession::getSourceUserKey, sourceUserKey.trim());
        }
        return sessionMapper.selectOne(wrapper);
    }

    @Override
    public ProcurementAiSessionVO getSession(Long id) {
        ProcurementAiSession session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "Session not found");
        }
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureSessionAccess(session, authUser);
        return toSessionVO(session);
    }

    @Override
    public ProcurementAiSessionVO latestWaitingSession(String sourceUserKey) {
        if (!StringUtils.hasText(sourceUserKey)) {
            return null;
        }
        ProcurementAiSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ProcurementAiSession>()
            .eq(ProcurementAiSession::getSourceUserKey, sourceUserKey.trim())
            .in(ProcurementAiSession::getStatus, STATUS_WAITING_CONFIRM, STATUS_AI_FAILED)
            .orderByDesc(ProcurementAiSession::getCreatedAt)
            .last("LIMIT 1"));
        return session == null ? null : toSessionVO(session);
    }

    @Override
    public ProcurementAiSessionVO latestDraftSession(String sourceUserKey, AuthUser applicant) {
        if (!StringUtils.hasText(sourceUserKey)) {
            return null;
        }
        ensureInternalUser(applicant);
        ProcurementAiSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ProcurementAiSession>()
            .eq(ProcurementAiSession::getSourceUserKey, sourceUserKey.trim())
            .eq(ProcurementAiSession::getStatus, STATUS_DRAFTING)
            .orderByDesc(ProcurementAiSession::getUpdatedAt)
            .orderByDesc(ProcurementAiSession::getCreatedAt)
            .last("LIMIT 1"));
        if (session == null) {
            return null;
        }
        ensureSessionAccess(session, applicant);
        return toSessionVO(session);
    }

    @Override
    public List<ProcurementAiSessionVO> waitingSessions(String sourceUserKey) {
        return waitingSessionEntities(sourceUserKey).stream()
            .map(this::toSessionVO)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiSessionVO saveDraft(ProcurementAiStartDTO dto, AuthUser applicant, String sourceChannel,
                                            String sourceUserKey, String sourceMessageId, String replyText,
                                            String difyRequestJson, String difyResponseText, String difyParsedJson) {
        ensureInternalUser(applicant);
        ProcurementAiSession session = latestDraftEntity(sourceUserKey, applicant);
        if (session == null) {
            session = new ProcurementAiSession();
            session.setSessionNo(generateSessionNo());
            session.setSourceChannel(firstText(sourceChannel, "FEISHU_BOT"));
            session.setSourceUserKey(limit(sourceUserKey, 128));
            session.setApplicantUserId(applicant.getUserId());
            session.setPlanMonth(firstText(dto.getPlanMonth(), LocalDate.now().format(MONTH_FORMATTER)));
            session.setDeptCode(limit(firstText(dto.getDeptCode(), applicant.getDeptCode()), 64));
            session.setDeptName(limit(firstText(dto.getDeptName(), applicant.getDeptName(), applicant.getRealName()), 128));
            session.setReqType(normalizeReqType(dto.getReqType()));
            session.setUrgencyLevel(dto.getUrgencyLevel() == null ? 1 : dto.getUrgencyLevel());
            session.setEmergencyFlag(Boolean.TRUE.equals(dto.getEmergency()) ? 1 : 0);
            session.setBudgetCode(limit(dto.getBudgetCode(), 64));
            session.setNeedDate(parseDate(dto.getNeedDate()));
            session.setStatus(STATUS_DRAFTING);
        }
        session.setSourceMessageId(limit(sourceMessageId, 128));
        session.setRawText(limit(appendDraftText(session.getRawText(), dto.getRawText()), 2000));
        session.setImageKey(limit(firstText(dto.getImageKey(), session.getImageKey()), 255));
        session.setImageUrl(limit(firstText(dto.getImageUrl(), session.getImageUrl()), 500));
        session.setReqTitle(limit(firstText(dto.getReqTitle(), session.getReqTitle(), dto.getRawText(), "待补充采购需求"), 255));
        session.setAiSummary(limit(firstText(replyText, "采购信息待补充"), 1000));
        session.setAiError(null);
        session.setDifyRequestJson(limit(difyRequestJson, 50000));
        session.setDifyResponseText(limit(difyResponseText, 50000));
        session.setDifyParsedJson(limit(difyParsedJson, 50000));
        session.setRemark(limit("等待用户补充采购信息", 500));
        if (session.getId() == null) {
            sessionMapper.insert(session);
        } else {
            sessionMapper.updateById(session);
        }
        return toSessionVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDraftConverted(Long draftSessionId, Long candidateSessionId) {
        if (draftSessionId == null) {
            return;
        }
        ProcurementAiSession draft = sessionMapper.selectById(draftSessionId);
        if (draft == null || !STATUS_DRAFTING.equals(draft.getStatus())) {
            return;
        }
        draft.setStatus(STATUS_DRAFT_CONVERTED);
        draft.setRemark(limit(appendRemark(draft.getRemark(), "已转为采购候选会话：" + candidateSessionId), 500));
        sessionMapper.updateById(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiSessionVO updateWaitingCandidateReason(String sourceUserKey, Integer itemNo, String reason,
                                                               AuthUser applicant) {
        ensureInternalUser(applicant);
        if (!StringUtils.hasText(reason)) {
            throw new BizException("PROCUREMENT_AI_REASON_REQUIRED", "请填写用途");
        }
        WaitingCandidateLine line = waitingCandidateAt(sourceUserKey, itemNo, applicant);
        ProcurementAiSession session = line.session();
        ProcurementAiCandidate candidate = line.candidate();
        candidate.setPurchaseReason(limit(reason, 500));
        candidateMapper.updateById(candidate);
        return toSessionVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiSessionVO updateWaitingCandidateQuantity(String sourceUserKey, Integer itemNo, BigDecimal quantity,
                                                                 String unit, AuthUser applicant) {
        ensureInternalUser(applicant);
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("PROCUREMENT_AI_QUANTITY_REQUIRED", "请填写大于 0 的采购数量");
        }
        WaitingCandidateLine line = waitingCandidateAt(sourceUserKey, itemNo, applicant);
        ProcurementAiSession session = line.session();
        ProcurementAiCandidate candidate = line.candidate();
        candidate.setQty(scaleMoney(quantity, BigDecimal.ONE));
        if (StringUtils.hasText(unit)) {
            candidate.setUnit(limit(unit, 32));
        }
        BigDecimal unitPrice = scaleMoney(candidate.getUnitPrice(), BigDecimal.ZERO);
        candidate.setLineAmount(candidate.getQty().multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
        candidateMapper.updateById(candidate);
        return toSessionVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiSessionVO cancelWaitingSession(String sourceUserKey, Integer itemNo, AuthUser applicant) {
        ensureInternalUser(applicant);
        WaitingCandidateLine line = waitingCandidateAt(sourceUserKey, itemNo, applicant);
        ProcurementAiSession session = cancelCandidateLine(line, "用户撤回第 " + line.itemNo() + " 项候选");
        return toSessionVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiSessionVO cancelWaitingCandidateBySessionNo(String sessionNo, Integer candidateNo,
                                                                    AuthUser applicant, String sourceUserKey) {
        ensureInternalUser(applicant);
        if (!StringUtils.hasText(sessionNo)) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "缺少候选会话号");
        }
        ProcurementAiSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ProcurementAiSession>()
            .eq(ProcurementAiSession::getSessionNo, sessionNo.trim())
            .last("LIMIT 1"));
        if (session == null) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "没有找到待确认的采购候选");
        }
        ensureSessionAccess(session, applicant);
        if (StringUtils.hasText(sourceUserKey) && StringUtils.hasText(session.getSourceUserKey())
            && !Objects.equals(sourceUserKey.trim(), session.getSourceUserKey())) {
            throw new BizException("PROCUREMENT_AI_SOURCE_MISMATCH", "Session does not belong to current source user");
        }
        if (!STATUS_WAITING_CONFIRM.equals(session.getStatus()) && !STATUS_AI_FAILED.equals(session.getStatus())) {
            throw new BizException("PROCUREMENT_AI_STATUS_INVALID", "当前候选状态不允许撤回");
        }
        ProcurementAiCandidate candidate = candidateMapper.selectOne(new LambdaQueryWrapper<ProcurementAiCandidate>()
            .eq(ProcurementAiCandidate::getSessionId, session.getId())
            .eq(ProcurementAiCandidate::getCandidateNo, candidateNo == null ? 1 : candidateNo)
            .eq(ProcurementAiCandidate::getStatus, STATUS_ACTIVE)
            .last("LIMIT 1"));
        if (candidate == null) {
            throw new BizException("PROCUREMENT_AI_CANDIDATE_NOT_FOUND", "候选物品不存在或已撤回");
        }
        return toSessionVO(cancelCandidateLine(new WaitingCandidateLine(0, session, candidate), "用户从卡片撤回该候选"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProcurementAiSessionVO> cancelAllWaiting(String sourceUserKey, AuthUser applicant) {
        ensureInternalUser(applicant);
        List<WaitingCandidateLine> lines = waitingCandidateLines(sourceUserKey, applicant);
        if (lines.isEmpty()) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "没有待确认的采购候选");
        }
        Set<Long> touchedSessionIds = new LinkedHashSet<>();
        for (WaitingCandidateLine line : lines) {
            ProcurementAiCandidate candidate = line.candidate();
            candidate.setStatus(STATUS_CANCELLED);
            candidate.setRemark(limit("用户撤回全部待提交候选", 500));
            candidateMapper.updateById(candidate);
            touchedSessionIds.add(line.session().getId());
        }
        List<ProcurementAiSessionVO> result = new ArrayList<>();
        for (Long sessionId : touchedSessionIds) {
            ProcurementAiSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                continue;
            }
            if (activeCandidates(sessionId).isEmpty()) {
                session.setStatus(STATUS_CANCELLED);
            }
            session.setRemark(limit(appendRemark(session.getRemark(), "用户撤回全部待提交候选"), 500));
            sessionMapper.updateById(session);
            result.add(toSessionVO(session));
        }
        return result;
    }

    @Override
    public ProcurementAiConfirmResultVO confirm(Long sessionId, ProcurementAiConfirmDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return confirm(sessionId, dto.getCandidateNo(), authUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementAiConfirmResultVO confirm(Long sessionId, Integer candidateNo, AuthUser applicant) {
        ProcurementAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "Session not found");
        }
        ensureInternalUser(applicant);
        ensureSessionAccess(session, applicant);
        if (STATUS_CONFIRMED.equals(session.getStatus())) {
            throw new BizException("PROCUREMENT_AI_ALREADY_CONFIRMED", "Session already confirmed");
        }
        if (!STATUS_WAITING_CONFIRM.equals(session.getStatus()) && !STATUS_AI_FAILED.equals(session.getStatus())) {
            throw new BizException("PROCUREMENT_AI_STATUS_INVALID", "当前候选状态不允许确认");
        }

        ProcurementAiCandidate candidate = candidateMapper.selectOne(new LambdaQueryWrapper<ProcurementAiCandidate>()
            .eq(ProcurementAiCandidate::getSessionId, sessionId)
            .eq(ProcurementAiCandidate::getCandidateNo, candidateNo)
            .last("LIMIT 1"));
        if (candidate == null) {
            throw new BizException("PROCUREMENT_AI_CANDIDATE_NOT_FOUND", "Candidate not found");
        }
        ensurePositiveCandidatePrice(candidate);

        AuthUser previous = AuthContextHolder.get();
        AuthContextHolder.set(applicant);
        try {
            Long requestId = purchaseRequestService.create(toPurchaseRequestDTO(session, candidate, applicant));
            purchaseRequestService.submit(requestId);
            PurchaseRequestVO request = purchaseRequestService.detail(requestId);
            candidate.setSelectedFlag(1);
            candidateMapper.updateById(candidate);
            session.setConfirmedCandidateId(candidate.getId());
            session.setPurchaseRequestId(requestId);
            session.setStatus(STATUS_CONFIRMED);
            sessionMapper.updateById(session);

            ProcurementAiConfirmResultVO result = new ProcurementAiConfirmResultVO();
            result.setSessionId(session.getId());
            result.setSessionNo(session.getSessionNo());
            result.setCandidateNo(candidate.getCandidateNo());
            result.setPurchaseRequestId(requestId);
            result.setPurchaseRequestNo(request == null ? null : request.getReqNo());
            result.setStatus(session.getStatus());
            result.setRequest(request);
            return result;
        } finally {
            if (previous == null) {
                AuthContextHolder.clear();
            } else {
                AuthContextHolder.set(previous);
            }
        }
    }

    @Override
    public ProcurementAiConfirmResultVO confirmBySessionNo(String sessionNo, Integer candidateNo, AuthUser applicant,
                                                           String sourceUserKey) {
        ProcurementAiSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ProcurementAiSession>()
            .eq(ProcurementAiSession::getSessionNo, sessionNo)
            .last("LIMIT 1"));
        if (session == null) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "Session not found");
        }
        if (StringUtils.hasText(sourceUserKey) && StringUtils.hasText(session.getSourceUserKey())
            && !Objects.equals(sourceUserKey.trim(), session.getSourceUserKey())) {
            throw new BizException("PROCUREMENT_AI_SOURCE_MISMATCH", "Session does not belong to current source user");
        }
        return confirm(session.getId(), candidateNo, applicant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProcurementAiConfirmResultVO> confirmAllWaiting(String sourceUserKey, AuthUser applicant) {
        return confirmAllWaiting(sourceUserKey, applicant, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProcurementAiConfirmResultVO> confirmAllWaiting(String sourceUserKey, AuthUser applicant,
                                                                List<String> excludedCandidateKeys) {
        ensureInternalUser(applicant);
        Set<String> excludedKeys = normalizedCandidateKeys(excludedCandidateKeys);
        List<ProcurementAiSession> sessions = waitingSessionEntities(sourceUserKey);
        if (sessions.isEmpty()) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "没有待确认的采购候选");
        }
        List<BatchConfirmLine> lines = new ArrayList<>();
        List<BatchConfirmLine> skippedLines = new ArrayList<>();
        for (ProcurementAiSession session : sessions) {
            ensureSessionAccess(session, applicant);
            List<ProcurementAiCandidate> candidates = activeCandidates(session.getId());
            if (candidates.isEmpty()) {
                throw new BizException("PROCUREMENT_AI_CANDIDATE_NOT_FOUND", "候选物品不存在：" + session.getSessionNo());
            }
            for (ProcurementAiCandidate candidate : candidates) {
                BatchConfirmLine line = new BatchConfirmLine(session, candidate);
                if (excludedKeys.contains(candidateKey(session, candidate))) {
                    skippedLines.add(line);
                    continue;
                }
                ensurePositiveCandidatePrice(candidate);
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new BizException("PROCUREMENT_AI_CANDIDATE_NOT_FOUND", "已标记撤回所有候选明细，请先恢复至少一条再提交。");
        }

        AuthUser previous = AuthContextHolder.get();
        AuthContextHolder.set(applicant);
        try {
            Long requestId = purchaseRequestService.create(toBatchPurchaseRequestDTO(lines, applicant));
            purchaseRequestService.submit(requestId);
            PurchaseRequestVO request = purchaseRequestService.detail(requestId);

            Set<Long> submittedSessionIds = new LinkedHashSet<>();
            for (BatchConfirmLine line : lines) {
                ProcurementAiCandidate candidate = line.candidate();
                candidate.setSelectedFlag(1);
                candidateMapper.updateById(candidate);

                ProcurementAiSession session = line.session();
                if (submittedSessionIds.add(session.getId())) {
                    session.setConfirmedCandidateId(candidate.getId());
                    session.setPurchaseRequestId(requestId);
                    session.setStatus(STATUS_CONFIRMED);
                    sessionMapper.updateById(session);
                }
            }
            markSkippedCandidates(skippedLines, submittedSessionIds);

            ProcurementAiConfirmResultVO result = new ProcurementAiConfirmResultVO();
            BatchConfirmLine first = lines.get(0);
            result.setSessionId(first.session().getId());
            result.setSessionNo(first.session().getSessionNo());
            result.setCandidateNo(first.candidate().getCandidateNo());
            result.setPurchaseRequestId(requestId);
            result.setPurchaseRequestNo(request == null ? null : request.getReqNo());
            result.setStatus(STATUS_CONFIRMED);
            result.setRequest(request);
            return List.of(result);
        } finally {
            if (previous == null) {
                AuthContextHolder.clear();
            } else {
                AuthContextHolder.set(previous);
            }
        }
    }

    private void markSkippedCandidates(List<BatchConfirmLine> skippedLines, Set<Long> submittedSessionIds) {
        if (skippedLines == null || skippedLines.isEmpty()) {
            return;
        }
        Set<Long> touchedSessionIds = new LinkedHashSet<>();
        for (BatchConfirmLine line : skippedLines) {
            ProcurementAiCandidate candidate = line.candidate();
            candidate.setStatus(STATUS_CANCELLED);
            candidate.setRemark(limit("用户在候选卡片标记撤回后提交剩余明细", 500));
            candidateMapper.updateById(candidate);
            touchedSessionIds.add(line.session().getId());
        }
        for (Long sessionId : touchedSessionIds) {
            ProcurementAiSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                continue;
            }
            session.setRemark(limit(appendRemark(session.getRemark(), "部分候选明细已在卡片中标记撤回"), 500));
            if (!submittedSessionIds.contains(sessionId) && activeCandidates(sessionId).isEmpty()) {
                session.setStatus(STATUS_CANCELLED);
            }
            sessionMapper.updateById(session);
        }
    }

    private Set<String> normalizedCandidateKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String key : keys) {
            if (StringUtils.hasText(key)) {
                result.add(key.trim());
            }
        }
        return result;
    }

    private String candidateKey(ProcurementAiSession session, ProcurementAiCandidate candidate) {
        if (session == null || candidate == null || !StringUtils.hasText(session.getSessionNo())) {
            return "";
        }
        return session.getSessionNo().trim() + "#" + (candidate.getCandidateNo() == null ? 1 : candidate.getCandidateNo());
    }

    private List<ProcurementAiSession> waitingSessionEntities(String sourceUserKey) {
        if (!StringUtils.hasText(sourceUserKey)) {
            return List.of();
        }
        return sessionMapper.selectList(new LambdaQueryWrapper<ProcurementAiSession>()
            .eq(ProcurementAiSession::getSourceUserKey, sourceUserKey.trim())
            .in(ProcurementAiSession::getStatus, STATUS_WAITING_CONFIRM, STATUS_AI_FAILED)
            .orderByAsc(ProcurementAiSession::getCreatedAt)
            .orderByAsc(ProcurementAiSession::getId));
    }

    private ProcurementAiSession latestDraftEntity(String sourceUserKey, AuthUser applicant) {
        if (!StringUtils.hasText(sourceUserKey)) {
            return null;
        }
        ProcurementAiSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ProcurementAiSession>()
            .eq(ProcurementAiSession::getSourceUserKey, sourceUserKey.trim())
            .eq(ProcurementAiSession::getStatus, STATUS_DRAFTING)
            .orderByDesc(ProcurementAiSession::getUpdatedAt)
            .orderByDesc(ProcurementAiSession::getCreatedAt)
            .last("LIMIT 1"));
        if (session != null) {
            ensureSessionAccess(session, applicant);
        }
        return session;
    }

    private WaitingCandidateLine waitingCandidateAt(String sourceUserKey, Integer itemNo, AuthUser applicant) {
        List<WaitingCandidateLine> lines = waitingCandidateLines(sourceUserKey, applicant);
        if (lines.isEmpty()) {
            throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "没有待确认的采购候选");
        }
        int index = itemNo == null ? (lines.size() == 1 ? 1 : 0) : itemNo;
        if (index <= 0 || index > lines.size()) {
            throw new BizException("PROCUREMENT_AI_ITEM_INDEX_INVALID", "请指定要处理的第几项");
        }
        return lines.get(index - 1);
    }

    private List<WaitingCandidateLine> waitingCandidateLines(String sourceUserKey, AuthUser applicant) {
        List<ProcurementAiSession> sessions = waitingSessionEntities(sourceUserKey);
        List<WaitingCandidateLine> lines = new ArrayList<>();
        int itemNo = 1;
        for (ProcurementAiSession session : sessions) {
            ensureSessionAccess(session, applicant);
            for (ProcurementAiCandidate candidate : activeCandidates(session.getId())) {
                lines.add(new WaitingCandidateLine(itemNo++, session, candidate));
            }
        }
        return lines;
    }

    private ProcurementAiSession cancelCandidateLine(WaitingCandidateLine line, String remark) {
        ProcurementAiCandidate candidate = line.candidate();
        candidate.setStatus(STATUS_CANCELLED);
        candidate.setRemark(limit(remark, 500));
        candidateMapper.updateById(candidate);

        ProcurementAiSession session = line.session();
        if (activeCandidates(session.getId()).isEmpty()) {
            session.setStatus(STATUS_CANCELLED);
        }
        session.setRemark(limit(appendRemark(session.getRemark(), remark), 500));
        sessionMapper.updateById(session);
        return session;
    }

    private List<ProcurementAiCandidate> activeCandidates(Long sessionId) {
        return candidateMapper.selectList(new LambdaQueryWrapper<ProcurementAiCandidate>()
            .eq(ProcurementAiCandidate::getSessionId, sessionId)
            .eq(ProcurementAiCandidate::getStatus, STATUS_ACTIVE)
            .orderByAsc(ProcurementAiCandidate::getCandidateNo));
    }

    private ProcurementAiCandidate toCandidate(Long sessionId, ProcurementAiCandidateDraft draft, int fallbackNo) {
        ProcurementAiCandidate candidate = new ProcurementAiCandidate();
        candidate.setSessionId(sessionId);
        candidate.setCandidateNo(draft.getCandidateNo() == null ? fallbackNo : draft.getCandidateNo());
        candidate.setItemName(limit(draft.getItemName(), 255));
        candidate.setSpecModel(limit(draft.getSpecModel(), 255));
        candidate.setUnit(limit(firstText(draft.getUnit(), "件"), 32));
        candidate.setQty(scaleMoney(draft.getQty(), BigDecimal.ONE));
        candidate.setUnitPrice(scaleMoney(draft.getUnitPrice(), BigDecimal.ZERO));
        candidate.setTaxRate(scaleMoney(draft.getTaxRate(), BigDecimal.ZERO));
        candidate.setLineAmount(scaleMoney(draft.getLineAmount(), candidate.getQty().multiply(candidate.getUnitPrice())));
        candidate.setReqType(normalizeReqType(draft.getReqType()));
        candidate.setPurchaseReason(limit(draft.getPurchaseReason(), 500));
        candidate.setEcommercePlatform(limit(draft.getEcommercePlatform(), 64));
        candidate.setEcommerceUrl(limit(draft.getEcommerceUrl(), 500));
        candidate.setImageUrl(limit(draft.getImageUrl(), 500));
        candidate.setScore(draft.getScore());
        candidate.setSelectedFlag(0);
        candidate.setSourceJson(limit(draft.getSourceJson(), 2000));
        candidate.setStatus(STATUS_ACTIVE);
        return candidate;
    }

    private PurchaseRequestCreateDTO toPurchaseRequestDTO(ProcurementAiSession session, ProcurementAiCandidate candidate,
                                                          AuthUser applicant) {
        PurchaseRequestItemDTO item = toPurchaseRequestItemDTO(session, candidate);
        BigDecimal lineAmount = scaleMoney(item.getLineAmount(), BigDecimal.ZERO);
        String requestReqType = normalizeReqType(firstText(candidate.getReqType(), session.getReqType()));

        PurchaseRequestCreateDTO dto = new PurchaseRequestCreateDTO();
        dto.setReqTitle(buildAiPurchaseRequestTitle(session.getDeptName(), applicant.getDeptName(), requestReqType));
        dto.setReqType(requestReqType);
        dto.setApplicantUserId(applicant.getUserId());
        dto.setPurchaserUserId(applicant.getUserId());
        dto.setRegionId(applicant.getRegionId() == null ? 1L : applicant.getRegionId());
        dto.setDeptCode(firstText(session.getDeptCode(), applicant.getDeptCode()));
        dto.setDeptName(firstText(session.getDeptName(), applicant.getDeptName(), applicant.getRealName()));
        dto.setBudgetCode(session.getBudgetCode());
        dto.setCurrency("CNY");
        dto.setTotalAmount(lineAmount);
        dto.setUrgencyLevel(session.getUrgencyLevel() == null ? 1 : session.getUrgencyLevel());
        dto.setNeedDate(session.getNeedDate());
        dto.setReqReason(firstText(candidate.getPurchaseReason(), session.getRawText(), "AI采购需求"));
        dto.setAiReviewOpinion(buildAiReviewOpinion(session));
        dto.setItems(List.of(item));
        return dto;
    }

    private PurchaseRequestCreateDTO toBatchPurchaseRequestDTO(List<BatchConfirmLine> lines, AuthUser applicant) {
        BatchConfirmLine first = lines.get(0);
        List<PurchaseRequestItemDTO> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Integer urgencyLevel = 1;
        LocalDate needDate = null;
        for (BatchConfirmLine line : lines) {
            PurchaseRequestItemDTO item = toPurchaseRequestItemDTO(line.session(), line.candidate());
            items.add(item);
            totalAmount = totalAmount.add(scaleMoney(item.getLineAmount(), BigDecimal.ZERO));
            if (needDate == null && item.getNeedDate() != null) {
                needDate = item.getNeedDate();
            }
            Integer currentUrgency = line.session().getUrgencyLevel();
            if (line.session().getEmergencyFlag() != null && line.session().getEmergencyFlag() == 1) {
                currentUrgency = Math.max(currentUrgency == null ? 1 : currentUrgency, 2);
            }
            urgencyLevel = Math.max(urgencyLevel, currentUrgency == null ? 1 : currentUrgency);
        }

        ProcurementAiSession firstSession = first.session();
        ProcurementAiCandidate firstCandidate = first.candidate();
        String requestReqType = normalizeReqType(firstText(firstCandidate.getReqType(), firstSession.getReqType()));
        PurchaseRequestCreateDTO dto = new PurchaseRequestCreateDTO();
        dto.setReqTitle(buildBatchReqTitle(lines, applicant, requestReqType));
        dto.setReqType(requestReqType);
        dto.setApplicantUserId(applicant.getUserId());
        dto.setPurchaserUserId(applicant.getUserId());
        dto.setRegionId(applicant.getRegionId() == null ? 1L : applicant.getRegionId());
        dto.setDeptCode(firstText(firstSession.getDeptCode(), applicant.getDeptCode()));
        dto.setDeptName(firstText(firstSession.getDeptName(), applicant.getDeptName(), applicant.getRealName()));
        dto.setBudgetCode(firstSession.getBudgetCode());
        dto.setCurrency("CNY");
        dto.setTotalAmount(totalAmount);
        dto.setUrgencyLevel(urgencyLevel);
        dto.setNeedDate(needDate);
        dto.setReqReason(buildBatchReqReason(lines));
        dto.setAiReviewOpinion(buildBatchAiReviewOpinion(lines));
        dto.setItems(items);
        return dto;
    }

    private PurchaseRequestItemDTO toPurchaseRequestItemDTO(ProcurementAiSession session, ProcurementAiCandidate candidate) {
        BigDecimal qty = scaleMoney(candidate.getQty(), BigDecimal.ONE);
        BigDecimal unitPrice = scaleMoney(candidate.getUnitPrice(), BigDecimal.ZERO);
        BigDecimal lineAmount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        PurchaseRequestItemDTO item = new PurchaseRequestItemDTO();
        item.setReqType(firstText(candidate.getReqType(), session.getReqType()));
        item.setItemName(firstText(candidate.getItemName(), session.getReqTitle(), session.getRawText()));
        item.setSpecModel(candidate.getSpecModel());
        item.setUnit(firstText(candidate.getUnit(), "件"));
        item.setQty(qty);
        item.setUnitPrice(unitPrice);
        item.setTaxRate(scaleMoney(candidate.getTaxRate(), BigDecimal.ZERO));
        item.setLineAmount(lineAmount);
        item.setNeedDate(session.getNeedDate());
        item.setPurpose(firstText(candidate.getPurchaseReason(), session.getRawText(), "采购需求"));
        item.setEcommerceUrl(candidate.getEcommerceUrl());
        item.setStatus(STATUS_ACTIVE);
        return item;
    }

    private String buildBatchReqTitle(List<BatchConfirmLine> lines, AuthUser applicant, String requestReqType) {
        BatchConfirmLine line = lines.get(0);
        return buildAiPurchaseRequestTitle(line.session().getDeptName(), applicant.getDeptName(), requestReqType);
    }

    static String buildAiPurchaseRequestTitle(String deptName, String fallbackDeptName, String reqType) {
        String roomName = requestTitleRoomName(deptName, fallbackDeptName);
        String typeLabel = requestTitleTypeLabel(reqType);
        return limitText(roomName + "-" + typeLabel + "采购申请", 255);
    }

    static String requestTitleRoomName(String deptName, String fallbackDeptName) {
        String value = firstNonBlank(deptName, fallbackDeptName, "未指定机房")
            .replace('／', '/')
            .replace('\\', '/')
            .replace('－', '-')
            .replace('—', '-')
            .trim();
        String segment = value;
        if (value.contains("/")) {
            segment = lastNonBlank(value.split("/"), value);
            int detailStart = segment.indexOf(" - [");
            if (detailStart > 0) {
                segment = segment.substring(0, detailStart);
            }
        } else if (value.contains("-")) {
            segment = lastNonBlank(value.split("-"), value);
        }
        return firstNonBlank(cleanDepartmentSegment(segment), cleanDepartmentSegment(value), "未指定机房");
    }

    static String requestTitleTypeLabel(String reqType) {
        String value = firstNonBlank(reqType, "服务/费用/低值易耗等");
        if (value.contains("工程")) {
            return "工程项目";
        }
        if (value.contains("资产") || value.contains("无形") || value.contains("长摊") || value.contains("长期待摊")) {
            return "资产";
        }
        return "低值易耗";
    }

    private static String cleanDepartmentSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
            .replaceAll("^\\[[^\\]]+\\]", "")
            .replaceAll("\\s*\\([^)]*\\)\\s*$", "")
            .replaceAll("\\s*（[^）]*）\\s*$", "")
            .trim();
    }

    private static String lastNonBlank(String[] values, String defaultValue) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (StringUtils.hasText(values[i])) {
                return values[i].trim();
            }
        }
        return defaultValue;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String buildBatchReqReason(List<BatchConfirmLine> lines) {
        StringBuilder builder = new StringBuilder("AI采购助手批量提交，共").append(lines.size()).append("项：");
        int limit = Math.min(lines.size(), 6);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("、");
            }
            BatchConfirmLine line = lines.get(i);
            builder.append(firstText(line.candidate().getItemName(), line.session().getReqTitle(), "采购需求"));
        }
        if (lines.size() > limit) {
            builder.append("等");
        }
        return limit(builder.toString(), 500);
    }

    private String batchSessionNos(List<BatchConfirmLine> lines) {
        return lines.stream()
            .map(line -> line.session().getSessionNo())
            .filter(StringUtils::hasText)
            .distinct()
            .limit(20)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    }

    private String buildAiReviewOpinion(ProcurementAiSession session) {
        return limit(ensureApprovalDecisionOpinion(firstText(session.getAiSummary(),
            "建议通过：AI已生成采购候选，建议按原流程审批并核对商品名称、数量、用途、规格和参考价格。")),
            1000);
    }

    private String buildBatchAiReviewOpinion(List<BatchConfirmLine> lines) {
        return limit(lines.stream()
            .map(line -> line.session().getAiSummary())
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(this::ensureApprovalDecisionOpinion)
            .distinct()
            .limit(3)
            .reduce((left, right) -> left + "；" + right)
            .orElse("建议通过：AI已生成" + lines.size() + "项采购候选，建议按原流程审批并重点核对明细用途、数量和参考价格。"),
            1000);
    }

    private String ensureApprovalDecisionOpinion(String opinion) {
        if (!StringUtils.hasText(opinion)) {
            return "";
        }
        String value = opinion.trim();
        if (hasApprovalDecisionPrefix(value)) {
            return value;
        }
        return approvalDecisionPrefixForText(value) + value;
    }

    private String approvalDecisionPrefixForText(String opinion) {
        if (opinion.contains("不通过") || opinion.contains("退回") || opinion.contains("驳回")) {
            return "建议退回：";
        }
        if (opinion.contains("补充") || opinion.contains("更正") || opinion.contains("缺失")
            || opinion.contains("不完整") || opinion.contains("未识别") || opinion.contains("小于等于 0")) {
            return "建议补充：";
        }
        return "建议通过：";
    }

    private boolean hasApprovalDecisionPrefix(String opinion) {
        if (!StringUtils.hasText(opinion)) {
            return false;
        }
        String value = opinion.trim();
        return value.startsWith("建议通过")
            || value.startsWith("建议补充")
            || value.startsWith("建议退回")
            || value.startsWith("建议驳回")
            || value.startsWith("建议不通过");
    }

    private ProcurementAiSessionVO toSessionVO(ProcurementAiSession session) {
        ProcurementAiSessionVO vo = new ProcurementAiSessionVO();
        vo.setId(session.getId());
        vo.setSessionNo(session.getSessionNo());
        vo.setSourceChannel(session.getSourceChannel());
        vo.setApplicantUserId(session.getApplicantUserId());
        vo.setRawText(session.getRawText());
        vo.setImageKey(session.getImageKey());
        vo.setImageUrl(session.getImageUrl());
        vo.setReqTitle(session.getReqTitle());
        vo.setReqType(session.getReqType());
        vo.setPlanMonth(session.getPlanMonth());
        vo.setDeptCode(session.getDeptCode());
        vo.setDeptName(session.getDeptName());
        vo.setBudgetCode(session.getBudgetCode());
        vo.setUrgencyLevel(session.getUrgencyLevel());
        vo.setEmergency(session.getEmergencyFlag() != null && session.getEmergencyFlag() == 1);
        vo.setNeedDate(session.getNeedDate());
        vo.setStatus(session.getStatus());
        vo.setConfirmedCandidateId(session.getConfirmedCandidateId());
        vo.setMonthlyDemandId(session.getMonthlyDemandId());
        vo.setPurchaseRequestId(session.getPurchaseRequestId());
        vo.setAiSummary(session.getAiSummary());
        vo.setAiError(session.getAiError());
        vo.setDifyRequestJson(session.getDifyRequestJson());
        vo.setDifyResponseText(session.getDifyResponseText());
        vo.setDifyParsedJson(session.getDifyParsedJson());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setCandidates(candidateMapper.selectList(new LambdaQueryWrapper<ProcurementAiCandidate>()
            .eq(ProcurementAiCandidate::getSessionId, session.getId())
            .orderByAsc(ProcurementAiCandidate::getCandidateNo))
            .stream()
            .map(this::toCandidateVO)
            .toList());
        return vo;
    }

    private ProcurementAiCandidateVO toCandidateVO(ProcurementAiCandidate candidate) {
        ProcurementAiCandidateVO vo = new ProcurementAiCandidateVO();
        vo.setId(candidate.getId());
        vo.setCandidateNo(candidate.getCandidateNo());
        vo.setItemName(candidate.getItemName());
        vo.setSpecModel(candidate.getSpecModel());
        vo.setUnit(candidate.getUnit());
        vo.setQty(candidate.getQty());
        vo.setUnitPrice(candidate.getUnitPrice());
        vo.setTaxRate(candidate.getTaxRate());
        vo.setLineAmount(candidate.getLineAmount());
        vo.setReqType(candidate.getReqType());
        vo.setPurchaseReason(candidate.getPurchaseReason());
        vo.setEcommercePlatform(candidate.getEcommercePlatform());
        vo.setEcommerceUrl(candidate.getEcommerceUrl());
        vo.setImageUrl(candidate.getImageUrl());
        vo.setScore(candidate.getScore());
        vo.setSelected(candidate.getSelectedFlag() != null && candidate.getSelectedFlag() == 1);
        vo.setStatus(candidate.getStatus());
        vo.setRemark(candidate.getRemark());
        return vo;
    }

    private void ensureSessionAccess(ProcurementAiSession session, AuthUser authUser) {
        if (DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (!Objects.equals(session.getApplicantUserId(), authUser.getUserId())) {
            throw new BizException("FORBIDDEN", "Cannot access other user's AI session");
        }
    }

    private void ensureInternalUser(AuthUser authUser) {
        if (authUser == null) {
            throw new BizException("UNAUTHORIZED", "Not logged in or session expired");
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "Supplier account cannot submit internal procurement demand");
        }
    }

    private void ensurePositiveCandidatePrice(ProcurementAiCandidate candidate) {
        if (candidate == null || !positiveMoney(candidate.getUnitPrice())) {
            String itemName = candidate == null ? "候选明细" : firstText(candidate.getItemName(), "候选明细");
            throw new BizException("PROCUREMENT_AI_PRICE_REQUIRED",
                itemName + " 的参考单价必须大于 0，请先补充或更正价格后再提交。");
        }
    }

    private boolean positiveMoney(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal scaleMoney(BigDecimal value, BigDecimal defaultValue) {
        BigDecimal result = value == null ? defaultValue : value;
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeReqType(String reqType) {
        String value = firstText(reqType, "服务/费用/低值易耗等");
        if ("工程项目".equals(value)) {
            return "工程项目";
        }
        if ("资产".equals(value) || "实物资产".equals(value) || "无形资产".equals(value) || "长摊".equals(value)
            || "长期待摊".equals(value) || "实物资产/无形资产/长摊等".equals(value)) {
            return "实物资产/无形资产/长摊等";
        }
        if ("低值易耗".equals(value) || "服务".equals(value) || "费用".equals(value) || "其他".equals(value)
            || "服务/费用/低值易耗等".equals(value)) {
            return "服务/费用/低值易耗等";
        }
        return "服务/费用/低值易耗等";
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String generateSessionNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "PA" + date + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String appendRemark(String current, String message) {
        if (!StringUtils.hasText(current)) {
            return message;
        }
        return current + "\n" + message;
    }

    private String appendDraftText(String current, String incoming) {
        if (!StringUtils.hasText(current)) {
            return incoming;
        }
        if (!StringUtils.hasText(incoming)) {
            return current;
        }
        return current.trim() + "\n" + incoming.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record BatchConfirmLine(ProcurementAiSession session, ProcurementAiCandidate candidate) {
    }

    private record WaitingCandidateLine(int itemNo, ProcurementAiSession session, ProcurementAiCandidate candidate) {
    }
}
