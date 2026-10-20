package com.company.procurement.modules.procurement.assistant.client;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.procurement.assistant.config.DifyProcurementRuntimeConfig;
import com.company.procurement.modules.procurement.assistant.config.DifyProcurementRuntimeConfig.Snapshot;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DifyProcurementClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final Pattern SIMPLE_QUANTITY_PATTERN = Pattern.compile(
        "(?:\\u91c7\\u8d2d|\\u7533\\u8d2d|\\u7533\\u8bf7|\\u8d2d\\u4e70|\\u9700\\u8981|\\u4e70|\\u8981)?\\s*"
            + "(\\d+(?:\\.\\d+)?)\\s*"
            + "([个件台套支条箱包双把卷瓶盒块张根只米本辆片副枚])"
    );
    private static final Pattern LOCAL_QUANTITY_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*([个件台套支条箱包双把卷瓶盒块张根只米本辆片副枚])"
    );
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\u3000<>\"'\\]\\[)）]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:¥|￥|单价|价格|预算|参考价)\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern EXCEL_ROW_PATTERN = Pattern.compile("^第(\\d+)行[:：](.+)$");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

    private final DifyProcurementRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder()
        .requestFactory(requestFactory())
        .build();

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    public ProcurementAiAnalysisResult analyze(ProcurementAiStartDTO dto, String userKey) {
        Snapshot config = runtimeConfig.current();
        if (!config.enabled() || !StringUtils.hasText(config.apiKey())) {
            return localDraft(dto);
        }

        if (config.chatApp()) {
            return analyzeWithChat(dto, userKey, config);
        }
        return analyzeWithWorkflow(dto, userKey, config);
    }

    public FeishuMessageIntentResult classifyFeishuMessage(ProcurementAiStartDTO dto, String userKey) {
        FeishuMessageIntentResult excelIntent = classifyExcelAttachment(dto);
        if (excelIntent != null) {
            return excelIntent;
        }
        Snapshot config = runtimeConfig.current();
        if (!config.enabled() || !StringUtils.hasText(config.apiKey())) {
            return unavailableMessageIntent("Dify intent is not configured");
        }
        try {
            if (config.chatApp()) {
                return classifyWithChat(dto, userKey, config);
            }
            return classifyWithWorkflow(dto, userKey, config);
        } catch (RestClientException ex) {
            return unavailableMessageIntent("Dify intent request failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return unavailableMessageIntent("Dify intent parse failed: " + ex.getMessage());
        }
    }

    private ProcurementAiAnalysisResult analyzeWithWorkflow(ProcurementAiStartDTO dto, String userKey, Snapshot config) {
        Map<String, Object> inputs = commonInputs(dto);
        inputs.put("task_type", "product_recommendation");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("response_mode", responseMode(config));
        body.put("user", StringUtils.hasText(userKey) ? userKey : "procurement-platform");

        try {
            Map<String, Object> response = restClient.post()
                .uri(config.workflowRunUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
            return withDifyDebug(parseWorkflowResponse(response, dto), body, response);
        } catch (RestClientException ex) {
            ProcurementAiAnalysisResult fallback = localDraft(dto);
            fallback.setAiError("Dify workflow request failed: " + ex.getMessage());
            return withDifyDebug(fallback, body, Map.of("error", ex.getMessage()));
        }
    }

    private FeishuMessageIntentResult classifyWithWorkflow(ProcurementAiStartDTO dto, String userKey, Snapshot config) {
        Map<String, Object> inputs = commonInputs(dto);
        inputs.put("task_type", "message_intent");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("response_mode", responseMode(config));
        body.put("user", StringUtils.hasText(userKey) ? userKey : "procurement-platform");

        Map<String, Object> response = restClient.post()
            .uri(config.workflowRunUrl())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE);
        return withDifyDebug(parseIntentWorkflowResponse(response, dto), body, response);
    }

    private ProcurementAiAnalysisResult analyzeWithChat(ProcurementAiStartDTO dto, String userKey, Snapshot config) {
        Map<String, Object> inputs = commonInputs(dto);
        inputs.put("task_type", "product_recommendation");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("query", chatQuery(dto));
        body.put("response_mode", chatResponseMode(config));
        body.put("user", StringUtils.hasText(userKey) ? userKey : "procurement-platform");
        if (StringUtils.hasText(dto.getImageUrl())) {
            body.put("files", List.of(Map.of(
                "type", "image",
                "transfer_method", "remote_url",
                "url", dto.getImageUrl()
            )));
        }

        try {
            String responseText = restClient.post()
                .uri(config.chatMessagesUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            return withDifyDebug(parseAgentResponseText(responseText, dto), body, responseText);
        } catch (RestClientException ex) {
            ProcurementAiAnalysisResult fallback = localDraft(dto);
            fallback.setAiError("Dify agent request failed: " + ex.getMessage());
            return withDifyDebug(fallback, body, Map.of("error", ex.getMessage()));
        }
    }

    private FeishuMessageIntentResult classifyWithChat(ProcurementAiStartDTO dto, String userKey, Snapshot config) {
        Map<String, Object> inputs = commonInputs(dto);
        inputs.put("task_type", "message_intent");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("query", chatQuery(dto));
        body.put("response_mode", chatResponseMode(config));
        body.put("user", StringUtils.hasText(userKey) ? userKey : "procurement-platform");
        if (StringUtils.hasText(dto.getImageUrl())) {
            body.put("files", List.of(Map.of(
                "type", "image",
                "transfer_method", "remote_url",
                "url", dto.getImageUrl()
            )));
        }

        String responseText = restClient.post()
            .uri(config.chatMessagesUrl())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
        return withDifyDebug(parseIntentAgentResponseText(responseText, dto), body, responseText);
    }

    private Map<String, Object> commonInputs(ProcurementAiStartDTO dto) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("text", text(dto.getRawText()));
        inputs.put("raw_text", text(dto.getRawText()));
        inputs.put("image_key", text(dto.getImageKey()));
        inputs.put("image_url", text(dto.getImageUrl()));
        inputs.put("req_type", text(dto.getReqType()));
        inputs.put("plan_month", text(dto.getPlanMonth()));
        inputs.put("need_date", text(dto.getNeedDate()));
        inputs.put("urgency_level", text(dto.getUrgencyLevel()));
        inputs.put("emergency", text(dto.getEmergency()));
        return inputs;
    }

    private ProcurementAiAnalysisResult parseWorkflowResponse(Map<String, Object> response, ProcurementAiStartDTO dto) {
        if (response == null) {
            throw new BizException("DIFY_EMPTY_RESPONSE", "Dify workflow returned empty response");
        }
        Map<String, Object> data = mapValue(response.get("data"));
        String status = text(data.get("status"));
        if (StringUtils.hasText(status) && !"succeeded".equalsIgnoreCase(status)) {
            ProcurementAiAnalysisResult fallback = localDraft(dto);
            fallback.setAiError(firstText(text(data.get("error")), "Dify workflow status: " + status));
            fallback.setDifyParsedJson(debugText(data));
            return fallback;
        }

        Map<String, Object> outputs = mapValue(data.get("outputs"));
        if (outputs.isEmpty()) {
            outputs = mapValue(response.get("outputs"));
        }

        return parseOutputs(outputs, dto, "workflow");
    }

    private FeishuMessageIntentResult parseIntentWorkflowResponse(Map<String, Object> response, ProcurementAiStartDTO dto) {
        if (response == null) {
            return unavailableMessageIntent("Dify workflow returned empty intent response");
        }
        Map<String, Object> data = mapValue(response.get("data"));
        String status = text(data.get("status"));
        if (StringUtils.hasText(status) && !"succeeded".equalsIgnoreCase(status)) {
            FeishuMessageIntentResult result = unavailableMessageIntent(firstText(text(data.get("error")), "Dify workflow status: " + status));
            result.setDifyParsedJson(debugText(data));
            return result;
        }
        Map<String, Object> outputs = mapValue(data.get("outputs"));
        if (outputs.isEmpty()) {
            outputs = mapValue(response.get("outputs"));
        }
        return parseIntentOutputs(outputs, dto, "workflow");
    }

    private ProcurementAiAnalysisResult parseAgentResponseText(String responseText, ProcurementAiStartDTO dto) {
        Map<String, Object> response = isSseResponse(responseText) ? parseSseResponse(responseText) : parseJsonObject(responseText);
        return parseAgentResponse(response, dto);
    }

    private FeishuMessageIntentResult parseIntentAgentResponseText(String responseText, ProcurementAiStartDTO dto) {
        Map<String, Object> response = isSseResponse(responseText) ? parseSseResponse(responseText) : parseJsonObject(responseText);
        String answer = extractResponseAnswer(response);
        Map<String, Object> outputs = extractResponseOutputs(response);
        if (outputs.isEmpty()) {
            outputs = parseJsonObject(answer);
        }
        FeishuMessageIntentResult result = parseIntentOutputs(outputs, dto, "agent");
        if (!StringUtils.hasText(result.getReply())) {
            result.setReply(answer);
        }
        return result;
    }

    private ProcurementAiAnalysisResult parseAgentResponse(Map<String, Object> response, ProcurementAiStartDTO dto) {
        if (response == null) {
            throw new BizException("DIFY_EMPTY_RESPONSE", "Dify agent returned empty response");
        }
        String answer = extractResponseAnswer(response);
        Map<String, Object> outputs = extractResponseOutputs(response);
        if (outputs.isEmpty()) {
            outputs = parseJsonObject(answer);
        }
        if (outputs.isEmpty()) {
            ProcurementAiAnalysisResult fallback = localDraft(dto);
            fallback.setAiSummary(answer);
            fallback.setAiError("Dify agent did not return parseable procurement JSON.");
            fallback.setDifyParsedJson(debugText(response));
            return fallback;
        }
        ProcurementAiAnalysisResult result = parseOutputs(outputs, dto, "agent");
        if (!StringUtils.hasText(result.getAiSummary())) {
            result.setAiSummary(answer);
        }
        return result;
    }

    private ProcurementAiAnalysisResult parseOutputs(Map<String, Object> outputs, ProcurementAiStartDTO dto, String source) {
        outputs = normalizeOutputObject(outputs);
        ProcurementAiAnalysisResult result = new ProcurementAiAnalysisResult();
        result.setReqTitle(firstText(text(outputs.get("reqTitle")), text(outputs.get("req_title")), text(outputs.get("title")), dto.getReqTitle()));
        result.setReqType(normalizeReqType(firstText(text(outputs.get("reqType")), text(outputs.get("req_type")), text(outputs.get("type")), dto.getReqType())));
        result.setPlanMonth(firstText(text(outputs.get("planMonth")), text(outputs.get("plan_month")), dto.getPlanMonth()));
        result.setNeedDate(parseDate(firstText(text(outputs.get("needDate")), text(outputs.get("need_date")), dto.getNeedDate())));
        result.setUrgencyLevel(numberValue(firstObject(outputs.get("urgencyLevel"), outputs.get("urgency_level")),
            dto.getUrgencyLevel() == null ? 1 : dto.getUrgencyLevel()));
        result.setEmergency(Boolean.TRUE.equals(dto.getEmergency()) || result.getUrgencyLevel() >= 2);
        List<ProcurementAiCandidateDraft> candidates = normalizeCandidates(parseCandidates(outputs, dto), dto);
        if (candidates.isEmpty()) {
            candidates = localDraft(dto).getCandidates();
            if (candidates.size() == 1 && !isExcelAttachmentText(dto == null ? null : dto.getRawText())
                && StringUtils.hasText(result.getReqTitle())) {
                candidates.get(0).setItemName(limit(result.getReqTitle(), 255));
            }
            result.setAiError("Dify " + source + " returned no procurement candidates with unit price greater than 0.");
        }
        result.setCandidates(candidates);
        String aiReviewOpinion = firstText(
            extractApprovalReviewOpinion(outputs),
            buildLocalApprovalReviewOpinion(result, candidates),
            text(outputs.get("summary")),
            text(outputs.get("ai_summary")),
            text(outputs.get("answer"))
        );
        result.setAiSummary(limit(normalizeApprovalReviewOpinion(aiReviewOpinion, candidates, outputs), 1000));
        result.setDifyParsedJson(debugText(outputs));
        return result;
    }

    private String extractApprovalReviewOpinion(Map<String, Object> outputs) {
        Map<String, Object> approvalReview = mapValue(firstObject(
            outputs.get("approvalReview"),
            outputs.get("approval_review"),
            outputs.get("aiReview"),
            outputs.get("ai_review"),
            outputs.get("review")
        ));
        String directOpinion = firstText(
            text(outputs.get("aiReviewOpinion")),
            text(outputs.get("ai_review_opinion")),
            text(outputs.get("auditOpinion")),
            text(outputs.get("audit_opinion")),
            text(outputs.get("reviewOpinion")),
            text(outputs.get("review_opinion")),
            text(approvalReview.get("approvalCommentSuggestion")),
            text(approvalReview.get("approval_comment_suggestion")),
            text(approvalReview.get("summary")),
            text(approvalReview.get("opinion"))
        );
        List<String> points = textList(firstObject(
            approvalReview.get("reviewPoints"),
            approvalReview.get("review_points"),
            outputs.get("reviewPoints"),
            outputs.get("review_points")
        ));
        if (points.isEmpty()) {
            return directOpinion;
        }
        StringBuilder builder = new StringBuilder(firstText(directOpinion, "AI已生成审核关注点"));
        int maxPoints = Math.min(points.size(), 3);
        for (int i = 0; i < maxPoints; i++) {
            builder.append("；").append(points.get(i));
        }
        return builder.toString();
    }

    private String normalizeApprovalReviewOpinion(String opinion,
                                                  List<ProcurementAiCandidateDraft> candidates,
                                                  Map<String, Object> outputs) {
        String value = firstText(opinion, "");
        if (hasApprovalDecisionPrefix(value)) {
            return value;
        }
        if (!StringUtils.hasText(value)) {
            value = "请审批人核对采购必要性、数量、用途、规格、参考价格和最终供应商报价。";
        }
        return approvalDecisionPrefix(candidates, outputs) + value;
    }

    private String approvalDecisionPrefix(List<ProcurementAiCandidateDraft> candidates,
                                          Map<String, Object> outputs) {
        List<ProcurementAiCandidateDraft> rows = candidates == null ? List.of() : candidates;
        boolean needsSupplement = rows.isEmpty();
        if (!needsSupplement) {
            for (ProcurementAiCandidateDraft candidate : rows) {
                if (candidate.getUnitPrice() == null || candidate.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0
                    || !StringUtils.hasText(candidate.getSpecModel())
                    || !StringUtils.hasText(candidate.getPurchaseReason())) {
                    needsSupplement = true;
                    break;
                }
            }
        }
        String decision = approvalDecisionSuggestion(outputs);
        if (StringUtils.hasText(decision)) {
            String normalized = decision.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("REJECT") || normalized.contains("退回")
                || normalized.contains("驳回") || normalized.contains("不通过")) {
                return "建议退回：";
            }
            if (needsSupplement) {
                return "建议补充：";
            }
            if (normalized.contains("SUPPLEMENT") || normalized.contains("补充")
                || normalized.contains("完善") || normalized.contains("更正")) {
                return "建议补充：";
            }
            if (normalized.contains("APPROVE") || normalized.contains("通过")
                || normalized.contains("同意")) {
                return "建议通过：";
            }
        }
        return needsSupplement ? "建议补充：" : "建议通过：";
    }

    private String approvalDecisionSuggestion(Map<String, Object> outputs) {
        Map<String, Object> approvalReview = mapValue(firstObject(
            outputs.get("approvalReview"),
            outputs.get("approval_review"),
            outputs.get("aiReview"),
            outputs.get("ai_review"),
            outputs.get("review")
        ));
        return firstText(
            text(outputs.get("decisionSuggestion")),
            text(outputs.get("decision_suggestion")),
            text(outputs.get("approvalDecision")),
            text(outputs.get("approval_decision")),
            text(approvalReview.get("decisionSuggestion")),
            text(approvalReview.get("decision_suggestion")),
            text(approvalReview.get("approvalDecision")),
            text(approvalReview.get("approval_decision"))
        );
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

    private String buildLocalApprovalReviewOpinion(ProcurementAiAnalysisResult result,
                                                   List<ProcurementAiCandidateDraft> candidates) {
        List<ProcurementAiCandidateDraft> rows = candidates == null ? List.of() : candidates;
        if (rows.isEmpty()) {
            return "建议补充：未识别出可提交明细，建议申请人补充商品名称、数量、用途、规格和大于 0 的参考单价或预算后再提交。";
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean missingPrice = false;
        boolean missingSpec = false;
        boolean missingPurpose = false;
        for (ProcurementAiCandidateDraft candidate : rows) {
            BigDecimal lineAmount = candidate.getLineAmount();
            if (lineAmount != null) {
                totalAmount = totalAmount.add(lineAmount);
            }
            if (candidate.getUnitPrice() == null || candidate.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                missingPrice = true;
            }
            if (!StringUtils.hasText(candidate.getSpecModel())) {
                missingSpec = true;
            }
            if (!StringUtils.hasText(candidate.getPurchaseReason())) {
                missingPurpose = true;
            }
        }
        boolean needsSupplement = missingPrice || missingSpec || missingPurpose;
        StringBuilder builder = new StringBuilder(needsSupplement ? "建议补充：" : "建议通过：");
        builder.append(Boolean.TRUE.equals(result.getEmergency()) ? "按紧急采购进入原审批流程" : "按普通采购进入原审批流程")
            .append("；共识别")
            .append(rows.size())
            .append("项");
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            builder.append("，参考金额约 ¥").append(totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        } else {
            builder.append("，参考金额待供应商报价确认");
        }
        if (missingSpec) {
            builder.append("；部分明细规格/型号不完整，审批前建议核对");
        }
        if (missingPrice) {
            builder.append("；参考价缺失或小于等于 0，需补充或更正后再提交");
        }
        if (missingPurpose) {
            builder.append("；采购用途不完整，建议补充后再审批");
        }
        if (!missingSpec && !missingPrice && !missingPurpose) {
            builder.append("；用途、数量和预算信息基本完整，审批人可重点核对必要性、资产归属和最终报价");
        }
        return builder.toString();
    }

    private FeishuMessageIntentResult parseIntentOutputs(Map<String, Object> outputs, ProcurementAiStartDTO dto, String source) {
        Map<String, Object> normalizedOutputs = normalizeOutputObject(outputs);
        if (normalizedOutputs.isEmpty()) {
            return unavailableMessageIntent("Dify " + source + " returned empty intent output");
        }
        String rawIntent = firstText(
            text(normalizedOutputs.get("messageIntent")),
            text(normalizedOutputs.get("message_intent"))
        );
        if (!StringUtils.hasText(rawIntent)) {
            return unavailableMessageIntent("Dify " + source + " returned no messageIntent");
        }
        String intent = normalizeMessageIntent(rawIntent);
        Map<String, Object> candidateAction = mapValue(normalizedOutputs.get("candidateAction"));
        if (candidateAction.isEmpty()) {
            candidateAction = mapValue(normalizedOutputs.get("candidate_action"));
        }
        String actionIntent = text(candidateAction.get("intent"));
        boolean needClarification = booleanValue(firstObject(
            normalizedOutputs.get("needClarification"),
            normalizedOutputs.get("need_clarification"),
            candidateAction.get("needClarification"),
            candidateAction.get("need_clarification")
        ), false) || "CLARIFY".equalsIgnoreCase(actionIntent);
        String clarificationQuestion = firstText(
            text(normalizedOutputs.get("clarificationQuestion")),
            text(normalizedOutputs.get("clarification_question")),
            text(candidateAction.get("clarificationQuestion")),
            text(candidateAction.get("clarification_question")),
            text(candidateAction.get("reply"))
        );
        if ("CREATE_CANDIDATE".equals(intent) && !hasPositivePriceSignal(dto, normalizedOutputs, candidateAction)) {
            needClarification = true;
            clarificationQuestion = firstText(
                clarificationQuestion,
                "请补充每项采购明细的参考单价或预算，价格必须大于 0。"
            );
        }
        String reply = firstText(
            text(normalizedOutputs.get("reply")),
            text(normalizedOutputs.get("messageReply")),
            text(normalizedOutputs.get("message_reply")),
            text(normalizedOutputs.get("summary")),
            clarificationQuestion
        );
        FeishuMessageIntentResult result = new FeishuMessageIntentResult();
        result.setIntent(intent);
        result.setIntentName(messageIntentName(intent));
        result.setReply(limit(needClarification ? firstText(clarificationQuestion, reply) : reply, 1000));
        result.setNeedClarification(needClarification);
        result.setClarificationQuestion(limit(clarificationQuestion, 1000));
        result.setProcurementDemand("CREATE_CANDIDATE".equals(intent) && !needClarification);
        result.setDifyParsedJson(debugText(normalizedOutputs));
        return result;
    }

    private boolean hasPositivePriceSignal(ProcurementAiStartDTO dto,
                                           Map<String, Object> outputs,
                                           Map<String, Object> candidateAction) {
        if (positiveDecimal(extractPrice(dto == null ? null : dto.getRawText()), null) != null) {
            return true;
        }
        if (positivePriceFromMap(outputs) != null) {
            return true;
        }
        if (hasPositivePriceInRows(firstObject(outputs.get("candidates"), outputs.get("items"), outputs.get("products")))) {
            return true;
        }
        if (candidateAction == null || candidateAction.isEmpty()) {
            return false;
        }
        if (positivePriceFromMap(candidateAction) != null) {
            return true;
        }
        Map<String, Object> itemCandidate = mapValue(firstObject(
            candidateAction.get("itemCandidate"),
            candidateAction.get("item_candidate")
        ));
        if (positivePriceFromMap(itemCandidate) != null) {
            return true;
        }
        Map<String, Object> fieldUpdates = mapValue(firstObject(
            candidateAction.get("fieldUpdates"),
            candidateAction.get("field_updates")
        ));
        return positivePriceFromMap(fieldUpdates) != null;
    }

    private boolean hasPositivePriceInRows(Object value) {
        for (Map<String, Object> row : toListOfMaps(value)) {
            if (positivePriceFromMap(row) != null) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal positivePriceFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return positiveDecimal(firstObject(
            map.get("unitPrice"),
            map.get("unit_price"),
            map.get("price"),
            map.get("budget"),
            map.get("referencePrice"),
            map.get("reference_price")
        ), null);
    }

    private Map<String, Object> normalizeOutputObject(Map<String, Object> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return new LinkedHashMap<>();
        }
        String nestedJson = firstText(
            text(outputs.get("text")),
            text(outputs.get("answer")),
            text(outputs.get("result")),
            text(outputs.get("output"))
        );
        Map<String, Object> parsed = parseJsonObject(nestedJson);
        return parsed.isEmpty() ? outputs : parsed;
    }

    private List<ProcurementAiCandidateDraft> parseCandidates(Map<String, Object> outputs, ProcurementAiStartDTO dto) {
        Object value = firstObject(outputs.get("candidates"), outputs.get("items"), outputs.get("products"));
        List<Map<String, Object>> rows = toListOfMaps(value);
        if (rows.isEmpty() && !outputs.isEmpty()) {
            rows = List.of(outputs);
        }

        List<ProcurementAiCandidateDraft> candidates = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> row : rows) {
            String itemName = firstText(text(row.get("itemName")), text(row.get("item_name")), text(row.get("name")), text(row.get("productName")), text(row.get("product_name")));
            if (!StringUtils.hasText(itemName)) {
                continue;
            }
            ProcurementAiCandidateDraft candidate = new ProcurementAiCandidateDraft();
            candidate.setCandidateNo(index++);
            candidate.setItemName(limit(itemName, 255));
            candidate.setSpecModel(limit(firstText(text(row.get("specModel")), text(row.get("spec_model")), text(row.get("spec")), text(row.get("model"))), 255));
            candidate.setUnit(limit(firstText(text(row.get("unit")), "件"), 32));
            candidate.setQty(nonNegativeDecimal(row.get("qty"), BigDecimal.ONE));
            BigDecimal unitPrice = positiveDecimal(firstObject(row.get("unitPrice"), row.get("unit_price"), row.get("price"), row.get("budget")), null);
            if (unitPrice == null) {
                continue;
            }
            candidate.setUnitPrice(unitPrice);
            candidate.setTaxRate(nonNegativeDecimal(firstObject(row.get("taxRate"), row.get("tax_rate")), BigDecimal.ZERO));
            candidate.setReqType(normalizeReqType(firstText(text(row.get("reqType")), text(row.get("req_type")), text(row.get("type")), dto.getReqType())));
            candidate.setPurchaseReason(limit(firstText(text(row.get("purchaseReason")), text(row.get("purchase_reason")), text(row.get("reason")), dto.getRawText()), 500));
            candidate.setEcommercePlatform(limit(firstText(text(row.get("ecommercePlatform")), text(row.get("ecommerce_platform")), text(row.get("platform")), text(row.get("vendor")), text(row.get("merchant"))), 64));
            candidate.setEcommerceUrl(limit(firstText(text(row.get("ecommerceUrl")), text(row.get("ecommerce_url")), text(row.get("url")), text(row.get("link"))), 500));
            candidate.setImageUrl(limit(firstText(text(row.get("imageUrl")), text(row.get("image_url")), text(row.get("image"))), 500));
            candidate.setScore(nonNegativeDecimal(row.get("score"), null));
            candidate.setSourceJson(toJson(row));
            calculateAmount(candidate);
            candidates.add(candidate);
        }
        return candidates;
    }

    private ProcurementAiAnalysisResult localDraft(ProcurementAiStartDTO dto) {
        ProcurementAiAnalysisResult result = new ProcurementAiAnalysisResult();
        result.setReqTitle(firstText(dto.getReqTitle(), limit(dto.getRawText(), 80), "采购需求"));
        result.setReqType(normalizeReqType(dto.getReqType()));
        result.setPlanMonth(dto.getPlanMonth());
        result.setNeedDate(parseDate(dto.getNeedDate()));
        result.setUrgencyLevel(dto.getUrgencyLevel() == null ? 1 : dto.getUrgencyLevel());
        result.setEmergency(Boolean.TRUE.equals(dto.getEmergency()) || result.getUrgencyLevel() >= 2);
        result.setAiSummary("Local candidate generated because Dify is not configured.");

        List<ProcurementAiCandidateDraft> localCandidates = parseLocalCandidates(dto, result.getReqType());
        if (!localCandidates.isEmpty()) {
            result.setCandidates(localCandidates);
            result.setAiSummary(buildLocalApprovalReviewOpinion(result, localCandidates));
            return result;
        }
        if (isExcelAttachmentText(dto == null ? null : dto.getRawText())) {
            List<Map<String, String>> rows = parseExcelAttachmentRows(dto.getRawText());
            List<String> issues = rows.isEmpty()
                ? List.of("未读取到有效采购明细行")
                : excelRowIssues(rows);
            String issueText = formatExcelIssueText(issues);
            result.setCandidates(List.of());
            result.setAiSummary("建议补充：已识别到 Excel 采购明细，但还不能生成候选；" + issueText + "。");
            result.setAiError("Excel attachment requires correction: " + issueText);
            return result;
        }

        ProcurementAiCandidateDraft candidate = createDefaultCandidate(dto, result.getReqType());
        if (candidate != null) {
            result.setCandidates(List.of(candidate));
            result.setAiSummary(buildLocalApprovalReviewOpinion(result, List.of(candidate)));
            return result;
        }
        result.setCandidates(List.of());
        result.setAiSummary("请补充每项采购明细的参考单价或预算，价格必须大于 0。");
        result.setAiError("缺少大于 0 的参考单价或预算，无法生成采购候选。");
        return result;
    }

    private ProcurementAiCandidateDraft createDefaultCandidate(ProcurementAiStartDTO dto, String reqType) {
        BigDecimal unitPrice = positiveDecimal(extractPrice(dto == null ? null : dto.getRawText()), null);
        if (unitPrice == null) {
            return null;
        }
        ProcurementAiCandidateDraft candidate = new ProcurementAiCandidateDraft();
        candidate.setCandidateNo(1);
        candidate.setItemName(limit(firstText(bestLinkTitle(dto.getRawText()), dto.getRawText(), "待确认物品"), 255));
        candidate.setUnit("件");
        candidate.setQty(BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));
        candidate.setUnitPrice(unitPrice);
        candidate.setTaxRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        candidate.setLineAmount(unitPrice);
        candidate.setReqType(reqType);
        candidate.setPurchaseReason(limit(firstText(extractGlobalPurpose(dto.getRawText()), dto.getRawText(), "采购需求"), 500));
        candidate.setEcommerceUrl(firstText(firstUrl(dto.getRawText()), dto.getImageUrl()));
        candidate.setEcommercePlatform(resolvePlatform(candidate.getEcommerceUrl()));
        applyLocalQuantity(dto, candidate);
        return candidate;
    }

    private void applyLocalQuantity(ProcurementAiStartDTO dto, ProcurementAiCandidateDraft candidate) {
        Matcher matcher = SIMPLE_QUANTITY_PATTERN.matcher(firstText(dto.getRawText(), ""));
        if (!matcher.find()) {
            return;
        }
        candidate.setQty(nonNegativeDecimal(matcher.group(1), BigDecimal.ONE));
        candidate.setUnit(limit(matcher.group(2), 32));
        calculateAmount(candidate);
    }

    private List<ProcurementAiCandidateDraft> normalizeCandidates(List<ProcurementAiCandidateDraft> candidates, ProcurementAiStartDTO dto) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ProcurementAiCandidateDraft> localSplit = parseLocalCandidates(dto, firstText(candidates.get(0).getReqType(), dto == null ? null : dto.getReqType()));
        if (localSplit.size() > candidates.size() && localSplit.size() > 1) {
            return localSplit;
        }
        List<ProcurementAiCandidateDraft> expanded = new ArrayList<>();
        for (ProcurementAiCandidateDraft candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            List<ProcurementAiCandidateDraft> split = shouldExpandFromRaw(candidate, dto)
                ? parseLocalCandidates(dto, candidate.getReqType())
                : List.of();
            expanded.addAll(split.isEmpty() ? List.of(candidate) : split);
        }
        return renumberAndDeduplicate(expanded);
    }

    private boolean shouldExpandFromRaw(ProcurementAiCandidateDraft candidate, ProcurementAiStartDTO dto) {
        String rawText = firstText(dto == null ? null : dto.getRawText(), "");
        if (!StringUtils.hasText(rawText)) {
            return false;
        }
        int quantityCount = quantityMatchCount(rawText);
        if (quantityCount < 2) {
            return false;
        }
        String itemName = firstText(candidate.getItemName(), "");
        if (!StringUtils.hasText(itemName)) {
            return true;
        }
        return looksLikeBroadCategory(itemName) || itemName.length() > 80 || containsListSeparator(itemName);
    }

    private int quantityMatchCount(String value) {
        Matcher matcher = LOCAL_QUANTITY_PATTERN.matcher(firstText(value, ""));
        int count = 0;
        while (matcher.find()) {
            if (isMeaningfulQuantityMatch(value, matcher.start(), matcher.end(), matcher.group(1), matcher.group(2))) {
                count++;
            }
        }
        return count;
    }

    private boolean looksLikeBroadCategory(String itemName) {
        String normalized = itemName.replaceAll("\\s+", "");
        return normalized.contains("采购")
            || normalized.contains("办公用品")
            || normalized.contains("设备采购")
            || normalized.contains("物资")
            || normalized.contains("用品及设备");
    }

    private boolean containsListSeparator(String value) {
        return StringUtils.hasText(value) && (value.contains("、") || value.contains("，") || value.contains(",") || value.contains(";") || value.contains("；"));
    }

    private List<ProcurementAiCandidateDraft> parseLocalCandidates(ProcurementAiStartDTO dto, String reqType) {
        String rawText = firstText(dto == null ? null : dto.getRawText(), "");
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }
        List<ProcurementAiCandidateDraft> excelCandidates = parseExcelAttachmentCandidates(rawText, reqType);
        if (!excelCandidates.isEmpty()) {
            return excelCandidates;
        }
        LinkedHashSet<String> urls = extractUrls(rawText);
        String inheritedUrl = urls.size() == 1 ? urls.iterator().next() : "";
        String globalPurpose = extractGlobalPurpose(rawText);
        List<String> segments = splitTopLevelItems(rawText);
        List<ProcurementAiCandidateDraft> candidates = new ArrayList<>();
        for (String segment : segments) {
            ProcurementAiCandidateDraft candidate = parseLocalCandidateSegment(segment, reqType, globalPurpose, inheritedUrl);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty() && !urls.isEmpty()) {
            ProcurementAiCandidateDraft candidate = createDefaultCandidate(dto, reqType);
            if (StringUtils.hasText(candidate.getEcommerceUrl()) && quantityMatchCount(rawText) > 0) {
                candidates.add(candidate);
            }
        }
        return renumberAndDeduplicate(candidates);
    }

    private FeishuMessageIntentResult classifyExcelAttachment(ProcurementAiStartDTO dto) {
        String rawText = firstText(dto == null ? null : dto.getRawText(), "");
        if (!isExcelAttachmentText(rawText)) {
            return null;
        }
        List<Map<String, String>> rows = parseExcelAttachmentRows(rawText);
        FeishuMessageIntentResult result = new FeishuMessageIntentResult();
        if (rows.isEmpty()) {
            result.setIntent("CHAT");
            result.setIntentName("补充采购信息");
            result.setNeedClarification(true);
            result.setProcurementDemand(false);
            result.setClarificationQuestion("已收到 Excel 文件，但没有读取到有效采购明细行。请确认使用采购明细批量导入模板，或复制表格内容发送。");
            result.setReply(result.getClarificationQuestion());
            result.setDifyParsedJson(debugText(Map.of("source", "local_excel_intent", "rows", 0)));
            return result;
        }
        List<String> issues = excelRowIssues(rows);
        if (!issues.isEmpty()) {
            String issueText = formatExcelIssueText(issues);
            String reply = "已识别到 Excel 采购明细，但还不能生成候选：" + issueText + "。请在表格中更正后重新发送，或文字补充对应行信息。";
            result.setIntent("CHAT");
            result.setIntentName("补充采购信息");
            result.setNeedClarification(true);
            result.setProcurementDemand(false);
            result.setClarificationQuestion(reply);
            result.setReply(reply);
            result.setDifyParsedJson(debugText(Map.of("source", "local_excel_intent", "rows", rows.size(), "issues", issues)));
            return result;
        }
        result.setIntent("CREATE_CANDIDATE");
        result.setIntentName(messageIntentName("CREATE_CANDIDATE"));
        result.setProcurementDemand(true);
        result.setNeedClarification(false);
        result.setReply("已识别 Excel 采购明细，将按标准模板逐行生成采购候选。");
        result.setDifyParsedJson(debugText(Map.of("source", "local_excel_intent", "rows", rows.size(), "valid", true)));
        return result;
    }

    private List<ProcurementAiCandidateDraft> parseExcelAttachmentCandidates(String rawText, String reqType) {
        List<Map<String, String>> rows = parseExcelAttachmentRows(rawText);
        List<ProcurementAiCandidateDraft> candidates = new ArrayList<>();
        int candidateNo = 1;
        for (Map<String, String> row : rows) {
            String itemName = excelValue(row, "商品名称", "物品名称", "名称", "itemName", "itemname");
            BigDecimal qty = positiveDecimal(excelValue(row, "数量", "qty"), null);
            BigDecimal unitPrice = positiveDecimal(excelValue(row, "单价", "参考单价", "价格", "预算", "unitPrice", "unitprice", "price", "budget"), null);
            if (!StringUtils.hasText(itemName) || qty == null || unitPrice == null) {
                continue;
            }
            ProcurementAiCandidateDraft candidate = new ProcurementAiCandidateDraft();
            candidate.setCandidateNo(candidateNo++);
            candidate.setItemName(limit(itemName, 255));
            candidate.setSpecModel(limit(excelValue(row, "规格型号", "规格", "型号", "specModel", "specmodel", "model"), 255));
            candidate.setUnit(limit(firstText(excelValue(row, "单位", "unit"), "件"), 32));
            candidate.setQty(qty);
            candidate.setUnitPrice(unitPrice);
            candidate.setTaxRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            candidate.setReqType(normalizeReqType(firstText(excelValue(row, "类型", "采购类型", "reqType", "reqtype"), reqType)));
            candidate.setPurchaseReason(limit(firstText(excelValue(row, "采购原因", "用途", "申请原因", "purchaseReason", "purchasereason"), "采购需求"), 500));
            String url = excelValue(row, "链接", "商品链接", "URL", "url", "ecommerceUrl", "ecommerceurl");
            candidate.setEcommerceUrl(limit(url, 500));
            candidate.setEcommercePlatform(resolvePlatform(url));
            candidate.setSourceJson(toJson(Map.of("source", "feishu_excel_template", "row", row)));
            calculateAmount(candidate);
            candidates.add(candidate);
        }
        return renumberAndDeduplicate(candidates);
    }

    private List<String> excelRowIssues(List<Map<String, String>> rows) {
        List<String> issues = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String rowNo = firstText(row.get("_rowNo"), "?");
            if (!StringUtils.hasText(excelValue(row, "商品名称", "物品名称", "名称", "itemName", "itemname"))) {
                issues.add("第" + rowNo + "行缺商品名称");
            }
            if (!StringUtils.hasText(excelValue(row, "规格型号", "规格", "型号", "specModel", "specmodel", "model"))) {
                issues.add("第" + rowNo + "行缺规格型号");
            }
            if (positiveDecimal(excelValue(row, "数量", "qty"), null) == null) {
                issues.add("第" + rowNo + "行数量必须大于 0");
            }
            if (positiveDecimal(excelValue(row, "单价", "参考单价", "价格", "预算", "unitPrice", "unitprice", "price", "budget"), null) == null) {
                issues.add("第" + rowNo + "行单价/预算必须大于 0");
            }
            if (!StringUtils.hasText(excelValue(row, "采购原因", "用途", "申请原因", "purchaseReason", "purchasereason"))) {
                issues.add("第" + rowNo + "行缺采购原因/用途");
            }
        }
        return issues;
    }

    private String formatExcelIssueText(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return "请确认商品名称、规格型号、数量、用途和大于 0 的单价/预算是否完整";
        }
        String issueText = String.join("；", issues.stream().limit(8).toList());
        if (issues.size() > 8) {
            issueText += "；还有 " + (issues.size() - 8) + " 条问题";
        }
        return issueText;
    }

    private List<Map<String, String>> parseExcelAttachmentRows(String rawText) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (!isExcelAttachmentText(rawText)) {
            return rows;
        }
        for (String rawLine : rawText.split("\\R")) {
            String line = rawLine.trim();
            Matcher matcher = EXCEL_ROW_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("_rowNo", matcher.group(1));
            for (String segment : matcher.group(2).split("；")) {
                int index = segment.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                String key = normalizeExcelKey(segment.substring(0, index));
                String value = segment.substring(index + 1).trim();
                if (StringUtils.hasText(key)) {
                    row.put(key, value);
                }
            }
            if (row.size() > 1) {
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean isExcelAttachmentText(String rawText) {
        return StringUtils.hasText(rawText) && rawText.contains("飞书Excel附件内容");
    }

    private String excelValue(Map<String, String> row, String... keys) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        for (String key : keys) {
            String value = row.get(normalizeExcelKey(key));
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeExcelKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("\uFEFF", "").replaceAll("[\\s_\\-/]", "").trim().toLowerCase(Locale.ROOT);
    }

    private List<String> splitTopLevelItems(String value) {
        String normalized = sanitizeRawForLocalSplit(value);
        List<String> rows = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '（' || ch == '(' || ch == '[' || ch == '【') {
                depth++;
            } else if ((ch == '）' || ch == ')' || ch == ']' || ch == '】') && depth > 0) {
                depth--;
            }
            if (depth == 0 && isTopLevelSeparator(ch)) {
                addLocalSegment(rows, current);
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        addLocalSegment(rows, current);
        if (rows.size() <= 1 && quantityMatchCount(normalized) > 1 && !containsParentheses(normalized)) {
            rows = splitByQuantityWindows(normalized);
        }
        return rows;
    }

    private String sanitizeRawForLocalSplit(String value) {
        StringBuilder builder = new StringBuilder();
        for (String rawLine : firstText(value, "").split("\\R")) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (line.startsWith("飞书链接/预览信息") || line.startsWith("URL：") || line.startsWith("URL:")) {
                continue;
            }
            if (line.startsWith("标题：") || line.startsWith("标题:")) {
                line = line.substring(line.indexOf('：') >= 0 ? line.indexOf('：') + 1 : line.indexOf(':') + 1).trim();
            }
            if (!builder.isEmpty()) {
                builder.append("、");
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private boolean isTopLevelSeparator(char ch) {
        return ch == '、' || ch == '，' || ch == ',' || ch == '；' || ch == ';' || ch == '\n' || ch == '\r';
    }

    private boolean containsParentheses(String value) {
        return StringUtils.hasText(value) && (value.contains("（") || value.contains("("));
    }

    private void addLocalSegment(List<String> rows, StringBuilder current) {
        String segment = current.toString().trim();
        if (!StringUtils.hasText(segment)) {
            return;
        }
        if (!LOCAL_QUANTITY_PATTERN.matcher(segment).find() && !containsUrl(segment)) {
            return;
        }
        rows.add(segment);
    }

    private List<String> splitByQuantityWindows(String value) {
        List<int[]> windows = new ArrayList<>();
        Matcher matcher = LOCAL_QUANTITY_PATTERN.matcher(value);
        while (matcher.find()) {
            if (isMeaningfulQuantityMatch(value, matcher.start(), matcher.end(), matcher.group(1), matcher.group(2))) {
                windows.add(new int[] {matcher.start(), matcher.end()});
            }
        }
        if (windows.size() <= 1) {
            return List.of(value);
        }
        List<String> rows = new ArrayList<>();
        int segmentStart = 0;
        for (int i = 0; i < windows.size(); i++) {
            int segmentEnd = i + 1 < windows.size() ? findPreviousBoundary(value, windows.get(i + 1)[0]) : value.length();
            String segment = value.substring(segmentStart, Math.max(segmentStart, segmentEnd)).trim();
            if (StringUtils.hasText(segment)) {
                rows.add(segment);
            }
            segmentStart = Math.max(segmentEnd, segmentStart);
        }
        return rows;
    }

    private int findPreviousBoundary(String value, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            char ch = value.charAt(i);
            if (isTopLevelSeparator(ch) || Character.isWhitespace(ch)) {
                return i + 1;
            }
        }
        return beforeIndex;
    }

    private ProcurementAiCandidateDraft parseLocalCandidateSegment(String segment, String reqType, String globalPurpose, String inheritedUrl) {
        String normalized = firstText(segment, "");
        QuantityMatch quantity = lastQuantityMatch(normalized);
        if (quantity == null) {
            return null;
        }
        BigDecimal qty = nonNegativeDecimal(quantity.qty(), BigDecimal.ONE);
        String unit = limit(quantity.unit(), 32);
        String beforeQty = cleanupItemText(normalized.substring(0, quantity.start()));
        String afterQty = normalized.substring(quantity.end()).trim();
        if (!StringUtils.hasText(beforeQty) || looksLikePurchaseVerbOnly(beforeQty)) {
            beforeQty = firstText(extractProductAfterQuantity(afterQty), beforeQty);
        }
        ParsedParentheses parsed = parseParentheses(beforeQty, normalized);
        if (StringUtils.hasText(parsed.innerBeforeQty())) {
            beforeQty = firstText(parsed.innerBeforeQty(), beforeQty);
            afterQty = firstText(parsed.innerAfterQty(), afterQty);
        }
        String itemName = limit(firstText(bestProductName(beforeQty, normalized), bestLinkTitle(normalized)), 255);
        if (!StringUtils.hasText(itemName)) {
            return null;
        }
        BigDecimal unitPrice = positiveDecimal(extractPrice(normalized), null);
        if (unitPrice == null) {
            return null;
        }
        ProcurementAiCandidateDraft candidate = new ProcurementAiCandidateDraft();
        candidate.setItemName(itemName);
        candidate.setSpecModel(limit(resolveSpecModel(parsed.outerName(), itemName), 255));
        candidate.setQty(qty);
        candidate.setUnit(unit);
        candidate.setUnitPrice(unitPrice);
        candidate.setTaxRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        candidate.setReqType(normalizeReqType(reqType));
        candidate.setPurchaseReason(limit(firstText(extractPurpose(afterQty), globalPurpose, "采购需求"), 500));
        String url = firstText(firstUrl(normalized), inheritedUrl);
        candidate.setEcommerceUrl(limit(url, 500));
        candidate.setEcommercePlatform(resolvePlatform(url));
        candidate.setSourceJson(toJson(Map.of("source", "local_split", "segment", normalized)));
        calculateAmount(candidate);
        return candidate;
    }

    private QuantityMatch lastQuantityMatch(String value) {
        Matcher matcher = LOCAL_QUANTITY_PATTERN.matcher(firstText(value, ""));
        QuantityMatch selected = null;
        while (matcher.find()) {
            if (isMeaningfulQuantityMatch(value, matcher.start(), matcher.end(), matcher.group(1), matcher.group(2))) {
                selected = new QuantityMatch(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2));
            }
        }
        return selected;
    }

    private boolean isMeaningfulQuantityMatch(String value, int start, int end, String qty, String unit) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if ("米".equals(unit) && StringUtils.hasText(qty) && qty.contains(".") && end < value.length()) {
            char next = value.charAt(end);
            if (Character.UnicodeScript.of(next) == Character.UnicodeScript.HAN) {
                return false;
            }
        }
        return true;
    }

    private String extractProductAfterQuantity(String afterQty) {
        if (!StringUtils.hasText(afterQty)) {
            return "";
        }
        String normalized = afterQty.trim();
        normalized = normalized.replaceAll("^(?:的)?\\s*", "");
        String[] parts = normalized.split("(?:用于|用途|主要用于|，|,|。|；|;|、)", 2);
        return cleanupItemText(parts.length == 0 ? normalized : parts[0]);
    }

    private ParsedParentheses parseParentheses(String beforeQty, String fullSegment) {
        Matcher matcher = Pattern.compile("^(.+?)[（(](.+)[）)]").matcher(fullSegment);
        if (!matcher.find()) {
            return new ParsedParentheses("", "", "");
        }
        String outerName = cleanupItemText(matcher.group(1));
        String inner = matcher.group(2);
        QuantityMatch quantity = lastQuantityMatch(inner);
        if (quantity == null) {
            return new ParsedParentheses(outerName, cleanupItemText(beforeQty), "");
        }
        String innerBeforeQty = cleanupItemText(inner.substring(0, quantity.start()));
        String innerAfterQty = inner.substring(quantity.end()).trim();
        return new ParsedParentheses(outerName, innerBeforeQty, innerAfterQty);
    }

    private String bestProductName(String beforeQty, String segment) {
        String cleaned = cleanupItemText(beforeQty);
        if (StringUtils.hasText(cleaned) && !looksLikePurchaseVerbOnly(cleaned)) {
            return cleaned;
        }
        return cleanupItemText(bestLinkTitle(segment));
    }

    private String cleanupItemText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = URL_PATTERN.matcher(value).replaceAll(" ");
        normalized = normalized.replaceAll("(?i)\\b(jd\\.com|taobao\\.com|tmall\\.com|1688\\.com|douyin\\.com)\\b", " ");
        normalized = normalized.replaceAll("^(?:我要|我想|请帮我|帮我|需要|采购|申购|申请|购买|买|要|来|新增|记录|这个|链接|商品|物品)\\s*", "");
        normalized = normalized.replaceAll("(?:用于|用途|主要用于).*$", "");
        normalized = normalized.replaceAll("^[：:，,。；;、\\s\\-]+|[：:，,。；;、\\s\\-]+$", "");
        return normalized.trim();
    }

    private boolean looksLikePurchaseVerbOnly(String value) {
        String normalized = firstText(value, "").replaceAll("\\s+", "");
        return normalized.isEmpty() || normalized.matches("^(我要|我想|需要|采购|申购|购买|买|要|这个|链接|商品|物品)+$");
    }

    private String resolveSpecModel(String outerName, String itemName) {
        if (!StringUtils.hasText(outerName) || outerName.equals(itemName)) {
            return "";
        }
        return outerName;
    }

    private String extractPurpose(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("^[：:，,。；;、\\s]+", "").trim();
        Matcher matcher = Pattern.compile("(?:用于|用途|主要用于)\\s*[:：]?\\s*(.+)$").matcher(normalized);
        if (matcher.find()) {
            return cleanupPurpose(matcher.group(1));
        }
        String noPrice = PRICE_PATTERN.matcher(normalized).replaceAll(" ").trim();
        return cleanupPurpose(noPrice);
    }

    private String cleanupPurpose(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("^[：:，,。；;、\\s]+|[：:，,。；;、\\s]+$", "").trim();
    }

    private String extractGlobalPurpose(String rawText) {
        Matcher matcher = Pattern.compile("(?:用于|用途|主要用于)\\s*[:：]?\\s*([^。；;\\n]+)").matcher(firstText(rawText, ""));
        return matcher.find() ? cleanupPurpose(matcher.group(1)) : "";
    }

    private String extractPrice(String value) {
        Matcher matcher = PRICE_PATTERN.matcher(firstText(value, ""));
        return matcher.find() ? matcher.group(1) : "";
    }

    private LinkedHashSet<String> extractUrls(String value) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(firstText(value, ""));
        while (matcher.find()) {
            String url = matcher.group().replaceAll("[，。；;、)）\\]}]+$", "");
            if (StringUtils.hasText(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private boolean containsUrl(String value) {
        return URL_PATTERN.matcher(firstText(value, "")).find();
    }

    private String firstUrl(String value) {
        Set<String> urls = extractUrls(value);
        return urls.isEmpty() ? "" : urls.iterator().next();
    }

    private String bestLinkTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        for (String rawLine : value.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("标题：") || line.startsWith("标题:")) {
                return cleanupItemText(line.substring(line.indexOf('：') >= 0 ? line.indexOf('：') + 1 : line.indexOf(':') + 1));
            }
        }
        String noUrl = URL_PATTERN.matcher(value).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return cleanupItemText(noUrl);
    }

    private String resolvePlatform(String url) {
        String normalized = firstText(url, "").toLowerCase(Locale.ROOT);
        if (normalized.contains("jd.com") || normalized.contains("jingdong")) {
            return "京东";
        }
        if (normalized.contains("1688.com")) {
            return "1688";
        }
        if (normalized.contains("taobao.com")) {
            return "淘宝";
        }
        if (normalized.contains("tmall.com")) {
            return "天猫";
        }
        if (normalized.contains("douyin.com")) {
            return "抖音";
        }
        if (normalized.contains("yangkeduo.com") || normalized.contains("pinduoduo.com")) {
            return "拼多多";
        }
        return StringUtils.hasText(normalized) ? "其他" : "";
    }

    private List<ProcurementAiCandidateDraft> renumberAndDeduplicate(List<ProcurementAiCandidateDraft> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ProcurementAiCandidateDraft> result = new ArrayList<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (ProcurementAiCandidateDraft candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getItemName())) {
                continue;
            }
            String key = dedupKey(candidate);
            if (keys.add(key)) {
                candidate.setCandidateNo(result.size() + 1);
                calculateAmount(candidate);
                result.add(candidate);
            }
        }
        return result;
    }

    private String dedupKey(ProcurementAiCandidateDraft candidate) {
        return String.join("|",
            firstText(candidate.getItemName(), ""),
            firstText(candidate.getSpecModel(), ""),
            candidate.getQty() == null ? "" : candidate.getQty().stripTrailingZeros().toPlainString(),
            firstText(candidate.getUnit(), ""),
            firstText(candidate.getPurchaseReason(), ""),
            firstText(candidate.getEcommerceUrl(), "")
        ).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record ParsedParentheses(String outerName, String innerBeforeQty, String innerAfterQty) {
    }

    private record QuantityMatch(int start, int end, String qty, String unit) {
    }

    private FeishuMessageIntentResult unavailableMessageIntent(String aiError) {
        FeishuMessageIntentResult result = new FeishuMessageIntentResult();
        result.setIntent("CHAT");
        result.setIntentName("AI意图判断失败");
        result.setProcurementDemand(false);
        result.setReply("AI 意图判断暂时不可用，我不会自动创建采购候选。请稍后重试，或明确发送要采购的物品、数量和用途。");
        result.setAiError(aiError);
        return result;
    }

    private String chatQuery(ProcurementAiStartDTO dto) {
        return firstText(dto.getRawText(), dto.getImageUrl(), "图片采购需求");
    }

    private String normalizeMessageIntent(String rawIntent) {
        String value = firstText(rawIntent, "").trim().toUpperCase();
        return switch (value) {
            case "CREATE_CANDIDATE" -> "CREATE_CANDIDATE";
            case "HELP", "USAGE_HELP" -> "HELP";
            case "CHAT", "CHITCHAT", "GENERAL_CHAT", "CLARIFY", "UNKNOWN", "IGNORE", "NONE" -> "CHAT";
            default -> "CHAT";
        };
    }

    private String messageIntentName(String intent) {
        return switch (intent) {
            case "CREATE_CANDIDATE" -> "创建采购候选";
            case "HELP" -> "使用说明";
            default -> "闲聊";
        };
    }

    private String responseMode(Snapshot config) {
        return firstText(config.responseMode(), "blocking");
    }

    private String chatResponseMode(Snapshot config) {
        String mode = firstText(config.responseMode(), "streaming");
        if ("blocking".equalsIgnoreCase(mode)) {
            return "streaming";
        }
        return mode;
    }

    private Map<String, Object> parseJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        String normalized = stripJsonFence(value);
        Map<String, Object> parsed = readJsonObject(normalized);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return readJsonObject(normalized.substring(start, end + 1));
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> parseSseResponse(String value) {
        Map<String, Object> lastMessage = new LinkedHashMap<>();
        Map<String, Object> messageEnd = new LinkedHashMap<>();
        Map<String, Object> lastOutputEvent = new LinkedHashMap<>();
        StringBuilder answer = new StringBuilder();
        if (!StringUtils.hasText(value)) {
            return lastMessage;
        }
        for (String rawLine : value.split("\\R")) {
            String line = rawLine.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            String json = line.substring("data:".length()).trim();
            if (!StringUtils.hasText(json) || "[DONE]".equalsIgnoreCase(json)) {
                continue;
            }
            Map<String, Object> event = readJsonObject(json);
            if (event.isEmpty()) {
                continue;
            }
            String eventName = text(event.get("event"));
            String eventAnswer = text(event.get("answer"));
            if (StringUtils.hasText(eventAnswer)) {
                answer.append(eventAnswer);
            }
            if (!mapValue(mapValue(event.get("data")).get("outputs")).isEmpty()) {
                lastOutputEvent = event;
            }
            if ("workflow_finished".equals(eventName)) {
                lastMessage = event;
            } else if ("message_end".equals(eventName)) {
                messageEnd = event;
                if (lastMessage.isEmpty()) {
                    lastMessage = event;
                }
            }
        }
        if (lastMessage.isEmpty() && !lastOutputEvent.isEmpty()) {
            lastMessage = lastOutputEvent;
        }
        if (lastMessage.isEmpty() && answer.isEmpty()) {
            return lastMessage;
        }
        Map<String, Object> response = new LinkedHashMap<>(lastMessage);
        if (!messageEnd.isEmpty()) {
            response.putIfAbsent("conversation_id", messageEnd.get("conversation_id"));
            response.putIfAbsent("message_id", messageEnd.get("message_id"));
            response.putIfAbsent("task_id", messageEnd.get("task_id"));
        }
        if (StringUtils.hasText(answer)) {
            response.put("answer", answer.toString());
        }
        return response;
    }

    private boolean isSseResponse(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String rawLine : value.split("\\R")) {
            if (rawLine.trim().startsWith("data:")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> extractResponseOutputs(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> outputs = mapValue(response.get("outputs"));
        if (!outputs.isEmpty()) {
            return outputs;
        }
        Map<String, Object> data = mapValue(response.get("data"));
        outputs = mapValue(data.get("outputs"));
        return outputs.isEmpty() ? new LinkedHashMap<>() : outputs;
    }

    private String extractResponseAnswer(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "";
        }
        Map<String, Object> data = mapValue(response.get("data"));
        Map<String, Object> outputs = extractResponseOutputs(response);
        return firstText(
            text(response.get("answer")),
            text(data.get("answer")),
            text(outputs.get("answer")),
            text(outputs.get("text")),
            text(outputs.get("result")),
            text(outputs.get("output"))
        );
    }

    private String stripJsonFence(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }

    private Map<String, Object> readJsonObject(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> toListOfMaps(Object value) {
        if (value == null) {
            return List.of();
        }
        Object normalized = value;
        if (value instanceof String textValue) {
            if (!StringUtils.hasText(textValue)) {
                return List.of();
            }
            try {
                normalized = objectMapper.readValue(textValue, new TypeReference<List<Map<String, Object>>>() {
                });
            } catch (JsonProcessingException listEx) {
                try {
                    normalized = objectMapper.readValue(textValue, new TypeReference<Map<String, Object>>() {
                    });
                } catch (JsonProcessingException mapEx) {
                    return List.of();
                }
            }
        }
        if (normalized instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> map = mapValue(item);
                if (!map.isEmpty()) {
                    rows.add(map);
                }
            }
            return rows;
        }
        Map<String, Object> single = mapValue(normalized);
        return single.isEmpty() ? List.of() : List.of(single);
    }

    private List<String> textList(Object value) {
        if (value == null) {
            return List.of();
        }
        Object normalized = value;
        if (value instanceof String textValue) {
            if (!StringUtils.hasText(textValue)) {
                return List.of();
            }
            try {
                normalized = objectMapper.readValue(textValue, new TypeReference<List<Object>>() {
                });
            } catch (JsonProcessingException ex) {
                return List.of(textValue.trim());
            }
        }
        if (!(normalized instanceof List<?> list)) {
            String singleText = text(normalized);
            return StringUtils.hasText(singleText) ? List.of(singleText) : List.of();
        }
        List<String> rows = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = mapValue(item);
            String rowText = map.isEmpty()
                ? text(item)
                : firstText(
                    text(map.get("point")),
                    text(map.get("content")),
                    text(map.get("summary")),
                    text(map.get("suggestion")),
                    text(map.get("reason")),
                    buildItemOpinionText(map)
                );
            if (StringUtils.hasText(rowText)) {
                rows.add(limit(rowText, 180));
            }
        }
        return rows;
    }

    private String buildItemOpinionText(Map<String, Object> map) {
        String itemName = firstText(text(map.get("itemName")), text(map.get("item_name")));
        String suggestion = firstText(text(map.get("suggestion")), text(map.get("decisionSuggestion")), text(map.get("decision_suggestion")));
        String reason = firstText(text(map.get("reason")), text(map.get("comment")));
        if (!StringUtils.hasText(itemName) && !StringUtils.hasText(suggestion) && !StringUtils.hasText(reason)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(itemName)) {
            builder.append(itemName).append("：");
        }
        builder.append(firstText(reason, suggestion, "需人工复核"));
        return builder.toString();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                target.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return target;
    }

    private void calculateAmount(ProcurementAiCandidateDraft candidate) {
        BigDecimal qty = candidate.getQty() == null ? BigDecimal.ZERO : candidate.getQty();
        BigDecimal unitPrice = candidate.getUnitPrice() == null ? BigDecimal.ZERO : candidate.getUnitPrice();
        candidate.setLineAmount(qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal nonNegativeDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue == null ? null : defaultValue.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            BigDecimal decimal = value instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue())
                : decimalFromText(String.valueOf(value));
            if (decimal.compareTo(BigDecimal.ZERO) < 0) {
                return defaultValue == null ? null : defaultValue.setScale(2, RoundingMode.HALF_UP);
            }
            return decimal.setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException ex) {
            return defaultValue == null ? null : defaultValue.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal decimalFromText(String value) {
        String normalized = firstText(value, "")
            .replace(",", "")
            .replace("￥", "")
            .replace("¥", "")
            .trim();
        Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(normalized);
        if (!matcher.find()) {
            throw new NumberFormatException("No numeric value");
        }
        return new BigDecimal(matcher.group());
    }

    private BigDecimal positiveDecimal(Object value, BigDecimal defaultValue) {
        BigDecimal decimal = nonNegativeDecimal(value, null);
        if (decimal == null || decimal.compareTo(BigDecimal.ZERO) <= 0) {
            return defaultValue == null ? null : defaultValue.setScale(2, RoundingMode.HALF_UP);
        }
        return decimal;
    }

    private Integer numberValue(Object value, Integer defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (!StringUtils.hasText(text(value))) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String textValue = text(value);
        if (!StringUtils.hasText(textValue)) {
            return defaultValue;
        }
        String normalized = textValue.trim().toLowerCase();
        return "true".equals(normalized)
            || "1".equals(normalized)
            || "yes".equals(normalized)
            || "y".equals(normalized)
            || "是".equals(normalized)
            || "需要".equals(normalized);
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

    private String normalizeReqType(String reqType) {
        String value = firstText(reqType, "服务/费用/低值易耗等").trim();
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
        if (value.toLowerCase().contains("asset")) {
            return "实物资产/无形资产/长摊等";
        }
        if (value.contains("低值") || value.toLowerCase().contains("consumable")) {
            return "服务/费用/低值易耗等";
        }
        return "服务/费用/低值易耗等";
    }

    private Object firstObject(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private ProcurementAiAnalysisResult withDifyDebug(ProcurementAiAnalysisResult result, Object request, Object response) {
        if (result == null) {
            return null;
        }
        result.setDifyRequestJson(debugText(request));
        result.setDifyResponseText(debugText(response));
        if (!StringUtils.hasText(result.getDifyParsedJson())) {
            result.setDifyParsedJson(debugText(Map.of(
                "reqTitle", firstText(result.getReqTitle(), ""),
                "reqType", firstText(result.getReqType(), ""),
                "summary", firstText(result.getAiSummary(), ""),
                "error", firstText(result.getAiError(), ""),
                "candidates", result.getCandidates() == null ? List.of() : result.getCandidates()
            )));
        }
        return result;
    }

    private FeishuMessageIntentResult withDifyDebug(FeishuMessageIntentResult result, Object request, Object response) {
        if (result == null) {
            return null;
        }
        result.setDifyRequestJson(debugText(request));
        result.setDifyResponseText(debugText(response));
        if (!StringUtils.hasText(result.getDifyParsedJson())) {
            result.setDifyParsedJson(debugText(Map.of(
                "intent", firstText(result.getIntent(), ""),
                "intentName", firstText(result.getIntentName(), ""),
                "reply", firstText(result.getReply(), ""),
                "error", firstText(result.getAiError(), ""),
                "procurementDemand", result.isProcurementDemand(),
                "needClarification", result.isNeedClarification(),
                "clarificationQuestion", firstText(result.getClarificationQuestion(), "")
            )));
        }
        return result;
    }

    private String debugText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String string) {
            return limit(string, 50000);
        }
        return limit(toJson(value), 50000);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    @Data
    public static class ProcurementAiAnalysisResult {
        private String reqTitle;
        private String reqType;
        private String planMonth;
        private Integer urgencyLevel;
        private Boolean emergency;
        private LocalDate needDate;
        private String aiSummary;
        private String aiError;
        private String difyRequestJson;
        private String difyResponseText;
        private String difyParsedJson;
        private List<ProcurementAiCandidateDraft> candidates = new ArrayList<>();
    }

    @Data
    public static class ProcurementAiCandidateDraft {
        private Integer candidateNo;
        private String itemName;
        private String specModel;
        private String unit;
        private BigDecimal qty;
        private BigDecimal unitPrice;
        private BigDecimal taxRate;
        private BigDecimal lineAmount;
        private String reqType;
        private String purchaseReason;
        private String ecommercePlatform;
        private String ecommerceUrl;
        private String imageUrl;
        private BigDecimal score;
        private String sourceJson;
    }

    @Data
    public static class FeishuMessageIntentResult {
        private String intent;
        private String intentName;
        private String reply;
        private String aiError;
        private boolean procurementDemand;
        private boolean needClarification;
        private String clarificationQuestion;
        private String difyRequestJson;
        private String difyResponseText;
        private String difyParsedJson;
    }
}
