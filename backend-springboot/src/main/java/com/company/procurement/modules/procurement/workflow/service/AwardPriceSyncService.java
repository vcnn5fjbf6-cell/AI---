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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AwardPriceSyncService {

    private static final String QUOTE_ITEM_STATUS_ACTIVE = "ACTIVE";
    private static final String REPLACEMENT_REVIEW_APPROVED = "APPROVED";

    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public BigDecimal syncAwardedPrices(PurchaseRequest request) {
        AwardPricePlan plan = buildPlan(request, true);
        applyPlan(request, plan);
        return plan.totalAmount();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean syncAwardedPricesIfComplete(PurchaseRequest request) {
        AwardPricePlan plan = buildPlan(request, false);
        if (plan == null) {
            return false;
        }
        applyPlan(request, plan);
        return true;
    }

    private AwardPricePlan buildPlan(PurchaseRequest request, boolean required) {
        if (request == null || request.getId() == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        List<PurchaseRequestItem> requestItems = purchaseRequestItemMapper.selectList(
            new LambdaQueryWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
                .and(wrapper -> wrapper.ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
                    .or()
                    .isNull(PurchaseRequestItem::getStatus))
                .orderByAsc(PurchaseRequestItem::getLineNo)
        );
        List<SupplierQuoteItem> awardedItems = supplierQuoteItemMapper.selectList(
            new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
                .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
                .eq(SupplierQuoteItem::getAwarded, 1)
        );
        Map<Long, SupplierQuoteItem> awardedByRequestItemId = new LinkedHashMap<>();
        for (SupplierQuoteItem awardedItem : awardedItems) {
            Long requestItemId = awardedItem.getPurchaseRequestItemId();
            if (requestItemId == null || awardedByRequestItemId.putIfAbsent(requestItemId, awardedItem) != null) {
                throw new BizException("AWARD_PRICE_INVALID", "Each purchase item must have exactly one awarded supplier quote");
            }
        }
        if (requestItems.isEmpty() || requestItems.stream().anyMatch(item -> !awardedByRequestItemId.containsKey(item.getId()))) {
            if (!required) {
                return null;
            }
            throw new BizException("AWARD_PRICE_INCOMPLETE", "Every active purchase item must have an awarded supplier quote");
        }

        List<AwardedItemPrice> itemPrices = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseRequestItem requestItem : requestItems) {
            SupplierQuoteItem awardedItem = awardedByRequestItemId.get(requestItem.getId());
            BigDecimal unitPrice = effectiveUnitPrice(awardedItem);
            BigDecimal lineAmount = effectiveLineAmount(awardedItem, requestItem.getQty(), unitPrice);
            if (!positive(unitPrice) || !positive(lineAmount)) {
                throw new BizException("AWARD_PRICE_INVALID", "Awarded supplier quote price must be greater than zero");
            }
            itemPrices.add(new AwardedItemPrice(requestItem, unitPrice, lineAmount));
            totalAmount = totalAmount.add(lineAmount);
        }
        return new AwardPricePlan(itemPrices, totalAmount.setScale(2, RoundingMode.HALF_UP));
    }

    private void applyPlan(PurchaseRequest request, AwardPricePlan plan) {
        for (AwardedItemPrice itemPrice : plan.itemPrices()) {
            PurchaseRequestItem requestItem = itemPrice.requestItem();
            if (requestItem.getReferenceUnitPrice() == null) {
                requestItem.setReferenceUnitPrice(requestItem.getUnitPrice());
            }
            if (requestItem.getReferenceLineAmount() == null) {
                requestItem.setReferenceLineAmount(requestItem.getLineAmount());
            }
            requestItem.setUnitPrice(itemPrice.unitPrice());
            requestItem.setLineAmount(itemPrice.lineAmount());
            purchaseRequestItemMapper.updateById(requestItem);
        }
        if (request.getReferenceTotalAmount() == null) {
            request.setReferenceTotalAmount(request.getTotalAmount());
        }
        request.setTotalAmount(plan.totalAmount());
        request.setPriceSource(ProcurementWorkflow.PRICE_SOURCE_SUPPLIER_QUOTE);
        purchaseRequestMapper.updateById(request);
    }

    private BigDecimal effectiveUnitPrice(SupplierQuoteItem item) {
        if (item != null
            && Objects.equals(item.getReplacementReviewStatus(), REPLACEMENT_REVIEW_APPROVED)
            && item.getReplacementUnitPrice() != null) {
            return item.getReplacementUnitPrice();
        }
        return item == null ? null : item.getUnitPrice();
    }

    private BigDecimal effectiveLineAmount(SupplierQuoteItem item, BigDecimal requestQty, BigDecimal unitPrice) {
        BigDecimal lineAmount = item != null
            && Objects.equals(item.getReplacementReviewStatus(), REPLACEMENT_REVIEW_APPROVED)
            && item.getReplacementLineAmount() != null
            ? item.getReplacementLineAmount()
            : item == null ? null : item.getLineAmount();
        if (lineAmount == null && requestQty != null && unitPrice != null) {
            return requestQty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        }
        return lineAmount;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private record AwardedItemPrice(PurchaseRequestItem requestItem, BigDecimal unitPrice, BigDecimal lineAmount) {
    }

    private record AwardPricePlan(List<AwardedItemPrice> itemPrices, BigDecimal totalAmount) {
    }
}
