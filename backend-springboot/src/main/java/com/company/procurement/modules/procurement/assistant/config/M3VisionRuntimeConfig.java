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
public class M3VisionRuntimeConfig {

    private static final String KEY_ENABLED = "m3_vision_enabled";
    private static final String KEY_BASE_URL = "m3_base_url";
    private static final String KEY_API_KEY = "m3_api_key";
    private static final String KEY_MODEL = "m3_vision_model";
    private static final String KEY_ENDPOINT = "m3_endpoint";

    private static final List<String> DB_KEYS = List.of(
        KEY_ENABLED,
        KEY_BASE_URL,
        KEY_API_KEY,
        KEY_MODEL,
        KEY_ENDPOINT
    );

    private final M3VisionProperties properties;
    private final ProcurementSettingMapper settingMapper;

    public Snapshot current() {
        Map<String, String> settings = settingMap();
        String baseUrl = textValue(settings, KEY_BASE_URL, properties.getBaseUrl());
        String endpoint = textValue(settings, KEY_ENDPOINT, properties.getEndpoint());
        String model = textValue(settings, KEY_MODEL, properties.getModel());
        boolean enabled = boolValue(settings, KEY_ENABLED, properties.isEnabled());
        return new Snapshot(
            enabled,
            baseUrl,
            textValue(settings, KEY_API_KEY, properties.getApiKey()),
            model,
            endpoint,
            resolveChatCompletionsUrl(baseUrl, endpoint)
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

    private String resolveChatCompletionsUrl(String baseUrl, String endpoint) {
        String base = trimTrailingSlash(baseUrl);
        String path = defaultText(endpoint);
        if (!StringUtils.hasText(base)) {
            return "";
        }
        if (base.toLowerCase(Locale.ROOT).endsWith("/chat/completions")) {
            return base;
        }
        if (!StringUtils.hasText(path)) {
            path = "/chat/completions";
        }
        if (path.toLowerCase(Locale.ROOT).startsWith("http://")
            || path.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private String textValue(Map<String, String> settings, String key, String defaultValue) {
        String value = settings.get(key);
        return StringUtils.hasText(value) ? value.trim() : defaultText(defaultValue);
    }

    private boolean boolValue(Map<String, String> settings, String key, boolean defaultValue) {
        String value = settings.get(key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
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
        String baseUrl,
        String apiKey,
        String model,
        String endpoint,
        String chatCompletionsUrl
    ) {

        public boolean ready() {
            return enabled && StringUtils.hasText(chatCompletionsUrl) && StringUtils.hasText(model);
        }
    }
}
