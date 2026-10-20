package com.company.procurement.modules.platform.errorlog.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.errorlog.dto.SystemErrorLogPageQueryDTO;
import com.company.procurement.modules.platform.errorlog.dto.SystemErrorLogStatusDTO;
import com.company.procurement.modules.platform.errorlog.vo.SystemErrorLogVO;
import jakarta.servlet.http.HttpServletRequest;

public interface SystemErrorLogService {

    void recordException(String errorCode, String errorMessage, Throwable throwable, HttpServletRequest request);

    PageResult<SystemErrorLogVO> page(SystemErrorLogPageQueryDTO queryDTO);

    SystemErrorLogVO detail(Long id);

    void updateStatus(Long id, SystemErrorLogStatusDTO dto);
}
