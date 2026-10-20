package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.ehs.vo.EhsDepartmentOptionVO;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.mapper.ProcurementApprovalRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class EhsDepartmentOptionService {

    private final ProcurementApprovalRouteMapper routeMapper;

    public List<EhsDepartmentOptionVO> listEnabledOptions() {
        return routeMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalRoute>()
                .eq(ProcurementApprovalRoute::getEnabled, 1)
                .orderByAsc(ProcurementApprovalRoute::getRegionName)
                .orderByAsc(ProcurementApprovalRoute::getRoomName)
                .orderByAsc(ProcurementApprovalRoute::getBuildingName))
            .stream()
            .map(this::toOption)
            .toList();
    }

    public ProcurementApprovalRoute requireEnabledRoute(Long routeId) {
        ProcurementApprovalRoute route = routeId == null ? null : routeMapper.selectById(routeId);
        if (route == null || !Integer.valueOf(1).equals(route.getEnabled())) {
            throw new BizException("EHS_DEPARTMENT_ROUTE_INVALID", "请选择已启用的部门审批配置");
        }
        return route;
    }

    private EhsDepartmentOptionVO toOption(ProcurementApprovalRoute route) {
        String label = Stream.of(route.getRegionName(), route.getRoomName(), route.getBuildingName(), route.getDeptName())
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .reduce((left, right) -> left + " / " + right)
            .orElse("部门配置 " + route.getId());
        return EhsDepartmentOptionVO.builder()
            .approvalRouteId(route.getId())
            .deptCode(firstText(route.getDeptCode(), route.getCostCenterCode()))
            .deptName(firstText(route.getDeptName(), route.getRoomName(), route.getBuildingName()))
            .regionName(route.getRegionName())
            .roomName(route.getRoomName())
            .buildingName(route.getBuildingName())
            .label(label)
            .build();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
