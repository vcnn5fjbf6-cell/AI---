package com.company.procurement.modules.procurement.monthly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQuantityUpdateDTO;
import com.company.procurement.modules.procurement.monthly.entity.MonthlyProcurementDemand;
import com.company.procurement.modules.procurement.monthly.mapper.MonthlyProcurementDemandMapper;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyDemandVO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.route.service.ProcurementDepartmentAccessService;
import com.company.procurement.modules.procurement.settings.service.ProcurementSettingsService;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingItemVO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingsVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyProcurementServiceImplDemandPoolEditTest {

    @Mock
    private MonthlyProcurementDemandMapper demandMapper;
    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;
    @Mock
    private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock
    private PurchaseRequestService purchaseRequestService;
    @Mock
    private ProcurementSettingsService procurementSettingsService;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProcurementDepartmentAccessService departmentAccessService;

    private MonthlyProcurementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MonthlyProcurementServiceImpl(
            demandMapper,
            purchaseRequestMapper,
            purchaseRequestItemMapper,
            purchaseRequestService,
            procurementSettingsService,
            workflowService,
            userMapper,
            departmentAccessService
        );
        AuthContextHolder.set(AuthUser.builder()
            .userId(10L)
            .userType("ENGINEER")
            .roleCodes(List.of("ENGINEER"))
            .build());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void reduceOwnDemandPoolItemQuantityRecalculatesLineAndRequestAmount() {
        PurchaseRequest request = demandPoolRequest();
        PurchaseRequestItem item = demandPoolItem();
        when(purchaseRequestItemMapper.selectById(100L)).thenReturn(item);
        when(purchaseRequestMapper.selectById(20L)).thenReturn(request);
        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        MonthlyDemandQuantityUpdateDTO dto = new MonthlyDemandQuantityUpdateDTO();
        dto.setQty(new BigDecimal("4"));

        MonthlyDemandVO result = service.reduceOwnDemandQuantity(100L, dto);

        ArgumentCaptor<PurchaseRequestItem> itemCaptor = ArgumentCaptor.forClass(PurchaseRequestItem.class);
        ArgumentCaptor<PurchaseRequest> requestCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestItemMapper).updateById(itemCaptor.capture());
        verify(purchaseRequestMapper).updateById(requestCaptor.capture());

        assertThat(itemCaptor.getValue().getQty()).isEqualByComparingTo("4.00");
        assertThat(itemCaptor.getValue().getLineAmount()).isEqualByComparingTo("20.00");
        assertThat(requestCaptor.getValue().getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(result.getQty()).isEqualByComparingTo("4.00");
        assertThat(result.getLineAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void reduceOwnDemandPoolItemQuantityRejectsIncrease() {
        PurchaseRequest request = demandPoolRequest();
        PurchaseRequestItem item = demandPoolItem();
        when(purchaseRequestItemMapper.selectById(100L)).thenReturn(item);
        when(purchaseRequestMapper.selectById(20L)).thenReturn(request);

        MonthlyDemandQuantityUpdateDTO dto = new MonthlyDemandQuantityUpdateDTO();
        dto.setQty(new BigDecimal("12"));

        assertThatThrownBy(() -> service.reduceOwnDemandQuantity(100L, dto))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("只能减少");
    }

    @Test
    void deleteOwnDemandPoolItemRejectsLastItemAndMovesRequestOutOfPool() {
        PurchaseRequest request = demandPoolRequest();
        PurchaseRequestItem item = demandPoolItem();
        when(purchaseRequestItemMapper.selectById(100L)).thenReturn(item);
        when(purchaseRequestMapper.selectById(20L)).thenReturn(request);
        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        service.deleteOwnDemand(100L);

        ArgumentCaptor<PurchaseRequestItem> itemCaptor = ArgumentCaptor.forClass(PurchaseRequestItem.class);
        ArgumentCaptor<PurchaseRequest> requestCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestItemMapper).updateById(itemCaptor.capture());
        verify(purchaseRequestMapper).updateById(requestCaptor.capture());

        assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ProcurementWorkflow.ITEM_STATUS_REJECTED);
        assertThat(requestCaptor.getValue().getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(ProcurementWorkflow.STATUS_REJECTED);
        assertThat(requestCaptor.getValue().getApprovalStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_REJECTED);
    }

    @Test
    void scheduledAggregationRunsAfterConfiguredCutoffDayWithoutLogin() {
        AuthContextHolder.clear();
        when(procurementSettingsService.getSettings()).thenReturn(settings("monthly_aggregate_day", "20"));
        when(demandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.aggregateForScheduledDate(LocalDate.of(2026, 7, 21));

        verify(workflowService).publishDemandPoolRequestsOnSchedule();
    }

    @Test
    void scheduledAggregationSkipsBeforeConfiguredCutoffDay() {
        when(procurementSettingsService.getSettings()).thenReturn(settings("monthly_aggregate_day", "20"));

        service.aggregateForScheduledDate(LocalDate.of(2026, 7, 20));

        verify(workflowService, never()).publishDemandPoolRequestsOnSchedule();
    }

    private ProcurementSettingsVO settings(String key, String value) {
        ProcurementSettingItemVO item = new ProcurementSettingItemVO();
        item.setSettingKey(key);
        item.setSettingValue(value);
        ProcurementSettingsVO settings = new ProcurementSettingsVO();
        settings.setSettings(List.of(item));
        return settings;
    }

    private PurchaseRequest demandPoolRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(20L);
        request.setReqNo("PTCG-100020");
        request.setApplicantUserId(10L);
        request.setReqTitle("测试需求");
        request.setReqType("服务/费用/低值易耗等");
        request.setDeptName("酒仙桥");
        request.setStatus(ProcurementWorkflow.STATUS_DEMAND_POOL);
        request.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
        request.setApprovedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        request.setTotalAmount(new BigDecimal("50.00"));
        return request;
    }

    private PurchaseRequestItem demandPoolItem() {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(100L);
        item.setPurchaseRequestId(20L);
        item.setLineNo(1);
        item.setItemCode("MAT-001");
        item.setItemName("测试商品");
        item.setSpecModel("A1");
        item.setUnit("件");
        item.setQty(new BigDecimal("10.00"));
        item.setUnitPrice(new BigDecimal("5.00"));
        item.setLineAmount(new BigDecimal("50.00"));
        item.setStatus(ProcurementWorkflow.ITEM_STATUS_ACTIVE);
        return item;
    }
}
