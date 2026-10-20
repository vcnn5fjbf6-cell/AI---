package com.company.procurement.modules.procurement.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
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
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.mapper.ProcurementMessageReadMapper;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierAwardNotificationService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplReturnedRequestCloseTest {

    @Mock
    private PurchaseRequestService purchaseRequestService;
    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;
    @Mock
    private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock
    private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock
    private ApprovalRecordMapper approvalRecordMapper;
    @Mock
    private ApprovalTaskService approvalTaskService;
    @Mock
    private ProcurementApprovalRouteResolver approvalRouteResolver;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProcurementSettingMapper procurementSettingMapper;
    @Mock
    private ProcurementSettingsService procurementSettingsService;
    @Mock
    private SupplierQuoteMapper supplierQuoteMapper;
    @Mock
    private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock
    private ProcurementAfterSaleMapper procurementAfterSaleMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private SupplierAwardNotificationService awardNotificationService;
    @Mock
    private TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    @Mock
    private PurchaseRequestNoService requestNoService;
    @Mock
    private ProcurementMessageReadMapper messageReadMapper;
    @Mock
    private AwardPriceSyncService awardPriceSyncService;
    @Mock
    private OverPriceReviewService overPriceReviewService;

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void applicantCanCloseReturnedNormalAndEmergencyRequests(int urgencyLevel) {
        AuthContextHolder.set(engineer(7L));
        PurchaseRequest request = returnedRequest(101L, 7L, urgencyLevel);
        ApprovalInstance instance = returnedInstance(201L, request.getId());
        WorkflowServiceImpl service = service();

        when(purchaseRequestMapper.selectById(request.getId())).thenReturn(request);
        when(approvalInstanceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(instance);

        service.closeReturnedRequest(request.getId());

        assertThat(request.getStatus()).isEqualTo(ProcurementWorkflow.STATUS_CANCELLED);
        assertThat(request.getApprovalStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_TERMINATED);
        assertThat(request.getCancelledAt()).isNotNull();
        assertThat(request.getCancelReason()).contains("关闭退回申请");
        assertThat(instance.getStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_TERMINATED);
        assertThat(instance.getCurrentApproverId()).isNull();
        assertThat(instance.getLastAction()).isEqualTo(ProcurementWorkflow.ACTION_CLOSE);
        assertThat(instance.getEndedAt()).isNotNull();
        verify(approvalTaskService).closePendingTasks(instance.getId(), "RETURNED_REQUEST_CLOSED");

        ArgumentCaptor<ApprovalRecord> recordCaptor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRecordMapper).insert(recordCaptor.capture());
        ApprovalRecord record = recordCaptor.getValue();
        assertThat(record.getAction()).isEqualTo(ProcurementWorkflow.ACTION_CLOSE);
        assertThat(record.getFromStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_RETURNED);
        assertThat(record.getToStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_TERMINATED);
        assertThat(record.getCanResubmit()).isZero();
        assertThat(record.getIsFinalNode()).isEqualTo(1);
        verify(approvalInstanceMapper).updateById(instance);
        verify(purchaseRequestMapper).updateById(request);
    }

    @Test
    void nonApplicantCannotCloseReturnedRequest() {
        AuthContextHolder.set(engineer(8L));
        PurchaseRequest request = returnedRequest(102L, 7L, 1);
        WorkflowServiceImpl service = service();
        when(purchaseRequestMapper.selectById(request.getId())).thenReturn(request);

        assertThatThrownBy(() -> service.closeReturnedRequest(request.getId()))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo("FORBIDDEN"));

        verify(approvalInstanceMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(purchaseRequestMapper, never()).updateById(any(PurchaseRequest.class));
    }

    @Test
    void systemAdministratorCanCloseReturnedRequestForApplicant() {
        AuthContextHolder.set(systemAdministrator(1L));
        PurchaseRequest request = returnedRequest(104L, 7L, 1);
        ApprovalInstance instance = returnedInstance(204L, request.getId());
        WorkflowServiceImpl service = service();
        when(purchaseRequestMapper.selectById(request.getId())).thenReturn(request);
        when(approvalInstanceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(instance);

        service.closeReturnedRequest(request.getId());

        assertThat(request.getStatus()).isEqualTo(ProcurementWorkflow.STATUS_CANCELLED);
        assertThat(request.getCancelReason()).contains("管理员确认");
        verify(purchaseRequestMapper).updateById(request);
    }

    @Test
    void onlyReturnedRequestCanBeClosed() {
        AuthContextHolder.set(engineer(7L));
        PurchaseRequest request = returnedRequest(103L, 7L, 1);
        request.setStatus(ProcurementWorkflow.STATUS_DRAFT);
        WorkflowServiceImpl service = service();
        when(purchaseRequestMapper.selectById(request.getId())).thenReturn(request);

        assertThatThrownBy(() -> service.closeReturnedRequest(request.getId()))
            .isInstanceOfSatisfying(BizException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("PURCHASE_REQUEST_STATUS_INVALID"));

        verify(approvalInstanceMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(purchaseRequestMapper, never()).updateById(any(PurchaseRequest.class));
    }

    private WorkflowServiceImpl service() {
        return new WorkflowServiceImpl(
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

    private PurchaseRequest returnedRequest(Long id, Long applicantUserId, int urgencyLevel) {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(id);
        request.setApplicantUserId(applicantUserId);
        request.setUrgencyLevel(urgencyLevel);
        request.setCurrentNodeNo(4);
        request.setSubmitCount(2);
        request.setApprovalStatus(ProcurementWorkflow.APPROVAL_RETURNED);
        request.setStatus(ProcurementWorkflow.STATUS_RETURNED);
        return request;
    }

    private ApprovalInstance returnedInstance(Long id, Long requestId) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(id);
        instance.setPurchaseRequestId(requestId);
        instance.setCurrentNodeNo(4);
        instance.setCurrentApproverId(99L);
        instance.setStatus(ProcurementWorkflow.APPROVAL_RETURNED);
        return instance;
    }

    private AuthUser engineer(Long userId) {
        return AuthUser.builder()
            .userId(userId)
            .userType("INTERNAL")
            .roleCodes(List.of(ProcurementWorkflow.ROLE_ENGINEER))
            .build();
    }

    private AuthUser systemAdministrator(Long userId) {
        return AuthUser.builder()
            .userId(userId)
            .userType("ADMIN")
            .roleCodes(List.of("ADMIN"))
            .build();
    }
}
