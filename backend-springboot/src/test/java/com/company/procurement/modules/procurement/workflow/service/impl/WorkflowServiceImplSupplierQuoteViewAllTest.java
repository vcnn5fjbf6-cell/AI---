package com.company.procurement.modules.procurement.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.procurement.order.mapper.ProcurementAfterSaleMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestNoService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.settings.service.ProcurementSettingsService;
import com.company.procurement.modules.procurement.workflow.mapper.ProcurementMessageReadMapper;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierAwardNotificationService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.supplier.support.SupplierQuoteAccess;
import com.company.procurement.modules.supplier.vo.SupplierQuoteSummaryVO;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplSupplierQuoteViewAllTest {

    @Mock private PurchaseRequestService purchaseRequestService;
    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock private ApprovalRecordMapper approvalRecordMapper;
    @Mock private ApprovalTaskService approvalTaskService;
    @Mock private ProcurementApprovalRouteResolver approvalRouteResolver;
    @Mock private SysUserMapper userMapper;
    @Mock private ProcurementSettingMapper procurementSettingMapper;
    @Mock private ProcurementSettingsService procurementSettingsService;
    @Mock private SupplierQuoteMapper supplierQuoteMapper;
    @Mock private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock private ProcurementAfterSaleMapper procurementAfterSaleMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private SupplierAwardNotificationService awardNotificationService;
    @Mock private TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    @Mock private PurchaseRequestNoService requestNoService;
    @Mock private ProcurementMessageReadMapper messageReadMapper;
    @Mock private AwardPriceSyncService awardPriceSyncService;
    @Mock private OverPriceReviewService overPriceReviewService;

    private WorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowServiceImpl(
            purchaseRequestService,
            purchaseRequestMapper,
            purchaseRequestItemMapper,
            approvalInstanceMapper,
            approvalRecordMapper,
            approvalTaskService,
            approvalRouteResolver,
            userMapper,
            procurementSettingMapper,
            procurementSettingsService,
            supplierQuoteMapper,
            supplierQuoteItemMapper,
            procurementAfterSaleMapper,
            supplierMapper,
            awardNotificationService,
            temporarySupplierRecommendationService,
            requestNoService,
            messageReadMapper,
            awardPriceSyncService,
            overPriceReviewService
        );
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void viewAllSupplierCanReadCompleteQuoteSummary() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(88L);
        when(purchaseRequestMapper.selectById(request.getId())).thenReturn(request);
        when(supplierQuoteMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        AuthContextHolder.set(supplierUser(List.of(SupplierQuoteAccess.VIEW_ALL_SUBMISSIONS_PERMISSION)));

        SupplierQuoteSummaryVO summary = service.getQuoteSummary(request.getId());

        assertThat(summary.getPurchaseRequestId()).isEqualTo(request.getId());
        assertThat(summary.getQuoteDetailsVisible()).isTrue();
        assertThat(summary.getQuotes()).isEmpty();
    }

    @Test
    void ordinarySupplierCannotReadAllQuoteSummary() {
        AuthContextHolder.set(supplierUser(List.of()));

        assertThatThrownBy(() -> service.getQuoteSummary(88L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("cannot access all supplier quote submissions");
    }

    private AuthUser supplierUser(List<String> permissions) {
        return AuthUser.builder()
            .userId(10L)
            .userType("SUPPLIER")
            .supplierId(100L)
            .roleCodes(List.of("SUPPLIER"))
            .permissionCodes(permissions)
            .build();
    }
}
