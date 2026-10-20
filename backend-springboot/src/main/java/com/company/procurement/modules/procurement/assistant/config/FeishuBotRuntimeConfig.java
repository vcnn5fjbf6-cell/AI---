package com.company.procurement.modules.procurement.assistant.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.auth.config.FeishuAuthProperties;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeishuBotRuntimeConfig {

    private static final String KEY_BOT_ENABLED = "feishu_bot_enabled";
    private static final String KEY_APP_ID = "feishu_app_id";
    private static final String KEY_APP_SECRET = "feishu_app_secret";
    private static final String KEY_VERIFICATION_TOKEN = "feishu_approval_card_verification_token";

    private static final List<String> DB_KEYS = List.of(
        KEY_BOT_ENABLED,
        KEY_APP_ID,
        KEY_APP_SECRET,
        KEY_VERIFICATION_TOKEN
    );

    private final FeishuBotProperties botProperties;
    private final FeishuAuthProperties authProperties;
    private final ProcurementSettingMapper settingMapper;

    public Snapshot current() {
        Map<String, String> settings = settingMap();
        return new Snapshot(
            boolValue(settings, KEY_BOT_ENABLED, botProperties.isEnabled()),
            textValue(settings, KEY_VERIFICATION_TOKEN, botProperties.getVerificationToken()),
            defaultText(botProperties.getMessageSendUrl()),
            textValue(settings, KEY_APP_ID, authProperties.getAppId()),
            textValue(settings, KEY_APP_SECRET, authProperties.getAppSecret()),
            defaultText(authProperties.getTenantAccessTokenUrl()),
            defaultText(authProperties.getUserDetailUrl()),
            defaultText(authProperties.getUserBatchGetIdUrl())
        );
    }

    private Map<String, String> settingMap() {
        Map<String, String> map = new LinkedHashMap<>();
        List<ProcurementSetting> rows = settingMapper.selectList(new LambdaQueryWrapper<ProcurementSetting>()
            .in(ProcurementSetting::getSettingKey, DB_KEYS));
        for (ProcurementSetting row : rows) {
            if (StringUtils.hasText(row.getSettingKey())) {
                map.put(row.getSettingKey().trim(), row.getSettingValue());
            }
        }
        return map;
    }

    private String textValue(Map<String, String> settings, String key, String defaultValue) {
        String value = settings.get(key);
        return StringUtils.hasText(value) ? value.trim() : defaultText(defaultValue);
    }

    private boolean boolValue(Map<String, String> settings, String key, boolean defaultValue) {
        String value = settings.get(key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public record Snapshot(
        boolean enabled,
        String verificationToken,
        String messageSendUrl,
        String appId,
        String appSecret,
        String tenantAccessTokenUrl,
        String userDetailUrl,
        String userBatchGetIdUrl
    ) {

        public String tenantTokenSignature() {
            return appId + "\n" + appSecret + "\n" + tenantAccessTokenUrl;
        }
    }
}
