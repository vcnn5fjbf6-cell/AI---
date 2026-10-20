package com.company.procurement.modules.platform.aimessagelog.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.aimessagelog.dto.ProcurementAiMessageLogPageQueryDTO;
import com.company.procurement.modules.platform.aimessagelog.entity.ProcurementAiMessageLog;
import com.company.procurement.modules.platform.aimessagelog.vo.ProcurementAiMessageLogVO;

public interface ProcurementAiMessageLogService {

    Long record(ProcurementAiMessageLog log);

    boolean existsBySourceAndMessageId(String sourceChannel, String messageId);

    void updateResult(ProcurementAiMessageLog log);

    PageResult<ProcurementAiMessageLogVO> page(ProcurementAiMessageLogPageQueryDTO queryDTO);

    ProcurementAiMessageLogVO detail(Long id);
}
