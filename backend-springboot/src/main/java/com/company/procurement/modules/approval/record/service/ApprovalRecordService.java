package com.company.procurement.modules.approval.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordCreateDTO;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordPageQueryDTO;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordUpdateDTO;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;

import java.util.List;

public interface ApprovalRecordService extends IService<ApprovalRecord> {

    PageResult<ApprovalRecordVO> page(ApprovalRecordPageQueryDTO queryDTO);

    List<ApprovalRecordVO> list(ApprovalRecordPageQueryDTO queryDTO);

    ApprovalRecordVO detail(Long id);

    Long create(ApprovalRecordCreateDTO dto);

    void update(Long id, ApprovalRecordUpdateDTO dto);

    void remove(Long id);
}
