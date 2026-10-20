package com.company.procurement.modules.procurement.monthly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyAggregateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandCreateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQueryDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQuantityUpdateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandSupplementDTO;
import com.company.procurement.modules.procurement.monthly.entity.MonthlyProcurementDemand;
import com.company.procurement.modules.procurement.monthly.mapper.MonthlyProcurementDemandMapper;
import com.company.procurement.modules.procurement.monthly.service.MonthlyProcurementService;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyAggregateResultVO;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyDemandVO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestItemDTO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.route.service.ProcurementDepartmentAccessService;
import com.company.procurement.modules.procurement.settings.service.ProcurementSettingsService;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingItemVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MonthlyProcurementServiceImpl implements MonthlyProcurementService {

    public static final String STATUS_POOLING = "POOLING";
    public static final String STATUS_REQUEST_CREATED = "REQUEST_CREATED";
    public static final String STATUS_EMERGENCY_SUBMITTED = "EMERGENCY_SUBMITTED";

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyProcurementDemandMapper demandMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final PurchaseRequestService purchaseRequestService;
    private final ProcurementSettingsService procurementSettingsService;
    private final WorkflowService workflowService;
    private final SysUserMapper userMapper;
    private final ProcurementDepartmentAccessService departmentAccessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonthlyDemandVO submitDemand(MonthlyDemandCreateDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureInternalUser(authUser);

        MonthlyProcurementDemand demand = buildDemand(dto, authUser);
        if (isEmergency(dto)) {
            Long requestId = createPurchaseRequest(List.of(demand), demand.getApplicantUserId());
            purchaseRequestService.submit(requestId);
            demand.setStatus(STATUS_EMERGENCY_SUBMITTED);
            demand.setPurchaseRequestId(requestId);
            demandMapper.insert(demand);
            return toVO(demand);
        }

        demand.setStatus(STATUS_POOLING);
        demandMapper.insert(demand);
        return toVO(demand);
    }

    @Override
    public PageResult<MonthlyDemandVO> pageMyDemands(MonthlyDemandQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        LambdaQueryWrapper<MonthlyProcurementDemand> wrapper = buildQuery(queryDTO)
            .eq(MonthlyProcurementDemand::getApplicantUserId, authUser.getUserId())
            .orderByDesc(MonthlyProcurementDemand::getCreatedAt);
        Page<MonthlyProcurementDemand> page = demandMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public PageResult<MonthlyDemandVO> pageCurrentMonthDemands(MonthlyDemandQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureInternalUser(authUser);

        MonthlyDemandQueryDTO effectiveQuery = queryDTO == null ? new MonthlyDemandQueryDTO() : queryDTO;
        String status = StringUtils.hasText(effectiveQuery.getStatus()) ? effectiveQuery.getStatus().trim() : ProcurementWorkflow.STATUS_DEMAND_POOL;
        if (!ProcurementWorkflow.STATUS_DEMAND_POOL.equals(status) && !STATUS_POOLING.equals(status)) {
            return PageResult.of(effectiveQuery.getPageNo(), effectiveQuery.getPageSize(), 0, List.of());
        }

        YearMonth planMonth = resolveYearMonth(effectiveQuery.getPlanMonth());
        LocalDateTime monthStart = planMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = planMonth.plusMonths(1).atDay(1).atStartOfDay();

        LambdaQueryWrapper<PurchaseRequest> requestWrapper = new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_DEMAND_POOL)
            .ge(PurchaseRequest::getApprovedAt, monthStart)
            .lt(PurchaseRequest::getApprovedAt, nextMonthStart);
        if (StringUtils.hasText(effectiveQuery.getReqType())) {
            requestWrapper.eq(PurchaseRequest::getReqType, effectiveQuery.getReqType().trim());
        }
        requestWrapper.orderByDesc(PurchaseRequest::getApprovedAt).orderByDesc(PurchaseRequest::getId);

        List<PurchaseRequest> requests = purchaseRequestMapper.selectList(requestWrapper);
        if (requests.isEmpty()) {
            return PageResult.of(effectiveQuery.getPageNo(), effectiveQuery.getPageSize(), 0, List.of());
        }

        Map<Long, PurchaseRequest> requestMap = new LinkedHashMap<>();
        List<Long> requestIds = new ArrayList<>();
        for (PurchaseRequest request : requests) {
            requestMap.put(request.getId(), request);
            requestIds.add(request.getId());
        }

        List<PurchaseRequestItem> items = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .in(PurchaseRequestItem::getPurchaseRequestId, requestIds)
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .orderByAsc(PurchaseRequestItem::getPurchaseRequestId)
            .orderByAsc(PurchaseRequestItem::getLineNo));
        Map<Long, List<PurchaseRequestItem>> itemsByRequestId = new HashMap<>();
        for (PurchaseRequestItem item : items) {
            itemsByRequestId.computeIfAbsent(item.getPurchaseRequestId(), key -> new ArrayList<>()).add(item);
        }

        List<MonthlyDemandVO> rows = new ArrayList<>();
        String planMonthText = planMonth.format(MONTH_FORMATTER);
        for (PurchaseRequest request : requests) {
            List<PurchaseRequestItem> requestItems = itemsByRequestId.getOrDefault(request.getId(), List.of());
            for (PurchaseRequestItem item : requestItems) {
                rows.add(toDemandPoolVO(request, item, planMonthText));
            }
        }

        long pageNo = effectiveQuery.getPageNo();
        long pageSize = effectiveQuery.getPageSize();
        int fromIndex = (int) Math.min(rows.size(), Math.max(0, (pageNo - 1) * pageSize));
        int toIndex = (int) Math.min(rows.size(), fromIndex + pageSize);
        return PageResult.of(pageNo, pageSize, rows.size(), rows.subList(fromIndex, toIndex));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonthlyDemandVO submitSupplementDemand(Long sourceDemandId, MonthlyDemandSupplementDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureInternalUser(authUser);
        if (sourceDemandId == null) {
            throw new BizException("DEMAND_NOT_FOUND", "Source demand not found");
        }
        BigDecimal supplementQty = normalizeNonNegative(dto == null ? null : dto.getQty(), "购买数量必须大于 0");
        String purchaseReason = normalizeRequiredText(dto == null ? null : dto.getPurchaseReason(), "购买原因不能为空", 500);

        PurchaseRequestItem sourceItem = purchaseRequestItemMapper.selectById(sourceDemandId);
        if (sourceItem != null) {
            return submitPurchasePoolSupplement(sourceItem, authUser, supplementQty, purchaseReason);
        }

        MonthlyProcurementDemand source = demandMapper.selectById(sourceDemandId);
        if (source == null) {
            throw new BizException("DEMAND_NOT_FOUND", "Source demand not found");
        }
        if (!STATUS_POOLING.equals(source.getStatus())) {
            throw new BizException("DEMAND_STATUS_INVALID", "Only pooling demands can be supplemented");
        }

        SysUser applicant = userMapper.selectById(authUser.getUserId());
        MonthlyProcurementDemand supplement = copyAsSupplementDemand(source, authUser, applicant, supplementQty, purchaseReason);
        demandMapper.insert(supplement);
        return toVO(supplement);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonthlyDemandVO reduceOwnDemandQuantity(Long sourceDemandId, MonthlyDemandQuantityUpdateDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureInternalUser(authUser);
        if (sourceDemandId == null) {
            throw new BizException("DEMAND_NOT_FOUND", "需求不存在");
        }
        BigDecimal newQty = normalizeNonNegative(dto == null ? null : dto.getQty(), "调整后数量必须大于 0");

        PurchaseRequestItem sourceItem = purchaseRequestItemMapper.selectById(sourceDemandId);
        if (sourceItem != null) {
            return reducePurchasePoolItemQuantity(sourceItem, authUser, newQty);
        }

        MonthlyProcurementDemand source = demandMapper.selectById(sourceDemandId);
        if (source == null) {
            throw new BizException("DEMAND_NOT_FOUND", "需求不存在");
        }
        ensureOwnPoolingDemand(source, authUser);
        ensureQtyReduced(source.getQty(), newQty);
        source.setQty(newQty);
        source.setLineAmount(calculateLineAmount(newQty, normalizeMoney(source.getUnitPrice(), "单价必须大于 0")));
        source.setRemark(appendRemark(source.getRemark(), "申请人在需求池将数量调整为 " + quantityText(newQty)));
        demandMapper.updateById(source);
        return toVO(source);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnDemand(Long sourceDemandId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureInternalUser(authUser);
        if (sourceDemandId == null) {
            throw new BizException("DEMAND_NOT_FOUND", "需求不存在");
        }

        PurchaseRequestItem sourceItem = purchaseRequestItemMapper.selectById(sourceDemandId);
        if (sourceItem != null) {
            deletePurchasePoolItem(sourceItem, authUser);
            return;
        }

        MonthlyProcurementDemand source = demandMapper.selectById(sourceDemandId);
        if (source == null) {
            throw new BizException("DEMAND_NOT_FOUND", "需求不存在");
        }
        ensureOwnPoolingDemand(source, authUser);
        source.setStatus(ProcurementWorkflow.STATUS_REJECTED);
        source.setRemark(appendRemark(source.getRemark(), "申请人在需求池删除该商品"));
        demandMapper.updateById(source);
    }

    @Override
    public PageResult<MonthlyDemandVO> pagePendingDemands(MonthlyDemandQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensurePurchaser(authUser);

        LambdaQueryWrapper<MonthlyProcurementDemand> wrapper = buildQuery(queryDTO)
            .eq(MonthlyProcurementDemand::getStatus, STATUS_POOLING)
            .orderByAsc(MonthlyProcurementDemand::getPlanMonth)
            .orderByAsc(MonthlyProcurementDemand::getApplicantUserId)
            .orderByAsc(MonthlyProcurementDemand::getId);
        Page<MonthlyProcurementDemand> page = demandMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonthlyAggregateResultVO aggregate(MonthlyAggregateDTO dto) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser != null) {
            ensurePurchaser(authUser);
        }

        String planMonth = resolvePlanMonth(dto == null ? null : dto.getPlanMonth());
        List<MonthlyProcurementDemand> demands = demandMapper.selectList(new LambdaQueryWrapper<MonthlyProcurementDemand>()
            .eq(MonthlyProcurementDemand::getPlanMonth, planMonth)
            .eq(MonthlyProcurementDemand::getStatus, STATUS_POOLING)
            .orderByAsc(MonthlyProcurementDemand::getApplicantUserId)
            .orderByAsc(MonthlyProcurementDemand::getReqType)
            .orderByAsc(MonthlyProcurementDemand::getId));

        MonthlyAggregateResultVO result = new MonthlyAggregateResultVO();
        result.setPlanMonth(planMonth);
        result.setDemandCount(demands.size());

        Map<String, List<MonthlyProcurementDemand>> grouped = new LinkedHashMap<>();
        for (MonthlyProcurementDemand demand : demands) {
            grouped.computeIfAbsent(groupKey(demand), key -> new ArrayList<>()).add(demand);
        }

        for (List<MonthlyProcurementDemand> group : grouped.values()) {
            Long requestId = createPurchaseRequest(group, group.get(0).getApplicantUserId());
            for (MonthlyProcurementDemand demand : group) {
                demand.setStatus(STATUS_REQUEST_CREATED);
                demand.setPurchaseRequestId(requestId);
                demandMapper.updateById(demand);
            }
            result.getPurchaseRequestIds().add(requestId);
        }
        result.setRequestCount(result.getPurchaseRequestIds().size());
        return result;
    }

    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    @Transactional(rollbackFor = Exception.class)
    public void aggregateCurrentMonthOnSchedule() {
        aggregateForScheduledDate(LocalDate.now());
    }

    void aggregateForScheduledDate(LocalDate runDate) {
        LocalDate aggregationDate = runDate.minusDays(1);
        if (!shouldAggregateOn(aggregationDate)) {
            return;
        }
        MonthlyAggregateDTO dto = new MonthlyAggregateDTO();
        dto.setPlanMonth(aggregationDate.format(MONTH_FORMATTER));
        aggregate(dto);
        workflowService.publishDemandPoolRequestsOnSchedule();
    }

    private boolean shouldAggregateOn(LocalDate aggregationDate) {
        int configuredDay = clampDay(parseIntSetting("monthly_aggregate_day", 20));
        int effectiveDay = Math.min(configuredDay, aggregationDate.lengthOfMonth());
        return aggregationDate.getDayOfMonth() == effectiveDay;
    }

    private int parseIntSetting(String key, int defaultValue) {
        try {
            return Integer.parseInt(settingValue(key, String.valueOf(defaultValue)));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String settingValue(String key, String defaultValue) {
        return procurementSettingsService.getSettings().getSettings().stream()
            .filter(setting -> key.equals(setting.getSettingKey()))
            .map(ProcurementSettingItemVO::getSettingValue)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(defaultValue);
    }

    private int clampDay(int day) {
        return Math.min(31, Math.max(1, day));
    }

    private MonthlyProcurementDemand buildDemand(MonthlyDemandCreateDTO dto, AuthUser authUser) {
        BigDecimal qty = normalizeNonNegative(dto.getQty(), "数量必须大于 0");
        BigDecimal unitPrice = normalizeMoney(dto.getUnitPrice(), "单价必须大于 0");
        BigDecimal taxRate = dto.getTaxRate() == null ? BigDecimal.ZERO : dto.getTaxRate();
        ProcurementDepartmentAccessService.DepartmentSelection selection = departmentAccessService.resolveSelection(
            authUser,
            dto.getApplicantUserId(),
            dto.getDeptCode(),
            dto.getDeptName(),
            dto.getActingReason()
        );

        MonthlyProcurementDemand demand = new MonthlyProcurementDemand();
        demand.setDemandNo(generateDemandNo());
        demand.setPlanMonth(resolvePlanMonth(dto.getPlanMonth()));
        demand.setApplicantUserId(selection.applicant().getId());
        demand.setRegionId(selection.applicant().getRegionId() == null ? authUser.getRegionId() : selection.applicant().getRegionId());
        demand.setDeptCode(firstText(selection.route().getDeptCode(), selection.route().getCostCenterCode()));
        demand.setDeptName(firstText(selection.route().getDeptName(), selection.route().getRoomName()));
        demand.setBudgetCode(firstText(selection.route().getCostCenterCode(), dto.getBudgetCode(), selection.route().getDeptCode()));
        demand.setReqType(normalizeReqType(dto.getReqType()));
        demand.setUrgencyLevel(dto.getUrgencyLevel() == null ? 1 : dto.getUrgencyLevel());
        demand.setEmergencyFlag(isEmergency(dto) ? 1 : 0);
        demand.setReqTitle(dto.getReqTitle());
        demand.setItemCode(generateMaterialCode());
        demand.setItemName(dto.getItemName());
        demand.setSpecModel(dto.getSpecModel());
        demand.setUnit(dto.getUnit());
        demand.setQty(qty);
        demand.setUnitPrice(unitPrice);
        demand.setTaxRate(taxRate);
        demand.setLineAmount(qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
        demand.setNeedDate(dto.getNeedDate());
        demand.setPurchaseReason(dto.getPurchaseReason());
        demand.setEcommerceUrl(dto.getEcommerceUrl());
        demand.setSourceChannel("FEISHU_WINDOW");
        demand.setRemark(buildDemandRemark(dto.getRemark(), selection));
        return demand;
    }

    private MonthlyProcurementDemand copyAsSupplementDemand(MonthlyProcurementDemand source,
                                                            AuthUser authUser,
                                                            SysUser applicant,
                                                            BigDecimal supplementQty,
                                                            String purchaseReason) {
        BigDecimal unitPrice = normalizeMoney(source.getUnitPrice(), "单价必须大于 0");
        BigDecimal lineAmount = calculateLineAmount(supplementQty, unitPrice);
        MonthlyProcurementDemand demand = new MonthlyProcurementDemand();
        demand.setDemandNo(generateDemandNo());
        demand.setPlanMonth(resolvePlanMonth(source.getPlanMonth()));
        demand.setApplicantUserId(authUser.getUserId());
        demand.setRegionId(chooseRegionId(authUser, applicant, source));
        demand.setDeptCode(firstText(applicant == null ? null : applicant.getDeptCode(), source.getDeptCode()));
        demand.setDeptName(firstText(applicant == null ? null : applicant.getDeptName(), source.getDeptName()));
        demand.setBudgetCode(source.getBudgetCode());
        demand.setReqType(source.getReqType());
        demand.setUrgencyLevel(1);
        demand.setEmergencyFlag(0);
        demand.setReqTitle(limitText("补充采购-" + source.getReqTitle(), 255));
        demand.setItemCode(generateMaterialCode());
        demand.setItemName(source.getItemName());
        demand.setSpecModel(source.getSpecModel());
        demand.setUnit(source.getUnit());
        demand.setQty(supplementQty);
        demand.setUnitPrice(unitPrice);
        demand.setTaxRate(source.getTaxRate());
        demand.setLineAmount(lineAmount);
        demand.setNeedDate(source.getNeedDate());
        demand.setPurchaseReason(purchaseReason);
        demand.setEcommerceUrl(source.getEcommerceUrl());
        demand.setSourceChannel("MONTHLY_POOL_SUPPLEMENT");
        demand.setStatus(STATUS_POOLING);
        demand.setRemark(limitText("由本月需求池补充采购需求，来源需求：" + source.getDemandNo(), 500));
        return demand;
    }

    private MonthlyDemandVO submitPurchasePoolSupplement(PurchaseRequestItem sourceItem,
                                                         AuthUser authUser,
                                                         BigDecimal supplementQty,
                                                         String purchaseReason) {
        PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceItem.getPurchaseRequestId());
        if (sourceRequest == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!ProcurementWorkflow.STATUS_DEMAND_POOL.equals(sourceRequest.getStatus())) {
            throw new BizException("DEMAND_STATUS_INVALID", "Only demand-pool items can be supplemented");
        }
        if (ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(sourceItem.getStatus())) {
            throw new BizException("DEMAND_STATUS_INVALID", "Rejected items cannot be supplemented");
        }

        SysUser applicant = userMapper.selectById(authUser.getUserId());
        BigDecimal unitPrice = normalizeMoney(sourceItem.getUnitPrice(), "单价必须大于 0");
        BigDecimal lineAmount = calculateLineAmount(supplementQty, unitPrice);
        PurchaseRequestCreateDTO request = new PurchaseRequestCreateDTO();
        request.setReqTitle(limitText("补充采购-" + sourceRequest.getReqTitle(), 255));
        request.setReqType(sourceRequest.getReqType());
        request.setApplicantUserId(authUser.getUserId());
        request.setPurchaserUserId(authUser.getUserId());
        request.setRegionId(chooseRegionId(authUser, applicant, sourceRequest));
        request.setDeptCode(firstText(applicant == null ? null : applicant.getDeptCode(), sourceRequest.getDeptCode()));
        request.setDeptName(firstText(applicant == null ? null : applicant.getDeptName(), sourceRequest.getDeptName()));
        request.setBudgetCode(sourceRequest.getBudgetCode());
        request.setCurrency(StringUtils.hasText(sourceRequest.getCurrency()) ? sourceRequest.getCurrency() : "CNY");
        request.setTotalAmount(lineAmount);
        request.setUrgencyLevel(1);
        request.setNeedDate(sourceItem.getNeedDate() == null ? sourceRequest.getNeedDate() : sourceItem.getNeedDate());
        request.setReqReason(purchaseReason);
        request.setAwardStrategy(ProcurementWorkflow.resolveAutoAwardStrategy(lineAmount));
        request.setRemark(limitText("由本月需求池补充采购需求，来源申请：" + sourceRequest.getReqNo() + "，来源行号：" + sourceItem.getLineNo(), 500));

        PurchaseRequestItemDTO item = new PurchaseRequestItemDTO();
        item.setItemCode(sourceItem.getItemCode());
        item.setReqType(sourceItem.getReqType());
        item.setItemName(sourceItem.getItemName());
        item.setSpecModel(sourceItem.getSpecModel());
        item.setItemBrand(sourceItem.getItemBrand());
        item.setUnit(sourceItem.getUnit());
        item.setQty(supplementQty);
        item.setUnitPrice(unitPrice);
        item.setTaxRate(sourceItem.getTaxRate());
        item.setLineAmount(lineAmount);
        item.setNeedDate(sourceItem.getNeedDate());
        item.setPurpose(purchaseReason);
        item.setEcommerceUrl(sourceItem.getEcommerceUrl());
        item.setStatus(ProcurementWorkflow.ITEM_STATUS_ACTIVE);
        item.setRemark(limitText("补充来源：" + sourceRequest.getReqNo() + "-" + sourceItem.getLineNo(), 500));
        request.setItems(List.of(item));

        Long requestId = purchaseRequestService.create(request);
        purchaseRequestService.submit(requestId);
        PurchaseRequest createdRequest = purchaseRequestMapper.selectById(requestId);
        PurchaseRequestItem createdItem = purchaseRequestItemMapper.selectOne(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, requestId)
            .orderByAsc(PurchaseRequestItem::getLineNo)
            .last("LIMIT 1"));
        String planMonth = LocalDate.now().format(MONTH_FORMATTER);
        return toDemandPoolVO(createdRequest, createdItem, planMonth);
    }

    private MonthlyDemandVO reducePurchasePoolItemQuantity(PurchaseRequestItem sourceItem,
                                                           AuthUser authUser,
                                                           BigDecimal newQty) {
        PurchaseRequest sourceRequest = ensureOwnDemandPoolItem(sourceItem, authUser);
        ensureQtyReduced(sourceItem.getQty(), newQty);
        sourceItem.setQty(newQty);
        sourceItem.setLineAmount(calculateLineAmount(newQty, normalizeMoney(sourceItem.getUnitPrice(), "单价必须大于 0")));
        sourceItem.setRemark(appendRemark(sourceItem.getRemark(), "申请人在需求池将数量调整为 " + quantityText(newQty)));
        purchaseRequestItemMapper.updateById(sourceItem);
        refreshDemandPoolRequestAfterApplicantChange(sourceRequest, "申请人在需求池调整商品数量");
        return toDemandPoolVO(sourceRequest, sourceItem, planMonthForRequest(sourceRequest));
    }

    private void deletePurchasePoolItem(PurchaseRequestItem sourceItem, AuthUser authUser) {
        PurchaseRequest sourceRequest = ensureOwnDemandPoolItem(sourceItem, authUser);
        sourceItem.setStatus(ProcurementWorkflow.ITEM_STATUS_REJECTED);
        sourceItem.setRemark(appendRemark(sourceItem.getRemark(), "申请人在需求池删除该商品"));
        purchaseRequestItemMapper.updateById(sourceItem);
        refreshDemandPoolRequestAfterApplicantChange(sourceRequest, "申请人在需求池删除商品");
    }

    private PurchaseRequest ensureOwnDemandPoolItem(PurchaseRequestItem sourceItem, AuthUser authUser) {
        if (ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(sourceItem.getStatus())) {
            throw new BizException("DEMAND_STATUS_INVALID", "已删除或已驳回的商品不能再修改");
        }
        PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceItem.getPurchaseRequestId());
        if (sourceRequest == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        }
        if (!ProcurementWorkflow.STATUS_DEMAND_POOL.equals(sourceRequest.getStatus())) {
            throw new BizException("DEMAND_STATUS_INVALID", "只有需求池内商品可以修改");
        }
        if (!Objects.equals(sourceRequest.getApplicantUserId(), authUser.getUserId())) {
            throw new BizException("FORBIDDEN", "只能修改自己提交的需求池商品");
        }
        return sourceRequest;
    }

    private void ensureOwnPoolingDemand(MonthlyProcurementDemand source, AuthUser authUser) {
        if (!STATUS_POOLING.equals(source.getStatus())) {
            throw new BizException("DEMAND_STATUS_INVALID", "只有需求池内商品可以修改");
        }
        if (!Objects.equals(source.getApplicantUserId(), authUser.getUserId())) {
            throw new BizException("FORBIDDEN", "只能修改自己提交的需求池商品");
        }
    }

    private void ensureQtyReduced(BigDecimal currentQty, BigDecimal newQty) {
        if (currentQty == null || currentQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("DEMAND_VALUE_INVALID", "原数量无效，不能修改");
        }
        if (newQty.compareTo(currentQty.setScale(2, RoundingMode.HALF_UP)) >= 0) {
            throw new BizException("DEMAND_QTY_ONLY_REDUCE", "需求池商品数量只能减少，不能增加");
        }
    }

    private void refreshDemandPoolRequestAfterApplicantChange(PurchaseRequest sourceRequest, String actionRemark) {
        List<PurchaseRequestItem> sourceItems = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, sourceRequest.getId()));
        List<PurchaseRequestItem> activeItems = sourceItems.stream()
            .filter(item -> !ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(item.getStatus()))
            .toList();
        BigDecimal activeAmount = sumRequestItemsAmount(activeItems);
        sourceRequest.setTotalAmount(activeAmount);
        sourceRequest.setAwardStrategy(ProcurementWorkflow.resolveAutoAwardStrategy(activeAmount));
        if (activeItems.isEmpty()) {
            sourceRequest.setApprovalStatus(ProcurementWorkflow.APPROVAL_REJECTED);
            sourceRequest.setStatus(ProcurementWorkflow.STATUS_REJECTED);
            sourceRequest.setRejectedAt(LocalDateTime.now());
            sourceRequest.setRemark(appendRemark(sourceRequest.getRemark(), actionRemark + "，无有效商品，移出需求池"));
        } else {
            sourceRequest.setRemark(appendRemark(sourceRequest.getRemark(), actionRemark));
        }
        purchaseRequestMapper.updateById(sourceRequest);
    }

    private Long createPurchaseRequest(List<MonthlyProcurementDemand> demands, Long applicantUserId) {
        MonthlyProcurementDemand first = demands.get(0);
        PurchaseRequestCreateDTO request = new PurchaseRequestCreateDTO();
        request.setReqTitle(buildRequestTitle(first, demands));
        request.setReqType(first.getReqType());
        request.setApplicantUserId(applicantUserId);
        request.setPurchaserUserId(applicantUserId);
        request.setRegionId(first.getRegionId());
        request.setDeptCode(first.getDeptCode());
        request.setDeptName(first.getDeptName());
        request.setBudgetCode(first.getBudgetCode());
        request.setCurrency("CNY");
        request.setUrgencyLevel(first.getEmergencyFlag() != null && first.getEmergencyFlag() == 1 ? 2 : 1);
        request.setNeedDate(resolveNeedDate(demands));
        request.setReqReason("由飞书窗口采购需求自动生成");
        request.setRemark(first.getEmergencyFlag() != null && first.getEmergencyFlag() == 1 ? "紧急采购自动提交" : "月度采购池汇总生成");

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseRequestItemDTO> items = new ArrayList<>();
        for (MonthlyProcurementDemand demand : demands) {
            PurchaseRequestItemDTO item = new PurchaseRequestItemDTO();
            item.setItemCode(demand.getItemCode());
            item.setReqType(demand.getReqType());
            item.setItemName(demand.getItemName());
            item.setSpecModel(demand.getSpecModel());
            item.setUnit(demand.getUnit());
            item.setQty(demand.getQty());
            item.setUnitPrice(demand.getUnitPrice());
            item.setTaxRate(demand.getTaxRate());
            item.setLineAmount(demand.getLineAmount());
            item.setNeedDate(demand.getNeedDate());
            item.setPurpose(demand.getPurchaseReason());
            item.setEcommerceUrl(demand.getEcommerceUrl());
            item.setStatus("ACTIVE");
            items.add(item);
            total = total.add(demand.getLineAmount());
        }
        request.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        request.setAwardStrategy(ProcurementWorkflow.resolveAutoAwardStrategy(request.getTotalAmount()));
        request.setItems(items);
        return purchaseRequestService.create(request);
    }

    private String buildRequestTitle(MonthlyProcurementDemand first, List<MonthlyProcurementDemand> demands) {
        if (first.getEmergencyFlag() != null && first.getEmergencyFlag() == 1) {
            return "紧急采购-" + first.getReqTitle();
        }
        return first.getPlanMonth() + "月度采购-" + first.getReqType() + "-" + first.getDeptName() + "(" + demands.size() + "项)";
    }

    private LocalDate resolveNeedDate(List<MonthlyProcurementDemand> demands) {
        return demands.stream()
            .map(MonthlyProcurementDemand::getNeedDate)
            .filter(date -> date != null)
            .min(LocalDate::compareTo)
            .orElse(null);
    }

    private LambdaQueryWrapper<MonthlyProcurementDemand> buildQuery(MonthlyDemandQueryDTO queryDTO) {
        LambdaQueryWrapper<MonthlyProcurementDemand> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getPlanMonth())) {
            wrapper.eq(MonthlyProcurementDemand::getPlanMonth, queryDTO.getPlanMonth().trim());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(MonthlyProcurementDemand::getStatus, queryDTO.getStatus().trim());
        }
        if (StringUtils.hasText(queryDTO.getReqType())) {
            wrapper.eq(MonthlyProcurementDemand::getReqType, queryDTO.getReqType().trim());
        }
        return wrapper;
    }

    private MonthlyDemandVO toVO(MonthlyProcurementDemand demand) {
        MonthlyDemandVO vo = new MonthlyDemandVO();
        vo.setId(demand.getId());
        vo.setDemandNo(demand.getDemandNo());
        vo.setPlanMonth(demand.getPlanMonth());
        vo.setApplicantUserId(demand.getApplicantUserId());
        vo.setRegionId(demand.getRegionId());
        vo.setDeptName(demand.getDeptName());
        vo.setBudgetCode(demand.getBudgetCode());
        vo.setReqType(demand.getReqType());
        vo.setUrgencyLevel(demand.getUrgencyLevel());
        vo.setEmergency(demand.getEmergencyFlag() != null && demand.getEmergencyFlag() == 1);
        vo.setReqTitle(demand.getReqTitle());
        vo.setItemCode(demand.getItemCode());
        vo.setItemName(demand.getItemName());
        vo.setSpecModel(demand.getSpecModel());
        vo.setUnit(demand.getUnit());
        vo.setQty(demand.getQty());
        vo.setUnitPrice(demand.getUnitPrice());
        vo.setLineAmount(demand.getLineAmount());
        vo.setNeedDate(demand.getNeedDate());
        vo.setPurchaseReason(demand.getPurchaseReason());
        vo.setEcommerceUrl(demand.getEcommerceUrl());
        vo.setSourceChannel(demand.getSourceChannel());
        vo.setStatus(demand.getStatus());
        vo.setPurchaseRequestId(demand.getPurchaseRequestId());
        vo.setCreatedAt(demand.getCreatedAt());
        return vo;
    }

    private MonthlyDemandVO toDemandPoolVO(PurchaseRequest request, PurchaseRequestItem item, String planMonth) {
        MonthlyDemandVO vo = new MonthlyDemandVO();
        vo.setId(item.getId());
        vo.setDemandNo(request.getReqNo());
        vo.setPlanMonth(planMonth);
        vo.setApplicantUserId(request.getApplicantUserId());
        vo.setRegionId(request.getRegionId());
        vo.setDeptName(request.getDeptName());
        vo.setBudgetCode(request.getBudgetCode());
        vo.setReqType(request.getReqType());
        vo.setUrgencyLevel(request.getUrgencyLevel());
        vo.setEmergency(request.getUrgencyLevel() != null && request.getUrgencyLevel() >= 2);
        vo.setReqTitle(request.getReqTitle());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setSpecModel(item.getSpecModel());
        vo.setUnit(item.getUnit());
        vo.setQty(item.getQty());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setLineAmount(item.getLineAmount());
        vo.setNeedDate(item.getNeedDate());
        vo.setPurchaseReason(item.getPurpose());
        vo.setEcommerceUrl(item.getEcommerceUrl());
        vo.setSourceChannel("PURCHASE_DEMAND_POOL");
        vo.setStatus(request.getStatus());
        vo.setPurchaseRequestId(request.getId());
        vo.setCreatedAt(request.getApprovedAt() == null ? request.getCreatedAt() : request.getApprovedAt());
        return vo;
    }

    private boolean isEmergency(MonthlyDemandCreateDTO dto) {
        return Boolean.TRUE.equals(dto.getEmergency()) || (dto.getUrgencyLevel() != null && dto.getUrgencyLevel() >= 2);
    }

    private String normalizeReqType(String reqType) {
        String value = reqType == null ? "" : reqType.trim();
        if ("工程项目".equals(value)) {
            return "工程项目";
        }
        if (List.of("资产", "实物资产", "无形资产", "长摊", "长期待摊", "实物资产/无形资产/长摊等").contains(value)) {
            return "实物资产/无形资产/长摊等";
        }
        if (List.of("低值易耗", "服务", "费用", "其他", "服务/费用/低值易耗等").contains(value)) {
            return "服务/费用/低值易耗等";
        }
        throw new BizException("REQ_TYPE_INVALID", "采购类型只能是工程项目、实物资产/无形资产/长摊等、服务/费用/低值易耗等");
    }

    private BigDecimal normalizeNonNegative(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("DEMAND_VALUE_INVALID", message);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("DEMAND_VALUE_INVALID", message);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLineAmount(BigDecimal qty, BigDecimal unitPrice) {
        return qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumRequestItemsAmount(List<PurchaseRequestItem> items) {
        return items.stream()
            .map(PurchaseRequestItem::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String planMonthForRequest(PurchaseRequest request) {
        LocalDateTime baseTime = request.getApprovedAt();
        if (baseTime == null) {
            baseTime = request.getCreatedAt();
        }
        if (baseTime == null) {
            baseTime = LocalDateTime.now();
        }
        return YearMonth.from(baseTime).format(MONTH_FORMATTER);
    }

    private String appendRemark(String original, String addition) {
        if (!StringUtils.hasText(addition)) {
            return original;
        }
        if (!StringUtils.hasText(original)) {
            return limitText(addition, 500);
        }
        return limitText(original + "；" + addition, 500);
    }

    private String buildDemandRemark(String original, ProcurementDepartmentAccessService.DepartmentSelection selection) {
        if (selection == null || !selection.acting()) {
            return original;
        }
        String actingText = "管理员代办：" + firstText(selection.actingUserName(), String.valueOf(selection.actingUserId()))
            + " 代申请人选择部门 " + firstText(selection.route().getDeptName(), selection.route().getDeptCode(), "-")
            + "，原因：" + firstText(selection.actingReason(), "管理员代用户选择采购部门");
        return appendRemark(original, actingText);
    }

    private String quantityText(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String normalizeRequiredText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BizException("DEMAND_VALUE_INVALID", message);
        }
        return limitText(value.trim(), maxLength);
    }

    private String resolvePlanMonth(String planMonth) {
        if (StringUtils.hasText(planMonth)) {
            return planMonth.trim();
        }
        return LocalDate.now().format(MONTH_FORMATTER);
    }

    private YearMonth resolveYearMonth(String planMonth) {
        try {
            return YearMonth.parse(resolvePlanMonth(planMonth), MONTH_FORMATTER);
        } catch (Exception ex) {
            throw new BizException("PLAN_MONTH_INVALID", "Plan month must be formatted as yyyy-MM");
        }
    }

    private String groupKey(MonthlyProcurementDemand demand) {
        return String.join("|",
            String.valueOf(demand.getApplicantUserId()),
            String.valueOf(demand.getRegionId()),
            safe(demand.getDeptCode()),
            safe(demand.getBudgetCode()),
            safe(demand.getReqType())
        );
    }

    private Long chooseRegionId(AuthUser authUser, SysUser applicant, MonthlyProcurementDemand source) {
        if (authUser.getRegionId() != null) {
            return authUser.getRegionId();
        }
        if (applicant != null && applicant.getRegionId() != null) {
            return applicant.getRegionId();
        }
        return source.getRegionId();
    }

    private Long chooseRegionId(AuthUser authUser, SysUser applicant, PurchaseRequest source) {
        if (authUser.getRegionId() != null) {
            return authUser.getRegionId();
        }
        if (applicant != null && applicant.getRegionId() != null) {
            return applicant.getRegionId();
        }
        return source.getRegionId();
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
        return values.length == 0 ? null : values[values.length - 1];
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String generateDemandNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "FD" + date + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String generateMaterialCode() {
        return "MAT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void ensureInternalUser(AuthUser authUser) {
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "供应商账号不能提交内部采购需求");
        }
    }

    private void ensurePurchaser(AuthUser authUser) {
        ensureInternalUser(authUser);
        if (DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (authUser.getRoleCodes() == null || !authUser.getRoleCodes().contains("PURCHASER")) {
            throw new BizException("FORBIDDEN", "仅采购员或管理员可以汇总月度采购");
        }
    }
}
