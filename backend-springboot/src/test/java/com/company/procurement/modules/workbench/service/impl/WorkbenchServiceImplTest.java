package com.company.procurement.modules.workbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.order.entity.ProcurementAfterSale;
import com.company.procurement.modules.procurement.order.mapper.ProcurementOrderQueryMapper;
import com.company.procurement.modules.procurement.order.service.OrderInvoiceService;
import com.company.procurement.modules.procurement.order.support.SupplierSoldOrderAccess;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestItemVO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.workbench.vo.WorkbenchDetailRowVO;
import com.company.procurement.modules.workbench.vo.WorkbenchRequestVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchServiceImplTest {

    @Mock
    private SupplierQuoteMapper supplierQuoteMapper;
    @Mock
    private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private ProcurementOrderQueryMapper orderQueryMapper;
    @Mock
    private OrderInvoiceService orderInvoiceService;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private WorkbenchServiceImpl service;

    @Test
    void purchaseApprovalDetailRowsIncludesAwardedSupplierName() {
        PurchaseRequestVO request = request(ProcurementWorkflow.PRICE_SOURCE_SUPPLIER_QUOTE);
        SupplierQuoteItem awardedItem = new SupplierQuoteItem();
        awardedItem.setPurchaseRequestItemId(100L);
        awardedItem.setQuoteId(200L);
        awardedItem.setAwarded(1);
        awardedItem.setStatus("ACTIVE");
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(awardedItem));

        SupplierQuote quote = new SupplierQuote();
        quote.setId(200L);
        quote.setSupplierId(300L);
        quote.setStatus(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        when(supplierQuoteMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(quote));

        Supplier supplier = new Supplier();
        supplier.setId(300L);
        supplier.setSupplierShortName("京东企业购");
        supplier.setSupplierName("北京京东世纪信息技术有限公司");
        when(supplierMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(supplier));

        List<WorkbenchDetailRowVO> rows = invokePurchaseApprovalDetailRows(request);

        assertThat(rows).singleElement()
            .extracting(WorkbenchDetailRowVO::getSupplierName)
            .isEqualTo("京东企业购");
    }

    @Test
    void purchaseApprovalDetailRowsDoesNotLoadSupplierBeforeAwardPriceIsApplied() {
        List<WorkbenchDetailRowVO> rows = invokePurchaseApprovalDetailRows(request(null));

        assertThat(rows).singleElement()
            .extracting(WorkbenchDetailRowVO::getSupplierName)
            .isNull();
        verify(supplierQuoteItemMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void sourceAssistantReceivesSupplierProposalConfirmationTask() {
        ProcurementAfterSale sale = new ProcurementAfterSale();
        sale.setId(9L);
        sale.setQuoteItemId(88L);
        sale.setRequesterUserId(1L);
        sale.setStatus("PROPOSAL_PENDING");
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setQuoteItemId(88L);
        order.setApplicantUserId(1L);
        AuthUser assistant = AuthUser.builder()
            .userId(33L)
            .username("assistant")
            .userType("EMPLOYEE")
            .roleCodes(List.of("EMPLOYEE"))
            .build();
        when(orderQueryMapper.selectOrderRowByQuoteItemId(88L)).thenReturn(order);
        when(orderInvoiceService.canOperateAsSourceAssistant(assistant, order)).thenReturn(true);

        WorkbenchRequestVO row = ReflectionTestUtils.invokeMethod(service, "afterSaleVisibleRow", sale, assistant);

        assertThat(row).isNotNull();
        assertThat(row.getRelation()).isEqualTo("todo");
        assertThat(row.getActionable()).isTrue();
    }

    @Test
    void supplierSupervisorReceivesOtherSupplierAfterSaleTask() {
        ProcurementAfterSale sale = new ProcurementAfterSale();
        sale.setId(10L);
        sale.setQuoteItemId(89L);
        sale.setRequesterUserId(1L);
        sale.setStatus("PENDING");
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setQuoteItemId(89L);
        order.setSupplierId(55L);
        AuthUser supervisor = AuthUser.builder()
            .userId(255L)
            .username("supplier-supervisor")
            .userType("SUPPLIER")
            .supplierId(14L)
            .roleCodes(List.of("SUPPLIER", "SUPPLIER_QUOTE_VIEWER"))
            .permissionCodes(List.of(SupplierSoldOrderAccess.MANAGE_ALL_PERMISSION))
            .build();
        when(orderQueryMapper.selectOrderRowByQuoteItemId(89L)).thenReturn(order);

        WorkbenchRequestVO row = ReflectionTestUtils.invokeMethod(service, "afterSaleVisibleRow", sale, supervisor);

        assertThat(row).isNotNull();
        assertThat(row.getRelation()).isEqualTo("todo");
        assertThat(row.getActionable()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private List<WorkbenchDetailRowVO> invokePurchaseApprovalDetailRows(PurchaseRequestVO request) {
        return ReflectionTestUtils.invokeMethod(service, "purchaseApprovalDetailRows", request);
    }

    private PurchaseRequestVO request(String priceSource) {
        PurchaseRequestItemVO item = new PurchaseRequestItemVO();
        item.setId(100L);
        item.setLineNo(1);
        item.setItemName("空气清新剂");
        item.setSpecModel("500ml");
        item.setQty(new BigDecimal("2"));
        item.setUnit("件");
        item.setLineAmount(new BigDecimal("39.20"));
        item.setReferenceLineAmount(new BigDecimal("99.80"));
        item.setStatus(ProcurementWorkflow.ITEM_STATUS_ACTIVE);

        PurchaseRequestVO request = new PurchaseRequestVO();
        request.setId(10L);
        request.setPriceSource(priceSource);
        request.setItems(List.of(item));
        return request;
    }
}
