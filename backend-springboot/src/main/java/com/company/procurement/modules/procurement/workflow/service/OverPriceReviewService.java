package com.company.procurement.modules.procurement.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OverPriceReviewService {

    private static final String QUOTE_ITEM_STATUS_ACTIVE = "ACTIVE";
    private static final String REPLACEMENT_REVIEW_APPROVED = "APPROVED";

    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserMapper userMapper;

    public List<ItemReview> reviewItems(PurchaseRequest request) {
        if (request == null || request.getId() == null) {
            return List.of();
        }
        List<PurchaseRequestItem> requestItems = purchaseRequestItemMapper.selectList(
            new LambdaQueryWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
                .and(wrapper -> wrapper.ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
                    .or()
                    .isNull(PurchaseRequestItem::getStatus))
                .orderByAsc(PurchaseRequestItem::getLineNo)
        );
        if (requestItems.isEmpty()) {
            return List.of();
        }

        List<SupplierQuoteItem> awardedItems = supplierQuoteItemMapper.selectList(
            new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
                .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
                .eq(SupplierQuoteItem::getAwarded, 1)
        );
        Map<Long, SupplierQuoteItem> awardedByRequestItemId = awardedItems.stream()
            .filter(item -> item.getPurchaseRequestItemId() != null)
            .collect(Collectors.toMap(
                SupplierQuoteItem::getPurchaseRequestItemId,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Set<Long> quoteIds = awardedItems.stream()
            .map(SupplierQuoteItem::getQuoteId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, SupplierQuote> quotesById = quoteIds.isEmpty()
            ? Map.of()
            : supplierQuoteMapper.selectBatchIds(quoteIds).stream()
                .collect(Collectors.toMap(SupplierQuote::getId, quote -> quote,
                    (left, right) -> left, LinkedHashMap::new));
        Set<Long> supplierIds = quotesById.values().stream()
            .map(SupplierQuote::getSupplierId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, Supplier> suppliersById = supplierIds.isEmpty()
            ? Map.of()
            : supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, supplier -> supplier,
                    (left, right) -> left, LinkedHashMap::new));

        Map<Long, PurchaseRequest> sourceRequests = new LinkedHashMap<>();
        Map<Long, SysUser> applicants = new LinkedHashMap<>();
        List<ItemReview> reviews = new ArrayList<>();
        for (PurchaseRequestItem requestItem : requestItems) {
            SupplierQuoteItem awardedItem = awardedByRequestItemId.get(requestItem.getId());
            if (awardedItem == null) {
                continue;
            }
            BigDecimal referenceUnitPrice = requestItem.getReferenceUnitPrice();
            BigDecimal awardedUnitPrice = effectiveUnitPrice(awardedItem);
            if (!positive(referenceUnitPrice) || !positive(awardedUnitPrice)) {
                continue;
            }
            BigDecimal unitDifference = awardedUnitPrice.subtract(referenceUnitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ratePercent = unitDifference
                .multiply(new BigDecimal("100"))
                .divide(referenceUnitPrice, 2, RoundingMode.HALF_UP);
            BigDecimal awardedLineAmount = effectiveLineAmount(awardedItem, requestItem, awardedUnitPrice);
            BigDecimal referenceLineAmount = requestItem.getQty() == null
                ? requestItem.getReferenceLineAmount()
                : requestItem.getQty().multiply(referenceUnitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineDifference = awardedLineAmount != null && referenceLineAmount != null
                ? awardedLineAmount.subtract(referenceLineAmount).setScale(2, RoundingMode.HALF_UP)
                : null;
            boolean reviewRequired = ratePercent.compareTo(ProcurementWorkflow.OVER_PRICE_REVIEW_THRESHOLD_PERCENT) > 0;

            SupplierQuote quote = quotesById.get(awardedItem.getQuoteId());
            Supplier supplier = quote == null ? null : suppliersById.get(quote.getSupplierId());
            PurchaseRequest sourceRequest = sourceRequest(request, requestItem, sourceRequests);
            SysUser applicant = applicant(sourceRequest, applicants);
            reviews.add(new ItemReview(
                requestItem.getId(),
                awardedItem.getId(),
                supplier == null ? null : supplier.getSupplierName(),
                referenceUnitPrice,
                awardedUnitPrice,
                unitDifference,
                referenceLineAmount,
                awardedLineAmount,
                lineDifference,
                ratePercent,
                reviewRequired,
                sourceRequest == null ? null : sourceRequest.getId(),
                sourceRequest == null ? null : sourceRequest.getReqNo(),
                applicantDisplayName(applicant),
                sourceRequest == null ? null : sourceRequest.getDeptName()
            ));
        }
        return reviews;
    }

    public Map<Long, ItemReview> reviewByRequestItemId(PurchaseRequest request) {
        return reviewItems(request).stream().collect(Collectors.toMap(
            ItemReview::requestItemId,
            item -> item,
            (left, right) -> left,
            LinkedHashMap::new
        ));
    }

    public List<ItemReview> requiredReviewItems(PurchaseRequest request) {
        return reviewItems(request).stream().filter(ItemReview::reviewRequired).toList();
    }

    public boolean requiresReview(PurchaseRequest request) {
        return reviewItems(request).stream().anyMatch(ItemReview::reviewRequired);
    }

    public Set<Long> requiredReviewItemIds(PurchaseRequest request) {
        return requiredReviewItems(request).stream()
            .map(ItemReview::requestItemId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private PurchaseRequest sourceRequest(PurchaseRequest request,
                                          PurchaseRequestItem item,
                                          Map<Long, PurchaseRequest> cache) {
        Long sourceRequestId = item.getSourceRequestId() == null ? request.getId() : item.getSourceRequestId();
        if (Objects.equals(sourceRequestId, request.getId())) {
            return request;
        }
        return cache.computeIfAbsent(sourceRequestId, purchaseRequestMapper::selectById);
    }

    private SysUser applicant(PurchaseRequest request, Map<Long, SysUser> cache) {
        if (request == null || request.getApplicantUserId() == null) {
            return null;
        }
        return cache.computeIfAbsent(request.getApplicantUserId(), userMapper::selectById);
    }

    private String applicantDisplayName(SysUser applicant) {
        if (applicant == null) {
            return null;
        }
        return StringUtils.hasText(applicant.getRealName()) ? applicant.getRealName().trim() : applicant.getUsername();
    }

    private BigDecimal effectiveUnitPrice(SupplierQuoteItem item) {
        if (item != null
            && Objects.equals(item.getReplacementReviewStatus(), REPLACEMENT_REVIEW_APPROVED)
            && item.getReplacementUnitPrice() != null) {
            return item.getReplacementUnitPrice();
        }
        return item == null ? null : item.getUnitPrice();
    }

    private BigDecimal effectiveLineAmount(SupplierQuoteItem item,
                                           PurchaseRequestItem requestItem,
                                           BigDecimal awardedUnitPrice) {
        if (requestItem.getQty() != null && awardedUnitPrice != null) {
            return requestItem.getQty().multiply(awardedUnitPrice).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal lineAmount = item != null
            && Objects.equals(item.getReplacementReviewStatus(), REPLACEMENT_REVIEW_APPROVED)
            && item.getReplacementLineAmount() != null
            ? item.getReplacementLineAmount()
            : item == null ? null : item.getLineAmount();
        return lineAmount;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public record ItemReview(
        Long requestItemId,
        Long awardedQuoteItemId,
        String supplierName,
        BigDecimal referenceUnitPrice,
        BigDecimal awardedUnitPrice,
        BigDecimal unitDifference,
        BigDecimal referenceLineAmount,
        BigDecimal awardedLineAmount,
        BigDecimal lineDifference,
        BigDecimal ratePercent,
        boolean reviewRequired,
        Long sourceRequestId,
        String sourceRequestNo,
        String applicantName,
        String applicantDeptName
    ) {
    }
}
