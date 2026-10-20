package com.company.procurement.modules.procurement.assistant.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService.RuntimeSkill;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementPurposeOptimizationServiceTest {

    @Mock
    private M3VisionClient m3VisionClient;

    @Mock
    private AiGovernanceService governanceService;

    private ProcurementPurposeOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new ProcurementPurposeOptimizationService(
            m3VisionClient,
            governanceService,
            new DefaultResourceLoader()
        );
    }

    @Test
    void loadsManagedSkillForEveryOptimizationAndReturnsDraftContent() {
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(governanceService.runtimeSkill(ProcurementPurposeOptimizationService.SKILL_CODE))
            .thenReturn(new RuntimeSkill(
                ProcurementPurposeOptimizationService.SKILL_CODE,
                "采购原因优化",
                3,
                "只使用原文已有事实进行优化",
                List.of(new AiGovernanceService.RuntimeKnowledge(
                    "procurement-compliance-rules",
                    "采购原因统一标准",
                    4,
                    "普通采购统一检查使用场景、现状及影响和采购必要性"
                ))
            ));
        when(m3VisionClient.optimizeProcurementPurpose(anyString(), anyString(), anyInt(), anyString()))
            .thenReturn("当前库存剩余20包，现有数量不足，需补充采购以满足实际使用需求。");

        var first = service.optimize("库房内还剩下20包了数量不够了");
        var second = service.optimize("库房内还剩下20包了数量不够了");

        assertThat(first.optimizedContent()).contains("库存剩余20包").doesNotStartWith("优化后");
        assertThat(second.optimizedContent()).isEqualTo(first.optimizedContent());
        verify(governanceService, times(2))
            .runtimeSkill(ProcurementPurposeOptimizationService.SKILL_CODE);
        ArgumentCaptor<String> skillCaptor = ArgumentCaptor.forClass(String.class);
        verify(m3VisionClient, times(2)).optimizeProcurementPurpose(
            skillCaptor.capture(), anyString(), anyInt(), anyString());
        assertThat(skillCaptor.getAllValues()).allSatisfy(skill -> assertThat(skill)
            .contains("procurement-purpose-optimization")
            .contains("修订 3")
            .contains("只使用原文已有事实")
            .contains("采购原因统一标准")
            .contains("现状及影响"));
    }

    @Test
    void databaseFailureUsesPackagedFallbackSkill() {
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(governanceService.runtimeSkill(ProcurementPurposeOptimizationService.SKILL_CODE))
            .thenThrow(new IllegalStateException("database unavailable"));
        when(m3VisionClient.optimizeProcurementPurpose(anyString(), anyString(), anyInt(), anyString()))
            .thenReturn("现有设备不够用；使用场景：【请补充】；现状问题及影响：【请补充】；采购必要性：【请补充】");

        var result = service.optimize("现有设备不够用");

        ArgumentCaptor<String> skillCaptor = ArgumentCaptor.forClass(String.class);
        verify(m3VisionClient).optimizeProcurementPurpose(
            skillCaptor.capture(), anyString(), anyInt(), anyString());
        assertThat(skillCaptor.getValue())
            .contains("采购原因优化")
            .contains("采购原因统一标准")
            .contains("不得用泛化套话");
        assertThat(result.optimizedContent()).contains("使用场景：【请补充】");
    }

    @Test
    void disabledManagedSkillDoesNotSilentlyUsePackagedFallback() {
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(governanceService.runtimeSkill(ProcurementPurposeOptimizationService.SKILL_CODE))
            .thenReturn(null);

        assertThatThrownBy(() -> service.optimize("现有设备不够用"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("Skill未启用");
    }

    @Test
    void modelUnavailableLeavesOriginalReasonUntouched() {
        when(m3VisionClient.canCompleteText()).thenReturn(false);

        assertThatThrownBy(() -> service.optimize("现有设备不够用"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("当前采购原因不会被修改");
    }
}
