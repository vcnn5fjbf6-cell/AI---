package com.company.procurement.modules.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogService;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogService.AssetCatalogEntry;
import com.company.procurement.modules.procurement.asset.service.QuoteAssetClassificationService;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestItemVO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.supplier.dto.SupplierAwardConfirmDTO;
import com.company.procurement.modules.supplier.dto.SupplierAwardWaiveDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteItemSubmitDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuotePageQueryDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteSubmitDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierAwardNotificationService;
import com.company.procurement.modules.supplier.service.SupplierProductLinkService;
import com.company.procurement.modules.supplier.service.SupplierQuoteService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.supplier.support.SupplierQuoteAccess;
import com.company.procurement.modules.supplier.vo.SupplierQuoteItemVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteRequestVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierQuoteServiceImpl implements SupplierQuoteService {

    private static final String QUOTE_ITEM_STATUS_ACTIVE = "ACTIVE";
    private static final String QUOTE_ITEM_STATUS_WAIVED = "WAIVED";
    private static final String REPLACEMENT_REVIEW_PENDING = "PENDING";
    private static final String NATIONAL_DEMAND_AGGREGATE_REASON = "需求池全国月度汇总生成";
    private static final int FAST_QUOTE_COUNTDOWN_HOURS = 3;
    private static final int AWARD_WAIVE_WINDOW_MONTHS = 6;
    private static final int AWARD_WAIVE_SUSPEND_THRESHOLD = 3;
    private static final int AWARD_WAIVE_SUSPEND_MONTHS = 3;
    private static final String RECEIPT_STATUS_PENDING = "PENDING";
    private static final String ASSET_REVIEW_PENDING = "PENDING";
    private static final String URGENT_DIRECT_AWARD_THRESHOLD_KEY = "urgent_direct_award_threshold";
    private static final BigDecimal DEFAULT_URGENT_DIRECT_AWARD_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal URGENT_DIRECT_AWARD_QUOTE_LIMIT_MULTIPLIER = new BigDecimal("1.30");

    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final PurchaseRequestService purchaseRequestService;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final SupplierMapper supplierMapper;
    private final ProcurementApprovalRouteResolver approvalRouteResolver;
    private final ProcurementSettingMapper procurementSettingMapper;
    private final SupplierAwardNotificationService awardNotificationService;
    private final TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    private final AwardPriceSyncService awardPriceSyncService;
    private final AssetCatalogService assetCatalogService;
    private final QuoteAssetClassificationService quoteAssetClassificationService;
    private final SupplierProductLinkService supplierProductLinkService;

    @Override
    public PageResult<SupplierQuoteRequestVO> pageQuoteRequests(SupplierQuotePageQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureQuoteModuleAccess(authUser);

        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<>();
        applyQuoteVisibilityFilter(wrapper);
        wrapper.orderByDesc(PurchaseRequest::getUpdatedAt);
        if (StringUtils.hasText(queryDTO.getReqNo())) {
            wrapper.like(PurchaseRequest::getReqNo, queryDTO.getReqNo());
        }
        if (StringUtils.hasText(queryDTO.getReqTitle())) {
            wrapper.like(PurchaseRequest::getReqTitle, queryDTO.getReqTitle());
        }
        if (DataScopeHelper.isSupplierUser(authUser) && !SupplierQuoteAccess.canViewAllSubmissions(authUser)) {
            if (authUser.getSupplierId() == null) {
                wrapper.eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_QUOTING);
            } else if (temporarySupplierRecommendationService.isTemporarySupplier(authUser.getSupplierId())) {
                Set<Long> recommendedRequestIds = temporarySupplierRecommendationService.activeRecommendationRequestIds(authUser.getSupplierId());
                wrapper.and(scope -> {
                    if (!recommendedRequestIds.isEmpty()) {
                        scope.in(PurchaseRequest::getId, recommendedRequestIds);
                    } else {
                        scope.eq(PurchaseRequest::getId, -1L);
                    }
                    scope.or().inSql(PurchaseRequest::getId,
                        "SELECT purchase_request_id FROM supplier_quote WHERE supplier_id = "
                            + authUser.getSupplierId()
                            + " AND is_deleted = 0");
                });
            } else {
                wrapper.and(scope -> scope
                    .and(open -> open
                        .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_QUOTING)
                        .and(access -> access
                            .isNull(PurchaseRequest::getDirectAwardApplied)
                            .or()
                            .eq(PurchaseRequest::getDirectAwardApplied, 0)
                            .or()
                            .eq(PurchaseRequest::getDirectAwardSupplierId, authUser.getSupplierId())))
                    .or()
                    .inSql(PurchaseRequest::getId,
                        "SELECT purchase_request_id FROM supplier_quote WHERE supplier_id = "
                            + authUser.getSupplierId()
                            + " AND is_deleted = 0"));
                applySupplierRegionScope(wrapper, authUser);
            }
        }

        if (!DataScopeHelper.isSupplierUser(authUser) && !DataScopeHelper.isSystemAdmin(authUser)) {
            wrapper.last("LIMIT 1000");
            List<PurchaseRequest> visibleRequests = purchaseRequestMapper.selectList(wrapper).stream()
                .filter(request -> canInternalViewQuoteRequest(authUser, request))
                .toList();
            long total = visibleRequests.size();
            long pageNo = Math.max(1, queryDTO.getPageNo());
            long pageSize = Math.max(1, queryDTO.getPageSize());
            int fromIndex = (int) Math.min((pageNo - 1) * pageSize, total);
            int toIndex = (int) Math.min(fromIndex + pageSize, total);
            List<SupplierQuoteRequestVO> records = visibleRequests.subList(fromIndex, toIndex).stream()
                .map(request -> toQuoteRequestVO(request, authUser))
                .toList();
            return PageResult.of(pageNo, pageSize, total, records);
        }

        Page<PurchaseRequest> page = purchaseRequestMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<SupplierQuoteRequestVO> records = page.getRecords().stream()
            .map(request -> toQuoteRequestVO(request, authUser))
            .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public PurchaseRequestVO quoteRequestDetail(Long purchaseRequestId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureQuoteModuleAccess(authUser);

        PurchaseRequest request = requireQuotingRequest(purchaseRequestId);
        if (!canViewQuoteRequest(authUser, request)) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Current request is not open for quotation");
        }
        if (!SupplierQuoteAccess.canViewAllSubmissions(authUser)) {
            ensureSupplierCanAccessRequest(authUser, request);
        }
        PurchaseRequestVO detail = purchaseRequestService.detail(purchaseRequestId);
        hideRejectedItemsForQuote(detail);
        filterTemporarySupplierScopedItems(detail, request.getId(), authUser);
        detail.setQuoteCountdownStartedAt(request.getQuoteCountdownStartedAt());
        detail.setQuoteCountdownDueAt(effectiveQuoteDeadline(request));
        if (DataScopeHelper.isSupplierUser(authUser) && !SupplierQuoteAccess.canViewAllSubmissions(authUser)) {
            maskRequestPricing(detail);
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierQuoteVO submitQuote(SupplierQuoteSubmitDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureSupplierAccount(authUser);
        if (authUser.getSupplierId() == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "Current supplier account is not bound to a supplier master record");
        }
        LocalDateTime now = LocalDateTime.now();
        ensureSupplierCanBid(authUser.getSupplierId(), now);
        Supplier currentSupplier = supplierMapper.selectById(authUser.getSupplierId());
        if (currentSupplier == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "Current supplier account is not bound to a supplier master record");
        }
        if (CollectionUtils.isEmpty(dto.getItems())) {
            throw new BizException("QUOTE_ITEMS_EMPTY", "Quote items must not be empty");
        }

        PurchaseRequest request = requireQuotingRequest(dto.getPurchaseRequestId());
        ensureSupplierCanAccessRequest(authUser, request);
        if (!ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())) {
            throw new BizException("QUOTE_LOCKED", "The request has entered quote review and can no longer be modified");
        }
        ensureQuoteSubmissionOpen(request, now);
        TemporarySupplierRecommendationService.ScopedRecommendation scopedRecommendation =
            temporarySupplierRecommendationService.resolveScopedRecommendation(request.getId(), authUser.getSupplierId());
        List<PurchaseRequestItem> requestItems = temporarySupplierRecommendationService.filterScopedItems(
            request.getId(),
            authUser.getSupplierId(),
            loadRequestItems(request.getId())
        );
        if (requestItems.isEmpty()) {
            throw new BizException("PURCHASE_REQUEST_ITEMS_EMPTY", "Current request has no items to quote");
        }
        Set<Long> requestItemIds = requestItems.stream()
            .map(PurchaseRequestItem::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

        Map<Long, SupplierQuoteItemSubmitDTO> itemDTOMap = new LinkedHashMap<>();
        for (SupplierQuoteItemSubmitDTO itemDTO : dto.getItems()) {
            if (!requestItemIds.contains(itemDTO.getPurchaseRequestItemId())) {
                throw new BizException("QUOTE_ITEM_NOT_IN_REQUEST", "Quote item is not in current purchase request");
            }
            if (itemDTOMap.put(itemDTO.getPurchaseRequestItemId(), itemDTO) != null) {
                throw new BizException("QUOTE_ITEM_DUPLICATED", "Duplicated quote item: " + itemDTO.getPurchaseRequestItemId());
            }
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        int maxLeadTimeDays = 0;
        for (PurchaseRequestItem requestItem : requestItems) {
            SupplierQuoteItemSubmitDTO itemDTO = itemDTOMap.get(requestItem.getId());
            if (itemDTO == null) {
                throw new BizException("QUOTE_ITEM_MISSING", "Every purchase request item must be quoted");
            }
            if (isWaived(itemDTO)) {
                continue;
            }
            if (itemDTO.getUnitPrice() == null) {
                throw new BizException("QUOTE_ITEM_PRICE_INVALID", "Quote unit price must be greater than 0");
            }
            BigDecimal unitPrice = itemDTO.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("QUOTE_ITEM_PRICE_INVALID", "Quote unit price must be greater than 0");
            }
            if (itemDTO.getLeadTimeDays() == null || itemDTO.getLeadTimeDays() <= 0) {
                throw new BizException("QUOTE_ITEM_LEAD_TIME_INVALID", "Quote lead time must be greater than 0");
            }
            validateCondition(itemDTO.getOriginalCondition(), itemDTO.getOriginalSecondHandRemark(), "原商品");
            if (isReplacement(itemDTO)) {
                validateAndNormalizeReplacement(
                    itemDTO,
                    currentSupplier,
                    requestItem.getLineNo() == null ? 1 : requestItem.getLineNo()
                );
            }
            BigDecimal qty = requestItem.getQty() == null ? BigDecimal.ZERO : requestItem.getQty();
            BigDecimal lineAmount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineAmount);
            maxLeadTimeDays = Math.max(maxLeadTimeDays, itemDTO.getLeadTimeDays());
        }

        SupplierQuote quote = supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, dto.getPurchaseRequestId())
            .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())
            .last("LIMIT 1"));
        if (quote == null) {
            quote = new SupplierQuote();
            quote.setQuoteNo(generateQuoteNo());
            quote.setPurchaseRequestId(dto.getPurchaseRequestId());
            quote.setSupplierId(authUser.getSupplierId());
            quote.setSupplierUserId(authUser.getUserId());
        }

        quote.setQuoteSource(scopedRecommendation == null
            ? TemporarySupplierRecommendationService.QUOTE_SOURCE_NORMAL
            : TemporarySupplierRecommendationService.QUOTE_SOURCE_TEMP_RECOMMENDED);
        quote.setRecommendationId(scopedRecommendation == null ? null : scopedRecommendation.recommendationId());
        quote.setTotalAmount(totalAmount);
        quote.setLeadTimeDays(maxLeadTimeDays);
        quote.setQuoteRemark(dto.getQuoteRemark());
        quote.setStatus(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_UNSELECTED);
        quote.setSupplierConfirmRemark(null);
        quote.setSupplierConfirmedBy(null);
        quote.setSupplierConfirmedAt(null);
        quote.setSupplierConfirmDueAt(null);
        quote.setSupplierConfirmNotifiedAt(null);
        quote.setSubmittedAt(now);

        if (quote.getId() == null) {
            supplierQuoteMapper.insert(quote);
        } else {
            supplierQuoteMapper.updateById(quote);
            supplierQuoteItemMapper.delete(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getQuoteId, quote.getId()));
        }

        saveQuoteItems(quote, request, requestItems, itemDTOMap);
        downgradeDirectAwardIfQuoteOverLimit(request, quote, totalAmount);
        startFastQuoteCountdownIfReady(request, quote.getSubmittedAt());

        return toQuoteVO(quote, currentSupplier);
    }

    @Override
    public SupplierQuoteVO myQuote(Long purchaseRequestId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureSupplierAccount(authUser);
        if (authUser.getSupplierId() == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "Current supplier account is not bound to a supplier master record");
        }

        SupplierQuote quote = supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())
            .last("LIMIT 1"));
        if (quote == null) {
            return null;
        }

        Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
        return toQuoteVO(quote, supplier);
    }

    @Override
    public SupplierQuoteVO awardConfirmationDetail(Long purchaseRequestId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureSupplierAccount(authUser);
        if (authUser.getSupplierId() == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "Current supplier account is not bound to a supplier master record");
        }

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        SupplierQuote quote = supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())
            .last("LIMIT 1"));
        if (quote == null) {
            throw new BizException("SUPPLIER_QUOTE_NOT_FOUND", "Current supplier has not submitted a quote for this request");
        }
        if (!ProcurementWorkflow.LEADER_CONFIRM_STATUS_CONFIRMED.equals(request.getLeaderConfirmStatus())) {
            throw new BizException("LEADER_CONFIRM_REQUIRED", "Leadership has not confirmed a winning supplier yet");
        }
        if (!isQuoteAwarded(request, quote.getId())) {
            throw new BizException("SUPPLIER_AWARD_NOT_SELECTED", "Current supplier is not the selected supplier for this request");
        }

        Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
        return toQuoteVO(quote, supplier, request, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierQuoteVO confirmAward(Long purchaseRequestId, SupplierAwardConfirmDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureSupplierAccount(authUser);
        if (authUser.getSupplierId() == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "Current supplier account is not bound to a supplier master record");
        }

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        SupplierQuote quote = supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())
            .last("LIMIT 1"));
        if (quote == null) {
            throw new BizException("SUPPLIER_QUOTE_NOT_FOUND", "Current supplier has not submitted a quote for this request");
        }
        if (!ProcurementWorkflow.LEADER_CONFIRM_STATUS_CONFIRMED.equals(request.getLeaderConfirmStatus())) {
            throw new BizException("LEADER_CONFIRM_REQUIRED", "Leadership has not confirmed a winning supplier yet");
        }
        if (!isQuoteAwarded(request, quote.getId())) {
            throw new BizException("SUPPLIER_AWARD_NOT_SELECTED", "Current supplier is not the selected supplier for this request");
        }
        if (!ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING.equals(quote.getSupplierConfirmStatus())) {
            throw new BizException("SUPPLIER_AWARD_CONFIRM_STATUS_INVALID", "Only pending awards can be confirmed");
        }
        ensureSupplierCanBid(authUser.getSupplierId(), LocalDateTime.now());
        if (quote.getSupplierConfirmDueAt() != null && LocalDateTime.now().isAfter(quote.getSupplierConfirmDueAt())) {
            throw new BizException("SUPPLIER_AWARD_CONFIRM_EXPIRED", "中标确认已超过期限，系统将自动递补下一名供应商");
        }

        LocalDateTime confirmedAt = LocalDateTime.now();
        quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED);
        quote.setSupplierConfirmRemark(dto == null ? null : dto.getConfirmRemark());
        quote.setSupplierConfirmedBy(authUser.getUserId());
        quote.setSupplierConfirmedAt(confirmedAt);
        supplierQuoteMapper.updateById(quote);
        initializeReceiptForAwardedItems(quote);
        awardPriceSyncService.syncAwardedPrices(request);

        Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
        return toQuoteVO(quote, supplier, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierQuoteVO waiveAward(Long purchaseRequestId, SupplierAwardWaiveDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureSupplierAccount(authUser);
        if (authUser.getSupplierId() == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "Current supplier account is not bound to a supplier master record");
        }

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        SupplierQuote quote = supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())
            .last("LIMIT 1"));
        if (quote == null) {
            throw new BizException("SUPPLIER_QUOTE_NOT_FOUND", "Current supplier has not submitted a quote for this request");
        }
        if (!ProcurementWorkflow.LEADER_CONFIRM_STATUS_CONFIRMED.equals(request.getLeaderConfirmStatus())) {
            throw new BizException("LEADER_CONFIRM_REQUIRED", "Leadership has not confirmed a winning supplier yet");
        }
        if (!isQuoteAwarded(request, quote.getId())) {
            throw new BizException("SUPPLIER_AWARD_NOT_SELECTED", "Current supplier is not the selected supplier for this request");
        }
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(quote.getSupplierConfirmStatus())) {
            throw new BizException("SUPPLIER_ALREADY_CONFIRMED", "Awarded supplier has already confirmed and cannot be waived");
        }
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED.equals(quote.getSupplierConfirmStatus())) {
            throw new BizException("SUPPLIER_AWARD_ALREADY_WAIVED", "Awarded supplier has already waived this award");
        }
        if (!ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING.equals(quote.getSupplierConfirmStatus())) {
            throw new BizException("SUPPLIER_AWARD_CONFIRM_STATUS_INVALID", "Only pending awards can be waived");
        }

        LocalDateTime now = LocalDateTime.now();
        waiveAwardConfirmationAndReassign(
            quote,
            now,
            authUser.getUserId(),
            appendRemark("供应商主动放弃中标", dto == null ? null : dto.getWaiveRemark()),
            "中标供应商主动放弃中标"
        );

        Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
        return toQuoteVO(quote, supplier, purchaseRequestMapper.selectById(purchaseRequestId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reassignPendingAwardsForSupplier(Long supplierId, String reason) {
        if (supplierId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<SupplierQuote> pendingQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getSupplierId, supplierId)
            .eq(SupplierQuote::getSupplierConfirmStatus, ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING)
            .orderByAsc(SupplierQuote::getSupplierConfirmDueAt)
            .orderByAsc(SupplierQuote::getId));
        for (SupplierQuote quote : pendingQuotes) {
            waiveAwardConfirmationAndReassign(
                quote,
                now,
                null,
                reason,
                reason
            );
        }
    }

    @Override
    public ExportFile exportAward(Long purchaseRequestId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureSupplierAccount(authUser);
        if (authUser.getSupplierId() == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "Current supplier account is not bound to a supplier master record");
        }

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        SupplierQuote quote = supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())
            .last("LIMIT 1"));
        if (quote == null) {
            throw new BizException("SUPPLIER_QUOTE_NOT_FOUND", "Current supplier has not submitted a quote for this request");
        }
        if (!isQuoteAwarded(request, quote.getId())) {
            throw new BizException("SUPPLIER_AWARD_NOT_SELECTED", "Current supplier is not the selected supplier for this request");
        }
        if (!ProcurementWorkflow.LEADER_CONFIRM_STATUS_CONFIRMED.equals(request.getLeaderConfirmStatus())) {
            throw new BizException("QUOTE_REVIEW_REQUIRED", "Quote review has not been approved yet");
        }
        if (!ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(quote.getSupplierConfirmStatus())) {
            throw new BizException("SUPPLIER_AWARD_CONFIRM_REQUIRED", "Awarded supplier must confirm before exporting the award list");
        }

        List<List<String>> rows = buildAwardExportRows(request, quote);
        String fileName = sanitizeFileName("中标清单-" + request.getReqNo() + ".xlsx");
        return new ExportFile(fileName, ExcelExportUtils.XLSX_CONTENT_TYPE, ExcelExportUtils.toXlsxBytes("中标清单", rows));
    }

    private PurchaseRequest requireQuotingRequest(Long purchaseRequestId) {
        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!isQuoteVisibleRequest(request)) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Current request is not open for quotation");
        }
        return request;
    }

    private void applyQuoteVisibilityFilter(LambdaQueryWrapper<PurchaseRequest> wrapper) {
        wrapper.and(status -> status
            .in(PurchaseRequest::getStatus, quoteBaseVisibleStatuses())
            .or(review -> review
                .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_PENDING_APPROVAL)
                .and(this::applyQuoteReviewStartedFilter)));
    }

    private void applyQuoteReviewStartedFilter(LambdaQueryWrapper<PurchaseRequest> wrapper) {
        wrapper.isNotNull(PurchaseRequest::getSelectedQuoteId)
            .or()
            .exists("""
                SELECT 1 FROM supplier_quote_item sqi
                WHERE sqi.purchase_request_id = purchase_request.id
                  AND sqi.awarded = 1
                  AND sqi.is_deleted = 0
                """);
    }

    private void applySupplierRegionScope(LambdaQueryWrapper<PurchaseRequest> wrapper, AuthUser authUser) {
        if (authUser.getRegionId() == null) {
            return;
        }
        wrapper.and(scope -> scope
            .eq(PurchaseRequest::getRegionId, authUser.getRegionId())
            .or()
            .eq(PurchaseRequest::getReqReason, NATIONAL_DEMAND_AGGREGATE_REASON)
            .or()
            .eq(authUser.getSupplierId() != null, PurchaseRequest::getDirectAwardSupplierId, authUser.getSupplierId()));
    }

    private boolean isQuoteVisibleRequest(PurchaseRequest request) {
        if (quoteBaseVisibleStatuses().contains(request.getStatus())) {
            return true;
        }
        return ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())
            && isQuoteReviewStarted(request);
    }

    private boolean canViewQuoteRequest(AuthUser authUser, PurchaseRequest request) {
        if (!isQuoteVisibleRequest(request)) {
            return false;
        }
        if (SupplierQuoteAccess.canViewAllSubmissions(authUser)) {
            return true;
        }
        if (!DataScopeHelper.isSupplierUser(authUser)) {
            return canInternalViewQuoteRequest(authUser, request);
        }
        if (ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())) {
            if (isDirectAwardRequest(request)) {
                return Objects.equals(authUser.getSupplierId(), request.getDirectAwardSupplierId());
            }
            return true;
        }
        return authUser.getSupplierId() != null
            && supplierQuoteMapper.selectCount(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getPurchaseRequestId, request.getId())
                .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())) > 0;
    }

    private boolean canInternalViewQuoteRequest(AuthUser authUser, PurchaseRequest request) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (request == null || authUser == null) {
            return false;
        }
        if (Objects.equals(authUser.getUserId(), request.getApplicantUserId())
            || Objects.equals(authUser.getUserId(), request.getPurchaserUserId())) {
            return true;
        }
        return approvalRouteResolver.canAccessRelatedApproval(request, authUser);
    }

    private boolean isQuoteReviewStarted(PurchaseRequest request) {
        if (request.getSelectedQuoteId() != null) {
            return true;
        }
        return supplierQuoteItemMapper.selectCount(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
            .eq(SupplierQuoteItem::getAwarded, 1)) > 0;
    }

    private List<String> quoteBaseVisibleStatuses() {
        return List.of(
            ProcurementWorkflow.STATUS_QUOTING,
            ProcurementWorkflow.STATUS_APPROVED
        );
    }

    private List<PurchaseRequestItem> loadRequestItems(Long purchaseRequestId) {
        return purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, purchaseRequestId)
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .orderByAsc(PurchaseRequestItem::getLineNo));
    }

    private void hideRejectedItemsForQuote(PurchaseRequestVO detail) {
        if (detail == null || CollectionUtils.isEmpty(detail.getItems())) {
            return;
        }
        List<PurchaseRequestItemVO> activeItems = detail.getItems().stream()
            .filter(item -> !ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(item.getStatus()))
            .toList();
        detail.setItems(activeItems);
        BigDecimal activeAmount = activeItems.stream()
            .map(PurchaseRequestItemVO::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        detail.setTotalAmount(activeAmount);
    }

    private void filterTemporarySupplierScopedItems(PurchaseRequestVO detail, Long purchaseRequestId, AuthUser authUser) {
        if (detail == null
            || !DataScopeHelper.isSupplierUser(authUser)
            || SupplierQuoteAccess.canViewAllSubmissions(authUser)
            || authUser.getSupplierId() == null
            || !temporarySupplierRecommendationService.isTemporarySupplier(authUser.getSupplierId())
            || CollectionUtils.isEmpty(detail.getItems())) {
            return;
        }
        TemporarySupplierRecommendationService.ScopedRecommendation scope =
            temporarySupplierRecommendationService.resolveScopedRecommendation(purchaseRequestId, authUser.getSupplierId());
        if (scope == null || scope.itemIds().isEmpty()) {
            detail.setItems(List.of());
            detail.setTotalAmount(BigDecimal.ZERO);
            return;
        }
        List<PurchaseRequestItemVO> scopedItems = detail.getItems().stream()
            .filter(item -> scope.itemIds().contains(item.getId()))
            .toList();
        detail.setItems(scopedItems);
        BigDecimal scopedAmount = scopedItems.stream()
            .map(PurchaseRequestItemVO::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        detail.setTotalAmount(scopedAmount);
    }

    private void saveQuoteItems(
        SupplierQuote quote,
        PurchaseRequest request,
        List<PurchaseRequestItem> requestItems,
        Map<Long, SupplierQuoteItemSubmitDTO> itemDTOMap
    ) {
        String receivingCity = resolveReceivingCity(request);
        String deliveryAddress = resolveDeliveryAddress(request);
        for (PurchaseRequestItem requestItem : requestItems) {
            SupplierQuoteItemSubmitDTO itemDTO = itemDTOMap.get(requestItem.getId());
            boolean waived = isWaived(itemDTO);
            BigDecimal unitPrice = waived ? BigDecimal.ZERO : itemDTO.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal qty = requestItem.getQty() == null ? BigDecimal.ZERO : requestItem.getQty();
            SupplierQuoteItem quoteItem = new SupplierQuoteItem();
            quoteItem.setQuoteId(quote.getId());
            quoteItem.setPurchaseRequestId(quote.getPurchaseRequestId());
            quoteItem.setPurchaseRequestItemId(requestItem.getId());
            quoteItem.setLineNo(requestItem.getLineNo());
            quoteItem.setItemCode(requestItem.getItemCode());
            quoteItem.setItemName(requestItem.getItemName());
            quoteItem.setSpecModel(requestItem.getSpecModel());
            quoteItem.setUnit(requestItem.getUnit());
            quoteItem.setQty(qty);
            quoteItem.setUnitPrice(unitPrice);
            quoteItem.setSupplierSubmittedUnitPrice(unitPrice);
            quoteItem.setLineAmount(waived ? BigDecimal.ZERO : qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
            quoteItem.setLeadTimeDays(waived ? 0 : itemDTO.getLeadTimeDays());
            quoteItem.setOriginalCondition(waived ? null : quoteAssetClassificationService.normalizeCondition(itemDTO.getOriginalCondition()));
            quoteItem.setOriginalSecondHandRemark(waived ? null : firstText(itemDTO.getOriginalSecondHandRemark()));
            quoteItem.setQuoteRemark(itemDTO.getQuoteRemark());
            quoteItem.setReceivingCity(firstText(requestItem.getReceivingCity(), receivingCity));
            quoteItem.setDeliveryAddress(firstText(requestItem.getDeliveryAddress(), deliveryAddress));
            boolean replacement = !waived && isReplacement(itemDTO);
            quoteItem.setReplacementFlag(replacement ? 1 : 0);
            if (replacement) {
                BigDecimal replacementUnitPrice = itemDTO.getReplacementUnitPrice().setScale(2, RoundingMode.HALF_UP);
                quoteItem.setReplacementItemName(itemDTO.getReplacementItemName().trim());
                quoteItem.setReplacementSpecModel(firstText(itemDTO.getReplacementSpecModel()));
                quoteItem.setReplacementEcommerceUrl(firstText(itemDTO.getReplacementEcommerceUrl()));
                quoteItem.setReplacementUnitPrice(replacementUnitPrice);
                quoteItem.setReplacementLineAmount(qty.multiply(replacementUnitPrice).setScale(2, RoundingMode.HALF_UP));
                quoteItem.setReplacementReason(firstText(itemDTO.getReplacementReason()));
                quoteItem.setReplacementCondition(quoteAssetClassificationService.normalizeCondition(itemDTO.getReplacementCondition()));
                quoteItem.setReplacementSecondHandRemark(firstText(itemDTO.getReplacementSecondHandRemark()));
                quoteItem.setReplacementReviewStatus(REPLACEMENT_REVIEW_PENDING);
            }
            quoteItem.setAssetReviewStatus(waived ? null : ASSET_REVIEW_PENDING);
            quoteItem.setConditionMismatch(0);
            if (!waived) {
                applyInitialAssetClassification(quoteItem);
            }
            quoteItem.setAwarded(0);
            quoteItem.setStatus(waived ? QUOTE_ITEM_STATUS_WAIVED : QUOTE_ITEM_STATUS_ACTIVE);
            supplierQuoteItemMapper.insert(quoteItem);
        }
    }

    private boolean isReplacement(SupplierQuoteItemSubmitDTO itemDTO) {
        return itemDTO != null && Boolean.TRUE.equals(itemDTO.getReplacementFlag());
    }

    private void validateAndNormalizeReplacement(SupplierQuoteItemSubmitDTO itemDTO, Supplier supplier, int lineNo) {
        if (!StringUtils.hasText(itemDTO.getReplacementItemName())) {
            throw new BizException("QUOTE_ITEM_REPLACEMENT_NAME_REQUIRED", "替换商品请填写替换后的商品名称");
        }
        if (!StringUtils.hasText(itemDTO.getReplacementSpecModel())) {
            throw new BizException("QUOTE_ITEM_REPLACEMENT_SPEC_REQUIRED", "替换商品请填写规格型号");
        }
        if (itemDTO.getReplacementUnitPrice() == null
            || itemDTO.getReplacementUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("QUOTE_ITEM_REPLACEMENT_PRICE_INVALID", "替换商品报价单价必须大于 0");
        }
        if (!StringUtils.hasText(itemDTO.getReplacementEcommerceUrl())) {
            throw new BizException("QUOTE_ITEM_REPLACEMENT_URL_REQUIRED", "替换商品请填写商品详情页链接");
        }
        itemDTO.setReplacementEcommerceUrl(
            supplierProductLinkService.normalizeForLine(itemDTO.getReplacementEcommerceUrl(), supplier, lineNo)
        );
        if (!StringUtils.hasText(itemDTO.getReplacementReason())) {
            throw new BizException("QUOTE_ITEM_REPLACEMENT_REASON_REQUIRED", "替换商品请填写替换原因");
        }
        validateCondition(itemDTO.getReplacementCondition(), itemDTO.getReplacementSecondHandRemark(), "替换商品");
    }

    private boolean isWaived(SupplierQuoteItemSubmitDTO itemDTO) {
        return Boolean.TRUE.equals(itemDTO.getWaived());
    }

    private void validateCondition(String condition, String secondHandRemark, String optionLabel) {
        if (!quoteAssetClassificationService.isKnownCondition(condition)) {
            throw new BizException("QUOTE_ITEM_CONDITION_REQUIRED", optionLabel + "成色必须选择全新或二手");
        }
        if (quoteAssetClassificationService.isUsed(condition) && !StringUtils.hasText(secondHandRemark)) {
            throw new BizException("QUOTE_ITEM_SECOND_HAND_REMARK_REQUIRED", optionLabel + "为二手时必须填写来源、使用年限或翻新/拆机情况");
        }
    }

    private void applyInitialAssetClassification(SupplierQuoteItem quoteItem) {
        AssetCatalogEntry originalCatalog = assetCatalogService
            .matchExact(quoteItem.getItemName(), quoteItem.getSpecModel())
            .orElse(null);
        applyOriginalCatalog(quoteItem, originalCatalog);
        quoteItem.setOriginalAssetClassification(quoteAssetClassificationService.classify(originalCatalog, false));
        quoteItem.setOriginalSpecialApprovalRequired(
            quoteAssetClassificationService.requiresSpecialApproval(
                quoteItem.getOriginalCondition(),
                quoteItem.getUnitPrice()
            ) ? 1 : 0
        );

        if (quoteItem.getReplacementFlag() != null && quoteItem.getReplacementFlag() == 1) {
            AssetCatalogEntry replacementCatalog = assetCatalogService
                .matchExact(quoteItem.getReplacementItemName(), quoteItem.getReplacementSpecModel())
                .orElse(null);
            applyReplacementCatalog(quoteItem, replacementCatalog);
            quoteItem.setReplacementAssetClassification(quoteAssetClassificationService.classify(replacementCatalog, false));
            quoteItem.setReplacementSpecialApprovalRequired(
                quoteAssetClassificationService.requiresSpecialApproval(
                    quoteItem.getReplacementCondition(),
                    quoteItem.getReplacementUnitPrice()
                ) ? 1 : 0
            );
        }
    }

    private void applyOriginalCatalog(SupplierQuoteItem quoteItem, AssetCatalogEntry catalog) {
        if (catalog == null) {
            return;
        }
        quoteItem.setOriginalAssetCategoryCode(catalog.getCategoryCode());
        quoteItem.setOriginalAssetNameCode(catalog.getAssetNameCode());
        quoteItem.setOriginalAssetCatalogName(catalog.getAssetName());
    }

    private void applyReplacementCatalog(SupplierQuoteItem quoteItem, AssetCatalogEntry catalog) {
        if (catalog == null) {
            return;
        }
        quoteItem.setReplacementAssetCategoryCode(catalog.getCategoryCode());
        quoteItem.setReplacementAssetNameCode(catalog.getAssetNameCode());
        quoteItem.setReplacementAssetCatalogName(catalog.getAssetName());
    }

    private void ensureQuoteModuleAccess(AuthUser authUser) {
        if (DataScopeHelper.isSupplierUser(authUser) || DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        if (authUser.getRoleCodes() != null && authUser.getRoleCodes().stream().anyMatch(role -> List.of(
            "ENGINEER",
            "PURCHASER",
            "MANAGER",
            "FLOOR_LEADER",
            "REGION_MANAGER",
            "SENIOR_MANAGER"
        ).contains(role))) {
            return;
        }
        throw new BizException("FORBIDDEN", "Current account cannot access quote management");
    }

    private void ensureSupplierAccount(AuthUser authUser) {
        if (!DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "Only supplier accounts can access this API");
        }
    }

    private void ensureSupplierCanAccessRequest(AuthUser authUser, PurchaseRequest request) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            if (temporarySupplierRecommendationService.hasActiveRecommendation(request.getId(), authUser.getSupplierId())) {
                return;
            }
            if (isDirectAwardRequest(request)) {
                if (!Objects.equals(authUser.getSupplierId(), request.getDirectAwardSupplierId())) {
                    throw new BizException("FORBIDDEN", "当前采购已定向给指定历史中标供应商报价");
                }
                return;
            }
            if (authUser.getRegionId() != null
                && !authUser.getRegionId().equals(request.getRegionId())
                && !isNationalDemandAggregate(request)) {
                throw new BizException("FORBIDDEN", "Current supplier account cannot access this request");
            }
            return;
        }
        if (!canInternalViewQuoteRequest(authUser, request)) {
            throw new BizException("FORBIDDEN", "Current account cannot access this request");
        }
    }

    private void ensureSupplierCanBid(Long supplierId, LocalDateTime now) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (isSupplierBanned(supplier)) {
            throw new BizException(
                "SUPPLIER_BIDDING_BANNED",
                StringUtils.hasText(supplier.getBlacklistReason())
                    ? supplier.getBlacklistReason()
                    : "Supplier bidding is banned"
            );
        }
        if (!isSupplierBiddingSuspended(supplier, now)) {
            applySupplierBiddingSuspensionIfNeeded(supplierId, now);
            supplier = supplierMapper.selectById(supplierId);
        }
        if (isSupplierBiddingSuspended(supplier, now)) {
            throw new BizException(
                "SUPPLIER_BIDDING_SUSPENDED",
                "Supplier bidding is suspended until " + supplier.getBidSuspendedUntil()
            );
        }
    }

    private boolean isSupplierBiddingSuspended(Long supplierId, LocalDateTime now) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        return isSupplierBiddingSuspended(supplier, now);
    }

    private boolean isSupplierBiddingBlocked(Long supplierId, LocalDateTime now) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        return isSupplierBanned(supplier) || isSupplierBiddingSuspended(supplier, now);
    }

    private boolean isNationalDemandAggregate(PurchaseRequest request) {
        return request != null && NATIONAL_DEMAND_AGGREGATE_REASON.equals(request.getReqReason());
    }

    private boolean isSupplierBanned(Supplier supplier) {
        return supplier != null && supplier.getBlacklistFlag() != null && supplier.getBlacklistFlag() == 1;
    }

    private boolean isSupplierBiddingSuspended(Supplier supplier, LocalDateTime now) {
        return supplier != null
            && supplier.getBidSuspendedUntil() != null
            && supplier.getBidSuspendedUntil().isAfter(now);
    }

    private void applySupplierBiddingSuspensionIfNeeded(SupplierQuote quote, LocalDateTime now) {
        if (quote.getSupplierId() == null) {
            return;
        }
        applySupplierBiddingSuspensionIfNeeded(quote.getSupplierId(), now);
    }

    private void applySupplierBiddingSuspensionIfNeeded(Long supplierId, LocalDateTime now) {
        List<SupplierQuote> waivedQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getSupplierId, supplierId)
            .eq(SupplierQuote::getSupplierConfirmStatus, ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED)
            .ge(SupplierQuote::getSupplierConfirmedAt, now.minusMonths(AWARD_WAIVE_WINDOW_MONTHS))
            .orderByDesc(SupplierQuote::getSupplierConfirmedAt)
            .orderByDesc(SupplierQuote::getId));
        if (waivedQuotes.size() < AWARD_WAIVE_SUSPEND_THRESHOLD) {
            return;
        }
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            return;
        }
        LocalDateTime latestWaiveAt = firstDateTime(waivedQuotes.get(0).getSupplierConfirmedAt(), now);
        LocalDateTime suspendedUntil = latestWaiveAt.plusMonths(AWARD_WAIVE_SUSPEND_MONTHS);
        if (!suspendedUntil.isAfter(now)) {
            return;
        }
        if (supplier.getBidSuspendedUntil() != null && supplier.getBidSuspendedUntil().isAfter(suspendedUntil)) {
            return;
        }
        supplier.setBidSuspendedUntil(suspendedUntil);
        supplier.setBidSuspendedReason("半年内累计放弃中标 " + waivedQuotes.size() + " 次，暂停投标 3 个月");
        supplierMapper.updateById(supplier);
    }

    private SupplierQuoteRequestVO toQuoteRequestVO(PurchaseRequest request, AuthUser authUser) {
        SupplierQuote myQuote = null;
        Supplier currentSupplier = null;
        if (authUser.getSupplierId() != null) {
            currentSupplier = supplierMapper.selectById(authUser.getSupplierId());
            myQuote = supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getPurchaseRequestId, request.getId())
                .eq(SupplierQuote::getSupplierId, authUser.getSupplierId())
                .last("LIMIT 1"));
        }

        SupplierQuoteRequestVO vo = new SupplierQuoteRequestVO();
        vo.setId(request.getId());
        vo.setReqNo(request.getReqNo());
        vo.setReqTitle(displayReqTitle(request.getReqTitle()));
        vo.setReqType(request.getReqType());
        vo.setRegionId(request.getRegionId());
        vo.setDeptName(request.getDeptName());
        vo.setReceivingCity(resolveDisplayReceivingCity(request));
        if (!DataScopeHelper.isSupplierUser(authUser)) {
            vo.setRequestAmount(request.getTotalAmount());
        }
        vo.setNeedDate(request.getNeedDate());
        vo.setAwardStrategy(ProcurementWorkflow.normalizeAwardStrategy(request.getAwardStrategy()));
        vo.setStatus(request.getStatus());
        vo.setApprovalStatus(request.getApprovalStatus());
        if (myQuote != null) {
            vo.setMyQuoteAmount(myQuote.getTotalAmount());
            vo.setMyLeadTimeDays(myQuote.getLeadTimeDays());
            vo.setMyQuoteStatus(myQuote.getStatus());
            vo.setMySubmittedAt(myQuote.getSubmittedAt());
            vo.setMyAwarded(isQuoteAwarded(request, myQuote.getId()));
            vo.setMySupplierConfirmStatus(myQuote.getSupplierConfirmStatus());
            vo.setMySupplierConfirmDueAt(myQuote.getSupplierConfirmDueAt());
        }
        if (currentSupplier != null) {
            vo.setSupplierBiddingSuspended(isSupplierBiddingSuspended(currentSupplier, LocalDateTime.now()));
            vo.setSupplierBidSuspendedUntil(currentSupplier.getBidSuspendedUntil());
            vo.setSupplierBanned(isSupplierBanned(currentSupplier));
            vo.setSupplierBanReason(currentSupplier.getBlacklistReason());
            vo.setInternalProductCodeRule(supplierProductLinkService.ruleCode(currentSupplier));
        }
        vo.setLeaderConfirmStatus(request.getLeaderConfirmStatus());
        if (!DataScopeHelper.isSupplierUser(authUser)) {
            vo.setQuoteCountdownStartedAt(request.getQuoteCountdownStartedAt());
        }
        vo.setQuoteCountdownDueAt(effectiveQuoteDeadline(request));
        vo.setDirectAwardApplied(isDirectAwardRequest(request));
        vo.setDirectAwardSupplierId(request.getDirectAwardSupplierId());
        vo.setDirectAwardSupplierName(request.getDirectAwardSupplierName());
        vo.setDirectAwardThresholdAmount(request.getDirectAwardThresholdAmount());
        vo.setDirectAwardReason(request.getDirectAwardReason());
        return vo;
    }

    private String displayReqTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return title;
        }
        return title
            .replace("全国月度需求汇总", "酒仙桥区域月度需求汇总")
            .replaceAll("[（(](\\d+)单/(\\d+)项[）)]", "（$1张申请/$2项明细）");
    }

    private void maskRequestPricing(PurchaseRequestVO detail) {
        if (detail == null) {
            return;
        }
        detail.setApplicantUserId(null);
        detail.setApplicantName(null);
        detail.setApplicantEmployeeNo(null);
        detail.setPurchaserUserId(null);
        detail.setBudgetCode(null);
        detail.setBuildingScopeId(null);
        detail.setApplicantSubjectId(null);
        detail.setApprovalPolicyId(null);
        detail.setSpecialtyCode(null);
        detail.setProfessionalReviewRequired(null);
        detail.setSelectedQuoteId(null);
        detail.setLeaderConfirmRemark(null);
        detail.setLeaderConfirmedBy(null);
        detail.setQuoteCountdownStartedAt(null);
        detail.setTotalAmount(null);
        if (detail.getItems() == null) {
            return;
        }
        detail.getItems().forEach(item -> {
            item.setUnitPrice(null);
            item.setLineAmount(null);
        });
    }

    private SupplierQuoteVO toQuoteVO(SupplierQuote quote, Supplier supplier) {
        PurchaseRequest request = purchaseRequestMapper.selectById(quote.getPurchaseRequestId());
        return toQuoteVO(quote, supplier, request);
    }

    private SupplierQuoteVO toQuoteVO(SupplierQuote quote, Supplier supplier, PurchaseRequest request) {
        return toQuoteVO(quote, supplier, request, false);
    }

    private SupplierQuoteVO toQuoteVO(SupplierQuote quote, Supplier supplier, PurchaseRequest request, boolean awardedOnly) {
        SupplierQuoteVO vo = new SupplierQuoteVO();
        vo.setId(quote.getId());
        vo.setQuoteNo(quote.getQuoteNo());
        vo.setPurchaseRequestId(quote.getPurchaseRequestId());
        vo.setSupplierId(quote.getSupplierId());
        vo.setSupplierName(supplier == null ? "供应商" : supplier.getSupplierName());
        vo.setSupplierUserId(quote.getSupplierUserId());
        vo.setQuoteSource(firstText(quote.getQuoteSource(), TemporarySupplierRecommendationService.QUOTE_SOURCE_NORMAL));
        vo.setRecommendationId(quote.getRecommendationId());
        vo.setAwardEligible(temporarySupplierRecommendationService.isQuoteAwardEligible(quote));
        vo.setAwardEligibilityText(temporarySupplierRecommendationService.quoteEligibilityText(quote));
        vo.setReceivingCity(resolveDisplayReceivingCity(request));
        vo.setTotalAmount(quote.getTotalAmount());
        vo.setLeadTimeDays(quote.getLeadTimeDays());
        vo.setQuoteRemark(quote.getQuoteRemark());
        vo.setStatus(quote.getStatus());
        vo.setSelected(request != null && isQuoteAwarded(request, quote.getId()));
        vo.setSupplierConfirmStatus(quote.getSupplierConfirmStatus());
        vo.setSupplierConfirmRemark(quote.getSupplierConfirmRemark());
        vo.setSupplierConfirmedBy(quote.getSupplierConfirmedBy());
        vo.setSupplierConfirmedAt(quote.getSupplierConfirmedAt());
        vo.setSupplierConfirmDueAt(quote.getSupplierConfirmDueAt());
        vo.setSupplierBiddingSuspended(isSupplierBiddingSuspended(supplier, LocalDateTime.now()));
        vo.setSupplierBidSuspendedUntil(supplier == null ? null : supplier.getBidSuspendedUntil());
        vo.setSupplierBanned(isSupplierBanned(supplier));
        vo.setSupplierBanReason(supplier == null ? null : supplier.getBlacklistReason());
        vo.setSubmittedAt(quote.getSubmittedAt());
        vo.setCreatedAt(quote.getCreatedAt());
        vo.setUpdatedAt(quote.getUpdatedAt());
        List<SupplierQuoteItemVO> items = listQuoteItems(quote.getId(), resolveDeliveryAddress(request), awardedOnly);
        vo.setItems(items);
        if (awardedOnly) {
            vo.setTotalAmount(items.stream()
                .map(SupplierQuoteItemVO::getLineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return vo;
    }

    private void startFastQuoteCountdownIfReady(PurchaseRequest request, LocalDateTime submittedAt) {
        if (request == null
            || !ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())
            || isDirectAwardRequest(request)) {
            return;
        }
        LocalDateTime openedAt = quoteOpenedAt(request);
        if (openedAt == null) {
            return;
        }
        LocalDateTime openDeadline = effectiveQuoteDeadline(request, openedAt);
        LocalDateTime actionTime = submittedAt == null ? LocalDateTime.now() : submittedAt;
        if (!actionTime.isBefore(openDeadline)) {
            return;
        }
        LocalDateTime thirdSubmittedAt = thirdCompleteSubmittedQuoteAt(request);
        if (thirdSubmittedAt == null || !thirdSubmittedAt.isBefore(openDeadline)) {
            return;
        }

        PurchaseRequest latest = purchaseRequestMapper.selectById(request.getId());
        if (latest == null || latest.getQuoteCountdownStartedAt() != null) {
            return;
        }
        if (latest.getQuoteCountdownDueAt() != null) {
            return;
        }
        LocalDateTime latestOpenDeadline = effectiveQuoteDeadline(latest, quoteOpenedAt(latest));
        LocalDateTime dueAt = thirdSubmittedAt.plusHours(FAST_QUOTE_COUNTDOWN_HOURS);
        if (latestOpenDeadline == null || !dueAt.isBefore(latestOpenDeadline)) {
            return;
        }
        latest.setQuoteCountdownStartedAt(thirdSubmittedAt);
        latest.setQuoteCountdownDueAt(dueAt);
        purchaseRequestMapper.updateById(latest);
    }

    private LocalDateTime thirdCompleteSubmittedQuoteAt(PurchaseRequest request) {
        List<SupplierQuote> submittedQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, request.getId())
            .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED)
            .isNotNull(SupplierQuote::getSubmittedAt)
            .orderByAsc(SupplierQuote::getSubmittedAt)
            .orderByAsc(SupplierQuote::getId))
            .stream()
            .filter(temporarySupplierRecommendationService::isQuoteCountedForFastCountdown)
            .filter(quote -> hasCompleteActiveQuoteItems(request, quote))
            .toList();
        return submittedQuotes.size() < 3 ? null : submittedQuotes.get(2).getSubmittedAt();
    }

    private boolean hasCompleteActiveQuoteItems(PurchaseRequest request, SupplierQuote quote) {
        long requestItemCount = purchaseRequestItemMapper.selectCount(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED));
        long activeQuoteItemCount = supplierQuoteItemMapper.selectCount(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, quote.getId())
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE));
        return requestItemCount > 0 && activeQuoteItemCount == requestItemCount;
    }

    private LocalDateTime quoteOpenedAt(PurchaseRequest request) {
        if (request.getApprovedAt() != null) {
            return request.getApprovedAt();
        }
        return request.getUpdatedAt();
    }

    private void ensureQuoteSubmissionOpen(PurchaseRequest request, LocalDateTime now) {
        LocalDateTime deadline = effectiveQuoteDeadline(request);
        if (deadline != null && !now.isBefore(deadline)) {
            throw new BizException("QUOTE_DEADLINE_EXPIRED", "Quote deadline has passed; unsubmitted suppliers are treated as waived");
        }
    }

    private LocalDateTime effectiveQuoteDeadline(PurchaseRequest request) {
        return effectiveQuoteDeadline(request, quoteOpenedAt(request));
    }

    private LocalDateTime effectiveQuoteDeadline(PurchaseRequest request, LocalDateTime openedAt) {
        if (request == null) {
            return null;
        }
        if (request.getQuoteCountdownDueAt() != null) {
            return request.getQuoteCountdownDueAt();
        }
        return openedAt == null ? null : openedAt.plusDays(quoteOpenDays());
    }

    private int quoteOpenDays() {
        ProcurementSetting setting = procurementSettingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, "quote_open_days")
            .last("LIMIT 1"));
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return 7;
        }
        try {
            return Math.max(0, Integer.parseInt(setting.getSettingValue().trim()));
        } catch (NumberFormatException ignored) {
            return 7;
        }
    }

    private boolean isDirectAwardRequest(PurchaseRequest request) {
        return request != null
            && request.getDirectAwardApplied() != null
            && request.getDirectAwardApplied() == 1
            && request.getDirectAwardSupplierId() != null;
    }

    private void downgradeDirectAwardIfQuoteOverLimit(PurchaseRequest request, SupplierQuote quote, BigDecimal quoteAmount) {
        if (!isDirectAwardRequest(request)
            || quote == null
            || !Objects.equals(quote.getSupplierId(), request.getDirectAwardSupplierId())) {
            return;
        }
        BigDecimal amount = quoteAmount == null ? quote.getTotalAmount() : quoteAmount;
        BigDecimal limit = directAwardQuoteLimit(request);
        if (amount == null || amount.compareTo(limit) <= 0) {
            return;
        }
        String supplierName = firstText(request.getDirectAwardSupplierName(), resolveSupplierName(quote.getSupplierId()), "定向供应商");
        request.setDirectAwardApplied(0);
        request.setDirectAwardSupplierId(null);
        request.setDirectAwardSupplierName(null);
        request.setDirectAwardThresholdAmount(null);
        request.setDirectAwardReason(null);
        request.setQuoteCountdownStartedAt(null);
        request.setQuoteCountdownDueAt(null);
        request.setRemark(appendRemark(request.getRemark(), "定向供应商 "
            + supplierName
            + " 报价 "
            + decimalText(amount)
            + " 超过低额紧急采购上限 "
            + decimalText(limit)
            + "，转入三方比价"));
        purchaseRequestMapper.updateById(request);
    }

    private BigDecimal directAwardQuoteLimit(PurchaseRequest request) {
        BigDecimal threshold = request == null ? null : request.getDirectAwardThresholdAmount();
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
            threshold = directAwardThresholdSetting();
        }
        return threshold.multiply(URGENT_DIRECT_AWARD_QUOTE_LIMIT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal directAwardThresholdSetting() {
        ProcurementSetting setting = procurementSettingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, URGENT_DIRECT_AWARD_THRESHOLD_KEY)
            .last("LIMIT 1"));
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return DEFAULT_URGENT_DIRECT_AWARD_THRESHOLD;
        }
        try {
            return new BigDecimal(setting.getSettingValue().trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return DEFAULT_URGENT_DIRECT_AWARD_THRESHOLD;
        }
    }

    @Scheduled(cron = "0 */30 * * * ?", zone = "Asia/Shanghai")
    @Transactional(rollbackFor = Exception.class)
    public void autoExpireSupplierAwardConfirmations() {
        LocalDateTime now = LocalDateTime.now();
        initializeMissingAwardConfirmDeadlines(now);
        List<SupplierQuote> dueQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getSupplierConfirmStatus, ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING)
            .isNotNull(SupplierQuote::getSupplierConfirmDueAt)
            .le(SupplierQuote::getSupplierConfirmDueAt, now)
            .orderByAsc(SupplierQuote::getSupplierConfirmDueAt)
            .orderByAsc(SupplierQuote::getId));
        for (SupplierQuote quote : dueQuotes) {
            expireAwardConfirmationAndReassign(quote, now);
        }
    }

    private void initializeMissingAwardConfirmDeadlines(LocalDateTime now) {
        List<SupplierQuote> pendingQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getSupplierConfirmStatus, ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING)
            .isNull(SupplierQuote::getSupplierConfirmDueAt)
            .orderByAsc(SupplierQuote::getUpdatedAt)
            .orderByAsc(SupplierQuote::getId));
        for (SupplierQuote quote : pendingQuotes) {
            PurchaseRequest request = purchaseRequestMapper.selectById(quote.getPurchaseRequestId());
            if (request == null || !isQuoteAwarded(request, quote.getId())) {
                quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_UNSELECTED);
                supplierQuoteMapper.updateById(quote);
                continue;
            }
            LocalDateTime baseTime = firstDateTime(request.getLeaderConfirmedAt(), quote.getUpdatedAt(), now);
            LocalDateTime dueAt = awardNotificationService.supplierConfirmDueAt(baseTime);
            quote.setSupplierConfirmDueAt(dueAt);
            supplierQuoteMapper.updateById(quote);
            sendAwardMailIfNeeded(request, quote, dueAt);
        }
    }

    private void expireAwardConfirmationAndReassign(SupplierQuote expiredQuote, LocalDateTime now) {
        waiveAwardConfirmationAndReassign(
            expiredQuote,
            now,
            null,
            "超过中标确认期限，系统自动视为放弃",
            "中标供应商超期未确认"
        );
    }

    private void waiveAwardConfirmationAndReassign(
        SupplierQuote quote,
        LocalDateTime now,
        Long confirmedBy,
        String waiveRemark,
        String reassignRemarkPrefix
    ) {
        PurchaseRequest request = purchaseRequestMapper.selectById(quote.getPurchaseRequestId());
        if (request == null || !isQuoteAwarded(request, quote.getId())) {
            if (request != null) {
                quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_UNSELECTED);
                supplierQuoteMapper.updateById(quote);
            }
            return;
        }
        waiveQuote(quote, now, confirmedBy, waiveRemark);
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            reassignItemLowestAwards(request, quote, now, reassignRemarkPrefix);
        } else {
            reassignTotalLowestAward(request, quote, now, reassignRemarkPrefix);
        }
    }

    private void reassignTotalLowestAward(PurchaseRequest request, SupplierQuote expiredQuote, LocalDateTime now, String remarkPrefix) {
        resetAwardMarkers(request.getId());
        SupplierQuote nextQuote = nextTotalLowestQuote(request, expiredQuote.getId(), now);
        if (nextQuote == null) {
            request.setSelectedQuoteId(null);
            request.setRemark(appendRemark(request.getRemark(), remarkPrefix + "，暂无可递补报价"));
            purchaseRequestMapper.updateById(request);
            return;
        }
        markQuoteItemsAwarded(nextQuote.getId());
        request.setSelectedQuoteId(nextQuote.getId());
        request.setRemark(appendRemark(request.getRemark(), remarkPrefix + "，已递补下一名供应商"));
        purchaseRequestMapper.updateById(request);
        awardPriceSyncService.syncAwardedPrices(request);
        markQuotePendingAndNotify(request, nextQuote, now);
    }

    private void reassignItemLowestAwards(PurchaseRequest request, SupplierQuote expiredQuote, LocalDateTime now, String remarkPrefix) {
        List<SupplierQuoteItem> expiredItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, expiredQuote.getId())
            .eq(SupplierQuoteItem::getAwarded, 1)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .orderByAsc(SupplierQuoteItem::getLineNo));
        Set<Long> nextQuoteIds = new HashSet<>();
        for (SupplierQuoteItem expiredItem : expiredItems) {
            expiredItem.setAwarded(0);
            clearReceiptState(expiredItem);
            supplierQuoteItemMapper.updateById(expiredItem);
            SupplierQuoteItem nextItem = nextItemLowestQuoteItem(expiredItem.getPurchaseRequestItemId(), expiredQuote.getId(), now);
            if (nextItem == null) {
                continue;
            }
            nextItem.setAwarded(1);
            clearReceiptState(nextItem);
            supplierQuoteItemMapper.updateById(nextItem);
            nextQuoteIds.add(nextItem.getQuoteId());
        }
        if (!nextQuoteIds.isEmpty()) {
            request.setRemark(appendRemark(request.getRemark(), remarkPrefix + "，已按单项最低价递补供应商"));
            purchaseRequestMapper.updateById(request);
        }
        for (Long nextQuoteId : nextQuoteIds) {
            SupplierQuote nextQuote = supplierQuoteMapper.selectById(nextQuoteId);
            if (nextQuote != null) {
                markQuotePendingAndNotify(request, nextQuote, now);
            }
        }
        awardPriceSyncService.syncAwardedPricesIfComplete(request);
    }

    private SupplierQuote nextTotalLowestQuote(PurchaseRequest request, Long expiredQuoteId, LocalDateTime now) {
        return supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getPurchaseRequestId, request.getId())
                .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED)
                .ne(SupplierQuote::getId, expiredQuoteId)
                .ne(SupplierQuote::getSupplierConfirmStatus, ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED)
                .orderByAsc(SupplierQuote::getTotalAmount)
                .orderByAsc(SupplierQuote::getLeadTimeDays)
            .orderByAsc(SupplierQuote::getId))
            .stream()
            .filter(quote -> !isSupplierBiddingBlocked(quote.getSupplierId(), now))
            .filter(temporarySupplierRecommendationService::isQuoteAwardEligible)
            .filter(quote -> hasCompleteActiveQuoteItems(request, quote))
            .findFirst()
            .orElse(null);
    }

    private SupplierQuoteItem nextItemLowestQuoteItem(Long purchaseRequestItemId, Long expiredQuoteId, LocalDateTime now) {
        return supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getPurchaseRequestItemId, purchaseRequestItemId)
                .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
                .ne(SupplierQuoteItem::getQuoteId, expiredQuoteId))
            .stream()
            .filter(item -> quoteAvailableForReaward(item.getQuoteId(), now))
            .min(Comparator
                .comparing((SupplierQuoteItem item) -> item.getLineAmount() == null ? BigDecimal.ZERO : item.getLineAmount())
                .thenComparing(item -> item.getLeadTimeDays() == null ? Integer.MAX_VALUE : item.getLeadTimeDays())
                .thenComparing(SupplierQuoteItem::getQuoteId))
            .orElse(null);
    }

    private boolean quoteAvailableForReaward(Long quoteId, LocalDateTime now) {
        SupplierQuote quote = supplierQuoteMapper.selectById(quoteId);
        return quote != null
            && ProcurementWorkflow.QUOTE_STATUS_SUBMITTED.equals(quote.getStatus())
            && !ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED.equals(quote.getSupplierConfirmStatus())
            && temporarySupplierRecommendationService.isQuoteAwardEligible(quote)
            && !isSupplierBiddingBlocked(quote.getSupplierId(), now);
    }

    private void waiveQuote(SupplierQuote quote, LocalDateTime now, Long confirmedBy, String remark) {
        quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED);
        quote.setSupplierConfirmRemark(appendRemark(quote.getSupplierConfirmRemark(), remark));
        quote.setSupplierConfirmedBy(confirmedBy);
        quote.setSupplierConfirmedAt(now);
        supplierQuoteMapper.updateById(quote);
        applySupplierBiddingSuspensionIfNeeded(quote, now);
    }

    private void markQuotePendingAndNotify(PurchaseRequest request, SupplierQuote quote, LocalDateTime now) {
        if (isSupplierBiddingBlocked(quote.getSupplierId(), now)) {
            return;
        }
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(quote.getSupplierConfirmStatus())) {
            return;
        }
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING.equals(quote.getSupplierConfirmStatus())
            && quote.getSupplierConfirmDueAt() != null
            && quote.getSupplierConfirmDueAt().isAfter(now)) {
            return;
        }
        LocalDateTime dueAt = awardNotificationService.supplierConfirmDueAt(now);
        quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING);
        quote.setSupplierConfirmRemark(null);
        quote.setSupplierConfirmedBy(null);
        quote.setSupplierConfirmedAt(null);
        quote.setSupplierConfirmDueAt(dueAt);
        quote.setSupplierConfirmNotifiedAt(null);
        supplierQuoteMapper.updateById(quote);
        sendAwardMailIfNeeded(request, quote, dueAt);
    }

    private void sendAwardMailIfNeeded(PurchaseRequest request, SupplierQuote quote, LocalDateTime dueAt) {
        if (quote.getSupplierConfirmNotifiedAt() != null) {
            return;
        }
        if (awardNotificationService.sendAwardConfirmationEmail(request, quote, dueAt)) {
            quote.setSupplierConfirmNotifiedAt(LocalDateTime.now());
            supplierQuoteMapper.updateById(quote);
        }
    }

    private void resetAwardMarkers(Long purchaseRequestId) {
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuoteItem::getAwarded, 1))) {
            item.setAwarded(0);
            clearReceiptState(item);
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private void markQuoteItemsAwarded(Long quoteId) {
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, quoteId)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE))) {
            item.setAwarded(1);
            clearReceiptState(item);
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private void clearReceiptState(SupplierQuoteItem item) {
        item.setReceiptStatus(null);
        item.setReceiptDueAt(null);
        item.setReceivedBy(null);
        item.setReceivedAt(null);
        item.setReceiptRemark(null);
        item.setAutoReceived(0);
    }

    private void initializeReceiptForAwardedItems(SupplierQuote quote) {
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, quote.getId())
            .eq(SupplierQuoteItem::getAwarded, 1)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE))) {
            item.setReceiptStatus(RECEIPT_STATUS_PENDING);
            item.setReceiptDueAt(null);
            item.setReceivedBy(null);
            item.setReceivedAt(null);
            item.setReceiptRemark(null);
            item.setAutoReceived(0);
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private List<SupplierQuoteItemVO> listQuoteItems(Long quoteId, String deliveryAddress) {
        return listQuoteItems(quoteId, deliveryAddress, false);
    }

    private List<SupplierQuoteItemVO> listQuoteItems(Long quoteId, String deliveryAddress, boolean awardedOnly) {
        return supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getQuoteId, quoteId)
                .eq(awardedOnly, SupplierQuoteItem::getAwarded, 1)
                .eq(awardedOnly, SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
                .orderByAsc(SupplierQuoteItem::getLineNo))
            .stream()
            .map(item -> toQuoteItemVO(item, deliveryAddress))
            .toList();
    }

    private SupplierQuoteItemVO toQuoteItemVO(SupplierQuoteItem item, String deliveryAddress) {
        SupplierQuoteItemVO vo = new SupplierQuoteItemVO();
        vo.setId(item.getId());
        vo.setQuoteId(item.getQuoteId());
        vo.setPurchaseRequestId(item.getPurchaseRequestId());
        vo.setPurchaseRequestItemId(item.getPurchaseRequestItemId());
        vo.setLineNo(item.getLineNo());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setSpecModel(item.getSpecModel());
        vo.setUnit(item.getUnit());
        vo.setQty(item.getQty());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setSupplierSubmittedUnitPrice(item.getSupplierSubmittedUnitPrice());
        vo.setLineAmount(item.getLineAmount());
        vo.setLeadTimeDays(item.getLeadTimeDays());
        vo.setOriginalCondition(item.getOriginalCondition());
        vo.setOriginalSecondHandRemark(item.getOriginalSecondHandRemark());
        vo.setQuoteRemark(item.getQuoteRemark());
        vo.setReplacementFlag(item.getReplacementFlag() != null && item.getReplacementFlag() == 1);
        vo.setReplacementItemName(item.getReplacementItemName());
        vo.setReplacementSpecModel(item.getReplacementSpecModel());
        vo.setReplacementEcommerceUrl(item.getReplacementEcommerceUrl());
        vo.setReplacementUnitPrice(item.getReplacementUnitPrice());
        vo.setReplacementLineAmount(item.getReplacementLineAmount());
        vo.setReplacementReason(item.getReplacementReason());
        vo.setReplacementCondition(item.getReplacementCondition());
        vo.setReplacementSecondHandRemark(item.getReplacementSecondHandRemark());
        vo.setReplacementReviewStatus(item.getReplacementReviewStatus());
        vo.setReplacementReviewRemark(item.getReplacementReviewRemark());
        vo.setAssetReviewStatus(item.getAssetReviewStatus());
        vo.setAssetReviewMessage(item.getAssetReviewMessage());
        vo.setOriginalAssetCategoryCode(item.getOriginalAssetCategoryCode());
        vo.setOriginalAssetNameCode(item.getOriginalAssetNameCode());
        vo.setOriginalAssetCatalogName(item.getOriginalAssetCatalogName());
        vo.setOriginalAssetClassification(item.getOriginalAssetClassification());
        vo.setOriginalSpecialApprovalRequired(
            item.getOriginalSpecialApprovalRequired() != null && item.getOriginalSpecialApprovalRequired() == 1
        );
        vo.setReplacementAssetCategoryCode(item.getReplacementAssetCategoryCode());
        vo.setReplacementAssetNameCode(item.getReplacementAssetNameCode());
        vo.setReplacementAssetCatalogName(item.getReplacementAssetCatalogName());
        vo.setReplacementAssetClassification(item.getReplacementAssetClassification());
        vo.setReplacementSpecialApprovalRequired(
            item.getReplacementSpecialApprovalRequired() != null && item.getReplacementSpecialApprovalRequired() == 1
        );
        vo.setConditionMismatch(item.getConditionMismatch() != null && item.getConditionMismatch() == 1);
        vo.setAssetReviewedAt(item.getAssetReviewedAt());
        vo.setWaived(QUOTE_ITEM_STATUS_WAIVED.equals(item.getStatus()));
        vo.setAwarded(item.getAwarded() != null && item.getAwarded() == 1);
        vo.setReceivingCity(firstText(item.getReceivingCity()));
        String itemDeliveryAddress = firstText(item.getDeliveryAddress(), deliveryAddress);
        if (vo.getAwarded() && StringUtils.hasText(itemDeliveryAddress)) {
            vo.setDeliveryAddress(itemDeliveryAddress);
        }
        vo.setStatus(item.getStatus());
        return vo;
    }

    private String generateQuoteNo() {
        return "QT"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private boolean isQuoteAwarded(PurchaseRequest request, Long quoteId) {
        if (quoteId == null) {
            return false;
        }
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            return supplierQuoteItemMapper.selectCount(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getQuoteId, quoteId)
                .eq(SupplierQuoteItem::getAwarded, 1)) > 0;
        }
        return request.getSelectedQuoteId() != null && request.getSelectedQuoteId().equals(quoteId);
    }

    private List<List<String>> buildAwardExportRows(PurchaseRequest request, SupplierQuote quote) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
            "申请单号",
            "申请标题",
            "供应商",
            "负责人确认状态",
            "供应商确认状态",
            "负责人确认备注",
            "供应商确认备注"
        ));
        rows.add(List.of(
            request.getReqNo(),
            defaultText(request.getReqTitle()),
            defaultText(resolveSupplierName(quote.getSupplierId())),
            defaultText(request.getLeaderConfirmStatus()),
            defaultText(quote.getSupplierConfirmStatus()),
            defaultText(request.getLeaderConfirmRemark()),
            defaultText(quote.getSupplierConfirmRemark())
        ));
        rows.add(List.of());
        rows.add(List.of(
            "行号",
            "物料编码",
            "商品名称",
            "规格型号",
            "送达地址",
            "单位",
            "数量",
            "中标单价",
            "中标金额",
            "是否替换商品",
            "备注"
        ));

        Map<Long, SupplierQuoteItem> quoteItemMap = new LinkedHashMap<>();
        LambdaQueryWrapper<SupplierQuoteItem> itemWrapper = new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, quote.getId())
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .orderByAsc(SupplierQuoteItem::getLineNo);
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            itemWrapper.eq(SupplierQuoteItem::getAwarded, 1);
        }
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(itemWrapper)) {
            quoteItemMap.put(item.getPurchaseRequestItemId(), item);
        }

        String deliveryAddress = resolveDeliveryAddress(request);
        for (PurchaseRequestItem requestItem : loadRequestItems(request.getId())) {
            SupplierQuoteItem quoteItem = quoteItemMap.get(requestItem.getId());
            if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy()) && quoteItem == null) {
                continue;
            }
            String rowDeliveryAddress = firstText(
                quoteItem == null ? null : quoteItem.getDeliveryAddress(),
                requestItem.getDeliveryAddress(),
                deliveryAddress
            );
            rows.add(List.of(
                String.valueOf(requestItem.getLineNo()),
                defaultText(requestItem.getItemCode()),
                defaultText(requestItem.getItemName()),
                defaultText(requestItem.getSpecModel()),
                defaultText(rowDeliveryAddress),
                defaultText(requestItem.getUnit()),
                decimalText(requestItem.getQty()),
                decimalText(quoteItem == null ? null : quoteItem.getUnitPrice()),
                decimalText(quoteItem == null ? null : quoteItem.getLineAmount()),
                quoteItem != null && quoteItem.getReplacementFlag() != null && quoteItem.getReplacementFlag() == 1 ? "是" : "否",
                buildAwardRemark(quoteItem)
            ));
        }
        return rows;
    }

    private String resolveDeliveryAddress(PurchaseRequest request) {
        OrgScope scope = approvalRouteResolver.findScope(request);
        if (scope == null) {
            return "";
        }
        if (StringUtils.hasText(scope.getAddress())) {
            return scope.getAddress().trim();
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, scope.getProvince());
        addIfPresent(parts, scope.getCity());
        addIfPresent(parts, scope.getDistrictName());
        addIfPresent(parts, scope.getTownName());
        addIfPresent(parts, scope.getRoomName());
        addIfPresent(parts, scope.getBuildingName());
        return String.join("", parts);
    }

    private String resolveReceivingCity(PurchaseRequest request) {
        OrgScope scope = approvalRouteResolver.findScope(request);
        if (scope == null) {
            return "";
        }
        if (StringUtils.hasText(scope.getCity())) {
            return scope.getCity().trim();
        }
        if (StringUtils.hasText(scope.getProvince())) {
            return scope.getProvince().trim();
        }
        return "";
    }

    private String resolveDisplayReceivingCity(PurchaseRequest request) {
        String itemCityText = resolveItemReceivingCity(request);
        if (StringUtils.hasText(itemCityText)) {
            return itemCityText;
        }
        return resolveReceivingCity(request);
    }

    private String resolveItemReceivingCity(PurchaseRequest request) {
        if (request == null || request.getId() == null) {
            return "";
        }
        List<String> cities = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
                .orderByAsc(PurchaseRequestItem::getLineNo))
            .stream()
            .map(PurchaseRequestItem::getReceivingCity)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        if (cities.isEmpty()) {
            return "";
        }
        if (cities.size() == 1) {
            return cities.get(0);
        }
        if (cities.size() <= 3) {
            return String.join("/", cities);
        }
        return cities.get(0) + "等" + cities.size() + "城";
    }

    private void addIfPresent(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private String buildAwardRemark(SupplierQuoteItem quoteItem) {
        if (quoteItem == null) {
            return "未找到中标报价明细";
        }
        if (StringUtils.hasText(quoteItem.getQuoteRemark())) {
            return quoteItem.getQuoteRemark();
        }
        return "";
    }

    private String resolveSupplierName(Long supplierId) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        return supplier == null ? "" : supplier.getSupplierName();
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private LocalDateTime firstDateTime(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return LocalDateTime.now();
    }

    private String appendRemark(String oldRemark, String append) {
        if (!StringUtils.hasText(oldRemark)) {
            return append;
        }
        if (!StringUtils.hasText(append)) {
            return oldRemark;
        }
        String value = oldRemark + "；" + append;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }
}
