package com.company.procurement.modules.procurement.assistant.service.impl;

import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiAnalysisResult;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiCandidateDraft;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuBotServiceImplFastPathTest {

    @Test
    void detectsClearProcurementTextWithPriceAndQuantity() {
        assertThat(FeishuBotServiceImpl.hasClearProcurementText(
            "采购 2 个罗技无线鼠标，参考价 129 元/个，用于机房值班电脑备用"))
            .isTrue();
    }

    @Test
    void keepsMissingPriceTextOnIntentClassificationPath() {
        assertThat(FeishuBotServiceImpl.hasClearProcurementText(
            "我想买100块电池用户更换旧电池使用"))
            .isFalse();
    }

    @Test
    void keepsExcelAttachmentOnLocalExcelValidationPath() {
        assertThat(FeishuBotServiceImpl.hasClearProcurementText(
            "飞书Excel附件内容：M6采购明细.xlsx\n表头：商品名称 | 数量 | 单价"))
            .isFalse();
    }

    @Test
    void asksForDemandWhenOnlyImageIsSent() {
        assertThat(FeishuBotServiceImpl.isImageWithoutUserDemandText("image", ""))
            .isTrue();
    }

    @Test
    void allowsImageMessageWithUserDemandToContinueRecognition() {
        assertThat(FeishuBotServiceImpl.isImageWithoutUserDemandText("image", "购买灯管200个，用于更换"))
            .isFalse();
    }

    @Test
    void extractsBareCandidateSelectionAsVariableXButNotConfirmCommand() {
        assertThat(FeishuBotServiceImpl.candidateSelectionNo("6号吧"))
            .isEqualTo(6);
        assertThat(FeishuBotServiceImpl.candidateSelectionNo("第2项"))
            .isEqualTo(2);
        assertThat(FeishuBotServiceImpl.candidateSelectionNo("第3个"))
            .isEqualTo(3);
        assertThat(FeishuBotServiceImpl.candidateSelectionNo("选123项"))
            .isEqualTo(123);
        assertThat(FeishuBotServiceImpl.candidateSelectionNo("确认第6项"))
            .isNull();
        assertThat(FeishuBotServiceImpl.candidateSelectionNo("买6个"))
            .isNull();
    }

    @Test
    void imageRecognitionReplyOnlyAsksForQuantityAndPurposeWhenProductFieldsDetected() {
        ProcurementAiAnalysisResult analysis = imageAnalysis("飞利浦T8LED灯管支架", "品牌：飞利浦；长度：1.2米", "21.60");

        String reply = FeishuBotServiceImpl.imageDemandClarificationReply(analysis);

        assertThat(reply)
            .contains("已从图片识别到：飞利浦T8LED灯管支架")
            .contains("采购数量和用途")
            .doesNotContain("请补充规格型号")
            .doesNotContain("参考单价/预算");
    }

    @Test
    void imageRecognitionDraftTextCarriesDetectedProductContext() {
        ProcurementAiAnalysisResult analysis = imageAnalysis("飞利浦T8LED灯管支架", "品牌：飞利浦；长度：1.2米", "21.60");

        String text = FeishuBotServiceImpl.imageRecognitionDraftText("图片采购需求", analysis);

        assertThat(text)
            .contains("商品名称：飞利浦T8LED灯管支架")
            .contains("规格型号：品牌：飞利浦；长度：1.2米")
            .contains("参考单价：¥21.60")
            .contains("待用户补充：采购数量、用途");
    }

    private ProcurementAiAnalysisResult imageAnalysis(String itemName, String specModel, String unitPrice) {
        ProcurementAiCandidateDraft candidate = new ProcurementAiCandidateDraft();
        candidate.setItemName(itemName);
        candidate.setSpecModel(specModel);
        candidate.setUnit("根");
        candidate.setUnitPrice(new BigDecimal(unitPrice));
        ProcurementAiAnalysisResult analysis = new ProcurementAiAnalysisResult();
        analysis.setCandidates(List.of(candidate));
        return analysis;
    }
}
