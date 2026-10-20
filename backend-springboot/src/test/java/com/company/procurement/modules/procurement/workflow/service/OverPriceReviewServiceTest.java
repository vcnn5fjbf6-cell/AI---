package com.company.procurement.modules.procurement.workflow.service;

import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverPriceReviewServiceTest {

    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock private SupplierQuoteMapper supplierQuoteMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private SysUserMapper userMapper;

    private OverPriceReviewService service;

    @BeforeEach
    void setUp() {
        service = new OverPriceReviewService(
            purchaseRequestMapper,
            purchaseRequestItemMapper,
            supplierQuoteItemMapper,
            supplierQuoteMapper,
            supplierMapper,
            userMapper
        );
    }

    @Test
    void awardedUnitPriceAboveThirteenPercentRequiresReview() {
        PurchaseRequest request = request();
        stubAward(request, new BigDecimal("114.00"));

        OverPriceReviewService.ItemReview review = service.requiredReviewItems(request).get(0);

        assertThat(review.reviewRequired()).isTrue();
        assertThat(review.ratePercent()).isEqualByComparingTo("14.00");
        assertThat(review.lineDifference()).isEqualByComparingTo("28.00");
        assertThat(review.supplierName()).isEqualTo("测试供应商");
        assertThat(review.applicantName()).isEqualTo("提交人甲");
        assertThat(review.sourceRequestNo()).isEqualTo("PTCG-100001");
    }

    @Test
    void awardedUnitPriceExactlyThirteenPercentDoesNotRequireReview() {
        PurchaseRequest request = request();
        stubAward(request, new BigDecimal("113.00"));

        assertThat(service.requiredReviewItems(request)).isEmpty();
        assertThat(service.reviewItems(request).get(0).ratePercent()).isEqualByComparingTo("13.00");
    }

    private PurchaseRequest request() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(1L);
        request.setReqNo("PTCG-100001");
        request.setReqTitle("标识牌采购");
        request.setApplicantUserId(9L);
        request.setDeptName("M6机房");
        return request;
    }

    private void stubAward(PurchaseRequest request, BigDecimal awardedUnitPrice) {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(11L);
        item.setPurchaseRequestId(request.getId());
        item.setReferenceUnitPrice(new BigDecimal("100.00"));
        item.setReferenceLineAmount(new BigDecimal("200.00"));
        item.setQty(new BigDecimal("2"));
        item.setStatus("ACTIVE");

        SupplierQuoteItem awarded = new SupplierQuoteItem();
        awarded.setId(12L);
        awarded.setPurchaseRequestId(request.getId());
        awarded.setPurchaseRequestItemId(item.getId());
        awarded.setQuoteId(21L);
        awarded.setUnitPrice(awardedUnitPrice);
        awarded.setLineAmount(new BigDecimal("999.00"));
        awarded.setAwarded(1);
        awarded.setStatus("ACTIVE");

        SupplierQuote quote = new SupplierQuote();
        quote.setId(21L);
        quote.setSupplierId(31L);
        Supplier supplier = new Supplier();
        supplier.setId(31L);
        supplier.setSupplierName("测试供应商");
        SysUser applicant = new SysUser();
        applicant.setId(9L);
        applicant.setRealName("提交人甲");

        when(purchaseRequestItemMapper.selectList(any())).thenReturn(List.of(item));
        when(supplierQuoteItemMapper.selectList(any())).thenReturn(List.of(awarded));
        when(supplierQuoteMapper.selectBatchIds(any())).thenReturn(List.of(quote));
        when(supplierMapper.selectBatchIds(any())).thenReturn(List.of(supplier));
        when(userMapper.selectById(9L)).thenReturn(applicant);
    }
}
