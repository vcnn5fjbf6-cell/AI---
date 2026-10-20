package com.company.procurement.modules.procurement.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcurementAiImageStoreService {

    private static final String KEY_PUBLIC_BASE_URL = "procurement_ai_image_public_base_url";
    private static final String KEY_FEISHU_APP_BASE_URL = "feishu_approval_card_app_base_url";
    private static final List<String> BASE_URL_KEYS = List.of(KEY_PUBLIC_BASE_URL, KEY_FEISHU_APP_BASE_URL);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final ProcurementSettingMapper settingMapper;

    @Value("${procurement.ai.image.storage-dir:data/procurement-ai-images}")
    private String storageDir;

    public String store(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String publicBaseUrl = publicBaseUrl();
        if (!StringUtils.hasText(publicBaseUrl)) {
            log.debug("Skip procurement AI public image relay because public base url is not configured");
            return "";
        }
        try {
            Path dir = storagePath();
            Files.createDirectories(dir);
            cleanupExpired(dir);
            String token = UUID.randomUUID().toString().replace("-", "");
            Files.write(dir.resolve(token + ".bin"), bytes);
            return UriComponentsBuilder.fromUriString(trimTrailingSlash(publicBaseUrl))
                .path("/api/procurement/ai/images/")
                .path(token)
                .build()
                .toUriString();
        } catch (IOException ex) {
            throw new BizException("PROCUREMENT_AI_IMAGE_RELAY_FAILED", "飞书图片中转失败，请稍后重试");
        }
    }

    public ImageResource load(String token) {
        if (!StringUtils.hasText(token) || !token.matches("[A-Za-z0-9_-]{16,80}")) {
            return null;
        }
        Path file = storagePath().resolve(token + ".bin").normalize();
        if (!file.startsWith(storagePath()) || !Files.exists(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return new ImageResource(bytes, sniffMediaType(bytes));
        } catch (IOException ex) {
            return null;
        }
    }

    private Path storagePath() {
        return Path.of(storageDir).toAbsolutePath().normalize();
    }

    private void cleanupExpired(Path dir) {
        try (var stream = Files.list(dir)) {
            Instant threshold = Instant.now().minus(RETENTION);
            stream.filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
                    } catch (IOException ex) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        log.warn("Delete expired procurement AI image failed: {}", path, ex);
                    }
                });
        } catch (IOException ex) {
            log.warn("Cleanup procurement AI image directory failed", ex);
        }
    }

    private MediaType sniffMediaType(byte[] bytes) {
        if (bytes.length >= 8
            && (bytes[0] & 0xff) == 0x89
            && bytes[1] == 0x50
            && bytes[2] == 0x4e
            && bytes[3] == 0x47) {
            return MediaType.IMAGE_PNG;
        }
        if (bytes.length >= 3
            && (bytes[0] & 0xff) == 0xff
            && (bytes[1] & 0xff) == 0xd8
            && (bytes[2] & 0xff) == 0xff) {
            return MediaType.IMAGE_JPEG;
        }
        if (bytes.length >= 6
            && bytes[0] == 'G'
            && bytes[1] == 'I'
            && bytes[2] == 'F') {
            return MediaType.IMAGE_GIF;
        }
        if (bytes.length >= 12
            && bytes[0] == 'R'
            && bytes[1] == 'I'
            && bytes[2] == 'F'
            && bytes[3] == 'F'
            && bytes[8] == 'W'
            && bytes[9] == 'E'
            && bytes[10] == 'B'
            && bytes[11] == 'P') {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String publicBaseUrl() {
        Map<String, String> settings = new LinkedHashMap<>();
        List<ProcurementSetting> rows = settingMapper.selectList(new LambdaQueryWrapper<ProcurementSetting>()
            .in(ProcurementSetting::getSettingKey, BASE_URL_KEYS));
        for (ProcurementSetting row : rows) {
            if (StringUtils.hasText(row.getSettingKey())) {
                settings.put(row.getSettingKey().trim(), row.getSettingValue());
            }
        }
        return firstText(
            settings.get(KEY_PUBLIC_BASE_URL),
            System.getenv("PROCUREMENT_AI_IMAGE_PUBLIC_BASE_URL"),
            settings.get(KEY_FEISHU_APP_BASE_URL),
            System.getenv("FEISHU_APPROVAL_CARD_APP_BASE_URL"),
            System.getenv("PROCUREMENT_PUBLIC_BASE_URL")
        );
    }

    private String trimTrailingSlash(String value) {
        String result = firstText(value);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    public record ImageResource(byte[] bytes, MediaType mediaType) {
    }
}
