package com.company.procurement.modules.integration.feishu.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.auth.config.FeishuAuthProperties;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuApprovalCardRuntimeConfigTest {

    @Test
    void readsDedicatedTestRecipientFromEnvironmentProperties() {
        ProcurementSettingMapper settingMapper = mock(ProcurementSettingMapper.class);
        when(settingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        FeishuApprovalCardProperties properties = new FeishuApprovalCardProperties();
        properties.setTestRecipientEmail("test-recipient@example.com");

        FeishuApprovalCardRuntimeConfig config = new FeishuApprovalCardRuntimeConfig(
            properties,
            new FeishuAuthProperties(),
            settingMapper
        );

        assertThat(config.current().testRecipientEmail()).isEqualTo("test-recipient@example.com");
    }

    @Test
    void preservesBlankAdminCopyUsernamesFromDatabase() {
        ProcurementSettingMapper settingMapper = mock(ProcurementSettingMapper.class);
        ProcurementSetting enabled = setting("feishu_admin_card_copy_enabled", "true");
        ProcurementSetting usernames = setting("feishu_admin_card_copy_usernames", "");
        when(settingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(enabled, usernames));

        FeishuApprovalCardRuntimeConfig config = new FeishuApprovalCardRuntimeConfig(
            new FeishuApprovalCardProperties(),
            new FeishuAuthProperties(),
            settingMapper
        );

        FeishuApprovalCardRuntimeConfig.Snapshot snapshot = config.current();

        assertThat(snapshot.adminCardCopyEnabled()).isTrue();
        assertThat(snapshot.adminCardCopyUsernames()).isEmpty();
    }

    @Test
    void readsSystemActionUrlFromDatabase() {
        ProcurementSettingMapper settingMapper = mock(ProcurementSettingMapper.class);
        ProcurementSetting actionUrl = setting(
            "feishu_approval_card_system_action_url",
            "https://mercy-sheep-essentials-genius.trycloudflare.com/login?redirect=/workbench"
        );
        when(settingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(actionUrl));

        FeishuApprovalCardRuntimeConfig config = new FeishuApprovalCardRuntimeConfig(
            new FeishuApprovalCardProperties(),
            new FeishuAuthProperties(),
            settingMapper
        );

        FeishuApprovalCardRuntimeConfig.Snapshot snapshot = config.current();

        assertThat(snapshot.systemActionUrl())
            .isEqualTo("https://mercy-sheep-essentials-genius.trycloudflare.com/login?redirect=/workbench");
    }

    private ProcurementSetting setting(String key, String value) {
        ProcurementSetting setting = new ProcurementSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
