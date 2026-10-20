package com.company.procurement.modules.ai.governance.controller;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiGovernanceControllerTest {

    @AfterEach
    void clearAuth() {
        AuthContextHolder.clear();
    }

    @Test
    void nonAdminCannotReadOrChangeGovernedContent() {
        AiGovernanceService service = mock(AiGovernanceService.class);
        AiGovernanceController controller = new AiGovernanceController(service);
        AuthContextHolder.set(AuthUser.builder()
            .userId(9L)
            .username("engineer")
            .userType("ENGINEER")
            .roleCodes(List.of("ENGINEER"))
            .build());

        assertThatThrownBy(controller::overview)
            .isInstanceOf(BizException.class)
            .hasMessageContaining("仅系统管理员");
        verifyNoInteractions(service);
    }
}
