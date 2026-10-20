package com.company.procurement.modules.procurement.route.service;

import com.company.procurement.common.export.ExportFile;
import com.company.procurement.modules.procurement.route.dto.ProcurementApprovalRouteQueryDTO;
import com.company.procurement.modules.procurement.route.dto.ProcurementApprovalRouteSaveDTO;
import com.company.procurement.modules.procurement.route.vo.ProcurementApprovalRouteConfigVO;
import com.company.procurement.modules.procurement.route.vo.ProcurementApprovalRouteVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProcurementApprovalRouteService {

    ProcurementApprovalRouteConfigVO getConfig(ProcurementApprovalRouteQueryDTO queryDTO);

    List<ProcurementApprovalRouteVO> listEnabledRoutes();

    Long createRoute(ProcurementApprovalRouteSaveDTO dto);

    void updateRoute(Long id, ProcurementApprovalRouteSaveDTO dto);

    void updateRouteAddress(Long id, ProcurementApprovalRouteSaveDTO dto);

    void deleteRoute(Long id);

    ExportFile exportImportTemplate(ProcurementApprovalRouteQueryDTO queryDTO);

    int importRouteTemplate(MultipartFile file);
}
