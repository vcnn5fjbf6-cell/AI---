package com.company.procurement.modules.workbench.service;

import com.company.procurement.modules.workbench.dto.WorkbenchApprovalDTO;
import com.company.procurement.modules.workbench.dto.WorkbenchFollowDTO;
import com.company.procurement.modules.workbench.dto.WorkbenchRequestQueryDTO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.workbench.vo.WorkbenchPageVO;
import com.company.procurement.modules.workbench.vo.WorkbenchRequestDetailVO;
import com.company.procurement.modules.workbench.vo.WorkbenchUserOptionVO;

import java.util.List;

public interface WorkbenchService {

    WorkbenchPageVO pageRequests(WorkbenchRequestQueryDTO queryDTO);

    WorkbenchRequestDetailVO detail(String sourceType, Long sourceId);

    WorkflowSubmitResultVO approve(String sourceType, Long sourceId, WorkbenchApprovalDTO dto);

    void setFollow(WorkbenchFollowDTO dto);

    List<WorkbenchUserOptionVO> userOptions(String keyword);
}
