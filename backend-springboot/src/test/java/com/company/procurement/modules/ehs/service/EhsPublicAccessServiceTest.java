package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.ehs.entity.EhsPublicAccessToken;
import com.company.procurement.modules.ehs.mapper.EhsPublicAccessTokenMapper;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EhsPublicAccessServiceTest {

    @Mock private EhsPublicAccessTokenMapper tokenMapper;
    @Mock private FeishuApprovalCardRuntimeConfig runtimeConfig;

    private EhsPublicAccessService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EhsPublicAccessToken.class);
        service = new EhsPublicAccessService(tokenMapper, runtimeConfig);
    }

    @Test
    void applicantTokenStoresOnlyHash() {
        when(tokenMapper.insert(any(EhsPublicAccessToken.class))).thenAnswer(invocation -> {
            EhsPublicAccessToken row = invocation.getArgument(0);
            row.setId(21L);
            return 1;
        });

        EhsPublicAccessService.IssuedToken issued = service.issueApplicantToken(8L);

        ArgumentCaptor<EhsPublicAccessToken> captor = ArgumentCaptor.forClass(EhsPublicAccessToken.class);
        verify(tokenMapper).insert(captor.capture());
        EhsPublicAccessToken stored = captor.getValue();
        assertThat(issued.rawToken()).hasSize(43);
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(issued.rawToken());
        assertThat(stored.getTokenType()).isEqualTo(EhsPublicAccessService.TYPE_APPLICANT);
        assertThat(stored.getPermitId()).isEqualTo(8L);
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(179));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void validationQueryRequiresTypeUnrevokedAndUnexpiredToken() {
        EhsPublicAccessToken stored = new EhsPublicAccessToken();
        stored.setId(22L);
        stored.setPermitId(8L);
        when(tokenMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);

        assertThat(service.requireApplicant("a".repeat(43))).isSameAs(stored);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tokenMapper).selectOne(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertThat(sql).contains("token_hash", "token_type", "revoked_at", "expires_at", "limit 1");
    }

    @Test
    void blankOrUnknownTokenIsRejected() {
        assertThatThrownBy(() -> service.requireApplicant(""))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("访问链接无效或已过期");

        assertThatThrownBy(() -> service.requireApprover("b".repeat(43)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("访问链接无效或已过期");
    }
}
