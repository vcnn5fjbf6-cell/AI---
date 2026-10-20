package com.company.procurement.modules.procurement.assistant.service;

import com.company.procurement.modules.ai.governance.service.AiGovernanceService;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.ComplianceItemReview;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.ComplianceReview;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.ImprovementSuggestion;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementCompliancePrecheckVO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestItemDTO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementComplianceReviewServiceTest {

    @Mock
    private M3VisionClient m3VisionClient;

    @Mock
    private AiGovernanceService governanceService;

    private ProcurementComplianceReviewService service;

    @BeforeEach
    void setUp() {
        service = new ProcurementComplianceReviewService(m3VisionClient, governanceService, new DefaultResourceLoader());
    }

    @Test
    void genericReasonRequiresSupplementWithoutCallingModel() {
        PurchaseRequest request = request("办公使用", 1);
        PurchaseRequestItem item = item("办公使用");

        String opinion = service.review(request, List.of(item));

        assertThat(opinion).startsWith("建议补充：").contains("具体使用场景");
        verify(m3VisionClient, never()).canCompleteText();
        verify(m3VisionClient, never()).reviewProcurementCompliance(anyString(), anyString(), anyString());
    }

    @Test
    void draftPrecheckUsesSameRulesWithoutCapturingGovernanceSuggestions() {
        PurchaseRequestCreateDTO dto = new PurchaseRequestCreateDTO();
        dto.setApplicantUserId(12L);
        PurchaseRequestItemDTO item = new PurchaseRequestItemDTO();
        item.setPurpose("办公使用");
        dto.setItems(List.of(item));

        ProcurementCompliancePrecheckVO result = service.precheck(dto);

        assertThat(result.overallOpinion()).startsWith("建议补充：").contains("具体使用场景");
        assertThat(result.itemOpinions()).singleElement().satisfies(itemOpinion -> {
            assertThat(itemOpinion.lineNo()).isEqualTo(1);
            assertThat(itemOpinion.decisionSuggestion()).isEqualTo("SUPPLEMENT");
            assertThat(itemOpinion.opinion()).startsWith("建议补充：").contains("采购原因过于笼统");
            assertThat(itemOpinion.missingFields())
                .containsExactly("USE_SCENE", "CURRENT_IMPACT", "NECESSITY");
        });
        verify(m3VisionClient, never()).reviewProcurementCompliance(anyString(), anyString(), anyString());
        verify(governanceService, never()).captureModelSuggestions(
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void draftPrecheckReturnsOneOpinionForEachPurchaseReason() {
        PurchaseRequestCreateDTO dto = new PurchaseRequestCreateDTO();
        dto.setApplicantUserId(12L);
        dto.setItems(List.of(
            itemDto("现有值班终端频繁死机，采购用于恢复机房监控值守"),
            itemDto("空调冷凝水管接头老化漏水，采购同规格配件完成维修")
        ));
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(m3VisionClient.reviewProcurementCompliance(anyString(), anyString(), anyString()))
            .thenReturn(new ComplianceReview(
                "APPROVE",
                "LOW",
                "两条采购原因均已说明业务场景和必要性",
                List.of(),
                List.of(),
                new BigDecimal("0.91"),
                List.of(
                    new ComplianceItemReview(1, "APPROVE", "已说明值班终端故障和采购用途", List.of()),
                    new ComplianceItemReview(2, "APPROVE", "已说明漏水问题和维修目的", List.of())
                ),
                List.of()
            ));

        ProcurementCompliancePrecheckVO result = service.precheck(dto);

        assertThat(result.overallOpinion()).startsWith("建议通过：");
        assertThat(result.itemOpinions()).hasSize(2);
        assertThat(result.itemOpinions().get(0).opinion()).isEqualTo("建议通过：已说明值班终端故障和采购用途");
        assertThat(result.itemOpinions().get(1).opinion()).isEqualTo("建议通过：已说明漏水问题和维修目的");
    }

    @Test
    void draftPrecheckDoesNotPassOverallWhenOneReasonStillNeedsSupplement() {
        PurchaseRequestCreateDTO dto = new PurchaseRequestCreateDTO();
        dto.setApplicantUserId(12L);
        dto.setItems(List.of(
            itemDto("办公使用"),
            itemDto("空调冷凝水管接头老化漏水，采购同规格配件完成维修")
        ));
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(m3VisionClient.reviewProcurementCompliance(anyString(), anyString(), anyString()))
            .thenReturn(new ComplianceReview(
                "APPROVE",
                "LOW",
                "整体采购原因可以通过",
                List.of(),
                List.of(),
                new BigDecimal("0.91"),
                List.of(
                    new ComplianceItemReview(1, "APPROVE", "可以通过", List.of()),
                    new ComplianceItemReview(2, "APPROVE", "已说明漏水问题和维修目的", List.of())
                ),
                List.of()
            ));

        ProcurementCompliancePrecheckVO result = service.precheck(dto);

        assertThat(result.overallOpinion()).startsWith("建议补充：");
        assertThat(result.itemOpinions().get(0).decisionSuggestion()).isEqualTo("SUPPLEMENT");
        assertThat(result.itemOpinions().get(1).decisionSuggestion()).isEqualTo("APPROVE");
    }

    @Test
    void draftPrecheckReturnsOnlyTheMissingFieldsSelectedByTheUnifiedStandard() {
        PurchaseRequestCreateDTO dto = new PurchaseRequestCreateDTO();
        dto.setApplicantUserId(12L);
        dto.setItems(List.of(itemDto("计划为多个值班点统一补充记录本，目前库存不足")));
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(m3VisionClient.reviewProcurementCompliance(anyString(), anyString(), anyString()))
            .thenReturn(new ComplianceReview(
                "SUPPLEMENT",
                "MEDIUM",
                "需要补充采购必要性和覆盖依据",
                List.of(),
                List.of("采购必要性", "覆盖依据"),
                new BigDecimal("0.91"),
                List.of(new ComplianceItemReview(
                    1,
                    "SUPPLEMENT",
                    "已说明使用场景和库存现状，请补充采购必要性和覆盖依据",
                    List.of("NECESSITY", "QUANTITY_BASIS", "UNKNOWN_FIELD")
                )),
                List.of()
            ));

        ProcurementCompliancePrecheckVO result = service.precheck(dto);

        assertThat(result.itemOpinions()).singleElement().satisfies(itemOpinion -> {
            assertThat(itemOpinion.decisionSuggestion()).isEqualTo("SUPPLEMENT");
            assertThat(itemOpinion.missingFields()).containsExactly("NECESSITY", "QUANTITY_BASIS");
        });
    }

    @Test
    void urgencyAndRequestReasonDoNotDriveReview() {
        PurchaseRequest request = request("应急使用", 2);
        PurchaseRequestItem item = item("M6机房漏水维修所需接头，用于恢复空调排水");
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(m3VisionClient.reviewProcurementCompliance(anyString(), anyString(), anyString()))
            .thenReturn(approvedReview());

        String opinion = service.review(request, List.of(item));

        assertThat(opinion).startsWith("建议通过：").doesNotContain("紧急事件");
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(m3VisionClient).reviewProcurementCompliance(anyString(), contextCaptor.capture(), anyString());
        assertThat(contextCaptor.getValue())
            .contains("采购原因：M6机房漏水维修所需接头，用于恢复空调排水")
            .doesNotContain("应急使用", "紧急程度", "M6机房维修配件采购", "1200.00");
    }

    @Test
    void packageFieldsDoNotTriggerLocalReview() {
        PurchaseRequest request = request("申请单级说明不参与审核", 1);
        PurchaseRequestItem item = item("按现场防火门数量补齐安全标识，便于日常巡检");
        item.setItemName("防火门标识牌贴纸 10张/包");
        item.setSpecModel("20x40cm，每包10张");
        item.setQty(new BigDecimal("120"));
        item.setUnit("张");

        String opinion = service.review(request, List.of(item));

        assertThat(opinion).startsWith("建议通过：").contains("采购原因已完成基础检查");
        verify(m3VisionClient).canCompleteText();
        verify(m3VisionClient, never()).reviewProcurementCompliance(anyString(), anyString(), anyString());
    }

    @Test
    void validReasonUsesSkillAndSendsOnlyPurchaseReason() {
        PurchaseRequest request = request("申请单级说明不参与审核", 2);
        PurchaseRequestItem item = item("现有值班终端频繁死机，采购用于恢复机房监控值守");
        item.setEcommerceUrl("https://example.com/item");
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(m3VisionClient.reviewProcurementCompliance(anyString(), anyString(), anyString()))
            .thenReturn(new ComplianceReview(
                "APPROVE",
                "LOW",
                "采购原因具体，已说明使用场景和采购必要性",
                List.of("审批人请结合现场实际情况复核"),
                List.of(),
                new BigDecimal("0.91"),
                List.of(new ComplianceItemReview(
                    1,
                    "APPROVE",
                    "采购原因具体，已说明使用场景和采购必要性",
                    List.of()
                )),
                List.of(new ImprovementSuggestion(
                    "KNOWLEDGE_BASE",
                    "procurement-compliance-rules",
                    "更新后的完整规则",
                    "补充可复用的采购原因审核示例"
                ))
            ));

        String opinion = service.review(request, List.of(item));

        assertThat(opinion)
            .startsWith("建议通过：")
            .contains("采购原因具体")
            .contains("审批人请结合现场实际情况复核");
        ArgumentCaptor<String> skillCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(m3VisionClient).reviewProcurementCompliance(
            skillCaptor.capture(), contextCaptor.capture(), anyString());
        assertThat(skillCaptor.getValue())
            .contains("procurement-compliance-review")
            .contains("采购原因统一标准")
            .contains("任何一方不得增加另一方没有的普通必填项")
            .contains("只有采购原因主动写明批量")
            .contains("系统固定审查范围")
            .contains("系统固定输出契约")
            .contains("improvementSuggestions");
        assertThat(contextCaptor.getValue())
            .contains("采购原因：现有值班终端频繁死机，采购用于恢复机房监控值守")
            .doesNotContain(
                "申请标题", "采购类型", "申请部门", "参考总金额", "紧急程度", "需求日期",
                "整体采购理由", "商品：", "规格：", "数量：", "参考单价：", "商品链接",
                "M6机房维修配件采购", "DN100-P110", "https://example.com/item"
            );
        verify(governanceService).captureModelSuggestions(
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.eq("PURCHASE_REQUEST_COMPLIANCE_REVIEW"),
            org.mockito.ArgumentMatchers.eq(100L)
        );
    }

    @Test
    void modelFailureFallsBackWithoutBlockingSubmission() {
        PurchaseRequest request = request("申请单级说明不参与审核", 1);
        PurchaseRequestItem item = item("空调冷凝水管接头老化漏水，采购同规格配件完成维修");
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(m3VisionClient.reviewProcurementCompliance(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("model unavailable"));

        String opinion = service.review(request, List.of(item));

        assertThat(opinion).startsWith("建议通过：").contains("审批人请结合实际情况复核");
    }

    private ComplianceReview approvedReview() {
        return new ComplianceReview(
            "APPROVE",
            "LOW",
            "采购原因具体，已说明使用场景和采购必要性",
            List.of(),
            List.of(),
            new BigDecimal("0.91"),
            List.of(new ComplianceItemReview(
                1,
                "APPROVE",
                "采购原因具体，已说明使用场景和采购必要性",
                List.of()
            )),
            List.of()
        );
    }

    private PurchaseRequestItemDTO itemDto(String purpose) {
        PurchaseRequestItemDTO item = new PurchaseRequestItemDTO();
        item.setPurpose(purpose);
        return item;
    }

    private PurchaseRequest request(String reason, int urgencyLevel) {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(100L);
        request.setReqTitle("M6机房维修配件采购");
        request.setReqType("服务/费用/低值易耗等");
        request.setDeptName("M6机房");
        request.setTotalAmount(new BigDecimal("1200.00"));
        request.setUrgencyLevel(urgencyLevel);
        request.setNeedDate(LocalDate.of(2026, 7, 30));
        request.setReqReason(reason);
        return request;
    }

    private PurchaseRequestItem item(String purpose) {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setItemName("维修接头");
        item.setSpecModel("DN100-P110");
        item.setQty(new BigDecimal("4"));
        item.setUnit("件");
        item.setUnitPrice(new BigDecimal("300.00"));
        item.setPurpose(purpose);
        return item;
    }
}
