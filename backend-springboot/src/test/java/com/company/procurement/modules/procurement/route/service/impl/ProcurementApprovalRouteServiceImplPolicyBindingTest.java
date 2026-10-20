package com.company.procurement.modules.procurement.route.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalLevel;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalLevelUser;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalPolicy;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalLevelMapper;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalLevelUserMapper;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalPolicyMapper;
import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.org.scope.mapper.OrgScopeMapper;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.mapper.ProcurementApprovalRouteMapper;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementApprovalRouteServiceImplPolicyBindingTest {

    @Mock
    private ProcurementApprovalRouteMapper routeMapper;
    @Mock
    private ProcurementApprovalRouteResolver approvalRouteResolver;
    @Mock
    private OrgScopeMapper orgScopeMapper;
    @Mock
    private ProcurementApprovalPolicyMapper policyMapper;
    @Mock
    private ProcurementApprovalLevelMapper levelMapper;
    @Mock
    private ProcurementApprovalLevelUserMapper levelUserMapper;
    @Mock
    private SysUserMapper userMapper;

    private ProcurementApprovalRouteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProcurementApprovalRouteServiceImpl(
            routeMapper,
            approvalRouteResolver,
            orgScopeMapper,
            policyMapper,
            levelMapper,
            levelUserMapper,
            userMapper
        );
    }

    @Test
    void ensurePolicyRebindsExistingPolicyCodeToCurrentScope() {
        ProcurementApprovalRoute route = route("酒仙桥|M6|N420N00077");
        OrgScope scope = scope(3L, "酒仙桥/M6");
        ProcurementApprovalPolicy existing = new ProcurementApprovalPolicy();
        existing.setId(427L);
        existing.setPolicyCode(route.getRouteKey());
        existing.setScopeId(46461L);
        when(policyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, existing);

        ProcurementApprovalPolicy result = ReflectionTestUtils.invokeMethod(service, "ensurePolicy", route, scope);

        assertThat(result).isSameAs(existing);
        assertThat(existing.getScopeId()).isEqualTo(scope.getId());
        verify(policyMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
        verify(policyMapper).updateById(existing);
        verify(policyMapper, never()).insert(any(ProcurementApprovalPolicy.class));
    }

    @Test
    void ensurePolicyCreatesPolicyWhenScopeAndCodeAreBothMissing() {
        ProcurementApprovalRoute route = route("酒仙桥|M5|N420N00076");
        OrgScope scope = scope(2L, "酒仙桥/M5");
        when(policyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ProcurementApprovalPolicy result = ReflectionTestUtils.invokeMethod(service, "ensurePolicy", route, scope);

        assertThat(result).isNotNull();
        assertThat(result.getPolicyCode()).isEqualTo(route.getRouteKey());
        assertThat(result.getScopeId()).isEqualTo(scope.getId());
        verify(policyMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
        verify(policyMapper).insert(result);
    }

    private ProcurementApprovalRoute route(String routeKey) {
        ProcurementApprovalRoute route = new ProcurementApprovalRoute();
        route.setRouteKey(routeKey);
        route.setEnabled(1);
        route.setPoolPushDay(20);
        return route;
    }

    private OrgScope scope(Long id, String name) {
        OrgScope scope = new OrgScope();
        scope.setId(id);
        scope.setScopeName(name);
        return scope;
    }
}
