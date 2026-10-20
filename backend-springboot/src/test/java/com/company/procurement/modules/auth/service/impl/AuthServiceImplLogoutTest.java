package com.company.procurement.modules.auth.service.impl;

import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.JwtProperties;
import com.company.procurement.common.security.JwtTokenProvider;
import com.company.procurement.modules.auth.config.FeishuAuthProperties;
import com.company.procurement.modules.auth.service.AuthUserResolver;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserPasswordHistoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplLogoutTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserPasswordHistoryMapper passwordHistoryMapper;
    @Mock
    private AuthUserResolver authUserResolver;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private FeishuAuthProperties feishuProperties;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userMapper, passwordHistoryMapper, authUserResolver,
            jwtTokenProvider, jwtProperties, feishuProperties);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void logoutRotatesSessionSecretSoExistingTokensAreRevoked() {
        AuthContextHolder.set(AuthUser.builder().userId(7L).username("alice").build());
        SysUser user = new SysUser();
        user.setId(7L);
        user.setSessionSecret("old-session-secret");
        when(userMapper.selectById(7L)).thenReturn(user);

        service.logout();

        assertThat(user.getSessionSecret()).isNotBlank();
        assertThat(user.getSessionSecret()).isNotEqualTo("old-session-secret");
        verify(userMapper).updateById(user);
        assertThat(AuthContextHolder.get()).isNull();
    }
}
