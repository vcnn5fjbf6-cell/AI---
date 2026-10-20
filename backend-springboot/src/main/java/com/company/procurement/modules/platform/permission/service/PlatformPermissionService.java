package com.company.procurement.modules.platform.permission.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionRequestCreateDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionRequestQueryDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionReviewDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformUnitSaveDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformUserAccessUpdateDTO;
import com.company.procurement.modules.platform.permission.vo.PlatformAppVO;
import com.company.procurement.modules.platform.permission.vo.PlatformMyAccessVO;
import com.company.procurement.modules.platform.permission.vo.PlatformPermissionRequestVO;
import com.company.procurement.modules.platform.permission.vo.PlatformSummaryVO;
import com.company.procurement.modules.platform.permission.vo.PlatformUnitVO;
import com.company.procurement.modules.platform.permission.vo.PlatformUserAccessVO;

import java.util.List;

public interface PlatformPermissionService {

    List<PlatformAppVO> apps();

    PlatformSummaryVO summary();

    List<PlatformUserAccessVO> userAccessList();

    void updateUserAccess(Long userId, PlatformUserAccessUpdateDTO dto);

    List<PlatformUnitVO> unitList();

    Long saveUnit(PlatformUnitSaveDTO dto);

    void removeUnit(Long id);

    PageResult<PlatformPermissionRequestVO> requestPage(PlatformPermissionRequestQueryDTO queryDTO);

    PlatformMyAccessVO myAccess();

    Long createRequest(PlatformPermissionRequestCreateDTO dto);

    void reviewRequest(Long id, PlatformPermissionReviewDTO dto);

    List<String> enabledAppCodesByUserId(Long userId);
}
