package com.company.procurement.modules.procurement.settings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.settings.dto.ProcurementDepartmentLimitDTO;
import com.company.procurement.modules.procurement.settings.dto.ProcurementSettingItemDTO;
import com.company.procurement.modules.procurement.settings.dto.ProcurementSettingsUpdateDTO;
import com.company.procurement.modules.procurement.settings.entity.ProcurementDepartmentLimit;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementDepartmentLimitMapper;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.settings.service.ProcurementSettingsService;
import com.company.procurement.modules.procurement.settings.vo.ProcurementDepartmentLimitVO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementRuleTestResultVO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingItemVO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProcurementSettingsServiceImpl implements ProcurementSettingsService {

    private static final String RETIRED_AUTO_RECEIPT_SETTING = "order_auto_receipt_days";

    private static final String DEFAULT_PERSONAL_LABOR_RULES_JSON = """
        [
          {"itemCode":"LABOR_SHOES","itemName":"劳保鞋","unit":"双","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},
          {"itemCode":"LABOR_GLOVES","itemName":"劳保手套","unit":"副","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":2,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},
          {"itemCode":"INSULATED_GLOVES","itemName":"绝缘手套","unit":"副","enabled":true,"forced":true,"cycleDays":180,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":true,"dailyUnclaimedReminderEnabled":true},
          {"itemCode":"FLASHLIGHT","itemName":"手电筒","unit":"个","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},
          {"itemCode":"TEST_PEN","itemName":"电笔","unit":"支","enabled":true,"forced":true,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":true,"dailyUnclaimedReminderEnabled":true},
          {"itemCode":"ANTI_STATIC_GLOVES","itemName":"防静电手套","unit":"副","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":2,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},
          {"itemCode":"ELECTRICAL_INSULATING_SHOES","itemName":"电绝缘鞋","unit":"双","enabled":true,"forced":true,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":true,"dailyUnclaimedReminderEnabled":true}
        ]
        """;

    private static final List<DefaultSetting> DEFAULT_SETTINGS = List.of(
        new DefaultSetting("dify_api_base_url", "", "API", "Dify API 地址", "TEXT", 10, "线上 Dify API Base URL"),
        new DefaultSetting("dify_api_key", "", "API", "Dify API Key", "PASSWORD", 20, "Dify 应用 API Key，仅填写应用访问 API 里的密钥，不要填写模型供应商 Key"),
        new DefaultSetting("dify_app_type", "chatflow", "API", "Dify 应用类型", "TEXT", 30, "chatflow 或 workflow；导入本项目 DSL 时请选择 chatflow"),
        new DefaultSetting("jd_enterprise_api_url", "", "API", "京东企业购 API 地址", "TEXT", 40, "演示或正式接口地址"),
        new DefaultSetting("jd_enterprise_app_key", "", "API", "京东企业购 AppKey", "TEXT", 50, "京东开放平台应用 Key"),
        new DefaultSetting("jd_enterprise_app_secret", "", "API", "京东企业购 AppSecret", "PASSWORD", 60, "京东开放平台应用密钥"),
        new DefaultSetting("alibaba_1688_api_url", "", "API", "1688 API 地址", "TEXT", 70, "1688 开放平台接口地址"),
        new DefaultSetting("alibaba_1688_app_key", "", "API", "1688 AppKey", "TEXT", 80, "1688 开放平台应用 Key"),
        new DefaultSetting("alibaba_1688_app_secret", "", "API", "1688 AppSecret", "PASSWORD", 90, "1688 开放平台应用密钥"),
        new DefaultSetting("shopping_tool_token", "", "API", "购物工具 Token", "PASSWORD", 100, "Dify 调用平台购物工具时使用"),
        new DefaultSetting("m3_vision_enabled", "false", "API", "启用 M3 视觉识别", "BOOLEAN", 110, "开启后，飞书图片采购需求优先调用 M3 视觉模型；识别失败时回退 Dify"),
        new DefaultSetting("m3_base_url", "", "API", "M3 Base URL", "TEXT", 120, "M3 OpenAI-compatible Base URL，例如 http://127.0.0.1:8082/v1"),
        new DefaultSetting("m3_api_key", "", "API", "M3 API Key", "PASSWORD", 130, "M3 OpenAI-compatible API Key，可按本地服务要求填写"),
        new DefaultSetting("m3_vision_model", "m3-vision", "API", "M3 视觉模型", "TEXT", 140, "用于图片商品识别的模型名，例如 WanWu/MiniMax-M3、m3-vision 或 vision-pro；接入网关时填写网关实际模型 ID"),
        new DefaultSetting("m3_endpoint", "/chat/completions", "API", "M3 Chat Endpoint", "TEXT", 150, "默认 /chat/completions；如果 Base URL 已包含完整路径，可保持默认"),
        new DefaultSetting("procurement_ai_image_public_base_url", "", "API", "AI 图片访问基址", "TEXT", 160, "飞书图片中转后给 Dify 或仅支持远程 URL 的视觉模型访问；WanWu/MiniMax-M3 会优先直传图片内容，可留空"),
        new DefaultSetting("feishu_app_id", "", "FEISHU", "飞书 App ID", "TEXT", 10, "企业自建应用 App ID；留空时使用服务器环境变量 FEISHU_APP_ID"),
        new DefaultSetting("feishu_app_secret", "", "FEISHU", "飞书 App Secret", "PASSWORD", 20, "企业自建应用 App Secret；留空时使用服务器环境变量 FEISHU_APP_SECRET"),
        new DefaultSetting("feishu_root_department_id", "", "FEISHU", "飞书同步根部门 ID 列表", "TEXT", 25, "同步飞书组织时使用的根部门 ID，可选择多个；留空时使用服务器环境变量 FEISHU_ROOT_DEPARTMENT_ID，默认 0"),
        new DefaultSetting("feishu_department_id_type", "", "FEISHU", "飞书部门 ID 类型", "TEXT", 26, "同步飞书组织时根部门 ID 的类型：department_id 或 open_department_id；留空时使用服务器环境变量 FEISHU_DEPARTMENT_ID_TYPE"),
        new DefaultSetting("feishu_bot_enabled", "", "FEISHU", "启用飞书采购AI助手", "BOOLEAN", 28, "开启后，飞书用户给机器人发送采购需求时，系统会转入 AI 候选流程并回复用户；留空时使用环境变量 FEISHU_BOT_ENABLED"),
        new DefaultSetting("feishu_approval_card_enabled", "", "FEISHU", "启用审批卡片", "BOOLEAN", 30, "开启后，本地心跳会扫描待审批任务并推送飞书交互卡片；留空时使用环境变量"),
        new DefaultSetting("feishu_approval_card_heartbeat_enabled", "", "FEISHU", "启用本地心跳", "BOOLEAN", 40, "开启后，由系统定时扫描待审批任务并主动推送卡片；留空时使用环境变量"),
        new DefaultSetting("feishu_approval_card_long_connection_enabled", "", "FEISHU", "启用长连接回调", "BOOLEAN", 50, "开启后，系统通过飞书长连接接收卡片按钮回调；留空时使用环境变量"),
        new DefaultSetting("feishu_approval_card_verification_token", "", "FEISHU", "回调 Verification Token", "PASSWORD", 60, "飞书开放平台事件与回调配置中的 Verification Token；留空时使用环境变量"),
        new DefaultSetting("feishu_approval_card_encrypt_key", "", "FEISHU", "回调 Encrypt Key", "PASSWORD", 70, "飞书开放平台事件与回调配置中的 Encrypt Key，未启用加密可留空"),
        new DefaultSetting("feishu_approval_card_app_base_url", "", "FEISHU", "系统访问基址", "TEXT", 80, "系统可访问基址，例如 http://100.125.220.73:10086；用于图片中转和未配置系统处理入口地址时的卡片兜底"),
        new DefaultSetting("feishu_approval_card_system_action_url", "https://mercy-sheep-essentials-genius.trycloudflare.com/login?redirect=/workbench", "FEISHU", "系统处理入口地址", "TEXT", 85, "审批卡片中“系统处理”按钮打开的完整地址，可填写登录后跳转工作台的链接"),
        new DefaultSetting("feishu_approval_card_receive_id_type", "email", "FEISHU", "消息接收 ID 类型", "TEXT", 90, "默认 email，使用系统用户邮箱发送；也可改为 open_id、user_id 或 union_id"),
        new DefaultSetting("feishu_approval_card_detail_item_limit", "", "FEISHU", "旧版卡片明细展示条数", "NUMBER", 100, "旧版兼容字段；当前审批卡片固定每页展示 10 条并支持翻页"),
        new DefaultSetting("feishu_supplier_quote_notice_enabled", "", "FEISHU", "启用供应商报价通知", "BOOLEAN", 110, "开启后，系统会扫描已进入报价中的采购申请，并向已绑定的供应商外部群或单聊推送报价任务卡片；报价仍走原供应商报价流程"),
        new DefaultSetting("feishu_supplier_quote_notice_scan_batch_size", "50", "FEISHU", "供应商通知扫描批量", "NUMBER", 120, "每次心跳最多扫描的报价中采购申请数量，范围 10-500"),
        new DefaultSetting("feishu_supplier_award_notice_enabled", "true", "FEISHU", "启用供应商中标确认通知", "BOOLEAN", 125, "开启后，系统会扫描待供应商确认的中标记录，并向已绑定供应商推送中标确认卡片"),
        new DefaultSetting("feishu_admin_card_copy_enabled", "true", "FEISHU", "管理员接收全部卡片", "BOOLEAN", 130, "开启后，系统会把正式审批卡片、供应商报价任务卡片和供应商中标确认卡片同步抄送给配置的系统管理员账号；管理员点击审批卡片时按系统管理员权限代处理原审批任务"),
        new DefaultSetting("feishu_admin_card_copy_usernames", "admin", "FEISHU", "管理员抄送账号列表", "TEXT", 140, "逗号分隔，可填用户名、工号、邮箱或用户ID；留空时自动选择所有启用的 SUPER_ADMIN/SYS_ADMIN/ADMIN 系统管理员账号"),
        new DefaultSetting("personal_labor_item_rules_json", DEFAULT_PERSONAL_LABOR_RULES_JSON, "LABOR", "个人劳保用品规则", "TEXT", 10, "个人劳保申领的品类、周期、数量、提前申领、提醒和强制监管规则；请优先通过采购规则设置页面维护"),
        new DefaultSetting("mail_enabled", "false", "MAIL", "启用中标邮件", "BOOLEAN", 10, "开启后，供应商中标会发送确认邮件"),
        new DefaultSetting("mail_smtp_host", "", "MAIL", "SMTP 服务器", "TEXT", 20, "企业邮箱 SMTP 服务器地址"),
        new DefaultSetting("mail_smtp_port", "25", "MAIL", "SMTP 端口", "NUMBER", 30, "企业邮箱 SMTP 端口，常见为 25/465/587"),
        new DefaultSetting("mail_smtp_username", "", "MAIL", "SMTP 用户名", "TEXT", 40, "企业邮箱发信账号"),
        new DefaultSetting("mail_smtp_password", "", "MAIL", "SMTP 密码", "PASSWORD", 50, "企业邮箱发信密码或授权码"),
        new DefaultSetting("mail_smtp_from", "", "MAIL", "发件邮箱", "TEXT", 60, "中标确认邮件发件地址"),
        new DefaultSetting("mail_smtp_ssl", "false", "MAIL", "启用 SSL", "BOOLEAN", 70, "SMTP 是否使用 SSL"),
        new DefaultSetting("mail_smtp_starttls", "false", "MAIL", "启用 STARTTLS", "BOOLEAN", 80, "SMTP 是否使用 STARTTLS"),
        new DefaultSetting("monthly_purchase_start_day", "1", "RULE", "月度采购开始日", "NUMBER", 10, "非紧急采购允许提交的开始日期"),
        new DefaultSetting("monthly_purchase_end_day", "25", "RULE", "月度采购截止日", "NUMBER", 20, "非紧急采购允许提交的截止日期"),
        new DefaultSetting("urgent_purchase_anytime", "true", "RULE", "紧急采购不限日期", "BOOLEAN", 30, "紧急程度为特急时可跳过采购周期限制"),
        new DefaultSetting("default_need_days", "7", "RULE", "默认需求提前天数", "NUMBER", 40, "新建申请默认需求日期可参考"),
        new DefaultSetting("quote_open_days", "7", "RULE", "报价开放天数", "NUMBER", 50, "采购进入报价后的默认开放天数"),
        new DefaultSetting("monthly_aggregate_day", "20", "RULE", "集采自动发起日", "NUMBER", 60, "每月几号自动汇总集采需求并生成采购申请"),
        new DefaultSetting("default_award_strategy", "TOTAL_LOWEST", "RULE", "默认中标策略", "TEXT", 70, "TOTAL_LOWEST 总价最低；ITEM_LOWEST 单项最低"),
        new DefaultSetting("urgent_direct_award_enabled", "true", "RULE", "低额紧急采购定向报价", "BOOLEAN", 80, "开启后，低于阈值的紧急采购优先定向推送历史单项中标次数最多的供应商；定向供应商报价不超过阈值130%时免三方比价，超过则转入三方比价"),
        new DefaultSetting("urgent_direct_award_threshold", "1000", "RULE", "低额紧急采购阈值", "NUMBER", 90, "紧急采购有效金额低于该值时启用定向报价；默认1000元时定向报价上限为1300元"),
        new DefaultSetting("approval_confirm_workdays", "4", "RULE", "审批确认工作日", "NUMBER", 95, "审批人最长确认工作日数；超期确认仍可提交，但普通采购顺延到下月执行"),
        new DefaultSetting("supplier_award_confirm_workdays", "4", "RULE", "供应商中标确认工作日", "NUMBER", 100, "中标后供应商最长确认工作日数"),
        new DefaultSetting("supplier_confirm_holidays", "", "RULE", "中标确认节假日", "TEXT", 110, "逗号分隔日期，如 2026-05-01,2026-05-04，不计入确认期限"),
        new DefaultSetting("supplier_confirm_extra_workdays", "", "RULE", "中标确认调休工作日", "TEXT", 120, "逗号分隔日期，周末调休上班日会计入确认期限"),
        new DefaultSetting("after_sale_no_reason_enabled", "true", "RULE", "启用7天无理由退货", "BOOLEAN", 140, "开启后，收货后指定天数内可申请无理由退货"),
        new DefaultSetting("after_sale_no_reason_days", "7", "RULE", "无理由退货天数", "NUMBER", 150, "从确认收货时间起计算的无理由退货窗口天数"),
        new DefaultSetting("after_sale_return_exchange_enabled", "true", "RULE", "启用14天退换货", "BOOLEAN", 160, "开启后，收货后指定天数内可申请退换货或商品更换"),
        new DefaultSetting("after_sale_return_exchange_days", "14", "RULE", "退换货天数", "NUMBER", 170, "从确认收货时间起计算的退换货窗口天数"),
        new DefaultSetting("import_file_log_retention_days", "90", "SYSTEM", "导入文件记录保留天数", "NUMBER", 5, "系统每天 03:30 清理早于该天数的导入文件记录；填 0 或负数表示不自动清理")
    );

    private final ProcurementSettingMapper settingMapper;
    private final ProcurementDepartmentLimitMapper departmentLimitMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementSettingsVO getSettings() {
        ensureDefaultSettings();
        ProcurementSettingsVO vo = new ProcurementSettingsVO();
        vo.setSettings(settingMapper.selectList(new LambdaQueryWrapper<ProcurementSetting>()
                .orderByAsc(ProcurementSetting::getSettingGroup)
                .orderByAsc(ProcurementSetting::getSortNo))
            .stream()
            .filter(setting -> !RETIRED_AUTO_RECEIPT_SETTING.equals(normalizeKey(setting.getSettingKey())))
            .map(this::toSettingVO)
            .toList());
        vo.setDepartmentLimits(departmentLimitMapper.selectList(new LambdaQueryWrapper<ProcurementDepartmentLimit>()
                .orderByAsc(ProcurementDepartmentLimit::getDeptCode)
                .orderByAsc(ProcurementDepartmentLimit::getDeptName))
            .stream()
            .map(this::toLimitVO)
            .toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSettings(ProcurementSettingsUpdateDTO dto) {
        ensureDefaultSettings();
        if (dto.getSettings() != null) {
            for (ProcurementSettingItemDTO item : dto.getSettings()) {
                if (!RETIRED_AUTO_RECEIPT_SETTING.equals(normalizeKey(item.getSettingKey()))) {
                    upsertSetting(item);
                }
            }
        }
        if (dto.getDepartmentLimits() != null) {
            replaceDepartmentLimits(dto.getDepartmentLimits());
        }
    }

    @Override
    public ProcurementRuleTestResultVO testRuleAction(String actionCode) {
        Map<String, ProcurementSetting> settings = settingMap();
        String normalized = normalizeCode(actionCode).replace('-', '_');
        return switch (normalized) {
            case "REGULAR_WINDOW" -> testRegularWindow(settings);
            case "URGENT_PURCHASE" -> testUrgentPurchase(settings);
            case "DEFAULT_NEED_DATE" -> testDefaultNeedDate(settings);
            case "QUOTE_OPEN_DAYS" -> testQuoteOpenDays(settings);
            case "MONTHLY_AGGREGATE_DAY" -> testMonthlyAggregateDay(settings);
            case "AWARD_STRATEGY" -> testAwardStrategy(settings);
            case "DEPT_SINGLE_LIMIT" -> testDepartmentSingleLimit();
            case "DEPT_MONTHLY_LIMIT" -> testDepartmentMonthlyLimit();
            default -> throw new BizException("RULE_TEST_ACTION_NOT_FOUND", "测试操作不存在");
        };
    }

    @Override
    public void validateRequest(
        AuthUser authUser,
        String deptCode,
        String deptName,
        BigDecimal totalAmount,
        Integer urgencyLevel,
        Long excludeRequestId
    ) {
        if (authUser == null || DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        Map<String, ProcurementSetting> settings = settingMap();
        if (!isWithinPurchaseWindow(settings, urgencyLevel)) {
            throw new BizException("PROCUREMENT_WINDOW_CLOSED", purchaseWindowClosedMessage(settings, urgencyLevel));
        }
        ProcurementDepartmentLimit limit = findDepartmentLimit(deptCode, deptName);
        if (limit == null || limit.getStatus() == null || limit.getStatus() != 1 || totalAmount == null) {
            return;
        }
        if (limit.getSingleRequestLimit() != null
            && limit.getSingleRequestLimit().compareTo(BigDecimal.ZERO) > 0
            && totalAmount.compareTo(limit.getSingleRequestLimit()) > 0) {
            throw new BizException("DEPT_SINGLE_LIMIT_EXCEEDED", "采购金额超过部门单次采购上限");
        }
        if (limit.getMonthlyLimit() != null
            && limit.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
            && monthlyUsedAmount(limit, excludeRequestId).add(totalAmount).compareTo(limit.getMonthlyLimit()) > 0) {
            throw new BizException("DEPT_MONTHLY_LIMIT_EXCEEDED", "采购金额超过部门月度采购上限");
        }
    }

    private ProcurementRuleTestResultVO testRegularWindow(Map<String, ProcurementSetting> settings) {
        int startDay = clampDay(intValue(settings, "monthly_purchase_start_day", 1));
        int endDay = clampDay(intValue(settings, "monthly_purchase_end_day", 25));
        int today = LocalDate.now().getDayOfMonth();
        boolean allowed = isWithinPurchaseWindow(settings, 1);
        return testResult(
            "REGULAR_WINDOW",
            "日期窗口",
            allowed,
            allowed ? "普通采购当前可提交" : "普通采购当前会被日期窗口拦截",
            Map.of("today", today, "startDay", startDay, "endDay", endDay)
        );
    }

    private ProcurementRuleTestResultVO testUrgentPurchase(Map<String, ProcurementSetting> settings) {
        boolean urgentAnytime = booleanValue(settings, "urgent_purchase_anytime", true);
        boolean allowed = isWithinPurchaseWindow(settings, 3);
        return testResult(
            "URGENT_PURCHASE",
            "紧急采购",
            allowed,
            allowed ? "紧急采购当前可提交" : "紧急采购当前仍会被日期窗口拦截",
            Map.of("urgentAnytime", urgentAnytime, "urgencyLevel", 3)
        );
    }

    private ProcurementRuleTestResultVO testDefaultNeedDate(Map<String, ProcurementSetting> settings) {
        int needDays = Math.max(0, intValue(settings, "default_need_days", 7));
        LocalDate needDate = LocalDate.now().plusDays(needDays);
        return testResult(
            "DEFAULT_NEED_DATE",
            "默认需求日",
            true,
            "默认需求日期已按规则计算",
            Map.of("needDays", needDays, "needDate", needDate.toString())
        );
    }

    private ProcurementRuleTestResultVO testQuoteOpenDays(Map<String, ProcurementSetting> settings) {
        int openDays = Math.max(0, intValue(settings, "quote_open_days", 7));
        LocalDate quoteCloseDate = LocalDate.now().plusDays(openDays);
        return testResult(
            "QUOTE_OPEN_DAYS",
            "报价截止",
            true,
            "报价截止日期已按规则计算",
            Map.of("openDays", openDays, "quoteCloseDate", quoteCloseDate.toString())
        );
    }

    private ProcurementRuleTestResultVO testMonthlyAggregateDay(Map<String, ProcurementSetting> settings) {
        int aggregateDay = clampDay(intValue(settings, "monthly_aggregate_day", 20));
        int today = LocalDate.now().getDayOfMonth();
        boolean triggered = today == aggregateDay;
        return testResult(
            "MONTHLY_AGGREGATE_DAY",
            "自动集采日",
            triggered,
            triggered ? "今天会触发集采自动发起日" : "今天不是集采自动发起日",
            Map.of("today", today, "aggregateDay", aggregateDay)
        );
    }

    private ProcurementRuleTestResultVO testAwardStrategy(Map<String, ProcurementSetting> settings) {
        ProcurementSetting setting = settings.get("default_award_strategy");
        String configured = setting == null || !StringUtils.hasText(setting.getSettingValue())
            ? ProcurementWorkflow.AWARD_STRATEGY_TOTAL_LOWEST
            : setting.getSettingValue();
        String normalized = ProcurementWorkflow.normalizeAwardStrategy(configured);
        return testResult(
            "AWARD_STRATEGY",
            "中标策略",
            true,
            ProcurementWorkflow.AWARD_STRATEGY_ITEM_LOWEST.equals(normalized) ? "当前按单项最低价中标" : "当前按总价最低中标",
            Map.of("configured", configured, "normalized", normalized)
        );
    }

    private ProcurementRuleTestResultVO testDepartmentSingleLimit() {
        ProcurementDepartmentLimit limit = firstEnabledDepartmentLimit();
        if (limit == null || limit.getSingleRequestLimit() == null || limit.getSingleRequestLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return testResult(
                "DEPT_SINGLE_LIMIT",
                "单次上限",
                false,
                "未找到已启用且配置单次上限的部门",
                Map.of()
            );
        }
        BigDecimal testAmount = limit.getSingleRequestLimit().add(BigDecimal.ONE);
        return testResult(
            "DEPT_SINGLE_LIMIT",
            "单次上限",
            true,
            "已触发部门单次采购上限拦截",
            departmentLimitDetail(limit, Map.of("testAmount", testAmount, "singleRequestLimit", limit.getSingleRequestLimit()))
        );
    }

    private ProcurementRuleTestResultVO testDepartmentMonthlyLimit() {
        ProcurementDepartmentLimit limit = firstEnabledDepartmentLimit();
        if (limit == null || limit.getMonthlyLimit() == null || limit.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return testResult(
                "DEPT_MONTHLY_LIMIT",
                "月度上限",
                false,
                "未找到已启用且配置月度上限的部门",
                Map.of()
            );
        }
        BigDecimal usedAmount = monthlyUsedAmount(limit, null);
        BigDecimal remaining = limit.getMonthlyLimit().subtract(usedAmount);
        BigDecimal testAmount = remaining.compareTo(BigDecimal.ZERO) >= 0 ? remaining.add(BigDecimal.ONE) : BigDecimal.ONE;
        return testResult(
            "DEPT_MONTHLY_LIMIT",
            "月度上限",
            true,
            "已触发部门月度采购上限拦截",
            departmentLimitDetail(limit, Map.of(
                "monthlyLimit", limit.getMonthlyLimit(),
                "usedAmount", usedAmount,
                "testAmount", testAmount
            ))
        );
    }

    private ProcurementDepartmentLimit firstEnabledDepartmentLimit() {
        return departmentLimitMapper.selectOne(new LambdaQueryWrapper<ProcurementDepartmentLimit>()
            .eq(ProcurementDepartmentLimit::getStatus, 1)
            .orderByAsc(ProcurementDepartmentLimit::getId)
            .last("LIMIT 1"));
    }

    private Map<String, Object> departmentLimitDetail(ProcurementDepartmentLimit limit, Map<String, ?> extra) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("deptCode", limit.getDeptCode());
        detail.put("deptName", limit.getDeptName());
        detail.putAll(extra);
        return detail;
    }

    private ProcurementRuleTestResultVO testResult(
        String actionCode,
        String actionName,
        boolean triggered,
        String message,
        Map<String, ?> detail
    ) {
        ProcurementRuleTestResultVO vo = new ProcurementRuleTestResultVO();
        vo.setActionCode(actionCode);
        vo.setActionName(actionName);
        vo.setTriggered(triggered);
        vo.setMessage(message);
        vo.setDetail(new LinkedHashMap<>(detail));
        return vo;
    }

    private void ensureDefaultSettings() {
        for (DefaultSetting defaultSetting : DEFAULT_SETTINGS) {
            ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
                .eq(ProcurementSetting::getSettingKey, defaultSetting.key())
                .last("LIMIT 1"));
            if (setting == null) {
                setting = new ProcurementSetting();
                setting.setSettingKey(defaultSetting.key());
                setting.setSettingValue(defaultSetting.value());
                applyDefaultMetadata(setting, defaultSetting);
                settingMapper.insert(setting);
                continue;
            }
            boolean changed = setting.getSettingValue() == null;
            if (changed) {
                setting.setSettingValue(defaultSetting.value());
            }
            if (applyDefaultMetadata(setting, defaultSetting) || changed) {
                settingMapper.updateById(setting);
            }
        }
    }

    private boolean applyDefaultMetadata(ProcurementSetting setting, DefaultSetting defaultSetting) {
        boolean changed = false;
        if (!Objects.equals(setting.getSettingGroup(), defaultSetting.group())) {
            setting.setSettingGroup(defaultSetting.group());
            changed = true;
        }
        if (!Objects.equals(setting.getSettingLabel(), defaultSetting.label())) {
            setting.setSettingLabel(defaultSetting.label());
            changed = true;
        }
        if (!Objects.equals(setting.getValueType(), defaultSetting.type())) {
            setting.setValueType(defaultSetting.type());
            changed = true;
        }
        if (!Objects.equals(setting.getSortNo(), defaultSetting.sortNo())) {
            setting.setSortNo(defaultSetting.sortNo());
            changed = true;
        }
        if (!Objects.equals(setting.getRemark(), defaultSetting.remark())) {
            setting.setRemark(defaultSetting.remark());
            changed = true;
        }
        return changed;
    }

    private void upsertSetting(ProcurementSettingItemDTO item) {
        String key = normalizeKey(item.getSettingKey());
        ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, key)
            .last("LIMIT 1"));
        if (setting == null) {
            setting = new ProcurementSetting();
            setting.setSettingKey(key);
        }
        DefaultSetting defaultSetting = defaultSetting(key);
        setting.setSettingValue(item.getSettingValue());
        setting.setSettingGroup(StringUtils.hasText(item.getSettingGroup()) ? normalizeCode(item.getSettingGroup()) : defaultSetting.group());
        setting.setSettingLabel(StringUtils.hasText(item.getSettingLabel()) ? item.getSettingLabel().trim() : defaultSetting.label());
        setting.setValueType(StringUtils.hasText(item.getValueType()) ? normalizeCode(item.getValueType()) : defaultSetting.type());
        setting.setSortNo(item.getSortNo() == null ? defaultSetting.sortNo() : item.getSortNo());
        setting.setRemark(StringUtils.hasText(item.getRemark()) ? item.getRemark() : defaultSetting.remark());
        if (setting.getId() == null) {
            settingMapper.insert(setting);
        } else {
            settingMapper.updateById(setting);
        }
    }

    private void replaceDepartmentLimits(List<ProcurementDepartmentLimitDTO> limits) {
        List<Long> keepIds = limits.stream()
            .map(ProcurementDepartmentLimitDTO::getId)
            .filter(Objects::nonNull)
            .toList();
        for (ProcurementDepartmentLimit existing : departmentLimitMapper.selectList(new LambdaQueryWrapper<>())) {
            if (!keepIds.contains(existing.getId())) {
                departmentLimitMapper.physicalDeleteById(existing.getId());
            }
        }
        for (ProcurementDepartmentLimitDTO dto : limits) {
            ProcurementDepartmentLimit limit = dto.getId() == null ? new ProcurementDepartmentLimit() : departmentLimitMapper.selectById(dto.getId());
            if (limit == null) {
                limit = new ProcurementDepartmentLimit();
            }
            limit.setDeptCode(StringUtils.hasText(dto.getDeptCode()) ? dto.getDeptCode().trim() : null);
            limit.setDeptName(dto.getDeptName().trim());
            limit.setSingleRequestLimit(defaultAmount(dto.getSingleRequestLimit()));
            limit.setMonthlyLimit(defaultAmount(dto.getMonthlyLimit()));
            limit.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            limit.setRemark(dto.getRemark());
            if (limit.getId() == null) {
                departmentLimitMapper.insert(limit);
            } else {
                departmentLimitMapper.updateById(limit);
            }
        }
    }

    private Map<String, ProcurementSetting> settingMap() {
        ensureDefaultSettings();
        Map<String, ProcurementSetting> map = new LinkedHashMap<>();
        for (ProcurementSetting setting : settingMapper.selectList(new LambdaQueryWrapper<>())) {
            map.put(normalizeKey(setting.getSettingKey()), setting);
        }
        return map;
    }

    private boolean isWithinPurchaseWindow(Map<String, ProcurementSetting> settings, Integer urgencyLevel) {
        boolean urgentAnytime = booleanValue(settings, "urgent_purchase_anytime", true);
        if (urgentAnytime && urgencyLevel != null && urgencyLevel >= 2) {
            return true;
        }
        int startDay = clampDay(intValue(settings, "monthly_purchase_start_day", 1));
        int endDay = clampDay(intValue(settings, "monthly_purchase_end_day", 25));
        int today = LocalDate.now().getDayOfMonth();
        return isDayInWindow(today, startDay, endDay);
    }

    private String purchaseWindowClosedMessage(Map<String, ProcurementSetting> settings, Integer urgencyLevel) {
        int startDay = clampDay(intValue(settings, "monthly_purchase_start_day", 1));
        int endDay = clampDay(intValue(settings, "monthly_purchase_end_day", 25));
        LocalDate today = LocalDate.now();
        LocalDate nextAllowedDate = nextAllowedSubmitDate(today, startDay, endDay);
        String purchaseType = urgencyLevel != null && urgencyLevel >= 2 ? "紧急采购" : "普通采购";
        String urgentHint = booleanValue(settings, "urgent_purchase_anytime", true)
            ? "如确属紧急需求，可切换为紧急采购后提交。"
            : "当前后台未开启紧急采购不限日期。";
        return purchaseType
            + "当前日期 " + today
            + " 不在采购设置允许的提交日期内；允许提交日期为每月 "
            + purchaseWindowText(startDay, endDay)
            + "，下一次可提交日期为 " + nextAllowedDate + "。"
            + urgentHint;
    }

    private String purchaseWindowText(int startDay, int endDay) {
        if (startDay <= endDay) {
            return startDay + " 日至 " + endDay + " 日";
        }
        return startDay + " 日至月底，或次月 1 日至 " + endDay + " 日";
    }

    private LocalDate nextAllowedSubmitDate(LocalDate today, int startDay, int endDay) {
        LocalDate candidate = today;
        for (int i = 0; i < 370; i++) {
            if (isDayInWindow(candidate.getDayOfMonth(), startDay, endDay)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        return today.withDayOfMonth(Math.min(startDay, today.lengthOfMonth()));
    }

    private boolean isDayInWindow(int day, int startDay, int endDay) {
        if (startDay <= endDay) {
            return day >= startDay && day <= endDay;
        }
        return day >= startDay || day <= endDay;
    }

    private ProcurementDepartmentLimit findDepartmentLimit(String deptCode, String deptName) {
        if (StringUtils.hasText(deptCode)) {
            ProcurementDepartmentLimit byCode = departmentLimitMapper.selectOne(new LambdaQueryWrapper<ProcurementDepartmentLimit>()
                .eq(ProcurementDepartmentLimit::getDeptCode, deptCode.trim())
                .last("LIMIT 1"));
            if (byCode != null) {
                return byCode;
            }
        }
        if (StringUtils.hasText(deptName)) {
            return departmentLimitMapper.selectOne(new LambdaQueryWrapper<ProcurementDepartmentLimit>()
                .eq(ProcurementDepartmentLimit::getDeptName, deptName.trim())
                .last("LIMIT 1"));
        }
        return null;
    }

    private BigDecimal monthlyUsedAmount(ProcurementDepartmentLimit limit, Long excludeRequestId) {
        LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
        LocalDateTime start = firstDay.atStartOfDay();
        LocalDateTime end = firstDay.plusMonths(1).atTime(LocalTime.MIN);
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<PurchaseRequest>()
            .ge(PurchaseRequest::getCreatedAt, start)
            .lt(PurchaseRequest::getCreatedAt, end)
            .notIn(PurchaseRequest::getStatus, List.of(
                ProcurementWorkflow.STATUS_REJECTED,
                ProcurementWorkflow.STATUS_RETURNED
            ));
        if (excludeRequestId != null) {
            wrapper.ne(PurchaseRequest::getId, excludeRequestId);
        }
        if (StringUtils.hasText(limit.getDeptCode())) {
            wrapper.eq(PurchaseRequest::getDeptCode, limit.getDeptCode());
        } else {
            wrapper.eq(PurchaseRequest::getDeptName, limit.getDeptName());
        }
        return purchaseRequestMapper.selectList(wrapper).stream()
            .map(PurchaseRequest::getTotalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ProcurementSettingItemVO toSettingVO(ProcurementSetting setting) {
        ProcurementSettingItemVO vo = new ProcurementSettingItemVO();
        vo.setId(setting.getId());
        vo.setSettingKey(setting.getSettingKey());
        vo.setSettingValue(setting.getSettingValue());
        vo.setSettingGroup(setting.getSettingGroup());
        vo.setSettingLabel(setting.getSettingLabel());
        vo.setValueType(setting.getValueType());
        vo.setSortNo(setting.getSortNo());
        vo.setRemark(setting.getRemark());
        return vo;
    }

    private ProcurementDepartmentLimitVO toLimitVO(ProcurementDepartmentLimit limit) {
        ProcurementDepartmentLimitVO vo = new ProcurementDepartmentLimitVO();
        vo.setId(limit.getId());
        vo.setDeptCode(limit.getDeptCode());
        vo.setDeptName(limit.getDeptName());
        vo.setSingleRequestLimit(limit.getSingleRequestLimit());
        vo.setMonthlyLimit(limit.getMonthlyLimit());
        vo.setStatus(limit.getStatus());
        vo.setRemark(limit.getRemark());
        vo.setUpdatedAt(limit.getUpdatedAt());
        return vo;
    }

    private DefaultSetting defaultSetting(String key) {
        return DEFAULT_SETTINGS.stream()
            .filter(setting -> setting.key().equals(key))
            .findFirst()
            .orElse(new DefaultSetting(key, "", "API", key, "TEXT", 999, ""));
    }

    private int intValue(Map<String, ProcurementSetting> settings, String key, int defaultValue) {
        try {
            ProcurementSetting setting = settings.get(key);
            return setting == null ? defaultValue : Integer.parseInt(setting.getSettingValue());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean booleanValue(Map<String, ProcurementSetting> settings, String key, boolean defaultValue) {
        ProcurementSetting setting = settings.get(key);
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return defaultValue;
        }
        return Boolean.parseBoolean(setting.getSettingValue());
    }

    private int clampDay(int day) {
        return Math.min(31, Math.max(1, day));
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private record DefaultSetting(String key, String value, String group, String label, String type, int sortNo, String remark) {
    }
}
