package com.company.procurement.modules.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationCreateDTO;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationPageQueryDTO;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationReviewDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.TemporarySupplierRecommendation;
import com.company.procurement.modules.supplier.entity.TemporarySupplierRecommendationItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.TemporarySupplierRecommendationItemMapper;
import com.company.procurement.modules.supplier.mapper.TemporarySupplierRecommendationMapper;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.supplier.vo.TemporarySupplierRecommendationItemVO;
import com.company.procurement.modules.supplier.vo.TemporarySupplierRecommendationVO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemporarySupplierRecommendationServiceImpl implements TemporarySupplierRecommendationService {

    private static final String ITEM_STATUS_ACTIVE = "ACTIVE";
    private static final DateTimeFormatter CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TemporarySupplierRecommendationMapper recommendationMapper;
    private final TemporarySupplierRecommendationItemMapper recommendationItemMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final ProcurementApprovalRouteResolver approvalRouteResolver;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<TemporarySupplierRecommendationVO> page(TemporarySupplierRecommendationPageQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureInternalUser(authUser);

        LambdaQueryWrapper<TemporarySupplierRecommendation> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getPurchaseRequestId() != null) {
            wrapper.eq(TemporarySupplierRecommendation::getPurchaseRequestId, queryDTO.getPurchaseRequestId());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(TemporarySupplierRecommendation::getStatus, queryDTO.getStatus());
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            wrapper.and(scope -> scope
                .like(TemporarySupplierRecommendation::getRecommendationNo, keyword)
                .or()
                .like(TemporarySupplierRecommendation::getRequestNo, keyword)
                .or()
                .like(TemporarySupplierRecommendation::getRequestTitle, keyword)
                .or()
                .like(TemporarySupplierRecommendation::getSupplierName, keyword)
                .or()
                .like(TemporarySupplierRecommendation::getSupplierCode, keyword));
        }
        applyPageScope(wrapper, authUser);
        wrapper.orderByDesc(TemporarySupplierRecommendation::getCreatedAt)
            .orderByDesc(TemporarySupplierRecommendation::getId);

        Page<TemporarySupplierRecommendation> page =
            recommendationMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<TemporarySupplierRecommendationVO> records = page.getRecords().stream()
            .map(this::toVO)
            .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public TemporarySupplierRecommendationVO detail(Long id) {
        TemporarySupplierRecommendation recommendation = requireRecommendation(id);
        ensureCanView(recommendation, AuthContextHolder.requireUser());
        return toVO(recommendation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TemporarySupplierRecommendationCreateDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureInternalUser(authUser);
        if (CollectionUtils.isEmpty(dto.getItemIds())) {
            throw new BizException("TEMP_SUPPLIER_ITEMS_EMPTY", "推荐供应商至少要关联一条采购明细");
        }

        PurchaseRequest request = purchaseRequestMapper.selectById(dto.getPurchaseRequestId());
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        }
        if (!DataScopeHelper.canAccessRegion(authUser, request.getRegionId())
            && !Objects.equals(authUser.getUserId(), request.getApplicantUserId())
            && !canReviewRole(authUser, request, ProcurementWorkflow.ROLE_MANAGER)
            && !canReviewRole(authUser, request, ProcurementWorkflow.ROLE_REGION_MANAGER)) {
            throw new BizException("FORBIDDEN", "当前账号不能推荐该采购申请的临时供应商");
        }

        List<PurchaseRequestItem> requestItems = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .in(PurchaseRequestItem::getId, new LinkedHashSet<>(dto.getItemIds()))
            .orderByAsc(PurchaseRequestItem::getLineNo));
        if (requestItems.size() != new LinkedHashSet<>(dto.getItemIds()).size()) {
            throw new BizException("TEMP_SUPPLIER_ITEM_SCOPE_INVALID", "推荐明细必须属于当前采购申请且未被驳回");
        }

        String supplierCode = generateSupplierCode();
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(dto.getSupplierName().trim());
        supplier.setSupplierShortName(dto.getSupplierName().trim());
        supplier.setTaxNo("TMP-" + supplierCode);
        supplier.setSupplierType(SUPPLIER_TYPE_TEMP_RECOMMENDED);
        supplier.setRegionId(request.getRegionId());
        supplier.setContactName(dto.getContactName().trim());
        supplier.setContactPhone(dto.getContactPhone().trim());
        supplier.setContactEmail(trimToNull(dto.getContactEmail()));
        supplier.setOnboardingStatus(SUPPLIER_ONBOARDING_TEMP_RECOMMENDED);
        supplier.setStatus("ACTIVE");
        supplier.setBlacklistFlag(0);
        supplier.setDataIsolationFlag(1);
        supplier.setEntryDate(LocalDate.now());
        supplier.setRemark("临时推荐供应商：" + firstText(dto.getRecommendReason(), ""));
        supplierMapper.insert(supplier);

        TemporarySupplierRecommendation recommendation = new TemporarySupplierRecommendation();
        recommendation.setRecommendationNo(generateRecommendationNo());
        recommendation.setPurchaseRequestId(request.getId());
        recommendation.setRequestNo(request.getReqNo());
        recommendation.setRequestTitle(request.getReqTitle());
        recommendation.setRegionId(request.getRegionId());
        recommendation.setDeptCode(request.getDeptCode());
        recommendation.setDeptName(request.getDeptName());
        recommendation.setSupplierId(supplier.getId());
        recommendation.setSupplierCode(supplier.getSupplierCode());
        recommendation.setSupplierName(supplier.getSupplierName());
        recommendation.setContactName(supplier.getContactName());
        recommendation.setContactPhone(supplier.getContactPhone());
        recommendation.setContactEmail(supplier.getContactEmail());
        recommendation.setRecommendedBy(authUser.getUserId());
        recommendation.setRecommendedByName(displayName(authUser));
        recommendation.setRecommendReason(dto.getRecommendReason().trim());
        recommendation.setStatus(STATUS_PENDING_ROOM_MANAGER);
        recommendation.setCurrentRoleCode(ProcurementWorkflow.ROLE_MANAGER);
        recommendation.setRemark(dto.getRemark());
        recommendationMapper.insert(recommendation);

        for (PurchaseRequestItem requestItem : requestItems) {
            TemporarySupplierRecommendationItem item = new TemporarySupplierRecommendationItem();
            item.setRecommendationId(recommendation.getId());
            item.setPurchaseRequestId(request.getId());
            item.setPurchaseRequestItemId(requestItem.getId());
            item.setLineNo(requestItem.getLineNo());
            item.setItemCode(requestItem.getItemCode());
            item.setReqType(requestItem.getReqType());
            item.setItemName(requestItem.getItemName());
            item.setSpecModel(requestItem.getSpecModel());
            item.setUnit(requestItem.getUnit());
            item.setQty(requestItem.getQty());
            item.setPurpose(requestItem.getPurpose());
            item.setStatus(ITEM_STATUS_ACTIVE);
            recommendationItemMapper.insert(item);
        }
        return recommendation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemporarySupplierRecommendationVO approve(Long id, TemporarySupplierRecommendationReviewDTO dto) {
        TemporarySupplierRecommendation recommendation = requireRecommendation(id);
        AuthUser authUser = AuthContextHolder.requireUser();
        PurchaseRequest request = requireRequest(recommendation.getPurchaseRequestId());
        LocalDateTime now = LocalDateTime.now();
        String comment = firstText(dto == null ? null : dto.getComment(), "同意");

        if (STATUS_PENDING_ROOM_MANAGER.equals(recommendation.getStatus())) {
            ensureCanReview(authUser, request, ProcurementWorkflow.ROLE_MANAGER);
            recommendation.setRoomManagerUserId(authUser.getUserId());
            recommendation.setRoomManagerName(displayName(authUser));
            recommendation.setRoomManagerComment(comment);
            recommendation.setRoomManagerReviewedAt(now);
            recommendation.setStatus(STATUS_PENDING_REGION_MANAGER);
            recommendation.setCurrentRoleCode(ProcurementWorkflow.ROLE_REGION_MANAGER);
            recommendationMapper.updateById(recommendation);
            return toVO(recommendation);
        }
        if (STATUS_PENDING_REGION_MANAGER.equals(recommendation.getStatus())) {
            ensureCanReview(authUser, request, ProcurementWorkflow.ROLE_REGION_MANAGER);
            recommendation.setRegionManagerUserId(authUser.getUserId());
            recommendation.setRegionManagerName(displayName(authUser));
            recommendation.setRegionManagerComment(comment);
            recommendation.setRegionManagerReviewedAt(now);
            recommendation.setStatus(STATUS_APPROVED);
            recommendation.setCurrentRoleCode(null);
            recommendationMapper.updateById(recommendation);
            return toVO(recommendation);
        }
        throw new BizException("TEMP_SUPPLIER_REVIEW_STATUS_INVALID", "当前推荐供应商不在待审批状态");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemporarySupplierRecommendationVO reject(Long id, TemporarySupplierRecommendationReviewDTO dto) {
        TemporarySupplierRecommendation recommendation = requireRecommendation(id);
        AuthUser authUser = AuthContextHolder.requireUser();
        PurchaseRequest request = requireRequest(recommendation.getPurchaseRequestId());
        if (!STATUS_PENDING_ROOM_MANAGER.equals(recommendation.getStatus())
            && !STATUS_PENDING_REGION_MANAGER.equals(recommendation.getStatus())) {
            throw new BizException("TEMP_SUPPLIER_REVIEW_STATUS_INVALID", "当前推荐供应商不在待审批状态");
        }
        ensureCanReview(authUser, request, recommendation.getCurrentRoleCode());
        recommendation.setRejectedBy(authUser.getUserId());
        recommendation.setRejectedByName(displayName(authUser));
        recommendation.setRejectReason(firstText(dto == null ? null : dto.getComment(), "不同意推荐该临时供应商"));
        recommendation.setRejectedAt(LocalDateTime.now());
        recommendation.setStatus(STATUS_REJECTED);
        recommendation.setCurrentRoleCode(null);
        recommendationMapper.updateById(recommendation);
        return toVO(recommendation);
    }

    @Override
    public boolean isTemporarySupplier(Long supplierId) {
        if (supplierId == null) {
            return false;
        }
        return isTemporarySupplier(supplierMapper.selectById(supplierId));
    }

    @Override
    public boolean isTemporarySupplier(Supplier supplier) {
        return supplier != null && SUPPLIER_TYPE_TEMP_RECOMMENDED.equalsIgnoreCase(firstText(supplier.getSupplierType(), ""));
    }

    @Override
    public boolean hasActiveRecommendation(Long purchaseRequestId, Long supplierId) {
        return activeRecommendation(purchaseRequestId, supplierId) != null;
    }

    @Override
    public boolean hasAnyActiveRecommendation(Long supplierId) {
        if (supplierId == null) {
            return false;
        }
        return recommendationMapper.selectCount(new LambdaQueryWrapper<TemporarySupplierRecommendation>()
            .eq(TemporarySupplierRecommendation::getSupplierId, supplierId)
            .in(TemporarySupplierRecommendation::getStatus, activeStatuses())) > 0;
    }

    @Override
    public Set<Long> activeRecommendationRequestIds(Long supplierId) {
        if (supplierId == null) {
            return Set.of();
        }
        return recommendationMapper.selectList(new LambdaQueryWrapper<TemporarySupplierRecommendation>()
                .eq(TemporarySupplierRecommendation::getSupplierId, supplierId)
                .in(TemporarySupplierRecommendation::getStatus, activeStatuses()))
            .stream()
            .map(TemporarySupplierRecommendation::getPurchaseRequestId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public ScopedRecommendation resolveScopedRecommendation(Long purchaseRequestId, Long supplierId) {
        TemporarySupplierRecommendation recommendation = activeRecommendation(purchaseRequestId, supplierId);
        if (recommendation == null) {
            return null;
        }
        Set<Long> itemIds = recommendationItemMapper.selectList(new LambdaQueryWrapper<TemporarySupplierRecommendationItem>()
                .eq(TemporarySupplierRecommendationItem::getRecommendationId, recommendation.getId())
                .eq(TemporarySupplierRecommendationItem::getStatus, ITEM_STATUS_ACTIVE))
            .stream()
            .map(TemporarySupplierRecommendationItem::getPurchaseRequestItemId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ScopedRecommendation(recommendation.getId(), itemIds, recommendation.getStatus());
    }

    @Override
    public List<PurchaseRequestItem> filterScopedItems(Long purchaseRequestId, Long supplierId, List<PurchaseRequestItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (!isTemporarySupplier(supplierId)) {
            return items;
        }
        ScopedRecommendation scope = resolveScopedRecommendation(purchaseRequestId, supplierId);
        if (scope == null || scope.itemIds().isEmpty()) {
            return List.of();
        }
        return items.stream()
            .filter(item -> scope.itemIds().contains(item.getId()))
            .toList();
    }

    @Override
    public List<Supplier> eligibleTemporarySuppliers(Long purchaseRequestId, LocalDateTime now) {
        if (purchaseRequestId == null) {
            return List.of();
        }
        List<Long> supplierIds = recommendationMapper.selectList(new LambdaQueryWrapper<TemporarySupplierRecommendation>()
                .eq(TemporarySupplierRecommendation::getPurchaseRequestId, purchaseRequestId)
                .in(TemporarySupplierRecommendation::getStatus, activeStatuses()))
            .stream()
            .map(TemporarySupplierRecommendation::getSupplierId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (supplierIds.isEmpty()) {
            return List.of();
        }
        return supplierMapper.selectBatchIds(supplierIds).stream()
            .filter(this::isTemporarySupplier)
            .filter(supplier -> supplierUsable(supplier, now))
            .sorted(Comparator.comparing(Supplier::getSupplierCode, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    @Override
    public boolean isQuoteCountedForFastCountdown(SupplierQuote quote) {
        return quote == null || !QUOTE_SOURCE_TEMP_RECOMMENDED.equalsIgnoreCase(firstText(quote.getQuoteSource(), QUOTE_SOURCE_NORMAL));
    }

    @Override
    public boolean isQuoteAwardEligible(SupplierQuote quote) {
        if (quote == null) {
            return false;
        }
        if (!QUOTE_SOURCE_TEMP_RECOMMENDED.equalsIgnoreCase(firstText(quote.getQuoteSource(), QUOTE_SOURCE_NORMAL))) {
            return true;
        }
        TemporarySupplierRecommendation recommendation = quote.getRecommendationId() == null
            ? activeRecommendation(quote.getPurchaseRequestId(), quote.getSupplierId())
            : recommendationMapper.selectById(quote.getRecommendationId());
        return recommendation != null && STATUS_APPROVED.equals(recommendation.getStatus());
    }

    @Override
    public String quoteEligibilityText(SupplierQuote quote) {
        if (quote == null) {
            return "";
        }
        if (!QUOTE_SOURCE_TEMP_RECOMMENDED.equalsIgnoreCase(firstText(quote.getQuoteSource(), QUOTE_SOURCE_NORMAL))) {
            return "正式供应商报价";
        }
        TemporarySupplierRecommendation recommendation = quote.getRecommendationId() == null
            ? activeRecommendation(quote.getPurchaseRequestId(), quote.getSupplierId())
            : recommendationMapper.selectById(quote.getRecommendationId());
        if (recommendation == null) {
            return "推荐供应商审批不存在或已失效";
        }
        return STATUS_APPROVED.equals(recommendation.getStatus())
            ? "推荐供应商审批已通过，可参与定标"
            : "推荐供应商审批未完成，暂不能中标";
    }

    private TemporarySupplierRecommendation activeRecommendation(Long purchaseRequestId, Long supplierId) {
        if (purchaseRequestId == null || supplierId == null) {
            return null;
        }
        return recommendationMapper.selectOne(new LambdaQueryWrapper<TemporarySupplierRecommendation>()
            .eq(TemporarySupplierRecommendation::getPurchaseRequestId, purchaseRequestId)
            .eq(TemporarySupplierRecommendation::getSupplierId, supplierId)
            .in(TemporarySupplierRecommendation::getStatus, activeStatuses())
            .orderByDesc(TemporarySupplierRecommendation::getId)
            .last("LIMIT 1"));
    }

    private TemporarySupplierRecommendation requireRecommendation(Long id) {
        TemporarySupplierRecommendation recommendation = recommendationMapper.selectById(id);
        if (recommendation == null) {
            throw new BizException("TEMP_SUPPLIER_RECOMMENDATION_NOT_FOUND", "推荐供应商审批记录不存在");
        }
        return recommendation;
    }

    private PurchaseRequest requireRequest(Long requestId) {
        PurchaseRequest request = purchaseRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        }
        return request;
    }

    private List<String> activeStatuses() {
        return List.of(STATUS_PENDING_ROOM_MANAGER, STATUS_PENDING_REGION_MANAGER, STATUS_APPROVED);
    }

    private boolean supplierUsable(Supplier supplier, LocalDateTime now) {
        return supplier != null
            && "ACTIVE".equalsIgnoreCase(firstText(supplier.getStatus(), ""))
            && (supplier.getBlacklistFlag() == null || supplier.getBlacklistFlag() == 0)
            && (supplier.getBidSuspendedUntil() == null || !supplier.getBidSuspendedUntil().isAfter(now));
    }

    private void applyPageScope(LambdaQueryWrapper<TemporarySupplierRecommendation> wrapper, AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        wrapper.and(scope -> {
            scope.eq(TemporarySupplierRecommendation::getRecommendedBy, authUser.getUserId());
            if (authUser.getRegionId() != null) {
                scope.or().eq(TemporarySupplierRecommendation::getRegionId, authUser.getRegionId());
            }
            scope.or().in(TemporarySupplierRecommendation::getStatus, pendingStatusesForReviewer(authUser));
        });
    }

    private List<String> pendingStatusesForReviewer(AuthUser authUser) {
        List<String> statuses = new ArrayList<>();
        if (DataScopeHelper.hasRole(authUser, ProcurementWorkflow.ROLE_MANAGER)) {
            statuses.add(STATUS_PENDING_ROOM_MANAGER);
        }
        if (DataScopeHelper.hasRole(authUser, ProcurementWorkflow.ROLE_REGION_MANAGER)) {
            statuses.add(STATUS_PENDING_REGION_MANAGER);
        }
        return statuses.isEmpty() ? List.of("__NONE__") : statuses;
    }

    private void ensureCanView(TemporarySupplierRecommendation recommendation, AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)
            || Objects.equals(recommendation.getRecommendedBy(), authUser.getUserId())
            || Objects.equals(recommendation.getRegionId(), authUser.getRegionId())) {
            return;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(recommendation.getPurchaseRequestId());
        if (request != null
            && (canReviewRole(authUser, request, ProcurementWorkflow.ROLE_MANAGER)
            || canReviewRole(authUser, request, ProcurementWorkflow.ROLE_REGION_MANAGER))) {
            return;
        }
        throw new BizException("FORBIDDEN", "当前账号不能查看该推荐供应商审批记录");
    }

    private void ensureInternalUser(AuthUser authUser) {
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "供应商账号不能维护推荐供应商审批");
        }
        if (DataScopeHelper.isSystemAdmin(authUser)
            || DataScopeHelper.hasRole(authUser, "ENGINEER")
            || DataScopeHelper.hasRole(authUser, "PURCHASER")
            || DataScopeHelper.hasRole(authUser, ProcurementWorkflow.ROLE_MANAGER)
            || DataScopeHelper.hasRole(authUser, ProcurementWorkflow.ROLE_REGION_MANAGER)
            || DataScopeHelper.hasRole(authUser, ProcurementWorkflow.ROLE_SENIOR_MANAGER)) {
            return;
        }
        throw new BizException("FORBIDDEN", "当前账号不能维护推荐供应商审批");
    }

    private void ensureCanReview(AuthUser authUser, PurchaseRequest request, String roleCode) {
        if (DataScopeHelper.isSystemAdmin(authUser) || canReviewRole(authUser, request, roleCode)) {
            return;
        }
        throw new BizException("FORBIDDEN", "当前账号不是该节点的审批人");
    }

    private boolean canReviewRole(AuthUser authUser, PurchaseRequest request, String roleCode) {
        if (authUser == null || !StringUtils.hasText(roleCode)) {
            return false;
        }
        List<Long> candidates = approvalRouteResolver.routeCandidateUserIds(request, roleCode);
        if (!candidates.isEmpty()) {
            return candidates.contains(authUser.getUserId());
        }
        return DataScopeHelper.hasRole(authUser, roleCode);
    }

    private TemporarySupplierRecommendationVO toVO(TemporarySupplierRecommendation recommendation) {
        TemporarySupplierRecommendationVO vo = new TemporarySupplierRecommendationVO();
        vo.setId(recommendation.getId());
        vo.setRecommendationNo(recommendation.getRecommendationNo());
        vo.setPurchaseRequestId(recommendation.getPurchaseRequestId());
        vo.setRequestNo(recommendation.getRequestNo());
        vo.setRequestTitle(recommendation.getRequestTitle());
        vo.setRegionId(recommendation.getRegionId());
        vo.setDeptCode(recommendation.getDeptCode());
        vo.setDeptName(recommendation.getDeptName());
        vo.setSupplierId(recommendation.getSupplierId());
        vo.setSupplierCode(recommendation.getSupplierCode());
        vo.setSupplierName(recommendation.getSupplierName());
        vo.setContactName(recommendation.getContactName());
        vo.setContactPhone(recommendation.getContactPhone());
        vo.setContactEmail(recommendation.getContactEmail());
        vo.setRecommendedBy(recommendation.getRecommendedBy());
        vo.setRecommendedByName(recommendation.getRecommendedByName());
        vo.setRecommendReason(recommendation.getRecommendReason());
        vo.setStatus(recommendation.getStatus());
        vo.setStatusText(statusText(recommendation.getStatus()));
        vo.setCurrentRoleCode(recommendation.getCurrentRoleCode());
        vo.setCurrentRoleName(roleText(recommendation.getCurrentRoleCode()));
        vo.setCurrentUserCanReview(canCurrentUserReview(recommendation));
        vo.setRoomManagerUserId(recommendation.getRoomManagerUserId());
        vo.setRoomManagerName(recommendation.getRoomManagerName());
        vo.setRoomManagerComment(recommendation.getRoomManagerComment());
        vo.setRoomManagerReviewedAt(recommendation.getRoomManagerReviewedAt());
        vo.setRegionManagerUserId(recommendation.getRegionManagerUserId());
        vo.setRegionManagerName(recommendation.getRegionManagerName());
        vo.setRegionManagerComment(recommendation.getRegionManagerComment());
        vo.setRegionManagerReviewedAt(recommendation.getRegionManagerReviewedAt());
        vo.setRejectedBy(recommendation.getRejectedBy());
        vo.setRejectedByName(recommendation.getRejectedByName());
        vo.setRejectReason(recommendation.getRejectReason());
        vo.setRejectedAt(recommendation.getRejectedAt());
        vo.setRemark(recommendation.getRemark());
        List<TemporarySupplierRecommendationItemVO> items = recommendationItemMapper.selectList(
                new LambdaQueryWrapper<TemporarySupplierRecommendationItem>()
                    .eq(TemporarySupplierRecommendationItem::getRecommendationId, recommendation.getId())
                    .orderByAsc(TemporarySupplierRecommendationItem::getLineNo))
            .stream()
            .map(this::toItemVO)
            .toList();
        vo.setItemCount(items.size());
        vo.setItems(items);
        vo.setCreatedAt(recommendation.getCreatedAt());
        vo.setUpdatedAt(recommendation.getUpdatedAt());
        return vo;
    }

    private TemporarySupplierRecommendationItemVO toItemVO(TemporarySupplierRecommendationItem item) {
        TemporarySupplierRecommendationItemVO vo = new TemporarySupplierRecommendationItemVO();
        vo.setId(item.getId());
        vo.setPurchaseRequestItemId(item.getPurchaseRequestItemId());
        vo.setLineNo(item.getLineNo());
        vo.setItemCode(item.getItemCode());
        vo.setReqType(item.getReqType());
        vo.setItemName(item.getItemName());
        vo.setSpecModel(item.getSpecModel());
        vo.setUnit(item.getUnit());
        vo.setQty(item.getQty());
        vo.setPurpose(item.getPurpose());
        vo.setStatus(item.getStatus());
        return vo;
    }

    private boolean canCurrentUserReview(TemporarySupplierRecommendation recommendation) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || !StringUtils.hasText(recommendation.getCurrentRoleCode())) {
            return false;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(recommendation.getPurchaseRequestId());
        return request != null && canReviewRole(authUser, request, recommendation.getCurrentRoleCode());
    }

    private String generateSupplierCode() {
        return "TMP" + LocalDateTime.now().format(CODE_FORMATTER)
            + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String generateRecommendationNo() {
        return "TSR" + LocalDateTime.now().format(CODE_FORMATTER)
            + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String displayName(AuthUser authUser) {
        if (authUser == null) {
            return "";
        }
        if (StringUtils.hasText(authUser.getRealName())) {
            return authUser.getRealName();
        }
        if (StringUtils.hasText(authUser.getUsername())) {
            return authUser.getUsername();
        }
        SysUser user = authUser.getUserId() == null ? null : userMapper.selectById(authUser.getUserId());
        if (user != null && StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        return authUser.getUserId() == null ? "" : String.valueOf(authUser.getUserId());
    }

    private String statusText(String status) {
        return switch (firstText(status, "")) {
            case STATUS_PENDING_ROOM_MANAGER -> "待机房经理审核";
            case STATUS_PENDING_REGION_MANAGER -> "待区域经理审核";
            case STATUS_APPROVED -> "已通过";
            case STATUS_REJECTED -> "已驳回";
            case STATUS_CANCELLED -> "已取消";
            default -> firstText(status, "-");
        };
    }

    private String roleText(String roleCode) {
        return switch (firstText(roleCode, "")) {
            case ProcurementWorkflow.ROLE_MANAGER -> "机房经理";
            case ProcurementWorkflow.ROLE_REGION_MANAGER -> "区域经理";
            default -> firstText(roleCode, "-");
        };
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
