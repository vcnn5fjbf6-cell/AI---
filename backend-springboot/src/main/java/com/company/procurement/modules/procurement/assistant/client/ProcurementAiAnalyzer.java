package com.company.procurement.modules.procurement.assistant.client;

import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiAnalysisResult;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiCandidateDraft;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcurementAiAnalyzer {

    private static final String IMAGE_DRAFT_MARKER = "图片识别结果（仅作草稿上下文";
    private static final String CURRENT_SUPPLEMENT_MARKER = "本次用户补充：";
    private static final Pattern PRICE_PATTERN = Pattern.compile("参考单价：\\s*[¥￥]?\\s*(\\d+(?:\\.\\d+)?)");

    private final M3VisionClient m3VisionClient;
    private final DifyProcurementClient difyProcurementClient;

    public boolean canAnalyzeWithM3(ProcurementAiStartDTO dto) {
        return m3VisionClient.canAnalyze(dto);
    }

    public boolean canCompleteTextWithM3() {
        return m3VisionClient.canCompleteText();
    }

    public M3VisionClient.CandidateCommand interpretCandidateCommand(String userText,
                                                                     List<ProcurementAiSessionVO> waitingSessions,
                                                                     String sourceUserKey) {
        return m3VisionClient.interpretCandidateCommand(userText, waitingSessions, sourceUserKey);
    }

    public ProcurementAiAnalysisResult analyze(ProcurementAiStartDTO dto, String sourceUserKey) {
        if (isImageRecognitionDraft(dto)) {
            return analyzeImageRecognitionDraft(dto, sourceUserKey);
        }
        if (m3VisionClient.canAnalyze(dto)) {
            try {
                return m3VisionClient.analyze(dto, sourceUserKey);
            } catch (RuntimeException ex) {
                log.warn("M3 vision analysis failed, fallback to Dify", ex);
            }
        }
        return difyProcurementClient.analyze(dto, sourceUserKey);
    }

    public static boolean isImageRecognitionDraft(ProcurementAiStartDTO dto) {
        String rawText = dto == null ? "" : firstText(dto.getRawText(), "");
        return rawText.contains(IMAGE_DRAFT_MARKER) && rawText.contains(CURRENT_SUPPLEMENT_MARKER);
    }

    private ProcurementAiAnalysisResult analyzeImageRecognitionDraft(ProcurementAiStartDTO dto, String sourceUserKey) {
        String rawText = dto == null ? "" : firstText(dto.getRawText(), "");
        String row = firstRecognitionRow(rawText);
        if (!StringUtils.hasText(row)) {
            return incompleteImageDraftResult(dto, null, "未能找到图片识别出的商品信息，请重新发送商品图片或直接说明商品、数量和用途。");
        }
        if (!m3VisionClient.canCompleteText()) {
            return incompleteImageDraftResult(dto, null, "AI 意图判断暂时不可用，我不会自动创建采购候选。请稍后重试，或明确发送要采购的物品、数量和用途。");
        }

        M3VisionClient.ImageDraftSupplement supplementResult;
        try {
            supplementResult = m3VisionClient.completeImageRecognitionDraft(dto, sourceUserKey);
        } catch (RuntimeException ex) {
            log.warn("M3 image draft supplement analysis failed", ex);
            return incompleteImageDraftResult(dto, null, "AI 意图判断暂时不可用，我不会自动创建采购候选。请稍后重试，或明确发送要采购的物品、数量和用途。");
        }

        String itemName = field(row, "商品名称：", "；规格型号：", "；参考单价：", "；建议单位：");
        String specModel = field(row, "；规格型号：", "；参考单价：", "；建议单位：");
        BigDecimal unitPrice = parsePrice(row);
        BigDecimal qty = positiveDecimal(supplementResult.quantity() == null ? null : supplementResult.quantity().toPlainString());
        String unit = firstText(supplementResult.unit(), field(row, "；建议单位："));
        String reason = firstText(supplementResult.purpose());
        if (!StringUtils.hasText(itemName) || unitPrice == null) {
            return incompleteImageDraftResult(dto, supplementResult, "图片识别出的商品信息还不完整，请重新发送商品图片或直接更正商品名称、规格和参考价格。");
        }
        if (qty == null || !StringUtils.hasText(reason)) {
            return incompleteImageDraftResult(dto, supplementResult, imageDraftClarification(supplementResult, qty, reason));
        }

        String reqType = normalizeReqType(dto.getReqType());
        ProcurementAiCandidateDraft candidate = new ProcurementAiCandidateDraft();
        candidate.setCandidateNo(1);
        candidate.setItemName(limit(itemName, 255));
        candidate.setSpecModel(limit(specModel, 255));
        candidate.setUnit(limit(firstText(unit, "件"), 32));
        candidate.setQty(qty);
        candidate.setUnitPrice(unitPrice);
        candidate.setTaxRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        candidate.setLineAmount(qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
        candidate.setReqType(reqType);
        candidate.setPurchaseReason(limit(reason, 500));
        candidate.setImageUrl(limit(dto.getImageUrl(), 500));
        candidate.setSourceJson("{\"source\":\"local_image_recognition_draft\"}");

        ProcurementAiAnalysisResult result = new ProcurementAiAnalysisResult();
        result.setReqTitle(limit(firstText(dto.getReqTitle(), itemName), 255));
        result.setReqType(reqType);
        result.setPlanMonth(dto.getPlanMonth());
        result.setNeedDate(parseDate(dto.getNeedDate()));
        result.setUrgencyLevel(dto.getUrgencyLevel() == null ? 1 : dto.getUrgencyLevel());
        result.setEmergency(Boolean.TRUE.equals(dto.getEmergency()) || result.getUrgencyLevel() >= 2);
        result.setCandidates(List.of(candidate));
        result.setAiSummary("建议通过：图片识别草稿已补齐采购数量和用途，参考金额约 ¥"
            + candidate.getLineAmount().setScale(2, RoundingMode.HALF_UP)
            + "；按原采购审批流程流转，请审批人核对必要性、规格、数量和最终供应商报价。");
        result.setDifyRequestJson(supplementResult.requestJson());
        result.setDifyResponseText(supplementResult.responseText());
        result.setDifyParsedJson(supplementResult.parsedJson());
        return result;
    }

    private static ProcurementAiAnalysisResult incompleteImageDraftResult(ProcurementAiStartDTO dto,
                                                                          M3VisionClient.ImageDraftSupplement supplement,
                                                                          String replyText) {
        ProcurementAiAnalysisResult result = new ProcurementAiAnalysisResult();
        result.setReqTitle(limit(firstText(dto == null ? null : dto.getReqTitle(), "图片采购需求待补充"), 255));
        result.setReqType(normalizeReqType(dto == null ? null : dto.getReqType()));
        result.setPlanMonth(dto == null ? null : dto.getPlanMonth());
        result.setNeedDate(parseDate(dto == null ? null : dto.getNeedDate()));
        result.setUrgencyLevel(dto == null || dto.getUrgencyLevel() == null ? 1 : dto.getUrgencyLevel());
        result.setEmergency(Boolean.TRUE.equals(dto == null ? null : dto.getEmergency()) || result.getUrgencyLevel() >= 2);
        result.setAiSummary(limit(firstText(replyText, "请补充采购数量和用途。"), 1000));
        result.setAiError("IMAGE_DRAFT_NEEDS_CLARIFICATION");
        result.setCandidates(List.of());
        if (supplement != null) {
            result.setDifyRequestJson(supplement.requestJson());
            result.setDifyResponseText(supplement.responseText());
            result.setDifyParsedJson(supplement.parsedJson());
        }
        return result;
    }

    private static String imageDraftClarification(M3VisionClient.ImageDraftSupplement supplement, BigDecimal qty, String reason) {
        String question = supplement == null ? "" : supplement.clarificationQuestion();
        if (StringUtils.hasText(question)) {
            return question.trim();
        }
        if (qty == null && !StringUtils.hasText(reason)) {
            return "请补充本次采购的数量和用途。";
        }
        if (qty == null) {
            return "请补充本次采购的数量。";
        }
        return "请补充本次采购的用途。";
    }

    private static String firstRecognitionRow(String rawText) {
        for (String line : rawText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains("商品名称：") && trimmed.contains("参考单价：")) {
                return trimmed;
            }
        }
        return "";
    }

    private static String field(String text, String startToken, String... endTokens) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int start = text.indexOf(startToken);
        if (start < 0) {
            return "";
        }
        int valueStart = start + startToken.length();
        int end = text.length();
        for (String endToken : endTokens) {
            int candidate = text.indexOf(endToken, valueStart);
            if (candidate >= 0 && candidate < end) {
                end = candidate;
            }
        }
        return text.substring(valueStart, end).trim();
    }

    private static BigDecimal parsePrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(firstText(text, ""));
        if (!matcher.find()) {
            return null;
        }
        return positiveDecimal(matcher.group(1));
    }

    private static BigDecimal positiveDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.trim());
            return decimal.compareTo(BigDecimal.ZERO) > 0 ? decimal.setScale(2, RoundingMode.HALF_UP) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeReqType(String value) {
        String reqType = firstText(value, "");
        if (!StringUtils.hasText(reqType) || "其他".equals(reqType)) {
            return "服务/费用/低值易耗等";
        }
        return reqType;
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String firstText(String... values) {
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

    private static String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
