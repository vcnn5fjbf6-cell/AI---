package com.company.procurement.modules.procurement.workflow;

import java.math.BigDecimal;
import java.util.List;

public final class ProcurementWorkflow {

    public static final String ROLE_ENGINEER = "ENGINEER";
    public static final String ROLE_DUTY_LEADER = "DUTY_LEADER";
    public static final String ROLE_PROFESSIONAL_ENGINEER = "PROFESSIONAL_ENGINEER";
    public static final String ROLE_FLOOR_LEADER = "FLOOR_LEADER";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_REGION_MANAGER = "REGION_MANAGER";
    public static final String ROLE_SENIOR_MANAGER = "SENIOR_MANAGER";
    public static final String ROLE_APPROVAL_V7 = "APPROVAL_V7";
    public static final String ROLE_APPROVAL_V8 = "APPROVAL_V8";
    public static final String ROLE_DEPARTMENT_ASSISTANT = "DEPARTMENT_ASSISTANT";
    public static final String ROLE_SUPPLIER = "SUPPLIER";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_DEMAND_POOL = "DEMAND_POOL";
    public static final String STATUS_DEMAND_AGGREGATED = "DEMAND_AGGREGATED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_TERMINATED = "TERMINATED";
    public static final String STATUS_QUOTING = "QUOTING";

    public static final String ITEM_STATUS_ACTIVE = "ACTIVE";
    public static final String ITEM_STATUS_REJECTED = "REJECTED";
    public static final String ITEM_STATUS_RETURNED = "RETURNED";

    public static final String APPROVAL_DRAFT = "DRAFT";
    public static final String APPROVAL_PENDING = "PENDING";
    public static final String APPROVAL_APPROVED = "APPROVED";
    public static final String APPROVAL_REJECTED = "REJECTED";
    public static final String APPROVAL_RETURNED = "RETURNED";
    public static final String APPROVAL_TERMINATED = "TERMINATED";

    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_RETURN = "RETURN";
    public static final String ACTION_SAVE = "SAVE";
    public static final String ACTION_CLOSE = "CLOSE";
    public static final String ACTION_TERMINATE = "TERMINATE";

    public static final String QUOTE_STATUS_SUBMITTED = "SUBMITTED";
    public static final String LEADER_CONFIRM_STATUS_PENDING = "PENDING";
    public static final String LEADER_CONFIRM_STATUS_CONFIRMED = "CONFIRMED";
    public static final String SUPPLIER_CONFIRM_STATUS_UNSELECTED = "UNSELECTED";
    public static final String SUPPLIER_CONFIRM_STATUS_PENDING = "PENDING";
    public static final String SUPPLIER_CONFIRM_STATUS_CONFIRMED = "CONFIRMED";
    public static final String SUPPLIER_CONFIRM_STATUS_WAIVED = "WAIVED";
    public static final String AWARD_STRATEGY_TOTAL_LOWEST = "TOTAL_LOWEST";
    public static final String AWARD_STRATEGY_ITEM_LOWEST = "ITEM_LOWEST";
    public static final String PRICE_SOURCE_REFERENCE = "REFERENCE";
    public static final String PRICE_SOURCE_SUPPLIER_QUOTE = "SUPPLIER_QUOTE";
    public static final String LEVEL_CODE_OVER_PRICE_REVIEW = "OVER_PRICE_REVIEW";

    public static final BigDecimal AWARD_STRATEGY_ITEM_LOWEST_THRESHOLD = new BigDecimal("10000.00");
    public static final BigDecimal REGION_APPROVAL_THRESHOLD = new BigDecimal("5000.00");
    public static final BigDecimal DIRECTOR_APPROVAL_THRESHOLD = new BigDecimal("100000.00");
    public static final BigDecimal QUOTE_APPROVAL_SENIOR_THRESHOLD = REGION_APPROVAL_THRESHOLD;
    public static final BigDecimal OVER_PRICE_REVIEW_THRESHOLD_PERCENT = new BigDecimal("13.00");

    public static final int NODE_DUTY_LEADER_REVIEW = 1;
    public static final int NODE_PROFESSIONAL_REVIEW = 2;
    public static final int NODE_FLOOR_LEADER_DEMAND_REVIEW = 3;
    public static final int NODE_MANAGER_REVIEW = 4;
    public static final int NODE_REGION_MANAGER_REVIEW = 5;
    public static final int NODE_DIRECTOR_REVIEW = 6;
    public static final int NODE_APPROVAL_V7_REVIEW = 7;
    public static final int NODE_APPROVAL_V8_REVIEW = 8;
    public static final int NODE_MANAGER_QUOTE_REVIEW = NODE_MANAGER_REVIEW;
    public static final int NODE_SENIOR_MANAGER_QUOTE_REVIEW = NODE_DIRECTOR_REVIEW;
    public static final int NODE_APPLICANT_REPLACEMENT_REVIEW = 10;

    private static final List<String> APPROVAL_ROLE_SEQUENCE = List.of(
        ROLE_DUTY_LEADER,
        ROLE_PROFESSIONAL_ENGINEER,
        ROLE_FLOOR_LEADER,
        ROLE_MANAGER,
        ROLE_REGION_MANAGER,
        ROLE_SENIOR_MANAGER,
        ROLE_APPROVAL_V7,
        ROLE_APPROVAL_V8
    );

    private ProcurementWorkflow() {
    }

    public static int totalApprovalNodes() {
        return NODE_APPROVAL_V8_REVIEW;
    }

    public static int totalQuoteApprovalNodes(BigDecimal amount) {
        if (amount != null && amount.compareTo(DIRECTOR_APPROVAL_THRESHOLD) > 0) {
            return NODE_DIRECTOR_REVIEW;
        }
        if (amount != null && amount.compareTo(REGION_APPROVAL_THRESHOLD) > 0) {
            return NODE_REGION_MANAGER_REVIEW;
        }
        return NODE_MANAGER_REVIEW;
    }

    public static int totalDemandApprovalNodes(BigDecimal activeAmount) {
        if (activeAmount != null && activeAmount.compareTo(DIRECTOR_APPROVAL_THRESHOLD) > 0) {
            return NODE_DIRECTOR_REVIEW;
        }
        if (activeAmount != null && activeAmount.compareTo(REGION_APPROVAL_THRESHOLD) > 0) {
            return NODE_REGION_MANAGER_REVIEW;
        }
        return NODE_MANAGER_REVIEW;
    }

    public static boolean isApprovalRole(String roleCode) {
        return APPROVAL_ROLE_SEQUENCE.contains(roleCode);
    }

    public static int nodeNoForRole(String roleCode) {
        if (ROLE_DUTY_LEADER.equals(roleCode)) {
            return NODE_DUTY_LEADER_REVIEW;
        }
        if (ROLE_PROFESSIONAL_ENGINEER.equals(roleCode)) {
            return NODE_PROFESSIONAL_REVIEW;
        }
        if (ROLE_FLOOR_LEADER.equals(roleCode)) {
            return NODE_FLOOR_LEADER_DEMAND_REVIEW;
        }
        if (ROLE_MANAGER.equals(roleCode)) {
            return NODE_MANAGER_REVIEW;
        }
        if (ROLE_REGION_MANAGER.equals(roleCode)) {
            return NODE_REGION_MANAGER_REVIEW;
        }
        if (ROLE_SENIOR_MANAGER.equals(roleCode)) {
            return NODE_DIRECTOR_REVIEW;
        }
        if (ROLE_APPROVAL_V7.equals(roleCode)) {
            return NODE_APPROVAL_V7_REVIEW;
        }
        if (ROLE_APPROVAL_V8.equals(roleCode)) {
            return NODE_APPROVAL_V8_REVIEW;
        }
        return 0;
    }

    public static String roleCodeForNode(int nodeNo) {
        return switch (nodeNo) {
            case NODE_DUTY_LEADER_REVIEW -> ROLE_DUTY_LEADER;
            case NODE_PROFESSIONAL_REVIEW -> ROLE_PROFESSIONAL_ENGINEER;
            case NODE_FLOOR_LEADER_DEMAND_REVIEW -> ROLE_FLOOR_LEADER;
            case NODE_MANAGER_REVIEW -> ROLE_MANAGER;
            case NODE_REGION_MANAGER_REVIEW -> ROLE_REGION_MANAGER;
            case NODE_DIRECTOR_REVIEW -> ROLE_SENIOR_MANAGER;
            case NODE_APPROVAL_V7_REVIEW -> ROLE_APPROVAL_V7;
            case NODE_APPROVAL_V8_REVIEW -> ROLE_APPROVAL_V8;
            default -> null;
        };
    }

    public static boolean isFinalApprovalNode(int nodeNo) {
        return nodeNo >= totalApprovalNodes();
    }

    public static boolean isFinalQuoteApprovalNode(int nodeNo, BigDecimal amount) {
        return nodeNo >= totalQuoteApprovalNodes(amount);
    }

    public static String normalizeAwardStrategy(String strategy) {
        if (AWARD_STRATEGY_ITEM_LOWEST.equals(strategy)) {
            return AWARD_STRATEGY_ITEM_LOWEST;
        }
        return AWARD_STRATEGY_TOTAL_LOWEST;
    }

    public static boolean isItemLowestAwardStrategy(String strategy) {
        return AWARD_STRATEGY_ITEM_LOWEST.equals(normalizeAwardStrategy(strategy));
    }

    public static String resolveAutoAwardStrategy(BigDecimal amount) {
        if (amount != null && amount.compareTo(AWARD_STRATEGY_ITEM_LOWEST_THRESHOLD) >= 0) {
            return AWARD_STRATEGY_ITEM_LOWEST;
        }
        return AWARD_STRATEGY_TOTAL_LOWEST;
    }

    public static String nodeNameForNode(int nodeNo) {
        return switch (nodeNo) {
            case NODE_DUTY_LEADER_REVIEW -> "值班长审核";
            case NODE_PROFESSIONAL_REVIEW -> "专业工程师审核";
            case NODE_FLOOR_LEADER_DEMAND_REVIEW -> "楼长需求审核";
            case NODE_MANAGER_REVIEW -> "经理审核";
            case NODE_REGION_MANAGER_REVIEW -> "区域经理审核";
            case NODE_DIRECTOR_REVIEW -> "总监审核";
            case NODE_APPROVAL_V7_REVIEW -> "V7 审核";
            case NODE_APPROVAL_V8_REVIEW -> "V8 审核";
            case NODE_APPLICANT_REPLACEMENT_REVIEW -> "申请人替换商品确认";
            default -> "审批节点";
        };
    }

    public static String nodeNameForRole(String roleCode) {
        return switch (roleCode) {
            case ROLE_DUTY_LEADER -> "值班长审核";
            case ROLE_PROFESSIONAL_ENGINEER -> "专业工程师审核";
            case ROLE_FLOOR_LEADER -> "楼长需求审核";
            case ROLE_MANAGER -> "经理审核";
            case ROLE_REGION_MANAGER -> "区域经理审核";
            case ROLE_SENIOR_MANAGER -> "总监审核";
            case ROLE_APPROVAL_V7 -> "V7 审核";
            case ROLE_APPROVAL_V8 -> "V8 审核";
            default -> "审批节点";
        };
    }
}
