package com.company.procurement.modules.procurement.assistant.service;

import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiAnalysisResult;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiConfirmDTO;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiConfirmResultVO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiSessionVO;

import java.math.BigDecimal;
import java.util.List;

public interface ProcurementAiService {

    ProcurementAiSessionVO startSession(ProcurementAiStartDTO dto);

    ProcurementAiSessionVO startSession(ProcurementAiStartDTO dto, AuthUser applicant, String sourceChannel,
                                        String sourceUserKey, String sourceMessageId);

    ProcurementAiSessionVO startSession(ProcurementAiStartDTO dto, ProcurementAiAnalysisResult analysis,
                                        AuthUser applicant, String sourceChannel,
                                        String sourceUserKey, String sourceMessageId);

    ProcurementAiSessionVO getSession(Long id);

    ProcurementAiSessionVO latestWaitingSession(String sourceUserKey);

    ProcurementAiSessionVO latestDraftSession(String sourceUserKey, AuthUser applicant);

    List<ProcurementAiSessionVO> waitingSessions(String sourceUserKey);

    ProcurementAiSessionVO saveDraft(ProcurementAiStartDTO dto, AuthUser applicant, String sourceChannel,
                                     String sourceUserKey, String sourceMessageId, String replyText,
                                     String difyRequestJson, String difyResponseText, String difyParsedJson);

    void markDraftConverted(Long draftSessionId, Long candidateSessionId);

    ProcurementAiSessionVO updateWaitingCandidateReason(String sourceUserKey, Integer itemNo, String reason,
                                                        AuthUser applicant);

    ProcurementAiSessionVO updateWaitingCandidateQuantity(String sourceUserKey, Integer itemNo, BigDecimal quantity,
                                                          String unit, AuthUser applicant);

    ProcurementAiSessionVO cancelWaitingSession(String sourceUserKey, Integer itemNo, AuthUser applicant);

    ProcurementAiSessionVO cancelWaitingCandidateBySessionNo(String sessionNo, Integer candidateNo,
                                                             AuthUser applicant, String sourceUserKey);

    List<ProcurementAiSessionVO> cancelAllWaiting(String sourceUserKey, AuthUser applicant);

    ProcurementAiConfirmResultVO confirm(Long sessionId, ProcurementAiConfirmDTO dto);

    ProcurementAiConfirmResultVO confirm(Long sessionId, Integer candidateNo, AuthUser applicant);

    ProcurementAiConfirmResultVO confirmBySessionNo(String sessionNo, Integer candidateNo, AuthUser applicant,
                                                    String sourceUserKey);

    List<ProcurementAiConfirmResultVO> confirmAllWaiting(String sourceUserKey, AuthUser applicant);

    List<ProcurementAiConfirmResultVO> confirmAllWaiting(String sourceUserKey, AuthUser applicant,
                                                         List<String> excludedCandidateKeys);
}
