package com.company.procurement.modules.procurement.settings.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.modules.integration.feishu.dto.FeishuApprovalCardNotificationQueryDTO;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardNotificationVO;
import com.company.procurement.modules.procurement.settings.dto.ProcurementSettingsUpdateDTO;
import com.company.procurement.modules.procurement.settings.service.ProcurementFlowTestService;
import com.company.procurement.modules.procurement.settings.service.ProcurementSettingsService;
import com.company.procurement.modules.procurement.settings.vo.ProcurementRuleTestResultVO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingsVO;
import com.company.procurement.modules.supplier.dto.SupplierFeishuQuoteNoticeQueryDTO;
import com.company.procurement.modules.supplier.service.SupplierFeishuNotificationService;
import com.company.procurement.modules.supplier.vo.SupplierFeishuQuoteNoticeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/procurement/settings")
@RequiredArgsConstructor
public class ProcurementSettingsController {

    private final ProcurementSettingsService procurementSettingsService;
    private final ProcurementFlowTestService procurementFlowTestService;
    private final FeishuApprovalCardService feishuApprovalCardService;
    private final SupplierFeishuNotificationService supplierFeishuNotificationService;

    @GetMapping
    public Result<ProcurementSettingsVO> getSettings() {
        return Result.ok(procurementSettingsService.getSettings());
    }

    @PutMapping
    public Result<Void> updateSettings(@RequestBody @Valid ProcurementSettingsUpdateDTO dto) {
        procurementSettingsService.updateSettings(dto);
        return Result.ok();
    }

    @PostMapping("/rule-tests/{actionCode}")
    public Result<ProcurementRuleTestResultVO> testRuleAction(@PathVariable String actionCode) {
        return Result.ok(procurementSettingsService.testRuleAction(actionCode));
    }

    @PostMapping("/flow-tests/{actionCode}")
    public Result<ProcurementRuleTestResultVO> triggerFormalAction(@PathVariable String actionCode) {
        return Result.ok(procurementFlowTestService.triggerFormalAction(actionCode));
    }

    @PostMapping("/feishu-approval-card/test")
    public Result<ProcurementRuleTestResultVO> sendFeishuApprovalCardTest() {
        FeishuApprovalCardActionResult result = feishuApprovalCardService.sendTestCardToUser(AuthContextHolder.requireUser().getUserId());
        ProcurementRuleTestResultVO vo = new ProcurementRuleTestResultVO();
        vo.setActionCode("FEISHU_APPROVAL_CARD_TEST");
        vo.setActionName("飞书审批卡片测试");
        vo.setTriggered(result.isSuccess());
        vo.setMessage(result.getMessage());
        vo.setDetail(java.util.Map.of("toastType", result.getToastType()));
        return Result.ok(vo);
    }

    @PostMapping("/feishu-supplier-quote-card/test")
    public Result<ProcurementRuleTestResultVO> sendFeishuSupplierQuoteCardTest(
        @RequestBody(required = false) java.util.Map<String, String> body) {
        java.util.Map<String, String> payload = body == null ? java.util.Map.of() : body;
        FeishuApprovalCardActionResult result = supplierFeishuNotificationService.sendSupplierQuoteNoticeTest(
            payload.getOrDefault("receiveIdType", "email"),
            payload.getOrDefault("receiveId", "")
        );
        ProcurementRuleTestResultVO vo = new ProcurementRuleTestResultVO();
        vo.setActionCode("FEISHU_SUPPLIER_QUOTE_CARD_TEST");
        vo.setActionName("供应商报价通知卡片测试");
        vo.setTriggered(result.isSuccess());
        vo.setMessage(result.getMessage());
        vo.setDetail(java.util.Map.of("toastType", result.getToastType()));
        return Result.ok(vo);
    }

    @GetMapping("/feishu-approval-card/notifications")
    public Result<PageResult<FeishuApprovalCardNotificationVO>> pageFeishuApprovalCardNotifications(
        @Valid FeishuApprovalCardNotificationQueryDTO queryDTO) {
        return Result.ok(feishuApprovalCardService.pageNotifications(queryDTO));
    }

    @PostMapping("/feishu-approval-card/notifications/{id}/resend")
    public Result<ProcurementRuleTestResultVO> resendFeishuApprovalCard(@PathVariable Long id) {
        FeishuApprovalCardActionResult result = feishuApprovalCardService.resendNotification(id);
        ProcurementRuleTestResultVO vo = new ProcurementRuleTestResultVO();
        vo.setActionCode("FEISHU_APPROVAL_CARD_RESEND");
        vo.setActionName("重新发送飞书审批卡片");
        vo.setTriggered(result.isSuccess());
        vo.setMessage(result.getMessage());
        vo.setDetail(java.util.Map.of("toastType", result.getToastType()));
        return Result.ok(vo);
    }

    @GetMapping("/feishu-supplier-quote-card/notices")
    public Result<PageResult<SupplierFeishuQuoteNoticeVO>> pageFeishuSupplierQuoteNotices(
        @Valid SupplierFeishuQuoteNoticeQueryDTO queryDTO) {
        return Result.ok(supplierFeishuNotificationService.pageQuoteNotices(queryDTO));
    }

    @PostMapping("/feishu-supplier-quote-card/notices/{id}/resend")
    public Result<ProcurementRuleTestResultVO> resendFeishuSupplierQuoteNotice(@PathVariable Long id) {
        FeishuApprovalCardActionResult result = supplierFeishuNotificationService.resendQuoteNotice(id);
        ProcurementRuleTestResultVO vo = new ProcurementRuleTestResultVO();
        vo.setActionCode("FEISHU_SUPPLIER_QUOTE_CARD_RESEND");
        vo.setActionName("重新发送供应商报价卡片");
        vo.setTriggered(result.isSuccess());
        vo.setMessage(result.getMessage());
        vo.setDetail(java.util.Map.of("toastType", result.getToastType()));
        return Result.ok(vo);
    }
}
