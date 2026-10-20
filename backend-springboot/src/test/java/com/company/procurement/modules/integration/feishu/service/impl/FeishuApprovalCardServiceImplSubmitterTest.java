package com.company.procurement.modules.integration.feishu.service.impl;

import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.modules.auth.service.AuthUserResolver;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig;
import com.company.procurement.modules.integration.feishu.mapper.FeishuApprovalCardNotificationMapper;
import com.company.procurement.modules.procurement.assistant.mapper.ProcurementAiSessionMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FeishuApprovalCardServiceImplSubmitterTest {

    @Mock private FeishuApprovalCardRuntimeConfig runtimeConfig;
    @Mock private ApprovalTaskMapper approvalTaskMapper;
    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock private ProcurementAiSessionMapper procurementAiSessionMapper;
    @Mock private SupplierQuoteMapper supplierQuoteMapper;
    @Mock private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private FeishuApprovalCardNotificationMapper notificationMapper;
    @Mock private AuthUserResolver authUserResolver;
    @Mock private WorkflowService workflowService;
    @Mock private OverPriceReviewService overPriceReviewService;
    @Mock private FeishuDemandSubmitterResolver demandSubmitterResolver;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private FeishuApprovalCardServiceImpl service;

    @Test
    void approvalSummaryShowsOriginalDemandSubmitters() {
        PurchaseRequest request = new PurchaseRequest();
        request.setReqNo("HZCG-000003");
        request.setReqTitle("月度需求汇总");
        request.setTotalAmount(new BigDecimal("6000"));
        ApprovalTask task = new ApprovalTask();
        task.setLevelName("楼长需求审核");
        SysUser approver = new SysUser();
        approver.setRealName("赵新");
        FeishuDemandSubmitterResolver.SubmitterContext submitters =
            new FeishuDemandSubmitterResolver.SubmitterContext("杨文婷、张三", Map.of(), 2);

        String markdown = ReflectionTestUtils.invokeMethod(
            service,
            "approvalSummaryMarkdown",
            request,
            task,
            approver,
            null,
            submitters
        );

        assertThat(markdown).contains("**需求提交人**：杨文婷、张三");
        assertThat(markdown).contains("**审批人**：赵新");
    }

    @Test
    void multiSubmitterRequestLabelsEachItem() {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(11L);
        item.setItemName("漫德莱黄铜球阀");
        item.setQty(new BigDecimal("5"));
        item.setUnit("件");
        item.setLineAmount(new BigDecimal("92.50"));
        FeishuDemandSubmitterResolver.SubmitterContext submitters =
            new FeishuDemandSubmitterResolver.SubmitterContext("杨文婷、张三", Map.of(11L, "杨文婷"), 2);

        String markdown = ReflectionTestUtils.invokeMethod(
            service,
            "itemMarkdown",
            1,
            item,
            false,
            new PurchaseRequest(),
            null,
            submitters
        );

        assertThat(markdown).contains("需求提交人：杨文婷");
    }

    @Test
    void multiSubmitterReplacementLabelsItsSourceItem() {
        SupplierQuoteItem replacement = new SupplierQuoteItem();
        replacement.setQuoteId(21L);
        replacement.setPurchaseRequestItemId(11L);
        replacement.setItemName("原商品");
        replacement.setReplacementItemName("替换商品");
        replacement.setQty(BigDecimal.ONE);
        replacement.setUnit("件");
        replacement.setLineAmount(new BigDecimal("100"));
        replacement.setReplacementLineAmount(new BigDecimal("90"));
        replacement.setOriginalCondition("USED");
        replacement.setOriginalAssetClassification("ASSET");
        replacement.setOriginalSpecialApprovalRequired(1);
        replacement.setReplacementCondition("USED");
        replacement.setReplacementAssetClassification("LOW_VALUE");
        replacement.setReplacementSpecialApprovalRequired(1);
        FeishuDemandSubmitterResolver.SubmitterContext submitters =
            new FeishuDemandSubmitterResolver.SubmitterContext("杨文婷、张三", Map.of(11L, "张三"), 2);

        String markdown = ReflectionTestUtils.invokeMethod(
            service,
            "replacementItemMarkdown",
            1,
            replacement,
            Map.of(),
            Map.of(),
            true,
            submitters
        );

        assertThat(markdown).contains("需求提交人：张三");
        assertThat(markdown).contains("原商品成色：二手；资产判定：固定资产；**需特殊审批**");
        assertThat(markdown).contains("替换商品成色：二手；资产判定：低值易耗；**需特殊审批**");
    }
}
