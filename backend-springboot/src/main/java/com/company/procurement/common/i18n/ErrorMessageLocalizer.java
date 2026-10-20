package com.company.procurement.common.i18n;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ErrorMessageLocalizer {

    private static final Pattern LINE_PATTERN = Pattern.compile("Line\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private ErrorMessageLocalizer() {
    }

    public static String localize(String code, String message) {
        String normalizedCode = normalizeCode(code);
        if (hasChinese(message)) {
            return message;
        }

        String mapped = mapByCode(normalizedCode, message);
        if (hasText(mapped)) {
            return mapped;
        }

        if (normalizedCode.endsWith("_NOT_FOUND")) {
            return "数据不存在或已被删除";
        }
        if (normalizedCode.endsWith("_EXISTS")) {
            return "数据已存在，请勿重复提交";
        }
        if (normalizedCode.endsWith("_INVALID")) {
            return "提交内容不合法，请检查后重试";
        }
        if (normalizedCode.endsWith("_REQUIRED")) {
            return "缺少必填信息，请补充后重试";
        }
        if (normalizedCode.endsWith("_MISSING")) {
            return "缺少必要配置或数据，请补充后重试";
        }
        if (normalizedCode.startsWith("DIFY_")) {
            return "AI 服务调用失败，请检查服务配置和网络连接";
        }
        return "操作失败，请联系管理员查看错误码：" + normalizedCode;
    }

    private static String mapByCode(String code, String message) {
        return switch (code) {
            case "LOGIN_FAILED" -> "用户名或密码错误";
            case "LOGIN_LOCKED" -> "失败次数过多，请稍后再试";
            case "UNAUTHORIZED" -> "登录已过期，请重新登录";
            case "FORBIDDEN" -> "当前账号无权执行此操作";
            case "VALIDATION_ERROR", "BIND_ERROR" -> "请求参数校验失败，请检查必填项和格式";
            case "NOT_FOUND" -> "请求的接口或资源不存在";
            case "SYSTEM_ERROR" -> "系统异常，请联系管理员处理";
            case "USER_DISABLED" -> "账号已停用，请联系管理员";
            case "ROLE_MISSING" -> "当前用户未分配角色，请联系管理员";
            case "USERNAME_EXISTS" -> "用户名已存在";
            case "MOBILE_EXISTS" -> "手机号已存在";
            case "EMAIL_EXISTS" -> "邮箱已存在";
            case "SUPPLIER_ID_REQUIRED" -> "供应商账号必须绑定供应商档案";
            case "SUPPLIER_BIND_FORBIDDEN" -> "只有供应商账号可以绑定供应商档案";
            case "SUPPLIER_HAS_LINKED_USERS" -> "该供应商已关联账号，请先在用户管理中解绑账号后再删除";
            case "ROLE_IN_USE" -> "角色已分配给用户，不能删除";
            case "ROLE_CODE_EXISTS" -> "角色编码已存在";
            case "APPROVER_NOT_CONFIGURED" -> "未配置审批人，请在部门管理中维护对应 V1-V8 审批候选人";
            case "ROUTE_APPROVER_NOT_CONFIGURED" -> "审批候选人未匹配启用账号，请在部门管理中维护对应 V1-V8 候选人";
            case "APPROVAL_ACTION_INVALID" -> "审批动作不合法";
            case "APPROVAL_INSTANCE_NOT_FOUND" -> "审批实例不存在";
            case "APPROVAL_RECORD_NOT_FOUND" -> "审批记录不存在";
            case "PURCHASE_REQUEST_NOT_FOUND" -> "采购申请不存在";
            case "PURCHASE_REQUEST_STATUS_INVALID" -> "当前采购申请状态不允许执行该操作";
            case "ITEMS_EMPTY" -> "采购申请明细不能为空";
            case "ITEM_QTY_INVALID" -> "第 " + lineNo(message) + " 行数量必须大于 0";
            case "ITEM_PRICE_INVALID" -> "第 " + lineNo(message) + " 行单价必须大于或等于 0";
            case "ITEM_PURPOSE_REQUIRED" -> "第 " + lineNo(message) + " 行采购用途不能为空";
            case "ITEM_AMOUNT_INVALID" -> "第 " + lineNo(message) + " 行金额必须等于数量乘以单价";
            case "TOTAL_AMOUNT_INVALID" -> "采购总金额必须等于明细金额合计";
            case "REQ_TYPE_INVALID" -> "采购类型只能是工程项目、实物资产/无形资产/长摊等、服务/费用/低值易耗等";
            case "QUOTE_COUNT_NOT_ENOUGH" -> "发起报价审核前至少需要 3 家供应商报价";
            case "SUPPLIER_ALREADY_CONFIRMED" -> "中标供应商已确认，不能再修改";
            case "SUPPLIER_QUOTE_NOT_FOUND" -> "供应商报价不存在或不可用";
            case "QUOTE_ITEM_MISSING" -> "每个采购明细都必须有供应商报价";
            case "QUOTE_ITEMS_EMPTY" -> "报价明细不能为空";
            case "QUOTE_ACTIVE_ITEMS_EMPTY" -> "至少需要保留一个有效报价明细";
            case "QUOTE_ITEM_DUPLICATED" -> "报价明细存在重复项";
            case "QUOTE_ITEM_PRICE_INVALID" -> "报价单价必须大于 0";
            case "QUOTE_ITEM_LEAD_TIME_INVALID" -> "报价交期必须大于 0";
            case "QUOTE_LOCKED" -> "采购申请已进入报价审核，报价不能再修改";
            case "QUOTE_DEADLINE_EXPIRED" -> "报价截止时间已到，未提交报价的供应商按放弃报价处理";
            case "QUOTE_DEADLINE_EXTENSION_INVALID" -> "延期天数必须在 1 到 60 天之间";
            case "PURCHASE_REQUEST_ITEMS_EMPTY" -> "当前采购申请没有可报价的明细";
            case "LEADER_CONFIRM_REQUIRED" -> "负责人尚未确认中标供应商";
            case "SUPPLIER_AWARD_NOT_SELECTED" -> "当前供应商不是该采购申请的中标供应商";
            case "SUPPLIER_AWARD_ALREADY_WAIVED" -> "当前中标已放弃";
            case "SUPPLIER_AWARD_CONFIRM_EXPIRED" -> "中标确认已超过期限，系统将自动递补下一名供应商";
            case "SUPPLIER_AWARD_CONFIRM_STATUS_INVALID" -> "当前中标状态不允许执行该操作";
            case "SUPPLIER_BIDDING_SUSPENDED" -> "供应商已暂停投标";
            case "SUPPLIER_BIDDING_BANNED" -> "供应商已被封禁，无法报价";
            case "QUOTE_REVIEW_REQUIRED" -> "报价审核尚未通过";
            case "SUPPLIER_ACCOUNT_INVALID" -> "当前供应商账号未绑定供应商档案";
            case "PROCUREMENT_WINDOW_CLOSED" -> "当前日期不在允许提交采购的时间范围内";
            case "DEPT_SINGLE_LIMIT_EXCEEDED" -> "采购金额超过部门单次采购上限";
            case "DEPT_MONTHLY_LIMIT_EXCEEDED" -> "采购金额超过部门月度采购上限";
            case "FLOW_TEST_ACTION_NOT_FOUND", "RULE_TEST_ACTION_NOT_FOUND" -> "测试操作不存在";
            case "FLOW_TEST_ITEMS_EMPTY" -> "测试采购申请没有明细，无法生成报价";
            case "FLOW_TEST_STAGE_INVALID" -> "最新测试单尚未进入报价审核节点";
            case "FLOW_TEST_AWARD_NOT_FOUND" -> "最新测试单没有待确认的中标报价";
            case "FLOW_TEST_REQUEST_NOT_FOUND" -> "没有找到当前步骤可用的测试采购申请，请先执行前置按钮";
            case "FEISHU_NOT_CONFIGURED" -> "飞书应用参数未配置或未启用";
            case "FEISHU_USER_NOT_BOUND" -> "飞书账号未绑定系统用户，请联系管理员维护邮箱或手机号";
            case "FEISHU_STATE_INVALID" -> "飞书登录状态已失效，请重新发起登录";
            case "FEISHU_APP_TOKEN_FAILED" -> "获取飞书应用令牌失败";
            case "FEISHU_TENANT_TOKEN_FAILED" -> "获取飞书租户访问凭证失败";
            case "FEISHU_CONTACT_PERMISSION_MISSING" -> "飞书应用没有目标部门的通讯录权限，请检查通讯录权限、应用可用范围和通讯录授权范围";
            case "FEISHU_USER_SYNC_FAILED" -> "获取飞书部门用户失败";
            case "FEISHU_DEPARTMENT_SYNC_FAILED" -> "获取飞书部门列表失败";
            case "FEISHU_BOT_DISABLED" -> "飞书机器人未启用";
            case "FEISHU_BOT_EVENT_FAILED" -> "飞书机器人事件处理失败";
            case "FEISHU_BOT_TOKEN_MISSING" -> "飞书机器人校验令牌未配置";
            case "FEISHU_BOT_TOKEN_INVALID" -> "飞书机器人校验令牌不正确";
            case "PROCUREMENT_AI_SESSION_NOT_FOUND" -> "没有找到待确认的采购推荐";
            case "PROCUREMENT_AI_ALREADY_CONFIRMED" -> "采购推荐已确认";
            case "PROCUREMENT_AI_CANDIDATE_NOT_FOUND" -> "采购推荐候选项不存在";
            case "PROCUREMENT_AI_SOURCE_MISMATCH" -> "采购推荐不属于当前来源用户";
            case "SHOPPING_TOOL_UNAUTHORIZED" -> "购物工具令牌无效";
            case "ERROR_LOG_NOT_FOUND" -> "错误记录不存在";
            case "ERROR_LOG_STATUS_INVALID" -> "错误处理状态不合法";
            default -> null;
        };
    }

    private static String normalizeCode(String code) {
        if (!hasText(code)) {
            return "SYSTEM_ERROR";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String lineNo(String message) {
        if (!hasText(message)) {
            return "?";
        }
        Matcher matcher = LINE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "?";
    }

    private static boolean hasChinese(String value) {
        if (!hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
