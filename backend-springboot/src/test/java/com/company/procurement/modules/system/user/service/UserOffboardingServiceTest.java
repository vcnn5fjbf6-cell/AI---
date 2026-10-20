package com.company.procurement.modules.system.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalLevelUser;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalLevelUserMapper;
import com.company.procurement.modules.system.user.dto.UserOffboardingDTO;
import com.company.procurement.modules.system.user.dto.UserOffboardingReplacementDTO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.UserOffboardingMapper;
import com.company.procurement.modules.system.user.vo.UserOffboardingImpactItemVO;
import com.company.procurement.modules.system.user.vo.UserVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOffboardingServiceTest {

    @Mock
    private SysUserService userService;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private UserOffboardingMapper offboardingMapper;
    @Mock
    private ProcurementApprovalLevelUserMapper levelUserMapper;
    @Mock
    private ApprovalTaskService approvalTaskService;

    private UserOffboardingService service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            ProcurementApprovalLevelUser.class
        );
    }

    @BeforeEach
    void setUp() {
        service = new UserOffboardingService(
            userService,
            userMapper,
            offboardingMapper,
            levelUserMapper,
            approvalTaskService
        );
        AuthContextHolder.set(AuthUser.builder().userId(99L).username("admin").build());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void offboardReassignsPendingTaskBeforeFreezingAccount() {
        UserVO departing = employeeUser(10L, "离职员工", 1);
        SysUser replacement = new SysUser();
        replacement.setId(20L);
        replacement.setUsername("replacement");
        replacement.setRealName("接替员工");
        replacement.setUserType("ENGINEER");
        replacement.setStatus(1);
        SysUser persisted = new SysUser();
        persisted.setId(10L);
        persisted.setStatus(1);
        persisted.setSessionSecret("old-secret");
        UserOffboardingImpactItemVO impact = new UserOffboardingImpactItemVO();
        impact.setImpactKey("TASK:50");
        impact.setImpactType(UserOffboardingService.IMPACT_PENDING_TASK);
        impact.setReferenceId(50L);

        when(userService.detail(10L)).thenReturn(departing);
        when(userService.detail(20L)).thenReturn(employeeUser(20L, "接替员工", 1));
        when(offboardingMapper.selectApprovalConfigImpacts(10L)).thenReturn(List.of());
        when(offboardingMapper.selectPendingTaskImpacts(10L)).thenReturn(List.of(impact));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(replacement));
        when(userMapper.selectById(10L)).thenReturn(persisted);

        UserOffboardingReplacementDTO replacementDTO = new UserOffboardingReplacementDTO();
        replacementDTO.setImpactKey("TASK:50");
        replacementDTO.setReplacementUserId(20L);
        UserOffboardingDTO dto = new UserOffboardingDTO();
        dto.setReplacements(List.of(replacementDTO));

        service.offboard(10L, dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApprovalTaskService.PendingTaskReassignment>> taskCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(approvalTaskService).reassignPendingTasks(taskCaptor.capture());
        assertThat(taskCaptor.getValue()).containsExactly(
            new ApprovalTaskService.PendingTaskReassignment(50L, 10L, 20L)
        );
        assertThat(persisted.getStatus()).isEqualTo(UserOffboardingService.STATUS_DEPARTED);
        assertThat(persisted.getSessionSecret()).isNotBlank().isNotEqualTo("old-secret");
        verify(userMapper).updateById(persisted);
    }

    @Test
    void offboardReplacesLongTermApprovalBinding() {
        UserVO departing = employeeUser(10L, "离职员工", 1);
        SysUser replacement = new SysUser();
        replacement.setId(20L);
        replacement.setUsername("replacement");
        replacement.setRealName("接替员工");
        replacement.setUserType("ENGINEER");
        replacement.setStatus(1);
        SysUser persisted = new SysUser();
        persisted.setId(10L);
        persisted.setStatus(1);
        UserOffboardingImpactItemVO impact = new UserOffboardingImpactItemVO();
        impact.setImpactKey("CONFIG:40");
        impact.setImpactType(UserOffboardingService.IMPACT_APPROVAL_CONFIG);
        impact.setReferenceId(40L);
        ProcurementApprovalLevelUser departingBinding = new ProcurementApprovalLevelUser();
        departingBinding.setId(40L);
        departingBinding.setLevelId(100L);
        departingBinding.setUserId(10L);
        departingBinding.setSortNo(2);
        departingBinding.setStatus(1);

        when(userService.detail(10L)).thenReturn(departing);
        when(userService.detail(20L)).thenReturn(employeeUser(20L, "接替员工", 1));
        when(offboardingMapper.selectApprovalConfigImpacts(10L)).thenReturn(List.of(impact));
        when(offboardingMapper.selectPendingTaskImpacts(10L)).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(replacement));
        when(levelUserMapper.selectById(40L)).thenReturn(departingBinding);
        when(levelUserMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectById(10L)).thenReturn(persisted);

        UserOffboardingReplacementDTO replacementDTO = new UserOffboardingReplacementDTO();
        replacementDTO.setImpactKey("CONFIG:40");
        replacementDTO.setReplacementUserId(20L);
        UserOffboardingDTO dto = new UserOffboardingDTO();
        dto.setReplacements(List.of(replacementDTO));

        service.offboard(10L, dto);

        verify(levelUserMapper).insert(org.mockito.ArgumentMatchers.<ProcurementApprovalLevelUser>argThat(binding ->
            Objects.equals(binding.getLevelId(), 100L)
                && Objects.equals(binding.getUserId(), 20L)
                && Objects.equals(binding.getStatus(), 1)
                && Objects.equals(binding.getSortNo(), 2)
        ));
        assertThat(departingBinding.getStatus()).isZero();
        assertThat(departingBinding.getEffectiveTo()).isNotNull();
        verify(levelUserMapper).updateById(departingBinding);
        assertThat(persisted.getStatus()).isEqualTo(UserOffboardingService.STATUS_DEPARTED);
    }

    private UserVO employeeUser(Long id, String name, Integer status) {
        UserVO user = new UserVO();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRealName(name);
        user.setUserType("ENGINEER");
        user.setStatus(status);
        return user;
    }
}
