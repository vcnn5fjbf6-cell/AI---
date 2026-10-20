package com.company.procurement.modules.supplier.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationCreateDTO;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationPageQueryDTO;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationReviewDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.vo.TemporarySupplierRecommendationVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface TemporarySupplierRecommendationService {

    String SUPPLIER_TYPE_TEMP_RECOMMENDED = "TEMP_RECOMMENDED";
    String SUPPLIER_ONBOARDING_TEMP_RECOMMENDED = "TEMP_RECOMMENDED";
    String QUOTE_SOURCE_NORMAL = "NORMAL";
    String QUOTE_SOURCE_TEMP_RECOMMENDED = "TEMP_RECOMMENDED";

    String STATUS_PENDING_ROOM_MANAGER = "PENDING_ROOM_MANAGER";
    String STATUS_PENDING_REGION_MANAGER = "PENDING_REGION_MANAGER";
    String STATUS_APPROVED = "APPROVED";
    String STATUS_REJECTED = "REJECTED";
    String STATUS_CANCELLED = "CANCELLED";

    PageResult<TemporarySupplierRecommendationVO> page(TemporarySupplierRecommendationPageQueryDTO queryDTO);

    TemporarySupplierRecommendationVO detail(Long id);

    Long create(TemporarySupplierRecommendationCreateDTO dto);

    TemporarySupplierRecommendationVO approve(Long id, TemporarySupplierRecommendationReviewDTO dto);

    TemporarySupplierRecommendationVO reject(Long id, TemporarySupplierRecommendationReviewDTO dto);

    boolean isTemporarySupplier(Long supplierId);

    boolean isTemporarySupplier(Supplier supplier);

    boolean hasActiveRecommendation(Long purchaseRequestId, Long supplierId);

    boolean hasAnyActiveRecommendation(Long supplierId);

    Set<Long> activeRecommendationRequestIds(Long supplierId);

    ScopedRecommendation resolveScopedRecommendation(Long purchaseRequestId, Long supplierId);

    List<PurchaseRequestItem> filterScopedItems(Long purchaseRequestId, Long supplierId, List<PurchaseRequestItem> items);

    List<Supplier> eligibleTemporarySuppliers(Long purchaseRequestId, LocalDateTime now);

    boolean isQuoteCountedForFastCountdown(SupplierQuote quote);

    boolean isQuoteAwardEligible(SupplierQuote quote);

    String quoteEligibilityText(SupplierQuote quote);

    record ScopedRecommendation(Long recommendationId, Set<Long> itemIds, String status) {
    }
}
