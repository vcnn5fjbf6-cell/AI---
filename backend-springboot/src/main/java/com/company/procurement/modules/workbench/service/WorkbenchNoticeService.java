package com.company.procurement.modules.workbench.service;

import com.company.procurement.modules.workbench.dto.WorkbenchNoticeSaveDTO;
import com.company.procurement.modules.workbench.vo.WorkbenchNoticeVO;

import java.util.List;

public interface WorkbenchNoticeService {

    List<WorkbenchNoticeVO> listVisible();

    List<WorkbenchNoticeVO> listManage();

    WorkbenchNoticeVO create(WorkbenchNoticeSaveDTO dto);

    WorkbenchNoticeVO update(Long id, WorkbenchNoticeSaveDTO dto);

    void delete(Long id);
}
