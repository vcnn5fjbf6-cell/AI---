package com.company.procurement.modules.procurement.labor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.labor.dto.PersonalLaborRequestDTO;
import com.company.procurement.modules.procurement.labor.dto.PersonalLaborRequestItemDTO;
import com.company.procurement.modules.procurement.labor.entity.PersonalLaborItemRecord;
import com.company.procurement.modules.procurement.labor.mapper.PersonalLaborItemRecordMapper;
import com.company.procurement.modules.procurement.labor.service.PersonalLaborService;
import com.company.procurement.modules.procurement.labor.vo.PersonalLaborItemStatusVO;
import com.company.procurement.modules.procurement.labor.vo.PersonalLaborSubmitResultVO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestItemDTO;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonalLaborServiceImpl implements PersonalLaborService {

    private static final String SETTING_KEY = "personal_labor_item_rules_json";
    private static final String REQ_TYPE_LOW_VALUE = "服务/费用/低值易耗等";
    private static final String STATUS_REQUESTED = "REQUESTED";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String ITEM_PREFIX = "LABOR-";
    private static final BigDecimal DEFAULT_REFERENCE_UNIT_PRICE = new BigDecimal("0.01");

    private final ProcurementSettingMapper settingMapper;
    private final PersonalLaborItemRecordMapper recordMapper;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PersonalLaborItemStatusVO> listMyItems() {
        AuthUser authUser = requireInternalUser();
        LocalDate today = LocalDate.now();
        List<PersonalLaborRule> rules = loadRules();
        List<PersonalLaborItemStatusVO> result = new ArrayList<>();
        for (PersonalLaborRule rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                result.add(toStatus(rule, null, today, "DISABLED", "已停用", false, false));
                continue;
            }
            PersonalLaborItemRecord latest = latestRecord(authUser.getUserId(), rule.getItemCode());
            result.add(toStatus(rule, latest, today, resolveStatus(rule, latest, today), null, null, null));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PersonalLaborSubmitResultVO submitRequest(PersonalLaborRequestDTO dto) {
        AuthUser authUser = requireInternalUser();
        if (dto == null || CollectionUtils.isEmpty(dto.getItems())) {
            throw new BizException("LABOR_ITEMS_EMPTY", "请选择要申领的劳保用品");
        }
        boolean emergency = Boolean.TRUE.equals(dto.getEmergency());
        if (emergency && !StringUtils.hasText(dto.getReason())) {
            throw new BizException("LABOR_EMERGENCY_REASON_REQUIRED", "紧急购买必须填写原因");
        }
        if (!StringUtils.hasText(dto.getDeptName())) {
            throw new BizException("LABOR_DEPARTMENT_REQUIRED", "请选择申请部门/机房楼栋");
        }

        Map<String, PersonalLaborRule> ruleMap = ruleMap(loadRules());
        List<PurchaseRequestItemDTO> requestItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        for (PersonalLaborRequestItemDTO item : dto.getItems()) {
            PersonalLaborRule rule = ruleMap.get(normalizeCode(item.getItemCode()));
            validateRequestItem(rule, item, emergency, authUser.getUserId(), today);
            BigDecimal qty = item.getQty().setScale(0, RoundingMode.UNNECESSARY);
            BigDecimal unitPrice = DEFAULT_REFERENCE_UNIT_PRICE;
            BigDecimal lineAmount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineAmount);

            PurchaseRequestItemDTO requestItem = new PurchaseRequestItemDTO();
            requestItem.setItemCode(ITEM_PREFIX + rule.getItemCode());
            requestItem.setReqType(REQ_TYPE_LOW_VALUE);
            requestItem.setItemName(rule.getItemName());
            requestItem.setSpecModel(trimToNull(item.getSpecModel()));
            requestItem.setUnit(rule.getUnit());
            requestItem.setQty(qty);
            requestItem.setUnitPrice(unitPrice);
            requestItem.setTaxRate(BigDecimal.ZERO);
            requestItem.setLineAmount(lineAmount);
            requestItem.setNeedDate(dto.getNeedDate());
            requestItem.setPurpose(firstText(item.getPurpose(), dto.getReason(), "个人劳保用品申领"));
            requestItem.setReceivingCity(trimToNull(dto.getReceivingCity()));
            requestItem.setStatus("ACTIVE");
            requestItem.setRemark(buildItemRemark(rule, emergency));
            requestItems.add(requestItem);
        }

        PurchaseRequestCreateDTO request = new PurchaseRequestCreateDTO();
        request.setReqTitle(resolveTitle(dto, authUser, emergency));
        request.setReqType(REQ_TYPE_LOW_VALUE);
        request.setApplicantUserId(authUser.getUserId());
        request.setPurchaserUserId(authUser.getUserId());
        request.setRegionId(authUser.getRegionId());
        request.setDeptCode(trimToNull(dto.getDeptCode()));
        request.setDeptName(dto.getDeptName().trim());
        request.setBudgetCode(trimToNull(dto.getBudgetCode()));
        request.setCurrency("CNY");
        request.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        request.setUrgencyLevel(emergency ? 2 : 1);
        request.setNeedDate(dto.getNeedDate() == null ? today.plusDays(7) : dto.getNeedDate());
        request.setReqReason(buildRequestReason(dto, emergency, requestItems.size()));
        request.setRemark("来源：个人劳保申领；按个人劳保周期和数量规则校验后生成。");
        request.setAwardStrategy("TOTAL_LOWEST");
        request.setItems(requestItems);

        Long requestId = workflowService.createRequest(request);
        WorkflowSubmitResultVO submitResult = workflowService.submitRequest(requestId);
        String requestNo = submitResult == null ? null : submitResult.getReqNo();
        saveRequestedRecords(authUser, dto, requestId, requestNo, requestItems, ruleMap, emergency);

        PersonalLaborSubmitResultVO result = new PersonalLaborSubmitResultVO();
        result.setPurchaseRequestId(requestId);
        result.setPurchaseRequestNo(requestNo);
        result.setNextNodeName(submitResult == null ? null : submitResult.getNextNodeName());
        result.setMessage("已生成采购申请并提交审批");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PersonalLaborItemStatusVO confirmReceived(Long recordId) {
        AuthUser authUser = requireInternalUser();
        PersonalLaborItemRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException("LABOR_RECORD_NOT_FOUND", "劳保领用记录不存在");
        }
        if (!authUser.getUserId().equals(record.getUserId())) {
            throw new BizException("FORBIDDEN", "只能确认自己的劳保用品领用记录");
        }
        if (!recordMapper.hasApprovedProcurement(recordId)) {
            throw new BizException("LABOR_PROCUREMENT_NOT_APPROVED", "关联采购申请审批通过后才能确认领用");
        }
        if (!recordMapper.hasCompletedDelivery(recordId)) {
            throw new BizException("LABOR_DELIVERY_NOT_COMPLETED", "关联采购订单完成交付并确认收货后才能确认领用");
        }
        PersonalLaborRule rule = ruleMap(loadRules()).get(normalizeCode(record.getItemCode()));
        if (rule == null) {
            throw new BizException("LABOR_RULE_NOT_FOUND", "劳保用品规则不存在");
        }
        LocalDate today = LocalDate.now();
        if (STATUS_RECEIVED.equalsIgnoreCase(String.valueOf(record.getStatus()))) {
            return toStatus(rule, record, today, STATUS_RECEIVED, "已领用", false, false);
        }
        record.setStatus(STATUS_RECEIVED);
        record.setLastReceivedDate(today);
        record.setNextDueDate(today.plusDays(safePositive(rule.getCycleDays(), 365)));
        recordMapper.updateById(record);
        return toStatus(rule, record, today, STATUS_RECEIVED, "已领用", false, false);
    }

    private AuthUser requireInternalUser() {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "供应商账号不能提交个人劳保申领");
        }
        return authUser;
    }

    private void validateRequestItem(PersonalLaborRule rule,
                                     PersonalLaborRequestItemDTO item,
                                     boolean emergency,
                                     Long userId,
                                     LocalDate today) {
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
            throw new BizException("LABOR_RULE_DISABLED", "所选劳保用品未启用");
        }
        if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0 || item.getQty().stripTrailingZeros().scale() > 0) {
            throw new BizException("LABOR_QTY_INVALID", rule.getItemName() + " 的申领数量必须是大于 0 的整数");
        }
        if (rule.getQuantityLimit() != null
            && rule.getQuantityLimit().compareTo(BigDecimal.ZERO) > 0
            && item.getQty().compareTo(rule.getQuantityLimit()) > 0) {
            throw new BizException("LABOR_QTY_LIMIT_EXCEEDED", rule.getItemName() + " 的申领数量不能超过 " + strip(rule.getQuantityLimit()) + rule.getUnit());
        }
        PersonalLaborItemRecord latest = latestRecord(userId, rule.getItemCode());
        if (latest != null && STATUS_REQUESTED.equalsIgnoreCase(String.valueOf(latest.getStatus()))) {
            throw new BizException("LABOR_REQUEST_IN_PROGRESS", rule.getItemName() + " 已有进行中的申领记录");
        }
        if (emergency) {
            if (!Boolean.TRUE.equals(rule.getEmergencyEnabled())) {
                throw new BizException("LABOR_EMERGENCY_DISABLED", rule.getItemName() + " 未开启紧急购买");
            }
            return;
        }
        PersonalLaborItemStatusVO status = toStatus(rule, latest, today, resolveStatus(rule, latest, today), null, null, null);
        if (!Boolean.TRUE.equals(status.getCanApply())) {
            throw new BizException("LABOR_CYCLE_NOT_READY", rule.getItemName() + " 还未到允许申领时间，最早可在 " + status.getEarliestApplyDate() + " 申领");
        }
    }

    private void saveRequestedRecords(AuthUser authUser,
                                      PersonalLaborRequestDTO dto,
                                      Long requestId,
                                      String requestNo,
                                      List<PurchaseRequestItemDTO> requestItems,
                                      Map<String, PersonalLaborRule> ruleMap,
                                      boolean emergency) {
        for (PurchaseRequestItemDTO requestItem : requestItems) {
            String itemCode = requestItem.getItemCode().replaceFirst("^" + ITEM_PREFIX, "");
            PersonalLaborRule rule = ruleMap.get(normalizeCode(itemCode));
            PersonalLaborItemRecord record = new PersonalLaborItemRecord();
            record.setUserId(authUser.getUserId());
            record.setUsername(authUser.getUsername());
            record.setRealName(firstText(authUser.getRealName(), authUser.getUsername(), "-"));
            record.setDeptCode(firstText(dto.getDeptCode(), authUser.getDeptCode(), ""));
            record.setDeptName(firstText(dto.getDeptName(), authUser.getDeptName(), ""));
            record.setItemCode(rule == null ? itemCode : rule.getItemCode());
            record.setItemName(rule == null ? requestItem.getItemName() : rule.getItemName());
            record.setSpecModel(requestItem.getSpecModel());
            record.setUnit(requestItem.getUnit());
            record.setQuantity(requestItem.getQty());
            record.setStatus(STATUS_REQUESTED);
            record.setPurchaseRequestId(requestId);
            record.setPurchaseRequestNo(requestNo);
            record.setEmergencyFlag(emergency ? 1 : 0);
            record.setRemark(buildItemRemark(rule, emergency));
            recordMapper.insert(record);
        }
    }

    private PersonalLaborItemRecord latestRecord(Long userId, String itemCode) {
        if (userId == null || !StringUtils.hasText(itemCode)) {
            return null;
        }
        return recordMapper.selectOne(new LambdaQueryWrapper<PersonalLaborItemRecord>()
            .eq(PersonalLaborItemRecord::getUserId, userId)
            .eq(PersonalLaborItemRecord::getItemCode, itemCode)
            .orderByDesc(PersonalLaborItemRecord::getUpdatedAt)
            .orderByDesc(PersonalLaborItemRecord::getId)
            .last("LIMIT 1"));
    }

    private String resolveStatus(PersonalLaborRule rule, PersonalLaborItemRecord latest, LocalDate today) {
        if (latest == null) {
            return "FIRST_AVAILABLE";
        }
        if (STATUS_REQUESTED.equalsIgnoreCase(String.valueOf(latest.getStatus()))) {
            return STATUS_REQUESTED;
        }
        if (latest.getLastReceivedDate() == null) {
            return "FIRST_AVAILABLE";
        }
        LocalDate nextDueDate = latest.getNextDueDate();
        if (nextDueDate == null) {
            nextDueDate = latest.getLastReceivedDate().plusDays(safePositive(rule.getCycleDays(), 365));
        }
        if (!today.isBefore(nextDueDate)) {
            return "OVERDUE";
        }
        LocalDate earliestApplyDate = nextDueDate.minusDays(safeNonNegative(rule.getAdvanceDays(), 30));
        if (!today.isBefore(earliestApplyDate)) {
            return "AVAILABLE";
        }
        LocalDate reminderDate = nextDueDate.minusDays(safeNonNegative(rule.getReminderDays(), 15));
        if (!today.isBefore(reminderDate)) {
            return "REMINDING";
        }
        return "NORMAL";
    }

    private PersonalLaborItemStatusVO toStatus(PersonalLaborRule rule,
                                               PersonalLaborItemRecord latest,
                                               LocalDate today,
                                               String status,
                                               String statusNameOverride,
                                               Boolean canApplyOverride,
                                               Boolean canConfirmOverride) {
        PersonalLaborItemStatusVO vo = new PersonalLaborItemStatusVO();
        vo.setRecordId(latest == null ? null : latest.getId());
        vo.setItemCode(rule.getItemCode());
        vo.setItemName(rule.getItemName());
        vo.setUnit(rule.getUnit());
        vo.setEnabled(Boolean.TRUE.equals(rule.getEnabled()));
        vo.setForced(Boolean.TRUE.equals(rule.getForced()));
        vo.setCycleDays(safePositive(rule.getCycleDays(), 365));
        vo.setQuantityLimit(rule.getQuantityLimit());
        vo.setAdvanceDays(safeNonNegative(rule.getAdvanceDays(), 30));
        vo.setReminderDays(safeNonNegative(rule.getReminderDays(), 15));
        vo.setEmergencyEnabled(Boolean.TRUE.equals(rule.getEmergencyEnabled()));
        vo.setManagerApprovalRequired(Boolean.TRUE.equals(rule.getManagerApprovalRequired()));
        vo.setDailyUnclaimedReminderEnabled(Boolean.TRUE.equals(rule.getDailyUnclaimedReminderEnabled()));
        vo.setSpecModel(latest == null ? null : latest.getSpecModel());
        vo.setQuantity(latest == null ? null : latest.getQuantity());
        vo.setLastReceivedDate(latest == null ? null : latest.getLastReceivedDate());
        LocalDate nextDueDate = latest == null ? null : latest.getNextDueDate();
        if (nextDueDate == null && latest != null && latest.getLastReceivedDate() != null) {
            nextDueDate = latest.getLastReceivedDate().plusDays(vo.getCycleDays());
        }
        vo.setNextDueDate(nextDueDate);
        vo.setEarliestApplyDate(nextDueDate == null ? today : nextDueDate.minusDays(vo.getAdvanceDays()));
        vo.setDaysUntilDue(nextDueDate == null ? null : (int) ChronoUnit.DAYS.between(today, nextDueDate));
        vo.setStatus(status);
        vo.setStatusName(StringUtils.hasText(statusNameOverride) ? statusNameOverride : statusName(status));
        boolean canApply = canApply(status);
        vo.setCanApply(canApplyOverride == null ? canApply : canApplyOverride);
        vo.setCanEmergencyPurchase(Boolean.TRUE.equals(rule.getEmergencyEnabled()));
        boolean canConfirm = latest != null && STATUS_REQUESTED.equalsIgnoreCase(String.valueOf(latest.getStatus()));
        vo.setCanConfirmReceived(canConfirmOverride == null ? canConfirm : canConfirmOverride);
        vo.setPurchaseRequestId(latest == null ? null : latest.getPurchaseRequestId());
        vo.setPurchaseRequestNo(latest == null ? null : latest.getPurchaseRequestNo());
        return vo;
    }

    private boolean canApply(String status) {
        return "FIRST_AVAILABLE".equals(status) || "AVAILABLE".equals(status) || "OVERDUE".equals(status);
    }

    private String statusName(String status) {
        return switch (String.valueOf(status)) {
            case "DISABLED" -> "已停用";
            case STATUS_REQUESTED -> "审批/采购中";
            case "RECEIVED" -> "已领用";
            case "OVERDUE" -> "已超期";
            case "AVAILABLE" -> "可申领";
            case "REMINDING" -> "临期提醒";
            case "NORMAL" -> "未到期";
            case "FIRST_AVAILABLE" -> "可首次申领";
            default -> "未知";
        };
    }

    private String resolveTitle(PersonalLaborRequestDTO dto, AuthUser authUser, boolean emergency) {
        if (StringUtils.hasText(dto.getRequestTitle())) {
            return dto.getRequestTitle().trim();
        }
        String prefix = emergency ? "个人劳保紧急购买" : "个人劳保申领";
        return prefix + " - " + firstText(authUser.getRealName(), authUser.getUsername(), "当前用户") + " - " + LocalDate.now();
    }

    private String buildRequestReason(PersonalLaborRequestDTO dto, boolean emergency, int itemCount) {
        String reason = firstText(dto.getReason(), "按个人劳保用品周期提交");
        String prefix = emergency ? "个人劳保紧急购买" : "个人劳保周期申领";
        return prefix + "，共 " + itemCount + " 项；" + reason;
    }

    private String buildItemRemark(PersonalLaborRule rule, boolean emergency) {
        if (rule == null) {
            return emergency ? "个人劳保紧急购买" : "个人劳保周期申领";
        }
        List<String> parts = new ArrayList<>();
        parts.add(emergency ? "个人劳保紧急购买" : "个人劳保周期申领");
        if (Boolean.TRUE.equals(rule.getForced())) {
            parts.add("强制监管");
        }
        if (Boolean.TRUE.equals(rule.getManagerApprovalRequired())) {
            parts.add("需楼长/经理确认");
        }
        parts.add("周期" + safePositive(rule.getCycleDays(), 365) + "天");
        return String.join("；", parts);
    }

    private List<PersonalLaborRule> loadRules() {
        ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, SETTING_KEY)
            .last("LIMIT 1"));
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return defaultRules();
        }
        try {
            List<PersonalLaborRule> rules = objectMapper.readValue(
                setting.getSettingValue(),
                new TypeReference<List<PersonalLaborRule>>() {
                }
            );
            return normalizeRules(rules);
        } catch (JsonProcessingException ex) {
            throw new BizException("LABOR_RULE_PARSE_FAILED", "个人劳保规则配置格式不正确，请在采购规则设置中检查");
        }
    }

    private List<PersonalLaborRule> normalizeRules(List<PersonalLaborRule> rules) {
        if (CollectionUtils.isEmpty(rules)) {
            return defaultRules();
        }
        List<PersonalLaborRule> result = new ArrayList<>();
        for (PersonalLaborRule rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.getItemCode()) || !StringUtils.hasText(rule.getItemName())) {
                continue;
            }
            rule.setItemCode(normalizeCode(rule.getItemCode()));
            rule.setUnit(firstText(rule.getUnit(), "件"));
            rule.setEnabled(rule.getEnabled() == null || rule.getEnabled());
            rule.setForced(Boolean.TRUE.equals(rule.getForced()));
            rule.setCycleDays(safePositive(rule.getCycleDays(), 365));
            rule.setAdvanceDays(safeNonNegative(rule.getAdvanceDays(), 30));
            rule.setReminderDays(safeNonNegative(rule.getReminderDays(), 15));
            rule.setQuantityLimit(rule.getQuantityLimit() == null ? BigDecimal.ONE : rule.getQuantityLimit());
            rule.setEmergencyEnabled(rule.getEmergencyEnabled() == null || rule.getEmergencyEnabled());
            rule.setManagerApprovalRequired(Boolean.TRUE.equals(rule.getManagerApprovalRequired()));
            rule.setDailyUnclaimedReminderEnabled(rule.getDailyUnclaimedReminderEnabled() == null || rule.getDailyUnclaimedReminderEnabled());
            result.add(rule);
        }
        return result.isEmpty() ? defaultRules() : result;
    }

    private Map<String, PersonalLaborRule> ruleMap(List<PersonalLaborRule> rules) {
        Map<String, PersonalLaborRule> map = new LinkedHashMap<>();
        for (PersonalLaborRule rule : rules) {
            map.put(normalizeCode(rule.getItemCode()), rule);
        }
        return map;
    }

    private List<PersonalLaborRule> defaultRules() {
        return List.of(
            rule("LABOR_SHOES", "劳保鞋", "双", 365, "1", false),
            rule("LABOR_GLOVES", "劳保手套", "副", 365, "2", false),
            rule("INSULATED_GLOVES", "绝缘手套", "副", 180, "1", true),
            rule("FLASHLIGHT", "手电筒", "个", 365, "1", false),
            rule("TEST_PEN", "电笔", "支", 365, "1", true),
            rule("ANTI_STATIC_GLOVES", "防静电手套", "副", 365, "2", false),
            rule("ELECTRICAL_INSULATING_SHOES", "电绝缘鞋", "双", 365, "1", true)
        );
    }

    private PersonalLaborRule rule(String code, String name, String unit, int cycleDays, String quantityLimit, boolean forced) {
        PersonalLaborRule rule = new PersonalLaborRule();
        rule.setItemCode(code);
        rule.setItemName(name);
        rule.setUnit(unit);
        rule.setEnabled(true);
        rule.setForced(forced);
        rule.setCycleDays(cycleDays);
        rule.setAdvanceDays(30);
        rule.setReminderDays(15);
        rule.setQuantityLimit(new BigDecimal(quantityLimit));
        rule.setEmergencyEnabled(true);
        rule.setManagerApprovalRequired(forced);
        rule.setDailyUnclaimedReminderEnabled(true);
        return rule;
    }

    private int safePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private int safeNonNegative(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private String normalizeCode(String code) {
        return String.valueOf(code == null ? "" : code).trim().toUpperCase(Locale.ROOT);
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

    private String strip(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    @Data
    private static class PersonalLaborRule {
        private String itemCode;
        private String itemName;
        private String unit;
        private Boolean enabled;
        private Boolean forced;
        private Integer cycleDays;
        private BigDecimal quantityLimit;
        private Integer advanceDays;
        private Integer reminderDays;
        private Boolean emergencyEnabled;
        private Boolean managerApprovalRequired;
        private Boolean dailyUnclaimedReminderEnabled;
    }
}
