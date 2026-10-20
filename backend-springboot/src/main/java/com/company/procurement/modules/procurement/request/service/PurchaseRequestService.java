package com.company.procurement.modules.procurement.request.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestPageQueryDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestUpdateDTO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;

import java.util.List;

public interface PurchaseRequestService extends IService<PurchaseRequest> {

    PageResult<PurchaseRequestVO> page(PurchaseRequestPageQueryDTO queryDTO);

    List<PurchaseRequestVO> list(PurchaseRequestPageQueryDTO queryDTO);

    PurchaseRequestVO detail(Long id);

    Long create(PurchaseRequestCreateDTO dto);

    void update(Long id, PurchaseRequestUpdateDTO dto);

    void remove(Long id);

    WorkflowSubmitResultVO previewSubmit(Long id);

    WorkflowSubmitResultVO submit(Long id);
}
