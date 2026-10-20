package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.modules.ehs.mapper.EhsKnowledgeDocumentMapper;
import com.company.procurement.modules.ehs.mapper.EhsNotificationLogMapper;
import com.company.procurement.modules.ehs.vo.EhsModuleStatusVO;
import com.company.procurement.modules.ehs.vo.EhsOverviewVO;
import com.company.procurement.modules.ehs.vo.EhsRegistryStatsVO;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EhsOverviewServiceTest {

    @Mock private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock private ApprovalTaskMapper approvalTaskMapper;
    @Mock private EhsRegistryService registryService;
    @Mock private EhsPermitService permitService;
    @Mock private EhsKnowledgeDocumentMapper knowledgeDocumentMapper;
    @Mock private EhsNotificationLogMapper notificationLogMapper;
    @Mock private FeishuApprovalCardRuntimeConfig feishuRuntimeConfig;
    @Mock private FeishuApprovalCardRuntimeConfig.Snapshot feishuSnapshot;

    private EhsOverviewService service;

    @BeforeEach
    void setUp() {
        service = new EhsOverviewService(
            approvalInstanceMapper,
            approvalTaskMapper,
            registryService,
            permitService,
            knowledgeDocumentMapper,
            notificationLogMapper,
            feishuRuntimeConfig
        );
        EhsRegistryStatsVO emptyStats = EhsRegistryStatsVO.builder().build();
        when(registryService.stats(anyString())).thenReturn(emptyStats);
        when(registryService.listByModule(anyString())).thenReturn(List.of());
        when(permitService.stats()).thenReturn(Map.of("total", 0L, "approving", 0L, "closed", 0L));
        when(knowledgeDocumentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(notificationLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(feishuRuntimeConfig.current()).thenReturn(feishuSnapshot);
        when(feishuSnapshot.enabled()).thenReturn(false);
    }

    @Test
    void shouldExposeAllModulesAsConnectedEvenWhenTablesAreEmpty() {
        when(approvalInstanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        EhsOverviewVO overview = service.overview(42L);

        assertThat(overview.isDataSourceConnected()).isTrue();
        assertThat(overview.isSharedApprovalAvailable()).isTrue();
        assertThat(overview.isSharedNotificationAvailable()).isFalse();
        assertThat(overview.getModules()).hasSize(5);
        assertThat(overview.getModules()).allMatch(module -> "CONNECTED".equals(module.getStatus()));
        assertThat(moduleStatus(overview, "HAZARD").getTotalCount()).isZero();
    }

    @Test
    void shouldCountPersistedPermitStatsAndCurrentUsersPendingTasks() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(101L);
        when(approvalInstanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(instance));
        when(approvalTaskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(permitService.stats()).thenReturn(Map.of("total", 3L, "approving", 1L, "closed", 1L));

        EhsOverviewVO overview = service.overview(42L);

        assertThat(overview.getPermitTotalCount()).isEqualTo(3L);
        assertThat(overview.getPermitApprovingCount()).isEqualTo(1L);
        assertThat(overview.getPermitCompletedCount()).isEqualTo(1L);
        assertThat(overview.getMyPendingApprovalCount()).isEqualTo(2L);
    }

    private EhsModuleStatusVO moduleStatus(EhsOverviewVO overview, String moduleCode) {
        return overview.getModules().stream()
            .filter(module -> moduleCode.equals(module.getModuleCode()))
            .findFirst()
            .orElseThrow();
    }
}
