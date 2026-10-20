package com.company.procurement.modules.procurement.workflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowSubmitResultVO {

    private Long requestId;
    private String reqNo;
    private String reqTitle;
    private Integer nextNodeNo;
    private String nextLevelCode;
    private String nextNodeName;
    private String passRule;
    private String passRuleName;
    private Long currentApproverId;
    private List<WorkflowSubmitApproverVO> approvers = new ArrayList<>();

    public boolean isHasNextApprover() {
        return approvers != null && !approvers.isEmpty();
    }
}
