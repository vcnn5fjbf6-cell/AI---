package com.company.procurement.modules.procurement.assistant.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DifyProcurementRuntimeConfig {

    private static final String KEY_API_BASE_URL = "dify_api_base_url";
    private static final String KEY_API_KEY = "dify_api_key";
    private static final String KEY_APP_TYPE = "dify_app_type";

    private static final List<String> DB_KEYS = List.of(
        KEY_API_BASE_URL,
        KEY_API_KEY,
        KEY_APP_TYPE
    );

    private final DifyProcurementProperties properties;
    private final ProcurementSettingMapper settingMapper;

    public Snapshot current() {
        Map<String, String> settings = settingMap();
        String apiKey = normalizeApiKey(textValue(settings, KEY_API_KEY, properties.getApiKey()));
        String appType = normalizeAppType(textValue(settings, KEY_APP_TYPE, properties.getAppType()));
        String apiBaseUrl = textValue(settings, KEY_API_BASE_URL, "");
        boolean enabledBySetting = StringUtils.hasText(settings.get(KEY_API_KEY));
        return new Snapshot(
            properties.isEnabled() || enabledBySetting,
            apiKey,
            appType,
            resolveWorkflowRunUrl(apiBaseUrl, properties.getWorkflowRunUrl()),
            resolveChatMessagesUrl(apiBaseUrl, properties.getChatMessagesUrl()),
            defaultText(properties.getResponseMode())
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

    private String resolveWorkflowRunUrl(String apiBaseUrl, String defaultUrl) {
        String url = defaultText(apiBaseUrl);
        if (!StringUtils.hasText(url)) {
            return defaultText(defaultUrl);
        }
        if (url.endsWith("/workflows/run")) {
            return url;
        }
        if (url.endsWith("/chat-messages")) {
            return url.substring(0, url.length() - "/chat-messages".length()) + "/workflows/run";
        }
        return trimTrailingSlash(url) + "/workflows/run";
    }

    private String resolveChatMessagesUrl(String apiBaseUrl, String defaultUrl) {
        String url = defaultText(apiBaseUrl);
        if (!StringUtils.hasText(url)) {
            return defaultText(defaultUrl);
        }
        if (url.endsWith("/chat-messages")) {
            return url;
        }
        if (url.endsWith("/workflows/run")) {
            return url.substring(0, url.length() - "/workflows/run".length()) + "/chat-messages";
        }
        return trimTrailingSlash(url) + "/chat-messages";
    }

    private String normalizeAppType(String appType) {
        String value = defaultText(appType).toLowerCase(Locale.ROOT);
        if ("chat".equals(value) || "chatflow".equals(value) || "agent".equals(value) || "advanced-chat".equals(value)) {
            return "chat";
        }
        return "workflow";
    }

    private String normalizeApiKey(String apiKey) {
        String value = defaultText(apiKey);
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("authorization:")) {
            value = value.substring("authorization:".length()).trim();
            lower = value.toLowerCase(Locale.ROOT);
        }
        if (lower.startsWith("bearer ")) {
            return value.substring("bearer ".length()).trim();
        }
        return value;
    }

    private String textValue(Map<String, String> settings, String key, String defaultValue) {
        String value = settings.get(key);
        return StringUtils.hasText(value) ? value.trim() : defaultText(defaultValue);
    }

    private String trimTrailingSlash(String value) {
        String trimmed = defaultText(value);
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public record Snapshot(
        boolean enabled,
        String apiKey,
        String appType,
        String workflowRunUrl,
        String chatMessagesUrl,
        String responseMode
    ) {

        public boolean chatApp() {
            return "chat".equalsIgnoreCase(appType);
        }
    }
}
