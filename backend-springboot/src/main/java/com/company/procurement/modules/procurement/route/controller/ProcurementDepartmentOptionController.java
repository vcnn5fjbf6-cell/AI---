package com.company.procurement.modules.procurement.route.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteService;
import com.company.procurement.modules.procurement.route.service.ProcurementDepartmentAccessService;
import com.company.procurement.modules.procurement.route.vo.ProcurementApprovalRouteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/workflow/procurement-departments")
@RequiredArgsConstructor
public class ProcurementDepartmentOptionController {

    private final ProcurementDepartmentAccessService departmentAccessService;
    private final ProcurementApprovalRouteService routeService;

    @GetMapping
    public Result<List<ProcurementApprovalRouteVO>> listEnabledRoutes() {
        Set<Long> allowedIds = departmentAccessService.listAllowedRoutes(AuthContextHolder.requireUser()).stream()
            .map(route -> route.getId())
            .collect(java.util.stream.Collectors.toSet());
        return Result.ok(routeService.listEnabledRoutes().stream()
            .filter(route -> allowedIds.contains(route.getId()))
            .map(this::toSafeOption)
            .toList());
    }

    private ProcurementApprovalRouteVO toSafeOption(ProcurementApprovalRouteVO source) {
        ProcurementApprovalRouteVO option = new ProcurementApprovalRouteVO();
        option.setId(source.getId());
        option.setEnabled(source.getEnabled());
        option.setRoomNature(source.getRoomNature());
        option.setRoomName(source.getRoomName());
        option.setBuildingName(source.getBuildingName());
        option.setCostCenterCode(source.getCostCenterCode());
        option.setDeptCode(source.getDeptCode());
        option.setProvince(source.getProvince());
        option.setCity(source.getCity());
        option.setDeptName(source.getDeptName());
        option.setRegionName(source.getRegionName());
        return option;
    }
}
