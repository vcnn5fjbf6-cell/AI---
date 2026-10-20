package com.company.procurement.modules.integration.feishu.config;

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
public class FeishuApprovalCardRuntimeConfig {

    private static final String KEY_APP_ID = "feishu_app_id";
    private static final String KEY_APP_SECRET = "feishu_app_secret";
    private static final String KEY_ENABLED = "feishu_approval_card_enabled";
    private static final String KEY_HEARTBEAT_ENABLED = "feishu_approval_card_heartbeat_enabled";
    private static final String KEY_LONG_CONNECTION_ENABLED = "feishu_approval_card_long_connection_enabled";
    private static final String KEY_VERIFICATION_TOKEN = "feishu_approval_card_verification_token";
    private static final String KEY_ENCRYPT_KEY = "feishu_approval_card_encrypt_key";
    private static final String KEY_APP_BASE_URL = "feishu_approval_card_app_base_url";
    private static final String KEY_SYSTEM_ACTION_URL = "feishu_approval_card_system_action_url";
    private static final String KEY_RECEIVE_ID_TYPE = "feishu_approval_card_receive_id_type";
    private static final String KEY_DETAIL_ITEM_LIMIT = "feishu_approval_card_detail_item_limit";
    private static final String KEY_ADMIN_CARD_COPY_ENABLED = "feishu_admin_card_copy_enabled";
    private static final String KEY_ADMIN_CARD_COPY_USERNAMES = "feishu_admin_card_copy_usernames";

    private static final List<String> DB_KEYS = List.of(
        KEY_APP_ID,
        KEY_APP_SECRET,
        KEY_ENABLED,
        KEY_HEARTBEAT_ENABLED,
        KEY_LONG_CONNECTION_ENABLED,
        KEY_VERIFICATION_TOKEN,
        KEY_ENCRYPT_KEY,
        KEY_APP_BASE_URL,
        KEY_SYSTEM_ACTION_URL,
        KEY_RECEIVE_ID_TYPE,
        KEY_DETAIL_ITEM_LIMIT,
        KEY_ADMIN_CARD_COPY_ENABLED,
        KEY_ADMIN_CARD_COPY_USERNAMES
    );

    private final FeishuApprovalCardProperties cardProperties;
    private final FeishuAuthProperties authProperties;
    private final ProcurementSettingMapper settingMapper;

    public Snapshot current() {
        Map<String, String> settings = settingMap();
        return new Snapshot(
            boolValue(settings, KEY_ENABLED, cardProperties.isEnabled()),
            boolValue(settings, KEY_HEARTBEAT_ENABLED, cardProperties.isHeartbeatEnabled()),
            boolValue(settings, KEY_LONG_CONNECTION_ENABLED, cardProperties.isLongConnectionEnabled()),
            cardProperties.getHeartbeatFixedDelayMs(),
            cardProperties.getBatchSize(),
            cardProperties.getMaxRetryCount(),
            intValue(settings, KEY_DETAIL_ITEM_LIMIT, cardProperties.getDetailItemLimit()),
            textValue(settings, KEY_RECEIVE_ID_TYPE, cardProperties.getReceiveIdType()),
            defaultText(cardProperties.getTestRecipientEmail()),
            textValue(settings, KEY_VERIFICATION_TOKEN, cardProperties.getVerificationToken()),
            textValue(settings, KEY_ENCRYPT_KEY, cardProperties.getEncryptKey()),
            cardProperties.getMessageSendUrl(),
            cardProperties.getMessagePatchUrl(),
            textValue(settings, KEY_APP_BASE_URL, cardProperties.getAppBaseUrl()),
            textValue(settings, KEY_SYSTEM_ACTION_URL, cardProperties.getSystemActionUrl()),
            cardProperties.getApprovalPath(),
            textValue(settings, KEY_APP_ID, authProperties.getAppId()),
            textValue(settings, KEY_APP_SECRET, authProperties.getAppSecret()),
            authProperties.getTenantAccessTokenUrl(),
            boolValue(settings, KEY_ADMIN_CARD_COPY_ENABLED, false),
            textValuePreserveConfiguredBlank(settings, KEY_ADMIN_CARD_COPY_USERNAMES, "admin")
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

    private String textValuePreserveConfiguredBlank(Map<String, String> settings, String key, String defaultValue) {
        if (!settings.containsKey(key)) {
            return defaultText(defaultValue);
        }
        String value = settings.get(key);
        return value == null ? "" : value.trim();
    }

    private boolean boolValue(Map<String, String> settings, String key, boolean defaultValue) {
        String value = settings.get(key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    private int intValue(Map<String, String> settings, String key, int defaultValue) {
        String value = settings.get(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public record Snapshot(
        boolean enabled,
        boolean heartbeatEnabled,
        boolean longConnectionEnabled,
        long heartbeatFixedDelayMs,
        int batchSize,
        int maxRetryCount,
        int detailItemLimit,
        String receiveIdType,
        String testRecipientEmail,
        String verificationToken,
        String encryptKey,
        String messageSendUrl,
        String messagePatchUrl,
        String appBaseUrl,
        String systemActionUrl,
        String approvalPath,
        String appId,
        String appSecret,
        String tenantAccessTokenUrl,
        boolean adminCardCopyEnabled,
        String adminCardCopyUsernames
    ) {

        public String longConnectionSignature() {
            return enabled + "\n"
                + longConnectionEnabled + "\n"
                + appId + "\n"
                + appSecret + "\n"
                + verificationToken + "\n"
                + encryptKey;
        }

        public String tenantTokenSignature() {
            return appId + "\n" + appSecret + "\n" + tenantAccessTokenUrl;
        }
    }
}
