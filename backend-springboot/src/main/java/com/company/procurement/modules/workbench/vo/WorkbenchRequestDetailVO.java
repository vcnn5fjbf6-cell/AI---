package com.company.procurement.modules.workbench.vo;

import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchRequestDetailVO {

    private WorkbenchRequestVO summary;

    private List<WorkbenchFlowNodeVO> flowNodes = new ArrayList<>();

    private List<WorkbenchDetailRowVO> detailRows = new ArrayList<>();

    private List<ApprovalRecordVO> approvalRecords = new ArrayList<>();

    private LocalDateTime currentApprovalDueAt;
}
