package com.company.procurement.modules.procurement.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardPriceSyncServiceTest {

    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;
    @Mock
    private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock
    private SupplierQuoteItemMapper supplierQuoteItemMapper;

    private AwardPriceSyncService service;

    @BeforeEach
    void setUp() {
        service = new AwardPriceSyncService(
            purchaseRequestMapper,
            purchaseRequestItemMapper,
            supplierQuoteItemMapper
        );
    }

    @Test
    void syncAwardedPricesPreservesReferenceAndWritesSupplierQuotePrice() {
        PurchaseRequest request = request(new BigDecimal("370.80"));
        PurchaseRequestItem requestItem = requestItem(new BigDecimal("123.60"), new BigDecimal("370.80"));
        SupplierQuoteItem awardedItem = awardedItem(new BigDecimal("137.50"), new BigDecimal("412.50"));
        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(requestItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(awardedItem));

        BigDecimal totalAmount = service.syncAwardedPrices(request);

        assertThat(totalAmount).isEqualByComparingTo("412.50");
        assertThat(requestItem.getReferenceUnitPrice()).isEqualByComparingTo("123.60");
        assertThat(requestItem.getReferenceLineAmount()).isEqualByComparingTo("370.80");
        assertThat(requestItem.getUnitPrice()).isEqualByComparingTo("137.50");
        assertThat(requestItem.getLineAmount()).isEqualByComparingTo("412.50");
        assertThat(request.getReferenceTotalAmount()).isEqualByComparingTo("370.80");
        assertThat(request.getTotalAmount()).isEqualByComparingTo("412.50");
        assertThat(request.getPriceSource()).isEqualTo(ProcurementWorkflow.PRICE_SOURCE_SUPPLIER_QUOTE);
        verify(purchaseRequestItemMapper).updateById(requestItem);
        verify(purchaseRequestMapper).updateById(request);
    }

    @Test
    void syncAwardedPricesDoesNotWriteWhenAwardCoverageIsIncomplete() {
        PurchaseRequest request = request(new BigDecimal("370.80"));
        PurchaseRequestItem requestItem = requestItem(new BigDecimal("123.60"), new BigDecimal("370.80"));
        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(requestItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.syncAwardedPrices(request))
            .isInstanceOf(BizException.class)
            .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo("AWARD_PRICE_INCOMPLETE"));
        verify(purchaseRequestItemMapper, never()).updateById(any(PurchaseRequestItem.class));
        verify(purchaseRequestMapper, never()).updateById(any(PurchaseRequest.class));
    }

    private PurchaseRequest request(BigDecimal totalAmount) {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(1L);
        request.setTotalAmount(totalAmount);
        return request;
    }

    private PurchaseRequestItem requestItem(BigDecimal unitPrice, BigDecimal lineAmount) {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(10L);
        item.setPurchaseRequestId(1L);
        item.setLineNo(1);
        item.setQty(new BigDecimal("3"));
        item.setUnitPrice(unitPrice);
        item.setLineAmount(lineAmount);
        item.setStatus("ACTIVE");
        return item;
    }

    private SupplierQuoteItem awardedItem(BigDecimal unitPrice, BigDecimal lineAmount) {
        SupplierQuoteItem item = new SupplierQuoteItem();
        item.setId(20L);
        item.setPurchaseRequestId(1L);
        item.setPurchaseRequestItemId(10L);
        item.setUnitPrice(unitPrice);
        item.setLineAmount(lineAmount);
        item.setStatus("ACTIVE");
        item.setAwarded(1);
        return item;
    }
}
