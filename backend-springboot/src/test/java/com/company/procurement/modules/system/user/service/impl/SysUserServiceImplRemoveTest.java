package com.company.procurement.modules.system.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.auth.config.FeishuAuthProperties;
import com.company.procurement.modules.platform.permission.entity.PlatformUserAppAccess;
import com.company.procurement.modules.platform.permission.mapper.PlatformUserAppAccessMapper;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.system.department.service.UserDepartmentResolver;
import com.company.procurement.modules.system.role.mapper.SysRoleMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.entity.SysUserRole;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplRemoveTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private PlatformUserAppAccessMapper userAppAccessMapper;
    @Mock
    private ProcurementSettingMapper procurementSettingMapper;
    @Mock
    private FeishuAuthProperties feishuProperties;
    @Mock
    private UserDepartmentResolver userDepartmentResolver;

    private SysUserServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysUserRole.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PlatformUserAppAccess.class);
    }

    @BeforeEach
    void setUp() {
        service = new SysUserServiceImpl(
            userRoleMapper,
            roleMapper,
            supplierMapper,
            userAppAccessMapper,
            procurementSettingMapper,
            feishuProperties,
            userDepartmentResolver
        );
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    @Test
    void removeRevokesRolesAndPlatformAccess() {
        SysUser user = new SysUser();
        user.setId(12L);
        user.setUserType("ENGINEER");
        when(userMapper.selectById(12L)).thenReturn(user);

        service.remove(12L);

        verify(userMapper).deleteById(12L);
        verify(userRoleMapper).delete(any());
        verify(userAppAccessMapper).delete(any());
    }

    @Test
    void importRejectsDepartmentCodeThatCannotResolveAName() {
        UserDepartmentResolver.DepartmentReferences references = emptyDepartmentReferences();
        when(userDepartmentResolver.resolve(eq("AMBIGUOUS"), isNull(), same(references)))
            .thenReturn(new UserDepartmentResolver.DepartmentFields("AMBIGUOUS", null));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            service,
            "importOneUser",
            Map.of("姓名", "测试人员", "部门编码", "AMBIGUOUS"),
            references
        ))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("USER_IMPORT_DEPARTMENT_AMBIGUOUS");
    }

    @Test
    void importRejectsDepartmentNameThatCannotResolveACode() {
        UserDepartmentResolver.DepartmentReferences references = emptyDepartmentReferences();
        when(userDepartmentResolver.resolve(isNull(), eq("同名部门"), same(references)))
            .thenReturn(new UserDepartmentResolver.DepartmentFields(null, "同名部门"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            service,
            "importOneUser",
            Map.of("姓名", "测试人员", "部门名称", "同名部门"),
            references
        ))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("USER_IMPORT_DEPARTMENT_AMBIGUOUS");
    }

    private static UserDepartmentResolver.DepartmentReferences emptyDepartmentReferences() {
        return new UserDepartmentResolver.DepartmentReferences(List.of(), List.of(), List.of());
    }
}
