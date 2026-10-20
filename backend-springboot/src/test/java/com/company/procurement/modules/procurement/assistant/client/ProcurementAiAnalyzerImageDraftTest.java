package com.company.procurement.modules.procurement.assistant.client;

import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiAnalysisResult;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiCandidateDraft;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.ImageDraftSupplement;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementAiAnalyzerImageDraftTest {

    @Test
    void createsCandidateFromImageRecognitionDraftAndUserSupplement() {
        ProcurementAiStartDTO dto = imageDraft("买200个用于更换使用");
        ProcurementAiAnalyzer analyzer = analyzer(supplement("200", "个", "用于更换使用"));

        ProcurementAiAnalysisResult result = analyzer.analyze(dto, "user1");

        assertThat(result).isNotNull();
        assertThat(result.getReqTitle()).isEqualTo("飞利浦T8LED灯管支架1.2米");
        assertThat(result.getCandidates()).hasSize(1);

        ProcurementAiCandidateDraft candidate = result.getCandidates().get(0);
        assertThat(candidate.getItemName()).isEqualTo("飞利浦T8LED灯管支架1.2米");
        assertThat(candidate.getSpecModel()).isEqualTo("品牌：飞利浦 Philips");
        assertThat(candidate.getQty()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(candidate.getUnit()).isEqualTo("个");
        assertThat(candidate.getUnitPrice()).isEqualByComparingTo(new BigDecimal("21.60"));
        assertThat(candidate.getLineAmount()).isEqualByComparingTo(new BigDecimal("4320.00"));
        assertThat(candidate.getPurchaseReason()).isEqualTo("用于更换使用");
        assertThat(result.getAiSummary()).contains("4320.00");
    }

    @Test
    void createsCandidateWhenModelExtractsQuantityWithoutFixedTextPattern() {
        ProcurementAiStartDTO dto = imageDraft("采购数量2，用途：M6V一期视频监控电源箱故障更换");
        ProcurementAiAnalyzer analyzer = analyzer(supplement("2", null, "M6V一期视频监控电源箱故障更换"));

        ProcurementAiAnalysisResult result = analyzer.analyze(dto, "user1");

        assertThat(result.getCandidates()).hasSize(1);
        ProcurementAiCandidateDraft candidate = result.getCandidates().get(0);
        assertThat(candidate.getItemName()).isEqualTo("飞利浦T8LED灯管支架1.2米");
        assertThat(candidate.getQty()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(candidate.getUnit()).isEqualTo("件");
        assertThat(candidate.getLineAmount()).isEqualByComparingTo(new BigDecimal("43.20"));
        assertThat(candidate.getPurchaseReason()).isEqualTo("M6V一期视频监控电源箱故障更换");
    }

    @Test
    void keepsDraftWhenModelFindsOnlyQuantityAndPurposeIsMissing() {
        ProcurementAiStartDTO dto = imageDraft("10");
        ProcurementAiAnalyzer analyzer = analyzer(new ImageDraftSupplement(
            new BigDecimal("10"),
            null,
            null,
            true,
            List.of("purpose"),
            "请补充本次采购的用途。",
            "{\"request\":true}",
            "{\"response\":true}",
            "{\"quantity\":10,\"missing_fields\":[\"purpose\"]}"
        ));

        ProcurementAiAnalysisResult result = analyzer.analyze(dto, "user1");

        assertThat(result.getCandidates()).isEmpty();
        assertThat(result.getAiError()).isEqualTo("IMAGE_DRAFT_NEEDS_CLARIFICATION");
        assertThat(result.getAiSummary()).isEqualTo("请补充本次采购的用途。");
        assertThat(result.getDifyParsedJson()).contains("quantity");
    }

    private ProcurementAiStartDTO imageDraft(String supplement) {
        ProcurementAiStartDTO dto = new ProcurementAiStartDTO();
        dto.setReqType("其他");
        dto.setRawText("""
            历史待补充采购需求：
            图片采购需求
            图片识别结果（仅作草稿上下文，不代表用户已确认采购数量和用途）：
            1. 商品名称：飞利浦T8LED灯管支架1.2米；规格型号：品牌：飞利浦 Philips；参考单价：¥21.60；建议单位：件
            待用户补充：采购数量、用途；如识别有误请直接更正。
            本次用户补充：
            %s
            """.formatted(supplement));
        return dto;
    }

    private ProcurementAiAnalyzer analyzer(ImageDraftSupplement supplement) {
        return new ProcurementAiAnalyzer(new StubM3VisionClient(supplement, true), null);
    }

    private ImageDraftSupplement supplement(String quantity, String unit, String purpose) {
        return new ImageDraftSupplement(
            new BigDecimal(quantity),
            unit,
            purpose,
            false,
            List.of(),
            "",
            "{\"request\":true}",
            "{\"response\":true}",
            "{\"quantity\":" + quantity + "}"
        );
    }

    private static class StubM3VisionClient extends M3VisionClient {
        private final ImageDraftSupplement supplement;
        private final boolean ready;

        StubM3VisionClient(ImageDraftSupplement supplement, boolean ready) {
            super(null, new ObjectMapper());
            this.supplement = supplement;
            this.ready = ready;
        }

        @Override
        public boolean canCompleteText() {
            return ready;
        }

        @Override
        public ImageDraftSupplement completeImageRecognitionDraft(ProcurementAiStartDTO dto, String userKey) {
            return supplement;
        }
    }
}
