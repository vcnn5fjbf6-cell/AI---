package com.company.procurement.modules.procurement.order.service.impl;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.procurement.order.dto.ProcurementOrderPageQueryDTO;
import com.company.procurement.modules.procurement.order.mapper.ProcurementAfterSaleMapper;
import com.company.procurement.modules.procurement.order.mapper.ProcurementOrderQueryMapper;
import com.company.procurement.modules.procurement.order.service.OrderInvoiceService;
import com.company.procurement.modules.procurement.order.support.SupplierSoldOrderAccess;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementOrderServiceImplPagingTest {

    @Mock
    private ProcurementAfterSaleMapper afterSaleMapper;
    @Mock
    private ProcurementOrderQueryMapper orderQueryMapper;
    @Mock
    private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private SupplierQuoteMapper supplierQuoteMapper;
    @Mock
    private ProcurementSettingMapper procurementSettingMapper;
    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;
    @Mock
    private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock
    private ApprovalRecordMapper approvalRecordMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrderInvoiceService orderInvoiceService;

    @InjectMocks
    private ProcurementOrderServiceImpl service;

    @AfterEach
    void clearAuthContext() {
        AuthContextHolder.clear();
    }

    @Test
    void internalOrderPageScansInBatchesAndCachesReviewerBySourceRequest() {
        AuthUser user = AuthUser.builder()
            .userId(7L)
            .username("internal")
            .userType("EMPLOYEE")
            .roleCodes(List.of("EMPLOYEE"))
            .build();
        AuthContextHolder.set(user);

        ProcurementOrderVO ownOrder = order(1L, 101L, 7L);
        ProcurementOrderVO reviewOrder = order(2L, 202L, 8L);
        ProcurementOrderVO sameRequestOrder = order(3L, 202L, 9L);
        when(orderQueryMapper.selectOrderRows(
                isNull(), isNull(), isNull(), isNull(), eq(7L), eq(0L), eq(200L)))
            .thenReturn(List.of(ownOrder, reviewOrder, sameRequestOrder));
        when(orderInvoiceService.canViewOriginalOrder(eq(user), any(ProcurementOrderVO.class)))
            .thenAnswer(invocation -> {
                ProcurementOrderVO order = invocation.getArgument(1);
                return user.getUserId().equals(order.getApplicantUserId());
            });
        when(orderInvoiceService.canReviewInvoice(user, reviewOrder)).thenReturn(true);
        when(orderInvoiceService.decorate(reviewOrder, user, true)).thenReturn(true);

        ProcurementOrderPageQueryDTO query = new ProcurementOrderPageQueryDTO();
        query.setPageNo(2);
        query.setPageSize(1);

        PageResult<ProcurementOrderVO> result = service.pageMyOrders(query);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getRecords()).containsExactly(reviewOrder);
        verify(orderQueryMapper).selectOrderRows(
            isNull(), isNull(), isNull(), isNull(), eq(7L), eq(0L), eq(200L));
        verify(orderInvoiceService).canReviewInvoice(user, reviewOrder);
        verify(orderInvoiceService).decorate(reviewOrder, user, true);
    }

    @Test
    void supplierSupervisorPageUsesAllSupplierScope() {
        AuthUser supervisor = AuthUser.builder()
            .userId(255L)
            .username("supplier-supervisor")
            .userType("SUPPLIER_QUOTE_VIEWER")
            .roleCodes(List.of("SUPPLIER_QUOTE_VIEWER"))
            .permissionCodes(List.of(SupplierSoldOrderAccess.MANAGE_ALL_PERMISSION))
            .build();
        AuthContextHolder.set(supervisor);
        ProcurementOrderVO otherSupplierOrder = order(2L, 202L, 8L);
        otherSupplierOrder.setSupplierId(2L);
        when(orderQueryMapper.countOrderRows(isNull(), isNull(), isNull(), isNull())).thenReturn(1L);
        when(orderQueryMapper.selectOrderRows(
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(0L), eq(10L)))
            .thenReturn(List.of(otherSupplierOrder));

        ProcurementOrderPageQueryDTO query = new ProcurementOrderPageQueryDTO();
        query.setPageNo(1);
        query.setPageSize(10);

        PageResult<ProcurementOrderVO> result = service.pageMyOrders(query);

        assertThat(result.getRecords()).containsExactly(otherSupplierOrder);
        verify(orderQueryMapper).countOrderRows(isNull(), isNull(), isNull(), isNull());
        verify(orderQueryMapper).selectOrderRows(
            isNull(), isNull(), isNull(), isNull(), isNull(), eq(0L), eq(10L));
        verify(orderInvoiceService).decorate(otherSupplierOrder, supervisor);
    }

    @Test
    void ordinarySupplierPageKeepsBoundSupplierScope() {
        AuthUser supplier = AuthUser.builder()
            .userId(77L)
            .username("supplier")
            .userType("SUPPLIER")
            .supplierId(14L)
            .roleCodes(List.of("SUPPLIER"))
            .permissionCodes(List.of("procurement_order:supplier-read"))
            .build();
        AuthContextHolder.set(supplier);
        when(orderQueryMapper.countOrderRows(isNull(), isNull(), isNull(), eq(14L))).thenReturn(0L);

        ProcurementOrderPageQueryDTO query = new ProcurementOrderPageQueryDTO();
        query.setPageNo(1);
        query.setPageSize(10);

        PageResult<ProcurementOrderVO> result = service.pageMyOrders(query);

        assertThat(result.getRecords()).isEmpty();
        verify(orderQueryMapper).countOrderRows(isNull(), isNull(), isNull(), eq(14L));
    }

    private ProcurementOrderVO order(Long quoteItemId, Long sourceRequestId, Long applicantUserId) {
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setQuoteItemId(quoteItemId);
        order.setPurchaseRequestId(sourceRequestId);
        order.setSourceRequestId(sourceRequestId);
        order.setApplicantUserId(applicantUserId);
        return order;
    }
}
