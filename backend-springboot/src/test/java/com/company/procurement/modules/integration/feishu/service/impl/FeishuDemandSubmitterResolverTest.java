package com.company.procurement.modules.integration.feishu.service.impl;

import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuDemandSubmitterResolverTest {

    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private SysUserMapper userMapper;

    private FeishuDemandSubmitterResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new FeishuDemandSubmitterResolver(purchaseRequestMapper, userMapper);
    }

    @Test
    void ordinaryRequestUsesItsApplicant() {
        PurchaseRequest request = request(1L, 101L);
        PurchaseRequestItem item = item(11L, null);
        when(userMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(user(101L, "杨文婷")));

        FeishuDemandSubmitterResolver.SubmitterContext result = resolver.resolve(request, List.of(item));

        assertThat(result.summary()).isEqualTo("杨文婷");
        assertThat(result.hasMultipleSubmitters()).isFalse();
        assertThat(result.itemSubmitter(11L)).isEqualTo("杨文婷");
        verify(purchaseRequestMapper, never()).selectBatchIds(any(Collection.class));
    }

    @Test
    void aggregateRequestUsesSourceApplicantsAndIgnoresTriggeringAdmin() {
        PurchaseRequest aggregate = request(1L, 999L);
        PurchaseRequestItem first = item(11L, 21L);
        PurchaseRequestItem second = item(12L, 22L);
        PurchaseRequestItem third = item(13L, 23L);
        when(purchaseRequestMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(
            request(21L, 101L),
            request(22L, 102L),
            request(23L, 103L)
        ));
        when(userMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(
            user(101L, "杨文婷"),
            user(102L, "张三"),
            user(103L, "李四"),
            user(999L, "系统管理员")
        ));

        FeishuDemandSubmitterResolver.SubmitterContext result = resolver.resolve(
            aggregate,
            List.of(first, second, third)
        );

        assertThat(result.summary()).isEqualTo("杨文婷、张三等3人");
        assertThat(result.hasMultipleSubmitters()).isTrue();
        assertThat(result.itemSubmitter(11L)).isEqualTo("杨文婷");
        assertThat(result.itemSubmitter(12L)).isEqualTo("张三");
        assertThat(result.itemSubmitter(13L)).isEqualTo("李四");
        assertThat(result.summary()).doesNotContain("系统管理员");
    }

    @Test
    void requestWithoutItemsFallsBackToApplicantForHistoricalCards() {
        PurchaseRequest request = request(1L, 101L);
        when(userMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(user(101L, "历史申请人")));

        FeishuDemandSubmitterResolver.SubmitterContext result = resolver.resolve(request, List.of());

        assertThat(result.summary()).isEqualTo("历史申请人");
        assertThat(result.itemSubmitters()).isEmpty();
    }

    private PurchaseRequest request(Long id, Long applicantUserId) {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(id);
        request.setApplicantUserId(applicantUserId);
        return request;
    }

    private PurchaseRequestItem item(Long id, Long sourceRequestId) {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(id);
        item.setSourceRequestId(sourceRequestId);
        return item;
    }

    private SysUser user(Long id, String realName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(realName);
        return user;
    }
}
