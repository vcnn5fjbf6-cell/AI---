package com.company.procurement.modules.platform.permission.service.impl;

import com.company.procurement.modules.ehs.config.EhsProperties;
import com.company.procurement.modules.outsourcing.config.OutsourcingProperties;
import com.company.procurement.modules.platform.permission.mapper.PlatformPermissionRequestMapper;
import com.company.procurement.modules.platform.permission.mapper.PlatformUnitAppScopeMapper;
import com.company.procurement.modules.platform.permission.mapper.PlatformUnitMapper;
import com.company.procurement.modules.platform.permission.mapper.PlatformUserAppAccessMapper;
import com.company.procurement.modules.system.role.mapper.SysRoleMapper;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformPermissionServiceOutsourcingTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private EhsProperties ehsProperties;
    @Mock
    private OutsourcingProperties outsourcingProperties;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private PlatformUnitMapper unitMapper;
    @Mock
    private PlatformUnitAppScopeMapper unitAppScopeMapper;
    @Mock
    private PlatformUserAppAccessMapper userAppAccessMapper;
    @Mock
    private PlatformPermissionRequestMapper requestMapper;

    @InjectMocks
    private PlatformPermissionServiceImpl service;

    @Test
    void exposesOutsourcingOnlyWhenEnvironmentFeatureIsEnabled() {
        when(outsourcingProperties.isEnabled()).thenReturn(false);

        assertThat(service.apps()).extracting("appCode").doesNotContain("OUTSOURCING");

        when(outsourcingProperties.isEnabled()).thenReturn(true);

        assertThat(service.apps()).extracting("appCode").contains("OUTSOURCING");
    }
}
