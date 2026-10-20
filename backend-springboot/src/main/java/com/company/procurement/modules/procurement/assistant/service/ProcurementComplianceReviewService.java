package com.company.procurement.modules.procurement.assistant.service;

import com.company.procurement.modules.procurement.assistant.client.M3VisionClient;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.ComplianceReview;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.ComplianceItemReview;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementCompliancePrecheckVO;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService.ModelImprovementSuggestion;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService.RuntimeSkill;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestItemDTO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcurementComplianceReviewService {

    public static final String SKILL_VERSION = "1.3.0";

    private static final String SKILL_RESOURCE =
        "classpath:ai-skills/procurement-compliance-review/SKILL.md";
    private static final String RULES_RESOURCE =
        "classpath:ai-skills/procurement-purpose-standard.md";
    private static final BigDecimal MIN_MODEL_CONFIDENCE = new BigDecimal("0.60");
    private static final int MAX_REASONS_IN_PROMPT = 30;
    private static final String OUTPUT_CONTRACT = """

        ## 系统固定审查范围

        本次 AI 审核只允许读取“采购原因”文字，且该范围优先于前述 Skill 和知识库说明。
        必须以提示词中的《采购原因统一标准》作为唯一的理由充分性标准，不得沿用其它标准。
        不得使用或要求补充申请标题、采购类型、部门、金额、紧急程度、需求日期、商品名称、规格、数量、单价或商品链接。
        未提供上述字段不属于信息缺失，不得因此输出补充或退回建议。
        只有采购原因主动写明批量、多人或多点覆盖、整批替换、备用比例时，才检查其是否说明数量或覆盖依据。
        只有采购原因主动写明紧急、急用或突发事件时，才检查其是否说明业务影响和时限依据。

        ## 系统固定输出契约

        只输出一个 JSON 对象，不得输出 Markdown、解释或思考过程：
        {
          "decisionSuggestion": "APPROVE|SUPPLEMENT|RETURN",
          "riskLevel": "LOW|MEDIUM|HIGH",
          "summary": "面向审批人的简洁中文结论",
          "issues": ["最多三条关注点"],
          "requiredInformation": ["需要补充的内容"],
          "itemReviews": [{
            "lineNo": 1,
            "decisionSuggestion": "APPROVE|SUPPLEMENT|RETURN",
            "opinion": "仅针对本行采购原因的简洁中文意见",
            "missingFields": ["USE_SCENE|CURRENT_IMPACT|NECESSITY|QUANTITY_BASIS|URGENCY_BASIS"]
          }],
          "confidence": 0.0,
          "improvementSuggestions": [{
            "targetType": "SKILL|KNOWLEDGE_BASE",
            "targetCode": "当前上下文中明确存在的编码",
            "proposedContent": "可完整替换当前正文的全文",
            "rationale": "具体且可复用的规则缺口"
          }]
        }
        必须为输入中每个采购原因行号返回且只返回一条 itemReviews，不能合并或遗漏。
        missingFields 只列出统一标准中的实际缺项；建议通过或退回时为空数组。
        普通理由不得出现 QUANTITY_BASIS 或 URGENCY_BASIS，除非原文主动触发对应条件。
        没有具体、可复用的规则缺口时 improvementSuggestions 必须为空数组。
        """;
    private static final Set<String> GENERIC_REASONS = Set.of(
        "使用", "采购", "备用", "办公使用", "工作需要", "业务需要", "部门使用", "领导要求",
        "日常使用", "应急使用", "维修使用", "更换使用", "生产需要", "项目需要"
    );
    private static final Set<String> PURPOSE_MISSING_FIELDS = Set.of(
        "USE_SCENE", "CURRENT_IMPACT", "NECESSITY", "QUANTITY_BASIS", "URGENCY_BASIS"
    );
    private static final List<String> CORE_MISSING_FIELDS = List.of(
        "USE_SCENE", "CURRENT_IMPACT", "NECESSITY"
    );

    private final M3VisionClient m3VisionClient;
    private final AiGovernanceService governanceService;
    private final ResourceLoader resourceLoader;

    private volatile String fallbackSkillInstructions;

    public String review(PurchaseRequest request, List<PurchaseRequestItem> items) {
        return reviewDetails(request, items,
                "purchase-request-" + (request == null ? "unknown" : request.getId()), true).overallOpinion();
    }

    public ProcurementCompliancePrecheckVO precheck(PurchaseRequestCreateDTO dto) {
        PurchaseRequest request = new PurchaseRequest();
        List<PurchaseRequestItem> items = new ArrayList<>();
        for (int index = 0; index < dto.getItems().size(); index++) {
            items.add(toPrecheckItem(dto.getItems().get(index), index + 1));
        }
        ReviewOutcome outcome = reviewDetails(
            request,
            items,
            "purchase-request-precheck-" + dto.getApplicantUserId(),
            false
        );
        List<ProcurementCompliancePrecheckVO.ItemOpinion> itemOpinions = new ArrayList<>();
        for (int index = 0; index < outcome.itemReviews().size(); index++) {
            ItemReviewOutcome itemReview = outcome.itemReviews().get(index);
            itemOpinions.add(new ProcurementCompliancePrecheckVO.ItemOpinion(
                index + 1,
                decisionFromOpinion(itemReview.opinion()),
                itemReview.opinion(),
                itemReview.missingFields()
            ));
        }
        return new ProcurementCompliancePrecheckVO(outcome.overallOpinion(), itemOpinions);
    }

    private ReviewOutcome reviewDetails(PurchaseRequest request,
                                        List<PurchaseRequestItem> items,
                                        String userKey,
                                        boolean captureSuggestions) {
        List<String> localIssues = localIssues(request, items);
        if (!localIssues.isEmpty()) {
            return localOutcome(
                limit("建议补充：" + String.join("；", localIssues), 1000),
                items
            );
        }
        if (!m3VisionClient.canCompleteText()) {
            return localOutcome(localPassFallback(), items);
        }
        try {
            ComplianceReview review = m3VisionClient.reviewProcurementCompliance(
                skillInstructions(),
                requestContext(items),
                userKey
            );
            if (review.confidence() == null || review.confidence().compareTo(MIN_MODEL_CONFIDENCE) < 0) {
                log.info("Ignore low-confidence procurement compliance review, requestId={}, skillVersion={}, confidence={}",
                    request.getId(), SKILL_VERSION, review.confidence());
                return localOutcome(localPassFallback(), items);
            }
            if (captureSuggestions) {
                captureImprovementSuggestions(request, review);
            }
            List<ItemReviewOutcome> itemReviews = formatItemReviews(review, items);
            String opinion = reconcileOverallOpinion(formatOpinion(review), itemReviews);
            log.info("Procurement compliance review completed, requestId={}, skillVersion={}, decision={}, confidence={}",
                request.getId(), SKILL_VERSION, review.decisionSuggestion(), review.confidence());
            return new ReviewOutcome(opinion, itemReviews);
        } catch (RuntimeException ex) {
            log.warn("Procurement compliance review failed; use local fallback, requestId={}, skillVersion={}",
                request == null ? null : request.getId(), SKILL_VERSION, ex);
            return localOutcome(localPassFallback(), items);
        }
    }

    private PurchaseRequestItem toPrecheckItem(PurchaseRequestItemDTO dto, int lineNo) {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setLineNo(lineNo);
        item.setPurpose(dto.getPurpose());
        return item;
    }

    private List<String> localIssues(PurchaseRequest request, List<PurchaseRequestItem> items) {
        List<String> issues = new ArrayList<>();
        if (request == null || items == null || items.isEmpty()) {
            issues.add("采购申请缺少有效明细");
            return issues;
        }
        boolean allReasonsGeneric = items.stream()
            .map(PurchaseRequestItem::getPurpose)
            .allMatch(this::isGenericReason);
        if (allReasonsGeneric) {
            issues.add("采购原因过于笼统，请说明具体使用场景、现状和采购必要性");
        }
        return issues;
    }

    private boolean isGenericReason(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.replaceAll("[\\s，。；、,.!！?？：:（）()]+", "");
        return normalized.length() < 4 || GENERIC_REASONS.contains(normalized);
    }

    private String requestContext(List<PurchaseRequestItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("请只根据以下采购原因生成整体审核意见和逐行审核意见，不要推测或追问其他申请字段。\n");
        int limit = Math.min(items.size(), MAX_REASONS_IN_PROMPT);
        for (int index = 0; index < limit; index++) {
            PurchaseRequestItem item = items.get(index);
            builder.append(index + 1).append(". 采购原因：").append(text(item.getPurpose()))
                .append('\n');
        }
        if (items.size() > MAX_REASONS_IN_PROMPT) {
            builder.append("其余 ").append(items.size() - MAX_REASONS_IN_PROMPT).append(" 条采购原因本次不展开。\n");
        }
        return builder.toString();
    }

    private String formatOpinion(ComplianceReview review) {
        String decision = normalizeDecision(review.decisionSuggestion());
        String prefix = decisionPrefix(decision);
        String body = stripDecisionPrefix(review.summary());
        if (!StringUtils.hasText(body)) {
            body = switch (decision) {
                case "APPROVE" -> "采购原因描述基本明确";
                case "RETURN" -> "采购原因显示存在明显业务合规风险";
                default -> "采购原因仍需补充具体场景和必要性";
            };
        }
        StringBuilder opinion = new StringBuilder(prefix).append(body);
        appendList(opinion, "关注点", review.issues());
        if ("SUPPLEMENT".equals(decision)) {
            appendList(opinion, "需补充", review.requiredInformation());
        }
        return limit(opinion.toString(), 1000);
    }

    private List<ItemReviewOutcome> formatItemReviews(ComplianceReview review, List<PurchaseRequestItem> items) {
        Map<Integer, ComplianceItemReview> reviewsByLine = new HashMap<>();
        if (review.itemReviews() != null) {
            for (ComplianceItemReview itemReview : review.itemReviews()) {
                if (itemReview.lineNo() != null && itemReview.lineNo() > 0 && itemReview.lineNo() <= items.size()) {
                    reviewsByLine.putIfAbsent(itemReview.lineNo(), itemReview);
                }
            }
        }
        List<ItemReviewOutcome> outcomes = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            PurchaseRequestItem item = items.get(index);
            if (isGenericReason(item.getPurpose())) {
                outcomes.add(new ItemReviewOutcome(genericReasonOpinion(), CORE_MISSING_FIELDS));
                continue;
            }
            ComplianceItemReview itemReview = reviewsByLine.get(index + 1);
            if (itemReview == null) {
                outcomes.add(new ItemReviewOutcome(localPassFallback(), List.of()));
                continue;
            }
            String opinion = formatItemOpinion(itemReview);
            List<String> missingFields = normalizeMissingFields(itemReview.missingFields());
            if (opinion.startsWith("建议补充：") && missingFields.isEmpty()) {
                missingFields = CORE_MISSING_FIELDS;
            } else if (!opinion.startsWith("建议补充：")) {
                missingFields = List.of();
            }
            outcomes.add(new ItemReviewOutcome(opinion, missingFields));
        }
        return outcomes;
    }

    private List<String> normalizeMissingFields(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(value -> text(value).toUpperCase(Locale.ROOT))
            .filter(PURPOSE_MISSING_FIELDS::contains)
            .distinct()
            .toList();
    }

    private String formatItemOpinion(ComplianceItemReview review) {
        String decision = normalizeDecision(review.decisionSuggestion());
        String body = stripDecisionPrefix(review.opinion());
        if (!StringUtils.hasText(body)) {
            body = switch (decision) {
                case "APPROVE" -> "本行采购原因已说明业务场景和采购必要性";
                case "RETURN" -> "本行采购原因显示存在明显业务合规风险";
                default -> "本行采购原因仍需补充具体场景和采购必要性";
            };
        }
        return limit(decisionPrefix(decision) + body, 600);
    }

    private String reconcileOverallOpinion(String overallOpinion, List<ItemReviewOutcome> itemReviews) {
        if (itemReviews.stream().map(ItemReviewOutcome::opinion).anyMatch(item -> item.startsWith("建议退回："))
            && !overallOpinion.startsWith("建议退回：")) {
            return "建议退回：存在需要退回处理的采购原因，请查看逐条预审意见。";
        }
        if (itemReviews.stream().map(ItemReviewOutcome::opinion).anyMatch(item -> item.startsWith("建议补充："))
            && overallOpinion.startsWith("建议通过：")) {
            return "建议补充：存在需要补充的采购原因，请查看逐条预审意见。";
        }
        return overallOpinion;
    }

    private ReviewOutcome localOutcome(String overallOpinion, List<PurchaseRequestItem> items) {
        List<ItemReviewOutcome> itemReviews = items == null ? List.of() : items.stream()
            .map(item -> isGenericReason(item.getPurpose())
                ? new ItemReviewOutcome(genericReasonOpinion(), CORE_MISSING_FIELDS)
                : new ItemReviewOutcome(localPassFallback(), List.of()))
            .toList();
        return new ReviewOutcome(overallOpinion, itemReviews);
    }

    private String genericReasonOpinion() {
        return "建议补充：采购原因过于笼统，请说明具体使用场景、现状和采购必要性。";
    }

    private String normalizeDecision(String value) {
        String decision = text(value).toUpperCase(Locale.ROOT);
        return switch (decision) {
            case "APPROVE", "RETURN" -> decision;
            default -> "SUPPLEMENT";
        };
    }

    private String decisionPrefix(String decision) {
        return switch (decision) {
            case "APPROVE" -> "建议通过：";
            case "RETURN" -> "建议退回：";
            default -> "建议补充：";
        };
    }

    private String decisionFromOpinion(String opinion) {
        if (opinion.startsWith("建议通过：")) {
            return "APPROVE";
        }
        if (opinion.startsWith("建议退回：")) {
            return "RETURN";
        }
        return "SUPPLEMENT";
    }

    private void appendList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        List<String> cleanValues = values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .limit(3)
            .toList();
        if (!cleanValues.isEmpty()) {
            builder.append("；").append(label).append("：").append(String.join("、", cleanValues));
        }
    }

    private String stripDecisionPrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceFirst("^建议(?:通过|补充|退回)[：:]\\s*", "");
    }

    private String localPassFallback() {
        return "建议通过：采购原因已完成基础检查，审批人请结合实际情况复核。";
    }

    private String skillInstructions() {
        try {
            RuntimeSkill runtimeSkill = governanceService.runtimeSkill("procurement-compliance-review");
            if (runtimeSkill != null && StringUtils.hasText(runtimeSkill.prompt())) {
                return "# Skill：" + runtimeSkill.code() + "（修订 " + runtimeSkill.revisionNo() + "）\n\n"
                    + runtimeSkill.prompt()
                    + OUTPUT_CONTRACT;
            }
        } catch (RuntimeException ex) {
            log.warn("Cannot load managed procurement compliance skill; use packaged fallback: {}", ex.toString());
        }
        String current = fallbackSkillInstructions;
        if (current != null) {
            return current + OUTPUT_CONTRACT;
        }
        synchronized (this) {
            if (fallbackSkillInstructions == null) {
                fallbackSkillInstructions = readResource(SKILL_RESOURCE) + "\n\n" + readResource(RULES_RESOURCE);
            }
            return fallbackSkillInstructions + OUTPUT_CONTRACT;
        }
    }

    private void captureImprovementSuggestions(PurchaseRequest request, ComplianceReview review) {
        if (review.improvementSuggestions() == null || review.improvementSuggestions().isEmpty()) {
            return;
        }
        try {
            List<ModelImprovementSuggestion> suggestions = review.improvementSuggestions().stream()
                .map(item -> new ModelImprovementSuggestion(
                    item.targetType(),
                    item.targetCode(),
                    item.proposedContent(),
                    item.rationale()
                ))
                .toList();
            governanceService.captureModelSuggestions(
                suggestions,
                "PURCHASE_REQUEST_COMPLIANCE_REVIEW",
                request == null ? null : request.getId()
            );
        } catch (RuntimeException ex) {
            log.warn("Cannot store procurement compliance improvement suggestion, requestId={}: {}",
                request == null ? null : request.getId(), ex.toString());
        }
    }

    private String readResource(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (var input = resource.getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load procurement compliance skill resource: " + location, ex);
        }
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record ReviewOutcome(String overallOpinion, List<ItemReviewOutcome> itemReviews) {
    }

    private record ItemReviewOutcome(String opinion, List<String> missingFields) {
    }
}
