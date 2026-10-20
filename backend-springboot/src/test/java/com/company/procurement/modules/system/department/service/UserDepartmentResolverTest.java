package com.company.procurement.modules.system.department.service;

import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserDepartmentResolverTest {

    private final UserDepartmentResolver resolver = new UserDepartmentResolver(null, null, null);

    @Test
    void dictionaryCodeStillResolvesToApprovalRouteFields() {
        UserDepartmentResolver.DepartmentReferences references = references(
            List.of(route("224", "N420N00077", "[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6", "酒仙桥", "M6")),
            List.of(),
            List.of(department("B-JXQ-M6", "世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6", "M6"))
        );

        UserDepartmentResolver.DepartmentFields resolved = resolver.resolve("B-JXQ-M6", null, references);

        assertThat(resolved.deptCode()).isEqualTo("224");
        assertThat(resolved.deptName()).isEqualTo("[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6");
    }

    @Test
    void prefersSpecificBuildingOverShortContainedBuilding() {
        UserDepartmentResolver.DepartmentReferences references = references(
            List.of(
                route("224", "N420N00077", "[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6", "酒仙桥", "M6"),
                route("225", "N420N00078", "[N420N00078]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6V&M5II&M5III", "酒仙桥", "M6V&M5II&M5III")
            ),
            List.of(),
            List.of()
        );

        UserDepartmentResolver.DepartmentFields resolved = resolver.resolve(null, "M6V&M5II&M5III", references);

        assertThat(resolved.deptCode()).isEqualTo("225");
        assertThat(resolved.deptName()).contains("M6V&M5II&M5III");
    }

    @Test
    void keepsOriginalWhenRouteMatchIsAmbiguous() {
        UserDepartmentResolver.DepartmentReferences references = references(
            List.of(
                route("224", "N420N00077", "酒仙桥-M6", "酒仙桥", "M6"),
                route("225", "N420N00078", "酒仙桥-M5", "酒仙桥", "M5")
            ),
            List.of(),
            List.of()
        );

        UserDepartmentResolver.DepartmentFields resolved = resolver.resolve(null, "酒仙桥", references);

        assertThat(resolved.deptCode()).isNull();
        assertThat(resolved.deptName()).isEqualTo("酒仙桥");
    }

    @Test
    void fallsBackToDictionaryOnlyWhenNoRouteOrScopeMatches() {
        UserDepartmentResolver.DepartmentReferences references = references(
            List.of(),
            List.of(),
            List.of(department("B-JXQ-WANG-YIN", "世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-网银", "网银"))
        );

        UserDepartmentResolver.DepartmentFields resolved = resolver.resolve(null, "网银", references);

        assertThat(resolved.deptCode()).isEqualTo("B-JXQ-WANG-YIN");
        assertThat(resolved.deptName()).contains("网银");
    }

    private UserDepartmentResolver.DepartmentReferences references(
        List<ProcurementApprovalRoute> routes,
        List<OrgScope> scopes,
        List<UserDepartmentResolver.DepartmentCandidate> departments
    ) {
        return new UserDepartmentResolver.DepartmentReferences(routes, scopes, departments);
    }

    private ProcurementApprovalRoute route(String deptCode, String costCenterCode, String deptName, String roomName, String buildingName) {
        ProcurementApprovalRoute route = new ProcurementApprovalRoute();
        route.setEnabled(1);
        route.setDeptCode(deptCode);
        route.setCostCenterCode(costCenterCode);
        route.setDeptName(deptName);
        route.setRoomName(roomName);
        route.setBuildingName(buildingName);
        return route;
    }

    private UserDepartmentResolver.DepartmentCandidate department(String code, String fullName, String shortName) {
        return new UserDepartmentResolver.DepartmentCandidate(code, fullName, shortName, "BUILDING");
    }
}
