package com.company.procurement.modules.platform.disaster.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherRecordQueryDTO;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherSettingsDTO;
import com.company.procurement.modules.platform.disaster.dto.DisasterWeatherTriggerDTO;
import com.company.procurement.modules.platform.disaster.entity.DisasterWeatherRecord;
import com.company.procurement.modules.platform.disaster.mapper.DisasterWeatherRecordMapper;
import com.company.procurement.modules.platform.disaster.service.DisasterWeatherService;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherOverviewVO;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherRecordVO;
import com.company.procurement.modules.platform.disaster.vo.DisasterWeatherSettingsVO;
import com.company.procurement.modules.procurement.assistant.config.FeishuBotRuntimeConfig;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisasterWeatherServiceImpl
    extends ServiceImpl<DisasterWeatherRecordMapper, DisasterWeatherRecord>
    implements DisasterWeatherService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_REF = new TypeReference<>() {
    };
    private static final DateTimeFormatter RECORD_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String KEY_ENABLED = "disaster_weather_enabled";
    private static final String KEY_QWEATHER_KEY = "disaster_weather_qweather_key";
    private static final String KEY_QWEATHER_HOST = "disaster_weather_qweather_host";
    private static final String KEY_QWEATHER_WARNING_API_MODE = "disaster_weather_qweather_warning_api_mode";
    private static final String KEY_QWEATHER_WARNING_HOST = "disaster_weather_qweather_warning_host";
    private static final String KEY_QWEATHER_WARNING_TOKEN = "disaster_weather_qweather_warning_token";
    private static final String KEY_CITY_CODE = "disaster_weather_city_code";
    private static final String KEY_LOCATION = "disaster_weather_location";
    private static final String KEY_CENTER_ADDRESS = "disaster_weather_center_address";
    private static final String KEY_PUSH_CITY = "disaster_weather_push_city";
    private static final String KEY_PUSH_GROUP = "disaster_weather_push_group";
    private static final String KEY_DUTY_CHAT_ID = "disaster_weather_duty_chat_id";
    private static final String KEY_TEST_CHAT_ID = "disaster_weather_test_chat_id";
    private static final String KEY_DAILY_PUSH_ENABLED = "disaster_weather_daily_push_enabled";
    private static final String KEY_WEEKLY_PUSH_ENABLED = "disaster_weather_weekly_push_enabled";
    private static final String KEY_WARNING_PUSH_ENABLED = "disaster_weather_warning_push_enabled";
    private static final String KEY_WARNING_RESOLVE_PUSH_ENABLED = "disaster_weather_warning_resolve_push_enabled";
    private static final String KEY_NEW_DELIVERY_PROMOTION_ENABLED = "disaster_weather_new_delivery_promotion_enabled";
    private static final String KEY_RAINY_SEASON_ENABLED = "disaster_weather_rainy_season_enabled";
    private static final String KEY_RAINY_SEASON_MONTHS = "disaster_weather_rainy_season_months";
    private static final String KEY_WARNING_RULES_JSON = "disaster_weather_warning_rules_json";
    private static final String KEY_WET_BULB_RULES_JSON = "disaster_weather_wet_bulb_rules_json";
    private static final String KEY_WET_BULB_NOTE = "disaster_weather_wet_bulb_note";
    private static final String DEFAULT_NMC_WARNING_HOST = "https://www.nmc.cn";

    private static final List<String> CLASS_ONE_EXTREME_WEATHER_TYPES = List.of("暴雨", "暴雪", "台风", "洪水");
    private static final List<String> CLASS_TWO_EXTREME_WEATHER_TYPES = List.of("雷电", "冰雹", "寒潮", "霜冻", "高温");

    private static final String DEFAULT_WARNING_RULES_JSON = "["
        + "{\"type\":\"暴雨\",\"color\":\"红色\",\"guard\":\"一级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"暴雪\",\"color\":\"红色\",\"guard\":\"一级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"台风\",\"color\":\"红色\",\"guard\":\"一级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"洪水\",\"color\":\"红色\",\"guard\":\"一级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"雷电\",\"color\":\"红色\",\"guard\":\"二级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"冰雹\",\"color\":\"红色\",\"guard\":\"二级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"寒潮\",\"color\":\"红色\",\"guard\":\"二级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"霜冻\",\"color\":\"红色\",\"guard\":\"二级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"高温\",\"color\":\"红色\",\"guard\":\"二级戒备\",\"card\":\"red\",\"enabled\":true},"
        + "{\"type\":\"暴雨\",\"color\":\"橙色\",\"guard\":\"二级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"暴雪\",\"color\":\"橙色\",\"guard\":\"二级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"台风\",\"color\":\"橙色\",\"guard\":\"二级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"洪水\",\"color\":\"橙色\",\"guard\":\"二级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"雷电\",\"color\":\"橙色\",\"guard\":\"三级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"冰雹\",\"color\":\"橙色\",\"guard\":\"三级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"寒潮\",\"color\":\"橙色\",\"guard\":\"三级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"霜冻\",\"color\":\"橙色\",\"guard\":\"三级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"高温\",\"color\":\"橙色\",\"guard\":\"三级戒备\",\"card\":\"orange\",\"enabled\":true},"
        + "{\"type\":\"暴雨\",\"color\":\"黄色\",\"guard\":\"三级戒备\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"暴雪\",\"color\":\"黄色\",\"guard\":\"三级戒备\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"台风\",\"color\":\"黄色\",\"guard\":\"三级戒备\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"洪水\",\"color\":\"黄色\",\"guard\":\"三级戒备\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"雷电\",\"color\":\"黄色\",\"guard\":\"无匹配标签\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"冰雹\",\"color\":\"黄色\",\"guard\":\"无匹配标签\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"寒潮\",\"color\":\"黄色\",\"guard\":\"无匹配标签\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"霜冻\",\"color\":\"黄色\",\"guard\":\"无匹配标签\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"高温\",\"color\":\"黄色\",\"guard\":\"无匹配标签\",\"card\":\"yellow\",\"enabled\":true},"
        + "{\"type\":\"暴雨\",\"color\":\"蓝色\",\"guard\":\"三级戒备\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"暴雪\",\"color\":\"蓝色\",\"guard\":\"三级戒备\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"台风\",\"color\":\"蓝色\",\"guard\":\"三级戒备\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"洪水\",\"color\":\"蓝色\",\"guard\":\"三级戒备\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"雷电\",\"color\":\"蓝色\",\"guard\":\"无匹配标签\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"冰雹\",\"color\":\"蓝色\",\"guard\":\"无匹配标签\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"寒潮\",\"color\":\"蓝色\",\"guard\":\"无匹配标签\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"霜冻\",\"color\":\"蓝色\",\"guard\":\"无匹配标签\",\"card\":\"blue\",\"enabled\":true},"
        + "{\"type\":\"高温\",\"color\":\"蓝色\",\"guard\":\"无匹配标签\",\"card\":\"blue\",\"enabled\":true}"
        + "]";

    private static final String DEFAULT_WET_BULB_RULES_JSON = "["
        + "{\"max\":6.5,\"includeMax\":true,\"advice\":\"建议完全自由冷却模式，由板式换热器免费制冷。\",\"enabled\":true},"
        + "{\"min\":6.5,\"max\":12.5,\"includeMin\":false,\"includeMax\":true,\"advice\":\"建议预冷制冷模式，冷水机组+板式换热器换热。\",\"enabled\":true},"
        + "{\"min\":12.5,\"includeMin\":false,\"advice\":\"建议机组制冷模式，由冷水机组制冷。\",\"enabled\":true},"
        + "{\"min\":18,\"max\":24,\"includeMin\":false,\"includeMax\":false,\"advice\":\"建议关注机房湿度情况，尽量加开除湿机组，保持运行。\",\"enabled\":true},"
        + "{\"min\":18,\"max\":24,\"includeMin\":false,\"includeMax\":false,\"advice\":\"建议减少新风系统，或经过除湿后再进入机房。\",\"enabled\":true},"
        + "{\"min\":18,\"max\":24,\"includeMin\":false,\"includeMax\":false,\"advice\":\"建议关注发电机房，避免湿度过高造成设备结露并影响运行。\",\"enabled\":true},"
        + "{\"min\":24,\"includeMin\":false,\"advice\":\"建议关注冷却塔运行情况；冷却侧若按出水温度控制，建议按湿球温度+4℃逼近度设定出水温度，过渡季意义较大。逼近度一般按3-7℃并结合相对湿度估算。\",\"enabled\":true}"
        + "]";

    private static final String DEFAULT_WET_BULB_NOTE =
        "湿度关系：空气湿度越高，蒸发越慢，湿球温度与干球温度差值越小；反之，干燥环境下两者温差越大。";
    private static final String FEISHU_CARD_VERSION = "v20260707.7";
    private static final String GUARD_CHECK_FORM_URL = "https://vnet.feishu.cn/share/base/form/shrcnqvIP6rEnYRUpfb2Rf7RMCc";

    private static final List<DefaultSetting> DEFAULT_SETTINGS = List.of(
        new DefaultSetting(KEY_ENABLED, "true", "WEATHER", "启用灾害预警", "BOOLEAN", 10, "关闭后不再自动拉取并推送当前配置区域天气预警"),
        new DefaultSetting(KEY_QWEATHER_KEY, "", "WEATHER", "和风天气 Key", "PASSWORD", 20, "和风天气 API Key，仅服务端保存和调用"),
        new DefaultSetting(KEY_QWEATHER_HOST, "https://devapi.qweather.com", "WEATHER", "普通天气 API 地址", "TEXT", 30, "普通天气接口域名，用于 /v7/weather/now 和 /v7/weather/7d；不要填写接口路径或参数"),
        new DefaultSetting(KEY_QWEATHER_WARNING_API_MODE, "NMC", "WEATHER", "预警 API 模式", "SELECT", 31, "NMC：中央气象台当前预警列表；AUTO：配置新版 Token 时走 /weatheralert/v1/current，否则兼容旧 /v7/warning/now；WEATHER_ALERT：强制新版；V7_WARNING：强制旧版"),
        new DefaultSetting(KEY_QWEATHER_WARNING_HOST, DEFAULT_NMC_WARNING_HOST, "WEATHER", "预警 API 地址", "TEXT", 32, "NMC 模式填写中央气象台域名；和风模式填写实时天气预警接口域名"),
        new DefaultSetting(KEY_QWEATHER_WARNING_TOKEN, "", "WEATHER", "预警 API Token", "PASSWORD", 33, "新版和风实时预警接口的 Bearer Token；NMC 和旧版 /v7/warning/now 不使用该值"),
        new DefaultSetting(KEY_CITY_CODE, "101010300", "WEATHER", "城市 Code", "TEXT", 40, "和风天气城市编码，可按目标机房所在区域调整"),
        new DefaultSetting(KEY_LOCATION, "116.50,39.98", "WEATHER", "中心经纬度", "TEXT", 50, "监控中心点经纬度，可按目标机房调整"),
        new DefaultSetting(KEY_CENTER_ADDRESS, "北京市朝阳区酒仙桥东路1号M6楼", "WEATHER", "监控中心地址", "TEXT", 60, "灾害预警按该中心地址附近区域使用，可按机房调整"),
        new DefaultSetting(KEY_PUSH_CITY, "北京市朝阳区酒仙桥", "WEATHER", "推送城市", "TEXT", 70, "卡片和记录中展示的区域名称"),
        new DefaultSetting(KEY_PUSH_GROUP, "值班群", "WEATHER", "推送群组名称", "TEXT", 80, "天气预报和灾害预警推送目标群名称"),
        new DefaultSetting(KEY_DUTY_CHAT_ID, "", "WEATHER", "值班群 chat_id", "TEXT", 90, "正式自动预报和自动预警推送目标群"),
        new DefaultSetting(KEY_TEST_CHAT_ID, "", "WEATHER", "测试群 chat_id", "TEXT", 95, "手动任务和测试推送只发送到该群；未配置时不会回退发送到正式值班群"),
        new DefaultSetting(KEY_DAILY_PUSH_ENABLED, "true", "WEATHER", "每日9点预报", "BOOLEAN", 100, "开启后每天 09:00 向值班群推送当天气象预报"),
        new DefaultSetting(KEY_WEEKLY_PUSH_ENABLED, "true", "WEATHER", "周一周报", "BOOLEAN", 110, "开启后每周一 09:05 向值班群推送本周天气预报"),
        new DefaultSetting(KEY_WARNING_PUSH_ENABLED, "true", "WEATHER", "天气预警推送", "BOOLEAN", 120, "开启后每1分钟检查天气预警，仅命中三级及以上重保规则时推送值班群"),
        new DefaultSetting(KEY_WARNING_RESOLVE_PUSH_ENABLED, "true", "WEATHER", "天气预警解除推送", "BOOLEAN", 125, "开启后收到官方解除预警通知时，向值班群推送一次解除通知"),
        new DefaultSetting(KEY_NEW_DELIVERY_PROMOTION_ENABLED, "false", "WEATHER", "新交付楼栋提级", "BOOLEAN", 126, "开启后按重保制度对未真实经历过极端天气的新交付楼栋执行预警提级"),
        new DefaultSetting(KEY_RAINY_SEASON_ENABLED, "true", "WEATHER", "雨季专项提醒", "BOOLEAN", 127, "开启后在配置月份内追加雨季专项防水、排水、应急物资检查提醒"),
        new DefaultSetting(KEY_RAINY_SEASON_MONTHS, "6,7,8,9", "WEATHER", "雨季月份", "TEXT", 128, "逗号分隔月份；各机房应按当地历史情况并经区域负责人确认后维护"),
        new DefaultSetting(KEY_WARNING_RULES_JSON, DEFAULT_WARNING_RULES_JSON, "WEATHER", "重保天气预警规则", "TEXT", 130, "JSON 数组；字段 type/color/guard/card/enabled，只有启用且 guard 为一级、二级或三级戒备的预警会通报"),
        new DefaultSetting(KEY_WET_BULB_RULES_JSON, DEFAULT_WET_BULB_RULES_JSON, "WEATHER", "湿球温度建议规则", "TEXT", 140, "JSON 数组；字段 min/max/includeMin/includeMax/advice/enabled，命中规则后合并为处置建议"),
        new DefaultSetting(KEY_WET_BULB_NOTE, DEFAULT_WET_BULB_NOTE, "WEATHER", "湿球温度说明", "TEXT", 150, "湿球建议后追加展示的说明文字，可按现场运行策略维护")
    );

    private final ProcurementSettingMapper settingMapper;
    private final FeishuBotRuntimeConfig feishuBotRuntimeConfig;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = weatherRestClient();

    private RestClient weatherRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private volatile String cachedTenantAccessToken;
    private volatile String cachedTenantAccessTokenSignature;
    private volatile long cachedTenantAccessTokenExpiresAt;

    @Override
    @Transactional
    public DisasterWeatherOverviewVO overview() {
        DisasterWeatherOverviewVO vo = new DisasterWeatherOverviewVO();
        vo.setSettings(getSettings());
        DisasterWeatherRecord latest = getOne(new LambdaQueryWrapper<DisasterWeatherRecord>()
            .orderByDesc(DisasterWeatherRecord::getCreatedAt)
            .last("LIMIT 1"), false);
        vo.setLatestRecord(toVO(latest));
        return vo;
    }

    @Override
    @Transactional
    public DisasterWeatherSettingsVO getSettings() {
        ensureDefaultSettings();
        Map<String, String> settings = settingsMap();
        DisasterWeatherSettingsVO vo = new DisasterWeatherSettingsVO();
        vo.setEnabled(value(settings, KEY_ENABLED));
        vo.setQweatherKeyMasked(maskSecret(value(settings, KEY_QWEATHER_KEY)));
        vo.setQweatherHost(normalizeQweatherHost(value(settings, KEY_QWEATHER_HOST)));
        vo.setQweatherWarningApiMode(normalizeQweatherWarningApiMode(value(settings, KEY_QWEATHER_WARNING_API_MODE)));
        vo.setQweatherWarningHost(normalizeQweatherHost(value(settings, KEY_QWEATHER_WARNING_HOST)));
        vo.setQweatherWarningTokenMasked(maskSecret(value(settings, KEY_QWEATHER_WARNING_TOKEN)));
        vo.setCityCode(value(settings, KEY_CITY_CODE));
        vo.setLocation(value(settings, KEY_LOCATION));
        vo.setCenterAddress(value(settings, KEY_CENTER_ADDRESS));
        vo.setPushCity(value(settings, KEY_PUSH_CITY));
        vo.setPushGroup(value(settings, KEY_PUSH_GROUP));
        vo.setDutyChatId(value(settings, KEY_DUTY_CHAT_ID));
        vo.setTestChatId(value(settings, KEY_TEST_CHAT_ID));
        vo.setDailyPushEnabled(value(settings, KEY_DAILY_PUSH_ENABLED));
        vo.setWeeklyPushEnabled(value(settings, KEY_WEEKLY_PUSH_ENABLED));
        vo.setWarningPushEnabled(value(settings, KEY_WARNING_PUSH_ENABLED));
        vo.setWarningResolvePushEnabled(value(settings, KEY_WARNING_RESOLVE_PUSH_ENABLED));
        vo.setNewDeliveryPromotionEnabled(value(settings, KEY_NEW_DELIVERY_PROMOTION_ENABLED));
        vo.setRainySeasonEnabled(value(settings, KEY_RAINY_SEASON_ENABLED));
        vo.setRainySeasonMonths(value(settings, KEY_RAINY_SEASON_MONTHS));
        vo.setWarningRulesJson(normalizeWarningRulesJson(value(settings, KEY_WARNING_RULES_JSON)));
        vo.setWetBulbRulesJson(normalizeWetBulbRulesJson(value(settings, KEY_WET_BULB_RULES_JSON)));
        vo.setWetBulbNote(defaultText(value(settings, KEY_WET_BULB_NOTE), DEFAULT_WET_BULB_NOTE));
        return vo;
    }

    @Override
    @Transactional
    public void updateSettings(DisasterWeatherSettingsDTO dto) {
        ensureDefaultSettings();
        if (dto == null) {
            return;
        }
        upsert(KEY_ENABLED, dto.getEnabled());
        if (dto.getQweatherHost() != null) {
            upsert(KEY_QWEATHER_HOST, normalizeQweatherHost(dto.getQweatherHost()));
        }
        if (dto.getQweatherWarningApiMode() != null) {
            upsert(KEY_QWEATHER_WARNING_API_MODE, normalizeQweatherWarningApiMode(dto.getQweatherWarningApiMode()));
        }
        if (dto.getQweatherWarningHost() != null) {
            upsert(KEY_QWEATHER_WARNING_HOST, normalizeQweatherHost(dto.getQweatherWarningHost()));
        }
        upsert(KEY_CITY_CODE, dto.getCityCode());
        upsert(KEY_LOCATION, dto.getLocation());
        upsert(KEY_CENTER_ADDRESS, dto.getCenterAddress());
        upsert(KEY_PUSH_CITY, dto.getPushCity());
        upsert(KEY_PUSH_GROUP, dto.getPushGroup());
        upsert(KEY_DUTY_CHAT_ID, dto.getDutyChatId());
        upsert(KEY_TEST_CHAT_ID, dto.getTestChatId());
        upsert(KEY_DAILY_PUSH_ENABLED, dto.getDailyPushEnabled());
        upsert(KEY_WEEKLY_PUSH_ENABLED, dto.getWeeklyPushEnabled());
        upsert(KEY_WARNING_PUSH_ENABLED, dto.getWarningPushEnabled());
        upsert(KEY_WARNING_RESOLVE_PUSH_ENABLED, dto.getWarningResolvePushEnabled());
        upsert(KEY_NEW_DELIVERY_PROMOTION_ENABLED, dto.getNewDeliveryPromotionEnabled());
        upsert(KEY_RAINY_SEASON_ENABLED, dto.getRainySeasonEnabled());
        upsert(KEY_RAINY_SEASON_MONTHS, normalizeRainySeasonMonths(dto.getRainySeasonMonths()));
        if (dto.getWarningRulesJson() != null) {
            upsert(KEY_WARNING_RULES_JSON, normalizeWarningRulesJson(dto.getWarningRulesJson()));
        }
        if (dto.getWetBulbRulesJson() != null) {
            upsert(KEY_WET_BULB_RULES_JSON, normalizeWetBulbRulesJson(dto.getWetBulbRulesJson()));
        }
        if (dto.getWetBulbNote() != null) {
            upsert(KEY_WET_BULB_NOTE, dto.getWetBulbNote());
        }
        if (dto.getQweatherKey() != null && !dto.getQweatherKey().contains("*")) {
            upsert(KEY_QWEATHER_KEY, dto.getQweatherKey());
        }
        if (dto.getQweatherWarningToken() != null && !dto.getQweatherWarningToken().contains("*")) {
            upsert(KEY_QWEATHER_WARNING_TOKEN, dto.getQweatherWarningToken());
        }
    }

    @Override
    public PageResult<DisasterWeatherRecordVO> pageRecords(DisasterWeatherRecordQueryDTO queryDTO) {
        LambdaQueryWrapper<DisasterWeatherRecord> wrapper = new LambdaQueryWrapper<DisasterWeatherRecord>()
            .orderByDesc(DisasterWeatherRecord::getCreatedAt)
            .orderByDesc(DisasterWeatherRecord::getId);
        if (StringUtils.hasText(queryDTO.getRecordType())) {
            wrapper.eq(DisasterWeatherRecord::getRecordType, normalizeCode(queryDTO.getRecordType()));
        }
        if (StringUtils.hasText(queryDTO.getSendStatus())) {
            wrapper.eq(DisasterWeatherRecord::getSendStatus, normalizeCode(queryDTO.getSendStatus()));
        }
        if (StringUtils.hasText(queryDTO.getGuardLevel())) {
            wrapper.like(DisasterWeatherRecord::getGuardTag, queryDTO.getGuardLevel().trim());
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            wrapper.and(scope -> scope
                .like(DisasterWeatherRecord::getQueryRecordId, keyword)
                .or()
                .like(DisasterWeatherRecord::getPushCity, keyword)
                .or()
                .like(DisasterWeatherRecord::getTodayWeatherInfo, keyword)
                .or()
                .like(DisasterWeatherRecord::getWarningInfo, keyword)
                .or()
                .like(DisasterWeatherRecord::getWetBulbAdvice, keyword)
                .or()
                .like(DisasterWeatherRecord::getLastError, keyword));
        }
        Page<DisasterWeatherRecord> page = page(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    @Transactional
    public DisasterWeatherRecordVO trigger(DisasterWeatherTriggerDTO dto) {
        String recordType = normalizeRecordType(dto == null ? null : dto.getRecordType());
        boolean sendToFeishu = dto != null && Boolean.TRUE.equals(dto.getSendToFeishu());
        DisasterWeatherRecord record = dto != null && StringUtils.hasText(dto.getTestCardColor())
            ? buildAndSaveTestCardRecord(recordType, dto.getTestCardColor())
            : fetchAndSave(recordType, true);
        if (sendToFeishu) {
            sendRecord(record);
        }
        return toVO(record);
    }

    @Override
    public void sendDailyForecastIfEnabled() {
        try {
            if (!enabled() || !boolSetting(KEY_DAILY_PUSH_ENABLED, true)) {
                return;
            }
            DisasterWeatherRecord record = fetchAndSave("DAILY", false);
            sendRecord(record);
        } catch (RuntimeException ex) {
            log.warn("Daily disaster weather forecast push failed", ex);
        }
    }

    @Override
    public void sendWeeklyForecastIfEnabled() {
        try {
            if (!enabled() || !boolSetting(KEY_WEEKLY_PUSH_ENABLED, true)) {
                return;
            }
            DisasterWeatherRecord record = fetchAndSave("WEEKLY", false);
            sendRecord(record);
        } catch (RuntimeException ex) {
            log.warn("Weekly disaster weather forecast push failed", ex);
        }
    }

    @Override
    public void pollWarningsIfEnabled() {
        try {
            if (!enabled() || !boolSetting(KEY_WARNING_PUSH_ENABLED, true)) {
                return;
            }
            DisasterWeatherRecord record = fetchAndSave("WARNING", false);
            if (isWarningResolveNoticeRecord(record)) {
                if (boolSetting(KEY_WARNING_RESOLVE_PUSH_ENABLED, true) && applyResolveNoticeIfNeeded(record)) {
                    sendRecord(record);
                    return;
                }
                markSkipped(record, "未找到可解除的前序三级及以上重保天气预警");
                return;
            }
            if (!StringUtils.hasText(record.getWarningInfo()) || !isReportableGuard(record.getGuardTag())) {
                markSkipped(record, "当前无三级及以上重保天气预警");
                return;
            }
            if (isDuplicateWarning(record)) {
                markSkipped(record, "同一连续预警已推送");
                return;
            }
            sendRecord(record);
        } catch (RuntimeException ex) {
            log.warn("Disaster weather warning poll failed", ex);
        }
    }

    @Override
    @Transactional
    public void cleanupWarningRecords() {
        int noWarningRemoved = cleanupExpiredNoWarningRecords();
        int compactedRemoved = compactContinuousWarningRecords();
        if (noWarningRemoved > 0 || compactedRemoved > 0) {
            log.info("Disaster warning records cleanup finished, noWarningRemoved={}, compactedRemoved={}",
                noWarningRemoved, compactedRemoved);
        }
    }

    @Transactional
    protected DisasterWeatherRecord fetchAndSave(String recordType, boolean manualTrigger) {
        ensureDefaultSettings();
        Map<String, String> settings = settingsMap();
        String key = value(settings, KEY_QWEATHER_KEY);
        if (!StringUtils.hasText(key)) {
            throw new BizException("DISASTER_QWEATHER_KEY_MISSING", "请先配置和风天气 Key");
        }
        String host = normalizeQweatherHost(value(settings, KEY_QWEATHER_HOST));
        String warningApiMode = normalizeQweatherWarningApiMode(value(settings, KEY_QWEATHER_WARNING_API_MODE));
        String warningHost = firstText(value(settings, KEY_QWEATHER_WARNING_HOST), host);
        String warningToken = value(settings, KEY_QWEATHER_WARNING_TOKEN);
        String cityCode = defaultText(value(settings, KEY_CITY_CODE), "101010300");
        String location = defaultText(value(settings, KEY_LOCATION), cityCode);
        WeatherPayload payload = fetchWeather(host, key, location, cityCode, warningApiMode, warningHost, warningToken, settings);
        DisasterWeatherRecord record = buildRecord(recordType, manualTrigger, settings, payload);
        save(record);
        return record;
    }

    @Transactional
    protected void sendRecord(DisasterWeatherRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        Map<String, String> settings = settingsMap();
        FeishuSendTarget target = resolveSendTarget(record, settings);
        if (!StringUtils.hasText(target.chatId())) {
            markFailed(record, target.test()
                ? "请先配置测试群 chat_id，手动任务和测试推送不会发送到正式值班群"
                : "请先配置值班群 chat_id");
            return;
        }
        try {
            FeishuBotRuntimeConfig.Snapshot config = feishuBotRuntimeConfig.current();
            String previewImageKey = buildAndUploadPreviewImageKey(config, record, settings, target.test());
            String messageId;
            try {
                messageId = sendFeishuCard(config, "chat_id", target.chatId(), buildFeishuCard(record, settings, target.test(), previewImageKey));
            } catch (RuntimeException ex) {
                if (!StringUtils.hasText(previewImageKey)) {
                    throw ex;
                }
                log.warn("Send disaster weather Feishu card with preview image failed, retry without image. recordId={}",
                    record.getId(), ex);
                messageId = sendFeishuCard(config, "chat_id", target.chatId(), buildFeishuCard(record, settings, target.test(), ""));
            }
            record.setMessageId(messageId);
            record.setSendStatus("SENT");
            record.setPushed(1);
            record.setPushGroup(target.groupName());
            record.setPushDescription("已发送至" + target.groupName() + (target.test() ? "（测试）" : ""));
            record.setLastPushTime(LocalDateTime.now());
            record.setLastError("");
            updateById(record);
        } catch (RuntimeException ex) {
            markFailed(record, firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    private FeishuSendTarget resolveSendTarget(DisasterWeatherRecord record, Map<String, String> settings) {
        boolean manual = record != null && Objects.equals(record.getManualTrigger(), 1);
        if (manual) {
            return new FeishuSendTarget(value(settings, KEY_TEST_CHAT_ID), "测试群", true);
        }
        return new FeishuSendTarget(
            value(settings, KEY_DUTY_CHAT_ID),
            defaultText(value(settings, KEY_PUSH_GROUP), "值班群"),
            false
        );
    }

    private WeatherPayload fetchWeather(String host,
                                        String key,
                                        String location,
                                        String cityCode,
                                        String warningApiMode,
                                        String warningHost,
                                        String warningToken,
                                        Map<String, String> settings) {
        String nowRaw = getQweather(host + "/v7/weather/now", key, location);
        String dailyRaw = getQweather(host + "/v7/weather/7d", key, location);
        String warningRaw;
        String warningError = "";
        try {
            warningRaw = fetchWarningRaw(host, key, location, cityCode, warningApiMode, warningHost, warningToken, settings);
        } catch (BizException ex) {
            warningError = ex.getMessage();
            warningRaw = "{\"warning\":[],\"alerts\":[]}";
        }
        return new WeatherPayload(readJson(nowRaw), nowRaw, readJson(dailyRaw), dailyRaw, readJson(warningRaw), warningRaw, warningError);
    }

    private String fetchWarningRaw(String weatherHost,
                                   String key,
                                   String location,
                                   String cityCode,
                                   String warningApiMode,
                                   String warningHost,
                                   String warningToken,
                                   Map<String, String> settings) {
        if (useNmcWarningApi(warningApiMode)) {
            return getNmcWarnings(normalizeNmcWarningHost(warningHost), settings);
        }
        if (useWeatherAlertApi(warningApiMode, warningToken)) {
            if (!StringUtils.hasText(warningToken)) {
                throw new BizException("DISASTER_QWEATHER_WARNING_TOKEN_MISSING", "请先配置和风天气预警 API Token");
            }
            return getQweatherWeatherAlert(normalizeQweatherHost(warningHost), warningToken, location);
        }
        return getQweather(weatherHost + "/v7/warning/now", key, defaultText(cityCode, location));
    }

    private boolean useNmcWarningApi(String warningApiMode) {
        return "NMC".equals(normalizeQweatherWarningApiMode(warningApiMode));
    }

    private boolean useWeatherAlertApi(String warningApiMode, String warningToken) {
        String mode = normalizeQweatherWarningApiMode(warningApiMode);
        return "WEATHER_ALERT".equals(mode) || ("AUTO".equals(mode) && StringUtils.hasText(warningToken));
    }

    private String getQweather(String api, String key, String location) {
        String url = UriComponentsBuilder.fromUriString(api)
            .queryParam("location", location)
            .queryParam("key", key)
            .build()
            .toUriString();
        try {
            byte[] responseBody = restClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .body(byte[].class);
            String body = decodeHttpBody(responseBody);
            validateQweather(body);
            return StringUtils.hasText(body) ? body : "{}";
        } catch (RestClientResponseException ex) {
            throw new BizException("DISASTER_QWEATHER_REQUEST_FAILED", "和风天气接口请求失败：" + limit(ex.getResponseBodyAsString(), 300));
        } catch (RestClientException ex) {
            throw new BizException("DISASTER_QWEATHER_REQUEST_FAILED", "和风天气接口请求失败：" + firstText(ex.getMessage(), "网络异常"));
        }
    }

    private String getQweatherWeatherAlert(String host, String token, String location) {
        WeatherAlertCoordinates coordinates = weatherAlertCoordinates(location);
        String url = UriComponentsBuilder.fromUriString(host)
            .pathSegment("weatheralert", "v1", "current", coordinates.latitude(), coordinates.longitude())
            .queryParam("lang", "zh")
            .build()
            .toUriString();
        try {
            byte[] responseBody = restClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(byte[].class);
            String body = decodeHttpBody(responseBody);
            validateQweather(body);
            return StringUtils.hasText(body) ? body : "{}";
        } catch (RestClientResponseException ex) {
            throw new BizException("DISASTER_QWEATHER_WARNING_REQUEST_FAILED", "和风天气预警接口请求失败：" + limit(ex.getResponseBodyAsString(), 300));
        } catch (RestClientException ex) {
            throw new BizException("DISASTER_QWEATHER_WARNING_REQUEST_FAILED", "和风天气预警接口请求失败：" + firstText(ex.getMessage(), "网络异常"));
        }
    }

    private String getNmcWarnings(String host, Map<String, String> settings) {
        String url = UriComponentsBuilder.fromUriString(host)
            .pathSegment("rest", "findAlarm")
            .queryParam("pageNo", 1)
            .queryParam("pageSize", 2000)
            .build()
            .toUriString();
        try {
            byte[] responseBody = restClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .header(HttpHeaders.REFERER, host + "/publish/alarm.html")
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .header("X-Requested-With", "XMLHttpRequest")
                .retrieve()
                .body(byte[].class);
            String body = decodeHttpBody(responseBody);
            return toNmcWarningJson(body, settings);
        } catch (RestClientResponseException ex) {
            throw new BizException("DISASTER_NMC_WARNING_REQUEST_FAILED", "中央气象台预警接口请求失败：" + limit(ex.getResponseBodyAsString(), 300));
        } catch (RestClientException ex) {
            throw new BizException("DISASTER_NMC_WARNING_REQUEST_FAILED", "中央气象台预警接口请求失败：" + firstText(ex.getMessage(), "网络异常"));
        }
    }

    private String toNmcWarningJson(String body, Map<String, String> settings) {
        JsonNode root = readJson(body);
        String code = text(root.path("code"));
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BizException("DISASTER_NMC_WARNING_RESPONSE_FAILED",
                "中央气象台预警接口返回异常：" + firstText(text(root.path("msg")), code));
        }
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode warnings = result.putArray("warning");
        JsonNode list = root.path("data").path("page").path("list");
        if (list.isArray()) {
            for (JsonNode item : list) {
                String title = text(item.path("title"));
                String alertId = text(item.path("alertid"));
                if (!matchesNmcTarget(title, alertId, settings)) {
                    continue;
                }
                ObjectNode warning = warnings.addObject();
                warning.put("title", title);
                warning.put("typeName", nmcWarningType(title));
                warning.put("severityColor", nmcWarningColor(title));
                warning.put("text", nmcWarningDetail(item));
                warning.put("status", "active");
                warning.put("alertId", alertId);
                warning.put("url", nmcWarningUrl(text(item.path("url"))));
            }
        }
        return result.toString();
    }

    private boolean matchesNmcTarget(String title, String alertId, Map<String, String> settings) {
        String normalizedTitle = defaultText(title, "");
        String normalizedAlertId = defaultText(alertId, "");
        String target = String.join(" ",
            defaultText(value(settings, KEY_PUSH_CITY), ""),
            defaultText(value(settings, KEY_CENTER_ADDRESS), ""),
            defaultText(value(settings, KEY_CITY_CODE), "")
        );
        if (target.contains("北京") && target.contains("朝阳")) {
            return normalizedAlertId.startsWith("110105")
                || normalizedAlertId.startsWith("110000")
                || normalizedTitle.contains("北京市北京市朝阳区")
                || normalizedTitle.contains("北京市朝阳区");
        }
        if (StringUtils.hasText(normalizedAlertId) && StringUtils.hasText(value(settings, KEY_CITY_CODE))
            && normalizedAlertId.startsWith(value(settings, KEY_CITY_CODE))) {
            return true;
        }
        List<String> tokens = nmcTargetTokens(target);
        return !tokens.isEmpty() && tokens.stream().allMatch(normalizedTitle::contains);
    }

    private List<String> nmcTargetTokens(String target) {
        String text = defaultText(target, "");
        List<String> tokens = new ArrayList<>();
        if (text.contains("北京市")) {
            tokens.add("北京市");
        }
        if (text.contains("朝阳区")) {
            tokens.add("朝阳区");
        }
        if (tokens.isEmpty() && StringUtils.hasText(text)) {
            String compact = text
                .replace("中国", "")
                .replace("省", " ")
                .replace("市", " ")
                .replace("区", " ")
                .replace("县", " ")
                .replace("自治州", " ")
                .replace("自治县", " ");
            for (String part : compact.split("\\s+")) {
                String token = part.trim();
                if (token.length() >= 2 && !List.of("天气", "预警", "机房", "中心").contains(token)) {
                    tokens.add(token);
                }
            }
        }
        return tokens.stream().distinct().limit(2).toList();
    }

    private String nmcWarningType(String title) {
        String text = defaultText(title, "");
        if (containsAny(text, "雷雨大风", "雷暴大风")) {
            return "雷电";
        }
        String type = matchedExtremeWeatherType(text);
        if (StringUtils.hasText(type)) {
            return type;
        }
        for (String candidate : List.of("强对流", "大风", "沙尘暴", "大雾", "道路结冰", "山洪灾害", "地质灾害")) {
            if (text.contains(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private String nmcWarningColor(String title) {
        String text = defaultText(title, "");
        for (String color : List.of("红色", "橙色", "黄色", "蓝色")) {
            if (text.contains(color)) {
                return color;
            }
        }
        return "";
    }

    private String nmcWarningDetail(JsonNode item) {
        String issueTime = text(item.path("issuetime"));
        String url = nmcWarningUrl(text(item.path("url")));
        return "来源：中央气象台当前预警列表"
            + (StringUtils.hasText(issueTime) ? "；发布时间：" + issueTime : "")
            + (StringUtils.hasText(url) ? "；详情：" + url : "");
    }

    private String nmcWarningUrl(String value) {
        String url = defaultText(value, "");
        if (!StringUtils.hasText(url)) {
            return "";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return DEFAULT_NMC_WARNING_HOST + (url.startsWith("/") ? url : "/" + url);
    }

    private void validateQweather(String body) {
        JsonNode node = readJson(body);
        String code = text(node.path("code"));
        if (StringUtils.hasText(code) && !"200".equals(code)) {
            throw new BizException("DISASTER_QWEATHER_RESPONSE_FAILED", "和风天气接口返回异常：" + code);
        }
    }

    private DisasterWeatherRecord buildRecord(String recordType,
                                              boolean manualTrigger,
                                              Map<String, String> settings,
                                              WeatherPayload payload) {
        JsonNode now = payload.now().path("now");
        List<String> forecastDays = dailyForecast(payload.daily());
        List<WarningItem> warnings = warningItems(payload.warning());
        List<WarningRule> warningRules = warningRules(settings);
        boolean newDeliveryPromotion = boolSetting(settings, KEY_NEW_DELIVERY_PROMOTION_ENABLED, false);
        WarningEvaluation warningEvaluation = evaluateWarnings(warnings, warningRules, newDeliveryPromotion);
        WarningResolveNotice warningResolveNotice = evaluateWarningResolveNotice(warnings, warningRules);
        boolean warningApiFailed = StringUtils.hasText(payload.warningError());
        if ("WARNING".equals(recordType)
            && !warningApiFailed
            && !StringUtils.hasText(warningEvaluation.summary())
            && !warningResolveNotice.present()) {
            WarningEvaluation forecastPromotion = evaluateNewDeliveryForecastPromotion(forecastDays, newDeliveryPromotion);
            if (forecastPromotion.present()) {
                warningEvaluation = forecastPromotion;
            }
        }
        double temp = doubleValue(now.path("temp"));
        double humidity = doubleValue(now.path("humidity"));
        double wetBulb = wetBulbCelsius(temp, humidity, 1013.25D);
        double dewPoint = dewPointCelsius(temp, humidity);
        String guardTag = guardTag(warningEvaluation.guardLevel(), wetBulb);
        boolean resolveNoticeOnly = !StringUtils.hasText(warningEvaluation.summary()) && warningResolveNotice.present();

        DisasterWeatherRecord record = new DisasterWeatherRecord();
        record.setQueryRecordId("DW" + LocalDateTime.now().format(RECORD_NO_FORMAT));
        record.setRecordType(recordType);
        record.setPushCity(defaultText(value(settings, KEY_PUSH_CITY), "北京市朝阳区酒仙桥"));
        record.setPushGroup(defaultText(value(settings, KEY_PUSH_GROUP), "值班群"));
        record.setCityCode(defaultText(value(settings, KEY_CITY_CODE), "101010300"));
        record.setCreatedPerson("SYSTEM");
        record.setWeatherRawJson(payload.dailyRaw());
        record.setTodayWeatherJson(payload.nowRaw());
        record.setTodayWeatherInfo(todayWeatherInfo(now));
        record.setWarningRawJson(payload.warningRaw());
        record.setWarningInfo(warningApiFailed && "WARNING".equals(recordType)
            ? "预警接口异常：" + limit(payload.warningError(), 300)
            : (resolveNoticeOnly ? warningResolveNotice.summary() : warningEvaluation.summary()));
        record.setWarningTypeExtract(warningApiFailed
            ? "预警接口异常"
            : (resolveNoticeOnly ? warningResolveNotice.typeAndLevel() : warningEvaluation.typeAndLevel()));
        record.setGuardTag(resolveNoticeOnly ? "预警解除" : guardTag);
        record.setGuardAlertTag(resolveNoticeOnly ? "green" : warningEvaluation.cardColor());
        record.setWetBulbAdvice(wetBulbAdvice(wetBulb, settings));
        String baseGuardSteps = resolveNoticeOnly ? resolveGuardSteps(warningResolveNotice) : guardSteps(guardTag);
        record.setGuardSteps(appendRainySeasonAdvice(baseGuardSteps, settings));
        record.setRelativeHumidity(scale(humidity));
        record.setSaturationTemp(scale(dewPoint));
        record.setDryBulbTemp(scale(temp));
        record.setWetBulbTemp(scale(wetBulb));
        record.setWetBulbInfo("干球温度 " + formatNumber(temp) + "℃，相对湿度 " + formatNumber(humidity)
            + "%，湿球温度 " + formatNumber(wetBulb) + "℃");
        record.setRealTimePrecipitation(scale(doubleValue(now.path("precip"))));
        record.setWarningFetchTime(LocalDateTime.now());
        record.setManualTrigger(manualTrigger ? 1 : 0);
        record.setSendStatus("NOT_SENT");
        fillForecastFields(record, forecastDays);
        return record;
    }

    @Transactional
    protected DisasterWeatherRecord buildAndSaveTestCardRecord(String recordType, String cardColor) {
        ensureDefaultSettings();
        Map<String, String> settings = settingsMap();
        String color = normalizeCardColor(cardColor);
        DisasterWeatherRecord record = new DisasterWeatherRecord();
        record.setQueryRecordId("DWT" + LocalDateTime.now().format(RECORD_NO_FORMAT));
        record.setRecordType(recordType);
        record.setPushCity(defaultText(value(settings, KEY_PUSH_CITY), "北京市朝阳区酒仙桥"));
        record.setPushGroup("测试群");
        record.setCityCode(defaultText(value(settings, KEY_CITY_CODE), "101010300"));
        record.setCreatedPerson("SYSTEM");
        record.setWeatherRawJson("{\"test\":true}");
        record.setTodayWeatherJson("{\"test\":true}");
        record.setTodayWeatherInfo("模拟天气：晴 / 温度26℃ / 体感26℃ / 湿度49% / 风向西南风 / 风力2级 / 降水0.0mm");
        record.setWarningRawJson("{\"test\":true,\"cardColor\":\"" + color + "\"}");
        applyTestWarningColor(record, color);
        record.setWetBulbAdvice("测试卡片：仅用于验证飞书卡片颜色和样式，不作为正式预警依据。");
        record.setRelativeHumidity(scale(49D));
        record.setSaturationTemp(scale(18.7D));
        record.setDryBulbTemp(scale(26D));
        record.setWetBulbTemp(scale(18.7D));
        record.setWetBulbInfo("干球温度 26℃，相对湿度 49%，湿球温度 18.7℃");
        record.setRealTimePrecipitation(scale(0D));
        record.setWarningFetchTime(LocalDateTime.now());
        record.setManualTrigger(1);
        record.setSendStatus("NOT_SENT");
        record.setTomorrowWeather("模拟未来天气 / 23~34℃ / 湿度61% / 降水0.0mm");
        record.setDay2Weather("模拟未来天气 / 22~30℃ / 湿度90% / 降水1.4mm");
        record.setDay3Weather("模拟未来天气 / 20~28℃ / 湿度82% / 降水5.0mm");
        save(record);
        return record;
    }

    private void applyTestWarningColor(DisasterWeatherRecord record, String color) {
        switch (color) {
            case "red" -> {
                record.setWarningInfo("模拟暴雨红色预警：一级戒备测试");
                record.setWarningTypeExtract("暴雨红色");
                record.setGuardTag("一级戒备");
                record.setGuardSteps(guardSteps("一级戒备"));
            }
            case "orange" -> {
                record.setWarningInfo("模拟暴雨橙色预警：二级戒备测试");
                record.setWarningTypeExtract("暴雨橙色");
                record.setGuardTag("二级戒备");
                record.setGuardSteps(guardSteps("二级戒备"));
            }
            case "yellow" -> {
                record.setWarningInfo("模拟暴雨黄色预警：三级戒备测试");
                record.setWarningTypeExtract("暴雨黄色");
                record.setGuardTag("三级戒备");
                record.setGuardSteps(guardSteps("三级戒备"));
            }
            case "blue" -> {
                record.setWarningInfo("模拟暴雨蓝色预警：蓝色卡片测试");
                record.setWarningTypeExtract("暴雨蓝色");
                record.setGuardTag("蓝色预警");
                record.setGuardSteps("蓝色预警：仅用于卡片颜色测试，正式自动预警仍按后台规则判断是否推送。");
            }
            default -> {
                record.setWarningInfo("");
                record.setWarningTypeExtract("");
                record.setGuardTag("常规观察");
                record.setGuardSteps(guardSteps("常规观察"));
            }
        }
        record.setGuardAlertTag(color);
    }

    private List<String> dailyForecast(JsonNode root) {
        List<String> result = new ArrayList<>();
        JsonNode daily = root.path("daily");
        if (!daily.isArray()) {
            return result;
        }
        for (JsonNode day : daily) {
            String text = firstText(text(day.path("textDay")), text(day.path("textNight")), "天气");
            String date = text(day.path("fxDate"));
            String tempMin = text(day.path("tempMin"));
            String tempMax = text(day.path("tempMax"));
            String humidity = text(day.path("humidity"));
            String precip = firstText(text(day.path("precip")), text(day.path("precipSum")));
            List<String> parts = new ArrayList<>();
            if (StringUtils.hasText(date)) {
                parts.add(date);
            }
            parts.add(text);
            if (StringUtils.hasText(tempMin) || StringUtils.hasText(tempMax)) {
                parts.add(tempMin + "~" + tempMax + "℃");
            }
            if (StringUtils.hasText(humidity)) {
                parts.add("湿度" + humidity + "%");
            }
            if (StringUtils.hasText(precip)) {
                parts.add("降水" + precip + "mm");
            }
            result.add(String.join(" / ", parts));
        }
        return result;
    }

    private void fillForecastFields(DisasterWeatherRecord record, List<String> days) {
        record.setTomorrowWeather(day(days, 1));
        record.setDay2Weather(day(days, 2));
        record.setDay3Weather(day(days, 3));
        record.setDay4Weather(day(days, 4));
        record.setDay5Weather(day(days, 5));
        record.setDay6Weather(day(days, 6));
        record.setDay7Weather(day(days, 7));
        record.setDay8Weather(day(days, 8));
        record.setDay9Weather(day(days, 9));
        record.setDay10Weather(day(days, 10));
        record.setDay11Weather(day(days, 11));
        record.setDay12Weather(day(days, 12));
        record.setDay13Weather(day(days, 13));
        record.setDay14Weather(day(days, 14));
    }

    private String day(List<String> days, int offsetFromToday) {
        int index = offsetFromToday;
        return index >= 0 && index < days.size() ? days.get(index) : "";
    }

    private List<WarningItem> warningItems(JsonNode root) {
        List<WarningItem> result = new ArrayList<>();
        JsonNode warnings = root.path("warning");
        if (warnings.isArray()) {
            for (JsonNode warning : warnings) {
                String title = text(warning.path("title"));
                String typeName = firstText(text(warning.path("typeName")), text(warning.path("type")));
                String severity = firstText(text(warning.path("severityColor")), text(warning.path("level")));
                String detail = firstText(text(warning.path("text")), text(warning.path("status")));
                result.add(new WarningItem(title, typeName, severity, detail, warningMatchText(title, typeName, severity, detail)));
            }
        }
        JsonNode alerts = root.path("alerts");
        if (alerts.isArray()) {
            for (JsonNode alert : alerts) {
                String title = firstText(text(alert.path("headline")), text(alert.path("senderName")));
                String typeName = firstText(
                    text(alert.path("eventType").path("name")),
                    text(alert.path("eventType").path("code")),
                    text(alert.path("icon"))
                );
                String severity = firstText(
                    text(alert.path("color").path("code")),
                    text(alert.path("severity")),
                    text(alert.path("level"))
                );
                String detail = firstText(
                    text(alert.path("description")),
                    text(alert.path("instruction")),
                    text(alert.path("criteria"))
                );
                result.add(new WarningItem(title, typeName, severity, detail, warningMatchText(title, typeName, severity, detail)));
            }
        }
        return result;
    }

    private String warningMatchText(String title, String typeName, String severity, String detail) {
        return List.of(title, typeName, severity, detail).stream()
            .filter(StringUtils::hasText)
            .collect(Collectors.joining(" "));
    }

    private String todayWeatherInfo(JsonNode now) {
        List<String> parts = new ArrayList<>();
        parts.add(firstText(text(now.path("text")), "天气"));
        appendIfText(parts, text(now.path("temp")), "温度", "℃");
        appendIfText(parts, text(now.path("feelsLike")), "体感", "℃");
        appendIfText(parts, text(now.path("humidity")), "湿度", "%");
        appendIfText(parts, text(now.path("windDir")), "风向", "");
        appendIfText(parts, text(now.path("windScale")), "风力", "级");
        appendIfText(parts, text(now.path("precip")), "降水", "mm");
        return String.join(" / ", parts);
    }

    private void appendIfText(List<String> parts, String value, String label, String suffix) {
        if (StringUtils.hasText(value)) {
            parts.add(label + value + suffix);
        }
    }

    private WarningEvaluation evaluateWarnings(List<WarningItem> warnings, List<WarningRule> rules, boolean newDeliveryPromotion) {
        List<MatchedWarning> matches = new ArrayList<>();
        for (WarningItem warning : warnings) {
            if (isWarningResolveNotice(warning)) {
                continue;
            }
            String warningColor = warningItemColor(warning);
            boolean matchedRule = false;
            for (WarningRule rule : rules) {
                if (rule.enabled()
                    && sameWarningColor(rule.warningColor(), warningColor)
                    && sameWarningType(rule.warningType(), warning.matchText())) {
                    matchedRule = true;
                    WarningRule effectiveRule = newDeliveryPromotion
                        ? promoteRuleForNewDelivery(warning, rule)
                        : rule;
                    if (isReportableGuard(effectiveRule.guardLevel())) {
                        matches.add(new MatchedWarning(warning, effectiveRule));
                    }
                    break;
                }
            }
            if (!matchedRule && newDeliveryPromotion) {
                WarningRule promotedRule = newDeliveryPromotionRule(warning);
                if (promotedRule != null) {
                    matches.add(new MatchedWarning(warning, promotedRule));
                }
            }
        }
        if (matches.isEmpty()) {
            return WarningEvaluation.empty();
        }
        matches.sort((a, b) -> {
            int guardCompare = compareGuardLevel(a.rule().guardLevel(), b.rule().guardLevel());
            return guardCompare != 0 ? guardCompare : warningColorOrder(a.warningColor()) - warningColorOrder(b.warningColor());
        });
        MatchedWarning top = matches.get(0);
        String summary = matches.stream()
            .map(MatchedWarning::summary)
            .filter(StringUtils::hasText)
            .distinct()
            .limit(5)
            .collect(Collectors.joining("；"));
        String typeAndLevel = matches.stream()
            .map(MatchedWarning::typeAndLevel)
            .filter(StringUtils::hasText)
            .distinct()
            .limit(5)
            .collect(Collectors.joining("；"));
        return new WarningEvaluation(summary, typeAndLevel, top.rule().guardLevel(), top.rule().cardColor());
    }

    private WarningRule promoteRuleForNewDelivery(WarningItem warning, WarningRule rule) {
        String type = firstText(rule.warningType(), matchedExtremeWeatherType(warning.matchText()));
        String promotedGuard = newDeliveryPromotedGuard(type, rule.warningColor(), rule.guardLevel());
        if (!StringUtils.hasText(promotedGuard)) {
            return rule;
        }
        return new WarningRule(
            rule.warningType(),
            rule.warningColor(),
            promotedGuard,
            normalizeCardColor(firstText(rule.cardColor(), cardColorForWarningColor(rule.warningColor()))),
            rule.enabled()
        );
    }

    private WarningRule newDeliveryPromotionRule(WarningItem warning) {
        String warningColor = warningItemColor(warning);
        String type = matchedExtremeWeatherType(warning.matchText());
        String promotedGuard = newDeliveryPromotedGuard(type, warningColor, "");
        if (!StringUtils.hasText(promotedGuard)) {
            return null;
        }
        return new WarningRule(type, warningColor, promotedGuard, cardColorForWarningColor(warningColor), true);
    }

    private String newDeliveryPromotedGuard(String warningType, String warningColor, String currentGuard) {
        String typeClass = extremeWeatherClass(warningType);
        String color = normalizeWarningColor(warningColor);
        String promotedGuard = "";
        if ("CLASS_ONE".equals(typeClass)) {
            promotedGuard = switch (color) {
                case "红色" -> "一级戒备";
                case "橙色" -> "一级戒备";
                case "黄色", "蓝色" -> "二级戒备";
                default -> "";
            };
        } else if ("CLASS_TWO".equals(typeClass)) {
            promotedGuard = switch (color) {
                case "红色" -> "一级戒备";
                case "橙色" -> "二级戒备";
                case "黄色", "蓝色" -> "三级戒备";
                default -> "";
            };
        }
        if (!StringUtils.hasText(promotedGuard)) {
            return "";
        }
        return !isReportableGuard(currentGuard) || compareGuardLevel(promotedGuard, currentGuard) < 0
            ? promotedGuard
            : currentGuard;
    }

    private WarningEvaluation evaluateNewDeliveryForecastPromotion(List<String> forecastDays, boolean newDeliveryPromotion) {
        if (!newDeliveryPromotion) {
            return WarningEvaluation.empty();
        }
        String matchedForecast = forecastDays.stream()
            .filter(this::containsMediumRainOrSnowForecast)
            .findFirst()
            .orElse("");
        if (!StringUtils.hasText(matchedForecast)) {
            return WarningEvaluation.empty();
        }
        String summary = "新交付楼栋提级：未来天气预报提示中雨/雪及以上，按制度启动三级戒备。"
            + " 预报：" + matchedForecast;
        return new WarningEvaluation(summary, "新交付楼栋未来中雨/雪及以上预报", "三级戒备", "yellow");
    }

    private boolean containsMediumRainOrSnowForecast(String forecast) {
        String text = defaultText(forecast, "");
        return containsAny(text, "中雨", "大雨", "暴雨", "特大暴雨", "中雪", "大雪", "暴雪", "雨夹雪", "冻雨");
    }

    private String matchedExtremeWeatherType(String value) {
        String text = defaultText(value, "");
        for (String type : CLASS_ONE_EXTREME_WEATHER_TYPES) {
            if (text.contains(type)) {
                return type;
            }
        }
        for (String type : CLASS_TWO_EXTREME_WEATHER_TYPES) {
            if (text.contains(type)) {
                return type;
            }
        }
        return "";
    }

    private String extremeWeatherClass(String warningType) {
        String type = defaultText(warningType, "");
        if (CLASS_ONE_EXTREME_WEATHER_TYPES.stream().anyMatch(type::contains)) {
            return "CLASS_ONE";
        }
        if (CLASS_TWO_EXTREME_WEATHER_TYPES.stream().anyMatch(type::contains)) {
            return "CLASS_TWO";
        }
        return "";
    }

    private WarningResolveNotice evaluateWarningResolveNotice(List<WarningItem> warnings, List<WarningRule> rules) {
        List<WarningResolveNotice> notices = new ArrayList<>();
        for (WarningItem warning : warnings) {
            if (!isWarningResolveNotice(warning)) {
                continue;
            }
            WarningRule matchedRule = matchedWarningRule(warning, rules);
            String warningType = matchedRule == null
                ? firstText(warning.typeName(), warning.matchText())
                : matchedRule.warningType();
            String warningColor = matchedRule == null
                ? firstText(warningItemColor(warning), normalizeWarningColor(warning.matchText()))
                : matchedRule.warningColor();
            String summary = firstText(
                warning.title(),
                warning.detail(),
                warning.summary(),
                warningType + warningColor + "预警已解除"
            );
            String typeAndLevel = List.of(warningType, warningColor).stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
            notices.add(new WarningResolveNotice(
                summary,
                defaultText(typeAndLevel, "天气预警") + " / 预警解除",
                warningMatchText(summary, warningType, warningColor, warning.matchText())
            ));
        }
        if (notices.isEmpty()) {
            return WarningResolveNotice.empty();
        }
        String summary = notices.stream()
            .map(WarningResolveNotice::summary)
            .filter(StringUtils::hasText)
            .distinct()
            .limit(5)
            .collect(Collectors.joining("；"));
        String typeAndLevel = notices.stream()
            .map(WarningResolveNotice::typeAndLevel)
            .filter(StringUtils::hasText)
            .distinct()
            .limit(5)
            .collect(Collectors.joining("；"));
        String matchText = notices.stream()
            .map(WarningResolveNotice::matchText)
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.joining(" "));
        return new WarningResolveNotice(summary, typeAndLevel, matchText);
    }

    private WarningRule matchedWarningRule(WarningItem warning, List<WarningRule> rules) {
        String warningColor = warningItemColor(warning);
        for (WarningRule rule : rules) {
            if (rule.enabled()
                && sameWarningColor(rule.warningColor(), warningColor)
                && sameWarningType(rule.warningType(), warning.matchText())) {
                return rule;
            }
        }
        return null;
    }

    private boolean isWarningResolveNotice(WarningItem warning) {
        return warning != null && isWarningResolveText(warning.matchText());
    }

    private boolean isWarningResolveText(String value) {
        String text = defaultText(value, "");
        if (!StringUtils.hasText(text) || containsAny(text, "未解除", "尚未解除", "没有解除")) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean resolveWord = containsAny(text, "解除", "取消", "终止")
            || containsAny(lower, "cancel", "clear", "resolved", "expired", "ended");
        boolean warningWord = containsAny(text, "预警")
            || containsAny(lower, "warning", "alert");
        return resolveWord && warningWord;
    }

    private String guardTag(String warningLevel, double wetBulb) {
        String wetBulbLevel = wetBulb >= 30D ? "二级戒备" : wetBulb >= 28D ? "三级戒备" : "";
        if (StringUtils.hasText(warningLevel)) {
            return warningLevel;
        }
        if (StringUtils.hasText(wetBulbLevel)) {
            return wetBulbLevel;
        }
        return "常规观察";
    }

    private int compareGuardLevel(String a, String b) {
        return guardLevelOrder(a) - guardLevelOrder(b);
    }

    private int guardLevelOrder(String level) {
        if (level == null) {
            return 99;
        }
        if (level.contains("一级")) {
            return 1;
        }
        if (level.contains("二级")) {
            return 2;
        }
        if (level.contains("三级")) {
            return 3;
        }
        return 9;
    }

    private String wetBulbAdvice(double wetBulb, Map<String, String> settings) {
        if (Double.isNaN(wetBulb)) {
            return "湿球温度无法计算，请检查天气接口返回的干球温度和相对湿度。";
        }
        List<String> advices = wetBulbRules(settings).stream()
            .filter(rule -> rule.enabled() && rule.matches(wetBulb))
            .map(WetBulbRule::advice)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        String advice = advices.isEmpty()
            ? "湿球温度未命中后台规则，请按现场运行策略处理。"
            : String.join("\n", advices);
        String note = defaultText(value(settings, KEY_WET_BULB_NOTE), DEFAULT_WET_BULB_NOTE);
        return StringUtils.hasText(note) ? advice + "\n" + note : advice;
    }

    private String guardSteps(String guardTag) {
        if (guardTag != null && guardTag.contains("一级")) {
            return "一级戒备：成立专项应急指挥组；现场支持人员必要时 2h 内到场；现场 7*24 值守，巡检 2h/次，必要时间隔 1h/次；停止所有设备变更、维修、施工、真实操作演练、设备清洁、危险作业和户外作业；每小时了解灾害最新情况，并按约 30 分钟/次通报；所有现场工单需求需在通报群同步并获授权后受理；按需协调供应商现场值守、电力、供水、供油等外部资源。";
        }
        if (guardTag != null && guardTag.contains("二级")) {
            return "二级戒备：现场团队内部通报；必要时安排现场支持人员 8h 内到场；现场 7*24 值守，巡检 4h/次，必要时间隔 2h/次；通知设备供应商停止所有维护、施工等现场操作；加强人员和物品进出管控，根据客户或相关部门要求进行真实通报，必要时组织损耗物资紧急采购。";
        }
        if (guardTag != null && guardTag.contains("三级")) {
            return "三级戒备：现场团队进行内部预警通报；明确重保联络清单和值班排班，7*24 On call；启动重保戒备检查并推动问题清零；通知相关设备供应商 7*24 On call；清点应急储备物资并检查可用性；节假日重保至少提前 1 周安排。";
        }
        return "常规观察：按日常巡检和天气预报提醒执行。";
    }

    private String resolveGuardSteps(WarningResolveNotice notice) {
        String summary = notice == null ? "" : notice.summary();
        return "已收到" + defaultText(summary, "天气预警解除通知") + "，解除对应重保处置，恢复常规巡检。";
    }

    private String appendRainySeasonAdvice(String baseSteps, Map<String, String> settings) {
        if (!boolSetting(settings, KEY_RAINY_SEASON_ENABLED, true) || !isRainySeasonMonth(settings)) {
            return baseSteps;
        }
        String advice = "雨季专项：按制度检查防水及排水系统；雨季时每月检查机房/包间天花板、墙角、地漏、空调管穿墙孔、电缆井等渗漏风险；雨季前和雨季期间确认水桶、雨布、抽水泵等防汛物资及空调间漏水传感器可用；复查历史漏水点。";
        return StringUtils.hasText(baseSteps) ? baseSteps + "\n" + advice : advice;
    }

    private boolean isRainySeasonMonth(Map<String, String> settings) {
        int month = LocalDateTime.now().getMonthValue();
        String months = defaultText(value(settings, KEY_RAINY_SEASON_MONTHS), "6,7,8,9");
        for (String item : months.split("[,，;；\\s]+")) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            try {
                if (Integer.parseInt(item.trim()) == month) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid month fragments and keep checking the rest.
            }
        }
        return false;
    }

    private List<WarningRule> warningRules(Map<String, String> settings) {
        String json = normalizeWarningRulesJson(value(settings, KEY_WARNING_RULES_JSON));
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, LIST_MAP_REF);
            List<WarningRule> rules = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                WarningRule rule = warningRule(row);
                if (StringUtils.hasText(rule.warningType()) && StringUtils.hasText(rule.warningColor())) {
                    rules.add(rule);
                }
            }
            return rules;
        } catch (JsonProcessingException ex) {
            log.warn("Disaster weather warning rules parse failed, fallback to defaults", ex);
            return defaultWarningRules();
        }
    }

    private List<WarningRule> defaultWarningRules() {
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(DEFAULT_WARNING_RULES_JSON, LIST_MAP_REF);
            return rows.stream().map(this::warningRule).toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private WarningRule warningRule(Map<String, Object> row) {
        String type = firstText(text(row.get("type")), text(row.get("warningType")));
        String color = normalizeWarningColor(firstText(text(row.get("color")), text(row.get("warningColor"))));
        String guard = firstText(text(row.get("guard")), text(row.get("guardLevel")));
        String card = normalizeCardColor(firstText(text(row.get("card")), text(row.get("cardColor")), cardColorForWarningColor(color)));
        boolean enabled = row.get("enabled") == null || Boolean.parseBoolean(String.valueOf(row.get("enabled")));
        return new WarningRule(type, color, guard, card, enabled);
    }

    private String normalizeWarningRulesJson(String value) {
        String raw = StringUtils.hasText(value) ? value.trim() : DEFAULT_WARNING_RULES_JSON;
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(raw, LIST_MAP_REF);
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                WarningRule rule = warningRule(row);
                if (!StringUtils.hasText(rule.warningType()) || !StringUtils.hasText(rule.warningColor())) {
                    continue;
                }
                Map<String, Object> target = new LinkedHashMap<>();
                target.put("type", rule.warningType());
                target.put("color", rule.warningColor());
                target.put("guard", defaultText(rule.guardLevel(), "无匹配标签"));
                target.put("card", normalizeCardColor(rule.cardColor()));
                target.put("enabled", rule.enabled());
                normalized.add(target);
            }
            String json = objectMapper.writeValueAsString(normalized);
            if (json.length() > 10000) {
                throw new BizException("DISASTER_WARNING_RULES_TOO_LONG", "重保天气预警规则过长，请减少规则数量后保存");
            }
            return StringUtils.hasText(json) && !"[]".equals(json) ? json : DEFAULT_WARNING_RULES_JSON;
        } catch (JsonProcessingException ex) {
            throw new BizException("DISASTER_WARNING_RULES_INVALID", "重保天气预警规则不是有效 JSON");
        }
    }

    private List<WetBulbRule> wetBulbRules(Map<String, String> settings) {
        String json = normalizeWetBulbRulesJson(value(settings, KEY_WET_BULB_RULES_JSON));
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, LIST_MAP_REF);
            List<WetBulbRule> rules = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                WetBulbRule rule = wetBulbRule(row);
                if (StringUtils.hasText(rule.advice())) {
                    rules.add(rule);
                }
            }
            return rules;
        } catch (JsonProcessingException ex) {
            log.warn("Disaster weather wet bulb rules parse failed, fallback to defaults", ex);
            return defaultWetBulbRules();
        }
    }

    private List<WetBulbRule> defaultWetBulbRules() {
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(DEFAULT_WET_BULB_RULES_JSON, LIST_MAP_REF);
            return rows.stream().map(this::wetBulbRule).toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private WetBulbRule wetBulbRule(Map<String, Object> row) {
        double minValue = objectDoubleValue(row.get("min"));
        double maxValue = objectDoubleValue(row.get("max"));
        Double min = Double.isNaN(minValue) ? null : minValue;
        Double max = Double.isNaN(maxValue) ? null : maxValue;
        boolean includeMin = row.get("includeMin") == null || Boolean.parseBoolean(String.valueOf(row.get("includeMin")));
        boolean includeMax = row.get("includeMax") == null || Boolean.parseBoolean(String.valueOf(row.get("includeMax")));
        boolean enabled = row.get("enabled") == null || Boolean.parseBoolean(String.valueOf(row.get("enabled")));
        return new WetBulbRule(min, max, includeMin, includeMax, text(row.get("advice")).trim(), enabled);
    }

    private String normalizeWetBulbRulesJson(String value) {
        String raw = StringUtils.hasText(value) ? value.trim() : DEFAULT_WET_BULB_RULES_JSON;
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(raw, LIST_MAP_REF);
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                WetBulbRule rule = wetBulbRule(row);
                if (!StringUtils.hasText(rule.advice()) || (rule.min() == null && rule.max() == null)) {
                    continue;
                }
                Map<String, Object> target = new LinkedHashMap<>();
                target.put("min", rule.min());
                target.put("max", rule.max());
                target.put("includeMin", rule.includeMin());
                target.put("includeMax", rule.includeMax());
                target.put("advice", rule.advice());
                target.put("enabled", rule.enabled());
                normalized.add(target);
            }
            String json = objectMapper.writeValueAsString(normalized);
            if (json.length() > 2000) {
                throw new BizException("DISASTER_WET_BULB_RULES_TOO_LONG", "湿球温度建议规则过长，请减少规则数量后保存");
            }
            return StringUtils.hasText(json) && !"[]".equals(json) ? json : DEFAULT_WET_BULB_RULES_JSON;
        } catch (JsonProcessingException ex) {
            throw new BizException("DISASTER_WET_BULB_RULES_INVALID", "湿球温度建议规则不是有效 JSON");
        }
    }

    private String normalizeRainySeasonMonths(String value) {
        if (value == null) {
            return null;
        }
        List<String> months = new ArrayList<>();
        for (String item : value.split("[,，;；\\s]+")) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            try {
                int month = Integer.parseInt(item.trim());
                if (month >= 1 && month <= 12) {
                    String text = String.valueOf(month);
                    if (!months.contains(text)) {
                        months.add(text);
                    }
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid fragments; validation falls back to the default set when nothing valid remains.
            }
        }
        return months.isEmpty() ? "6,7,8,9" : String.join(",", months);
    }

    private boolean sameWarningType(String ruleType, String warningType) {
        String rule = defaultText(ruleType, "");
        String warning = defaultText(warningType, "");
        return StringUtils.hasText(rule) && StringUtils.hasText(warning) && (warning.contains(rule) || rule.contains(warning));
    }

    private boolean sameWarningColor(String ruleColor, String warningColor) {
        return Objects.equals(normalizeWarningColor(ruleColor), normalizeWarningColor(warningColor));
    }

    private boolean isReportableGuard(String guardLevel) {
        return guardLevel != null && (guardLevel.contains("一级") || guardLevel.contains("二级") || guardLevel.contains("三级"));
    }

    private String warningItemColor(WarningItem warning) {
        if (warning == null) {
            return "";
        }
        return firstText(
            knownWarningColor(warning.severity()),
            knownWarningColor(warning.matchText()),
            warningColor(warning.severity())
        );
    }

    private String knownWarningColor(String value) {
        String color = normalizeWarningColor(value);
        return List.of("红色", "橙色", "黄色", "蓝色").contains(color) ? color : "";
    }

    private String warningColor(String severity) {
        return normalizeWarningColor(severity);
    }

    private String normalizeWarningColor(String value) {
        String text = defaultText(value, "");
        if (containsAny(text, "红", "Red", "RED", "red")) {
            return "红色";
        }
        if (containsAny(text, "橙", "Orange", "ORANGE", "orange")) {
            return "橙色";
        }
        if (containsAny(text, "黄", "Yellow", "YELLOW", "yellow")) {
            return "黄色";
        }
        if (containsAny(text, "蓝", "Blue", "BLUE", "blue")) {
            return "蓝色";
        }
        return text;
    }

    private int warningColorOrder(String value) {
        return switch (normalizeWarningColor(value)) {
            case "红色" -> 1;
            case "橙色" -> 2;
            case "黄色" -> 3;
            case "蓝色" -> 4;
            default -> 9;
        };
    }

    private String cardColorForWarningColor(String color) {
        return switch (normalizeWarningColor(color)) {
            case "红色" -> "red";
            case "橙色" -> "orange";
            case "黄色" -> "yellow";
            case "蓝色" -> "blue";
            default -> "blue";
        };
    }

    private String normalizeCardColor(String value) {
        String color = defaultText(value, "blue").toLowerCase(Locale.ROOT);
        return List.of("red", "orange", "yellow", "blue", "green").contains(color) ? color : "blue";
    }

    private boolean applyResolveNoticeIfNeeded(DisasterWeatherRecord record) {
        if (!isWarningResolveNoticeRecord(record)) {
            return false;
        }
        DisasterWeatherRecord previous = latestResolvablePreviousWarningRecord(record);
        if (previous == null
            || !isEffectiveWarningRecord(previous)
            || !isContinuousWarningAlreadyHandled(previous.getSendStatus())) {
            return false;
        }
        String previousWarning = firstText(previous.getWarningTypeExtract(), previous.getWarningInfo(), "上一轮天气预警");
        String resolveNotice = firstText(record.getWarningInfo(), "已收到天气预警解除通知");
        record.setWarningInfo(resolveNotice + "；前序预警：" + previousWarning);
        record.setWarningTypeExtract(firstText(record.getWarningTypeExtract(), "预警解除"));
        record.setGuardTag("预警解除");
        record.setGuardAlertTag("green");
        record.setGuardSteps("已收到官方天气预警解除通知，解除对应重保处置，恢复常规巡检。");
        record.setLastError("");
        updateById(record);
        return true;
    }

    private DisasterWeatherRecord latestResolvablePreviousWarningRecord(DisasterWeatherRecord record) {
        if (record == null) {
            return null;
        }
        LambdaQueryWrapper<DisasterWeatherRecord> wrapper = new LambdaQueryWrapper<DisasterWeatherRecord>()
            .eq(DisasterWeatherRecord::getRecordType, "WARNING");
        if (StringUtils.hasText(record.getCityCode())) {
            wrapper.eq(DisasterWeatherRecord::getCityCode, record.getCityCode());
        }
        wrapper.lt(record.getId() != null, DisasterWeatherRecord::getId, record.getId())
            .orderByDesc(DisasterWeatherRecord::getCreatedAt)
            .orderByDesc(DisasterWeatherRecord::getId)
            .last("LIMIT 200");
        List<DisasterWeatherRecord> records = baseMapper.selectList(wrapper);
        for (DisasterWeatherRecord previous : records) {
            if (isSentResolveNoticeRecord(previous)) {
                return null;
            }
            if (isEffectiveWarningRecord(previous)) {
                return isContinuousWarningAlreadyHandled(previous.getSendStatus())
                    && resolveNoticeMatchesPreviousWarning(record, previous) ? previous : null;
            }
        }
        return null;
    }

    private boolean isWarningResolveNoticeRecord(DisasterWeatherRecord record) {
        if (record == null) {
            return false;
        }
        if ("预警解除".equals(record.getGuardTag())) {
            return true;
        }
        return !isReportableGuard(record.getGuardTag())
            && isWarningResolveText(warningRecordMatchText(record));
    }

    private boolean isSentResolveNoticeRecord(DisasterWeatherRecord record) {
        return isWarningResolveNoticeRecord(record) && "SENT".equals(record.getSendStatus());
    }

    private boolean resolveNoticeMatchesPreviousWarning(DisasterWeatherRecord record, DisasterWeatherRecord previous) {
        if (!isWarningResolveNoticeRecord(record)) {
            return true;
        }
        String resolveText = warningRecordMatchText(record);
        String previousText = warningRecordMatchText(previous);
        boolean typeMatched = warningRules(settingsMap()).stream()
            .map(WarningRule::warningType)
            .filter(StringUtils::hasText)
            .distinct()
            .anyMatch(type -> resolveText.contains(type) && previousText.contains(type));
        if (!typeMatched) {
            return false;
        }
        List<String> resolveColors = warningColorTokens(resolveText);
        List<String> previousColors = warningColorTokens(previousText);
        return resolveColors.isEmpty()
            || previousColors.isEmpty()
            || resolveColors.stream().anyMatch(previousColors::contains);
    }

    private String warningRecordMatchText(DisasterWeatherRecord record) {
        if (record == null) {
            return "";
        }
        return warningMatchText(record.getWarningInfo(), record.getWarningTypeExtract(), record.getGuardTag(), record.getWarningRawJson());
    }

    private List<String> warningColorTokens(String value) {
        String text = defaultText(value, "");
        List<String> colors = new ArrayList<>();
        for (String color : List.of("红色", "橙色", "黄色", "蓝色")) {
            String shortColor = color.substring(0, 1);
            if (text.contains(color) || text.contains(shortColor)) {
                colors.add(color);
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, String> english = Map.of(
            "red", "红色",
            "orange", "橙色",
            "yellow", "黄色",
            "blue", "蓝色"
        );
        for (Map.Entry<String, String> entry : english.entrySet()) {
            if (lower.contains(entry.getKey()) && !colors.contains(entry.getValue())) {
                colors.add(entry.getValue());
            }
        }
        return colors;
    }

    private boolean isDuplicateWarning(DisasterWeatherRecord record) {
        if (!isEffectiveWarningRecord(record)) {
            return false;
        }
        LambdaQueryWrapper<DisasterWeatherRecord> wrapper = new LambdaQueryWrapper<DisasterWeatherRecord>()
            .eq(DisasterWeatherRecord::getRecordType, "WARNING");
        if (StringUtils.hasText(record.getCityCode())) {
            wrapper.eq(DisasterWeatherRecord::getCityCode, record.getCityCode());
        }
        if (record.getId() != null) {
            wrapper.lt(DisasterWeatherRecord::getId, record.getId());
        }
        wrapper.orderByDesc(DisasterWeatherRecord::getCreatedAt)
            .orderByDesc(DisasterWeatherRecord::getId)
            .last("LIMIT 1");
        DisasterWeatherRecord latest = baseMapper.selectOne(wrapper);
        return latest != null
            && isEffectiveWarningRecord(latest)
            && Objects.equals(warningSegmentKey(latest), warningSegmentKey(record))
            && isContinuousWarningAlreadyHandled(latest.getSendStatus());
    }

    private boolean isContinuousWarningAlreadyHandled(String sendStatus) {
        return "SENT".equals(sendStatus) || "SKIPPED".equals(sendStatus);
    }

    private int cleanupExpiredNoWarningRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        return baseMapper.delete(new LambdaQueryWrapper<DisasterWeatherRecord>()
            .eq(DisasterWeatherRecord::getRecordType, "WARNING")
            .lt(DisasterWeatherRecord::getCreatedAt, cutoff)
            .and(wrapper -> wrapper
                .isNull(DisasterWeatherRecord::getWarningInfo)
                .or()
                .eq(DisasterWeatherRecord::getWarningInfo, "")));
    }

    private int compactContinuousWarningRecords() {
        List<DisasterWeatherRecord> records = list(new LambdaQueryWrapper<DisasterWeatherRecord>()
            .eq(DisasterWeatherRecord::getRecordType, "WARNING")
            .orderByAsc(DisasterWeatherRecord::getCityCode)
            .orderByAsc(DisasterWeatherRecord::getCreatedAt)
            .orderByAsc(DisasterWeatherRecord::getId));
        List<Long> deleteIds = new ArrayList<>();
        List<DisasterWeatherRecord> segment = new ArrayList<>();
        String segmentKey = "";
        for (DisasterWeatherRecord record : records) {
            if (!isEffectiveWarningRecord(record)) {
                collectMiddleRecordIds(segment, deleteIds);
                segment.clear();
                segmentKey = "";
                continue;
            }
            String currentKey = warningSegmentKey(record);
            if (!Objects.equals(segmentKey, currentKey)) {
                collectMiddleRecordIds(segment, deleteIds);
                segment.clear();
                segmentKey = currentKey;
            }
            segment.add(record);
        }
        collectMiddleRecordIds(segment, deleteIds);
        return deleteIds.isEmpty() ? 0 : baseMapper.deleteBatchIds(deleteIds);
    }

    private boolean isEffectiveWarningRecord(DisasterWeatherRecord record) {
        return record != null
            && StringUtils.hasText(record.getWarningInfo())
            && !record.getWarningInfo().startsWith("预警接口异常")
            && isReportableGuard(record.getGuardTag());
    }

    private String warningSegmentKey(DisasterWeatherRecord record) {
        return defaultText(record.getCityCode(), "")
            + "|"
            + defaultText(firstText(record.getWarningTypeExtract(), record.getWarningInfo()), "")
            + "|"
            + defaultText(record.getGuardTag(), "");
    }

    private void collectMiddleRecordIds(List<DisasterWeatherRecord> segment, List<Long> deleteIds) {
        if (segment.size() <= 2) {
            return;
        }
        for (int i = 1; i < segment.size() - 1; i++) {
            Long id = segment.get(i).getId();
            if (id != null) {
                deleteIds.add(id);
            }
        }
    }

    private Map<String, Object> buildFeishuCard(DisasterWeatherRecord record,
                                                Map<String, String> settings,
                                                boolean testDelivery,
                                                String previewImageKey) {
        String area = defaultText(record.getPushCity(), defaultText(value(settings, KEY_PUSH_CITY), "当前区域"));
        String cardTypeTitle = switch (record.getRecordType()) {
            case "WEEKLY" -> area + "本周天气预报";
            case "WARNING" -> area + "天气预警";
            default -> area + "今日天气预报";
        };
        String guardTag = defaultText(record.getGuardTag(), "常规观察");
        String title = guardTag + "｜" + cardTypeTitle;
        if (testDelivery) {
            title = "【测试】" + title;
        }
        String template = cardTemplate(record);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", Map.of(
            "template", template,
            "title", Map.of("tag", "plain_text", "content", title)
        ));
        List<Object> elements = new ArrayList<>();
        if (StringUtils.hasText(previewImageKey)) {
            elements.add(imageElement(previewImageKey, "灾害预警结果预览"));
        }
        elements.add(labelValueRow(
            "发送类型", testDelivery ? "测试群" : "正式值班群",
            "区域", area
        ));
        elements.add(labelValueRow(
            "中心", defaultText(value(settings, KEY_CENTER_ADDRESS), "未配置"),
            "时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        ));
        boolean warningCard = "WARNING".equals(record.getRecordType());
        if (warningCard) {
            boolean hasWarning = StringUtils.hasText(record.getWarningInfo());
            elements.add(labelValueRow(
                "等级", hasWarning ? defaultText(record.getGuardTag(), "未命中") : "未命中",
                "通报时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            ));
            elements.add(labelValueRow(
                "现象描述", defaultText(record.getWarningInfo(), "当前无预警"),
                "机房现状", hasWarning ? "请运维经理按现场情况确认并同步" : "常规观察"
            ));
            elements.add(markdown("预警处置\n" + markdownText(hasWarning
                ? defaultText(record.getGuardSteps(), "请按重保预案执行。")
                : "当前未获取到三级及以上重保天气预警，保持常规巡检。")));
            elements.add(labelValueRow(
                "辅助天气", record.getTodayWeatherInfo(),
                "湿球温度", record.getWetBulbInfo()
            ));
            elements.add(labelValueRow(
                "发送状态", sendStatusText(record.getSendStatus()),
                "预警接口", StringUtils.hasText(record.getWarningRawJson()) ? "已查询" : "未查询"
            ));
        } else {
            elements.add(labelValueRow(
                "预警信息", defaultText(record.getWarningInfo(), "当前无预警"),
                "今日天气", record.getTodayWeatherInfo()
            ));
            elements.add(labelValueRow(
                "湿球温度", record.getWetBulbInfo(),
                "发送状态", sendStatusText(record.getSendStatus())
            ));
            elements.add(markdown("处置建议\n" + markdownText(record.getWetBulbAdvice())));
            if (StringUtils.hasText(record.getWarningInfo())) {
                elements.add(labelValueRow(
                    "戒备等级", record.getGuardTag(),
                    "重保步骤", record.getGuardSteps()
                ));
            }
            List<String> forecasts = forecastList(record).stream().filter(StringUtils::hasText).limit("WEEKLY".equals(record.getRecordType()) ? 7 : 3).toList();
            if (!forecasts.isEmpty()) {
                StringBuilder builder = new StringBuilder("未来天气：");
                for (int i = 0; i < forecasts.size(); i++) {
                    builder.append("\n").append(i + 1).append(". ").append(markdownText(forecasts.get(i)));
                }
                elements.add(markdown(builder.toString()));
            }
        }
        elements.add(buttonRow(List.of(linkButton("重保检查表填写完成审批", GUARD_CHECK_FORM_URL, "danger"))));
        elements.add(markdown("记录编号：" + markdownText(record.getQueryRecordId()) + "\n卡片版本：" + FEISHU_CARD_VERSION));
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private String cardTemplate(DisasterWeatherRecord record) {
        if (StringUtils.hasText(record.getGuardAlertTag())) {
            return normalizeCardColor(record.getGuardAlertTag());
        }
        if (StringUtils.hasText(record.getWarningTypeExtract())) {
            if (record.getWarningTypeExtract().contains("红色")) {
                return "red";
            }
            if (record.getWarningTypeExtract().contains("橙色")) {
                return "orange";
            }
            if (record.getWarningTypeExtract().contains("黄色")) {
                return "yellow";
            }
            if (record.getWarningTypeExtract().contains("蓝色")) {
                return "blue";
            }
        }
        if (record.getGuardTag() != null && record.getGuardTag().contains("一级")) {
            return "red";
        }
        if (record.getGuardTag() != null && record.getGuardTag().contains("二级")) {
            return "orange";
        }
        if (record.getGuardTag() != null && record.getGuardTag().contains("三级")) {
            return "yellow";
        }
        return "green";
    }

    private Map<String, Object> markdown(String content) {
        return Map.of("tag", "markdown", "content", content == null ? "" : content);
    }

    private Map<String, Object> imageElement(String imageKey, String altText) {
        return Map.of(
            "tag", "img",
            "img_key", imageKey,
            "alt", plainText(altText)
        );
    }

    private Map<String, Object> labelValueRow(String leftLabel, String leftValue, String rightLabel, String rightValue) {
        Map<String, Object> leftColumn = labelValueColumn(leftLabel, leftValue);
        Map<String, Object> rightColumn = labelValueColumn(rightLabel, rightValue);
        return Map.of(
            "tag", "column_set",
            "flex_mode", "none",
            "horizontal_spacing", "default",
            "background_style", "default",
            "columns", List.of(leftColumn, rightColumn)
        );
    }

    private Map<String, Object> labelValueColumn(String label, String value) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("tag", "column");
        column.put("width", "weighted");
        column.put("weight", 1);
        column.put("elements", List.of(markdown(label + "\n" + markdownText(value))));
        return column;
    }

    private Map<String, Object> buttonRow(List<Map<String, Object>> buttons) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map<String, Object> button : buttons) {
            columns.add(Map.of(
                "tag", "column",
                "width", "auto",
                "elements", List.of(button)
            ));
        }
        return Map.of(
            "tag", "column_set",
            "flex_mode", "none",
            "horizontal_spacing", "default",
            "background_style", "default",
            "columns", columns
        );
    }

    private Map<String, Object> linkButton(String text, String url, String type) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("tag", "button");
        button.put("text", plainText(text));
        button.put("type", defaultText(type, "default"));
        button.put("size", "medium");
        button.put("width", "default");
        button.put("behaviors", List.of(Map.of(
            "type", "open_url",
            "default_url", url,
            "pc_url", url,
            "ios_url", url,
            "android_url", url
        )));
        return button;
    }

    private Map<String, Object> plainText(String content) {
        return Map.of("tag", "plain_text", "content", content == null ? "" : content);
    }

    private String buildAndUploadPreviewImageKey(FeishuBotRuntimeConfig.Snapshot config,
                                                 DisasterWeatherRecord record,
                                                 Map<String, String> settings,
                                                 boolean testDelivery) {
        try {
            byte[] imageBytes = renderPreviewImage(record, settings, testDelivery);
            return uploadFeishuImage(config, imageBytes, "disaster-weather-" + defaultText(record.getQueryRecordId(), "preview") + ".png");
        } catch (RuntimeException | IOException ex) {
            log.warn("Build or upload disaster weather preview image failed, recordId={}", record == null ? null : record.getId(), ex);
            return "";
        }
    }

    private byte[] renderPreviewImage(DisasterWeatherRecord record, Map<String, String> settings, boolean testDelivery) throws IOException {
        int width = 900;
        int height = 960;
        int margin = 28;
        int y = 0;
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            Color headerColor = previewColor(cardTemplate(record));
            g.setColor(headerColor);
            g.fillRoundRect(margin, margin, width - margin * 2, 76, 18, 18);
            g.fillRect(margin, margin + 38, width - margin * 2, 38);

            Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 24);
            Font normalFont = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
            Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);
            Font smallFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);

            String area = defaultText(record.getPushCity(), defaultText(value(settings, KEY_PUSH_CITY), "当前区域"));
            String guardTag = defaultText(record.getGuardTag(), "常规观察");
            g.setFont(titleFont);
            g.setColor(Color.WHITE);
            drawText(g, (testDelivery ? "【测试】" : "") + guardTag, margin + 24, margin + 48);
            FontMetrics titleMetrics = g.getFontMetrics();
            int areaWidth = titleMetrics.stringWidth(area);
            drawText(g, area, width - margin - 24 - areaWidth, margin + 48);

            y = margin + 112;
            y = drawPreviewRow(g, labelFont, normalFont, margin + 24, y, "预警信息", defaultText(record.getWarningInfo(), "当前无预警"), width - margin * 2 - 48, 3);
            y = drawPreviewRow(g, labelFont, normalFont, margin + 24, y, "今日天气", defaultText(record.getTodayWeatherInfo(), "-"), width - margin * 2 - 48, 2);
            y = drawPreviewRow(g, labelFont, normalFont, margin + 24, y, "湿球温度", defaultText(record.getWetBulbInfo(), "-"), width - margin * 2 - 48, 2);
            y = drawPreviewRow(g, labelFont, normalFont, margin + 24, y, "发送类型", testDelivery ? "测试群" : "正式值班群", width - margin * 2 - 48, 1);
            y = drawPreviewRow(g, labelFont, normalFont, margin + 24, y, "发送状态", sendStatusText(record.getSendStatus()), width - margin * 2 - 48, 1);
            String adviceTitle = "WARNING".equals(record.getRecordType()) ? "预警处置" : "处置建议";
            String advice = "WARNING".equals(record.getRecordType())
                ? defaultText(record.getGuardSteps(), "请按重保预案执行。")
                : defaultText(record.getWetBulbAdvice(), "请按现场运行策略处理。");
            y = drawPreviewRow(g, labelFont, normalFont, margin + 24, y, adviceTitle, advice, width - margin * 2 - 48, 5);

            g.setColor(new Color(246, 248, 252));
            g.fillRoundRect(margin, y + 8, width - margin * 2, 76, 14, 14);
            drawLegend(g, smallFont, margin + 24, y + 38);
            y += 108;

            g.setColor(new Color(129, 140, 158));
            g.setFont(smallFont);
            drawText(g, "记录编号：" + defaultText(record.getQueryRecordId(), "-") + "    卡片版本：" + FEISHU_CARD_VERSION, margin + 24, y);
            y += 28;
        } finally {
            g.dispose();
        }

        int cropHeight = Math.min(height, Math.max(360, y + margin));
        BufferedImage cropped = canvas.getSubimage(0, 0, width, cropHeight);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(cropped, "png", output);
        return output.toByteArray();
    }

    private int drawPreviewRow(Graphics2D g,
                               Font labelFont,
                               Font valueFont,
                               int x,
                               int y,
                               String label,
                               String value,
                               int width,
                               int maxLines) {
        int labelWidth = 112;
        g.setFont(labelFont);
        g.setColor(new Color(71, 85, 105));
        drawText(g, label, x, y);
        g.setFont(valueFont);
        g.setColor(new Color(15, 23, 42));
        List<String> lines = wrapLines(g.getFontMetrics(), defaultText(value, "-"), Math.max(120, width - labelWidth), maxLines);
        int lineY = y;
        for (String line : lines) {
            drawText(g, line, x + labelWidth, lineY);
            lineY += 28;
        }
        return Math.max(y + 34, lineY + 8);
    }

    private void drawLegend(Graphics2D g, Font font, int x, int y) {
        g.setFont(font);
        int cursor = x;
        cursor = drawLegendItem(g, cursor, y, new Color(239, 68, 68), "一级戒备 / 红色预警");
        cursor = drawLegendItem(g, cursor + 20, y, new Color(249, 115, 22), "二级戒备 / 橙色预警");
        cursor = drawLegendItem(g, cursor + 20, y, new Color(234, 179, 8), "三级戒备 / 黄色预警");
        drawLegendItem(g, cursor + 20, y, new Color(22, 163, 74), "常规观察");
    }

    private int drawLegendItem(Graphics2D g, int x, int y, Color color, String text) {
        g.setColor(color);
        g.fillOval(x, y - 12, 16, 16);
        g.setColor(new Color(51, 65, 85));
        drawText(g, text, x + 22, y);
        return x + 22 + g.getFontMetrics().stringWidth(text);
    }

    private List<String> wrapLines(FontMetrics metrics, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String normalized = defaultText(text, "-").replace("\r", "").replace("\n", " ");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            String candidate = current.toString() + ch;
            if (current.length() > 0 && metrics.stringWidth(candidate) > width) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(ch);
                if (lines.size() >= maxLines) {
                    break;
                }
            } else {
                current.append(ch);
            }
        }
        if (lines.size() < maxLines && current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
        }
        if (lines.size() == maxLines && metrics.stringWidth(String.join("", lines)) < metrics.stringWidth(normalized)) {
            String last = lines.get(lines.size() - 1);
            while (last.length() > 1 && metrics.stringWidth(last + "...") > width) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(lines.size() - 1, last + "...");
        }
        return lines.isEmpty() ? List.of("-") : lines;
    }

    private void drawText(Graphics2D g, String text, int x, int y) {
        g.drawString(defaultText(text, ""), x, y);
    }

    private Color previewColor(String template) {
        return switch (normalizeCardColor(template)) {
            case "red" -> new Color(220, 38, 38);
            case "orange" -> new Color(234, 88, 12);
            case "yellow" -> new Color(234, 179, 8);
            case "blue" -> new Color(37, 99, 235);
            default -> new Color(22, 163, 74);
        };
    }

    private String uploadFeishuImage(FeishuBotRuntimeConfig.Snapshot config, byte[] imageBytes, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image_type", "message");
        body.add("image", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        Map<String, Object> response = restClient.post()
            .uri(imageUploadUrl(config))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(MAP_TYPE);
        validateFeishuResponse(response);
        Map<String, Object> data = mapValue(response == null ? null : response.get("data"));
        String imageKey = firstText(text(data.get("image_key")), text(data.get("imageKey")), text(response == null ? null : response.get("image_key")));
        if (!StringUtils.hasText(imageKey)) {
            throw new BizException("FEISHU_IMAGE_UPLOAD_FAILED", "飞书响应缺少 image_key");
        }
        return imageKey;
    }

    private String imageUploadUrl(FeishuBotRuntimeConfig.Snapshot config) {
        String messageUrl = firstText(config.messageSendUrl(), "https://open.feishu.cn/open-apis/im/v1/messages").replaceAll("\\?.*$", "");
        if (messageUrl.endsWith("/messages")) {
            return messageUrl.substring(0, messageUrl.length() - "/messages".length()) + "/images";
        }
        return "https://open.feishu.cn/open-apis/im/v1/images";
    }

    private String sendFeishuCard(FeishuBotRuntimeConfig.Snapshot config, String receiveIdType, String receiveId, Map<String, Object> card) {
        String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
            .queryParam("receive_id_type", receiveIdType)
            .build()
            .toUriString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "interactive");
        body.put("content", toJson(card));
        Map<String, Object> response = restClient.post()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE);
        validateFeishuResponse(response);
        Map<String, Object> data = mapValue(response == null ? null : response.get("data"));
        return firstText(text(data.get("message_id")), text(data.get("messageId")));
    }

    private String resolveTenantAccessToken(FeishuBotRuntimeConfig.Snapshot config) {
        long now = System.currentTimeMillis();
        String signature = config.tenantTokenSignature();
        if (StringUtils.hasText(cachedTenantAccessToken)
            && Objects.equals(cachedTenantAccessTokenSignature, signature)
            && cachedTenantAccessTokenExpiresAt > now) {
            return cachedTenantAccessToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedTenantAccessToken)
                && Objects.equals(cachedTenantAccessTokenSignature, signature)
                && cachedTenantAccessTokenExpiresAt > now) {
                return cachedTenantAccessToken;
            }
            if (!StringUtils.hasText(config.appId()) || !StringUtils.hasText(config.appSecret())) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "请先配置飞书 App ID 和 App Secret");
            }
            try {
                Map<String, Object> response = restClient.post()
                    .uri(config.tenantAccessTokenUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app_id", config.appId(), "app_secret", config.appSecret()))
                    .retrieve()
                    .body(MAP_TYPE);
                validateFeishuResponse(response);
                Map<String, Object> data = mapValue(response == null ? null : response.get("data"));
                String token = firstText(
                    text(response == null ? null : response.get("tenant_access_token")),
                    text(data.get("tenant_access_token"))
                );
                if (!StringUtils.hasText(token)) {
                    throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "飞书响应缺少 tenant_access_token");
                }
                long expireSeconds = numberValue(data.containsKey("expire") ? data.get("expire") : response.get("expire"), 7200L);
                cachedTenantAccessToken = token;
                cachedTenantAccessTokenSignature = signature;
                cachedTenantAccessTokenExpiresAt = now + Math.max(60L, expireSeconds - 60L) * 1000L;
                return token;
            } catch (RestClientResponseException ex) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "获取飞书租户访问凭证失败：" + limit(ex.getResponseBodyAsString(), 300));
            } catch (RestClientException ex) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "获取飞书租户访问凭证失败：" + firstText(ex.getMessage(), "网络异常"));
            }
        }
    }

    private void validateFeishuResponse(Map<String, Object> response) {
        if (response == null) {
            throw new BizException("FEISHU_API_FAILED", "飞书响应为空");
        }
        String code = firstText(text(response.get("code")), text(response.get("Code")));
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BizException("FEISHU_API_FAILED",
                firstText(text(response.get("msg")), text(response.get("message")), "飞书接口返回失败"));
        }
    }

    private void markFailed(DisasterWeatherRecord record, String message) {
        record.setSendStatus("FAILED");
        record.setPushed(0);
        record.setLastError(limit(message, 1000));
        updateById(record);
    }

    private void markSkipped(DisasterWeatherRecord record, String message) {
        record.setSendStatus("SKIPPED");
        record.setPushed(0);
        record.setLastError(limit(message, 1000));
        updateById(record);
    }

    private DisasterWeatherRecordVO toVO(DisasterWeatherRecord record) {
        if (record == null) {
            return null;
        }
        DisasterWeatherRecordVO vo = new DisasterWeatherRecordVO();
        vo.setId(record.getId());
        vo.setQueryRecordId(record.getQueryRecordId());
        vo.setRecordType(record.getRecordType());
        vo.setPushCity(record.getPushCity());
        vo.setPushGroup(record.getPushGroup());
        vo.setCityCode(record.getCityCode());
        vo.setTodayWeatherInfo(record.getTodayWeatherInfo());
        vo.setWarningInfo(record.getWarningInfo());
        vo.setWarningTypeExtract(record.getWarningTypeExtract());
        vo.setGuardTag(record.getGuardTag());
        vo.setGuardAlertTag(record.getGuardAlertTag());
        vo.setWetBulbAdvice(record.getWetBulbAdvice());
        vo.setGuardSteps(record.getGuardSteps());
        vo.setRelativeHumidity(record.getRelativeHumidity());
        vo.setSaturationTemp(record.getSaturationTemp());
        vo.setDryBulbTemp(record.getDryBulbTemp());
        vo.setWetBulbTemp(record.getWetBulbTemp());
        vo.setWetBulbInfo(record.getWetBulbInfo());
        vo.setRealTimePrecipitation(record.getRealTimePrecipitation());
        vo.setManualTrigger(record.getManualTrigger());
        vo.setSendStatus(record.getSendStatus());
        vo.setMessageId(record.getMessageId());
        vo.setLastError(record.getLastError());
        vo.setLastPushTime(record.getLastPushTime());
        vo.setWarningFetchTime(record.getWarningFetchTime());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        vo.setForecastDays(forecastList(record));
        return vo;
    }

    private List<String> forecastList(DisasterWeatherRecord record) {
        if (record == null) {
            return List.of();
        }
        return List.of(
            defaultText(record.getTomorrowWeather(), ""),
            defaultText(record.getDay2Weather(), ""),
            defaultText(record.getDay3Weather(), ""),
            defaultText(record.getDay4Weather(), ""),
            defaultText(record.getDay5Weather(), ""),
            defaultText(record.getDay6Weather(), ""),
            defaultText(record.getDay7Weather(), ""),
            defaultText(record.getDay8Weather(), ""),
            defaultText(record.getDay9Weather(), ""),
            defaultText(record.getDay10Weather(), ""),
            defaultText(record.getDay11Weather(), ""),
            defaultText(record.getDay12Weather(), ""),
            defaultText(record.getDay13Weather(), ""),
            defaultText(record.getDay14Weather(), "")
        );
    }

    private boolean enabled() {
        return boolSetting(KEY_ENABLED, true);
    }

    private boolean boolSetting(String key, boolean defaultValue) {
        String value = settingsMap().get(key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    private boolean boolSetting(Map<String, String> settings, String key, boolean defaultValue) {
        String value = value(settings, key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    private Map<String, String> settingsMap() {
        ensureDefaultSettings();
        Map<String, String> map = new LinkedHashMap<>();
        for (ProcurementSetting setting : settingMapper.selectList(new LambdaQueryWrapper<ProcurementSetting>()
            .in(ProcurementSetting::getSettingKey, DEFAULT_SETTINGS.stream().map(DefaultSetting::key).toList()))) {
            map.put(setting.getSettingKey(), setting.getSettingValue());
        }
        for (DefaultSetting item : DEFAULT_SETTINGS) {
            map.putIfAbsent(item.key(), item.value());
        }
        return map;
    }

    private void ensureDefaultSettings() {
        for (DefaultSetting item : DEFAULT_SETTINGS) {
            ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
                .eq(ProcurementSetting::getSettingKey, item.key())
                .last("LIMIT 1"));
            if (setting == null) {
                setting = new ProcurementSetting();
                setting.setSettingKey(item.key());
                setting.setSettingValue(item.value());
                setting.setSettingGroup(item.group());
                setting.setSettingLabel(item.label());
                setting.setValueType(item.type());
                setting.setSortNo(item.sortNo());
                setting.setRemark(item.remark());
                settingMapper.insert(setting);
                continue;
            }
            boolean changed = false;
            if (!Objects.equals(setting.getSettingGroup(), item.group())) {
                setting.setSettingGroup(item.group());
                changed = true;
            }
            if (!Objects.equals(setting.getSettingLabel(), item.label())) {
                setting.setSettingLabel(item.label());
                changed = true;
            }
            if (!Objects.equals(setting.getValueType(), item.type())) {
                setting.setValueType(item.type());
                changed = true;
            }
            if (!Objects.equals(setting.getSortNo(), item.sortNo())) {
                setting.setSortNo(item.sortNo());
                changed = true;
            }
            if (!Objects.equals(setting.getRemark(), item.remark())) {
                setting.setRemark(item.remark());
                changed = true;
            }
            if (changed) {
                settingMapper.updateById(setting);
            }
        }
    }

    private void upsert(String key, String value) {
        if (value == null) {
            return;
        }
        DefaultSetting defaultSetting = defaultSetting(key);
        ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, key)
            .last("LIMIT 1"));
        if (setting == null) {
            setting = new ProcurementSetting();
            setting.setSettingKey(key);
        }
        setting.setSettingValue(value.trim());
        setting.setSettingGroup(defaultSetting.group());
        setting.setSettingLabel(defaultSetting.label());
        setting.setValueType(defaultSetting.type());
        setting.setSortNo(defaultSetting.sortNo());
        setting.setRemark(defaultSetting.remark());
        if (setting.getId() == null) {
            settingMapper.insert(setting);
        } else {
            settingMapper.updateById(setting);
        }
    }

    private DefaultSetting defaultSetting(String key) {
        return DEFAULT_SETTINGS.stream()
            .filter(item -> item.key().equals(key))
            .findFirst()
            .orElse(new DefaultSetting(key, "", "WEATHER", key, "TEXT", 999, ""));
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(StringUtils.hasText(body) ? body : "{}");
        } catch (JsonProcessingException ex) {
            throw new BizException("DISASTER_JSON_PARSE_FAILED", "天气接口响应解析失败");
        }
    }

    private String decodeHttpBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "{}";
        }
        if (body.length >= 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new BizException("DISASTER_QWEATHER_RESPONSE_DECODE_FAILED", "天气接口压缩响应解码失败");
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException("JSON_SERIALIZE_FAILED", "生成飞书卡片失败");
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> target = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    target.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return target;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return objectMapper.readValue(text, MAP_REF);
            } catch (JsonProcessingException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private double wetBulbCelsius(double tempC, double relativeHumidity, double pressureHpa) {
        if (Double.isNaN(tempC) || Double.isNaN(relativeHumidity) || relativeHumidity <= 0D) {
            return Double.NaN;
        }
        double actualVaporPressure = relativeHumidity / 100D * saturationVaporPressure(tempC);
        double low = Math.min(-40D, tempC);
        double high = tempC;
        for (int i = 0; i < 80; i++) {
            double mid = (low + high) / 2D;
            double estimated = saturationVaporPressure(mid)
                - 0.00066D * (1D + 0.00115D * mid) * pressureHpa * (tempC - mid);
            if (estimated > actualVaporPressure) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return (low + high) / 2D;
    }

    private double saturationVaporPressure(double tempC) {
        return 6.112D * Math.exp(17.67D * tempC / (tempC + 243.5D));
    }

    private double dewPointCelsius(double tempC, double relativeHumidity) {
        if (Double.isNaN(tempC) || Double.isNaN(relativeHumidity) || relativeHumidity <= 0D) {
            return Double.NaN;
        }
        double a = 17.625D;
        double b = 243.04D;
        double gamma = Math.log(relativeHumidity / 100D) + a * tempC / (b + tempC);
        return b * gamma / (a - gamma);
    }

    private BigDecimal scale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double doubleValue(JsonNode node) {
        try {
            String value = text(node);
            return StringUtils.hasText(value) ? Double.parseDouble(value) : Double.NaN;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    private double objectDoubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? Double.NaN : Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    private long numberValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private String normalizeRecordType(String value) {
        String type = normalizeCode(value);
        return List.of("DAILY", "WEEKLY", "WARNING").contains(type) ? type : "DAILY";
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String value(Map<String, String> settings, String key) {
        return settings == null ? "" : defaultText(settings.get(key), "");
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String markdownText(String value) {
        return defaultText(value, "-").replace("\n", " ");
    }

    private String sendStatusText(String status) {
        return switch (normalizeCode(status)) {
            case "SENT" -> "已发送";
            case "FAILED" -> "发送失败";
            case "SKIPPED" -> "已跳过";
            case "NOT_SENT" -> "未发送";
            default -> defaultText(status, "-");
        };
    }

    private String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= 6 ? "******" : value.substring(0, 3) + "******" + value.substring(value.length() - 3);
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizeQweatherHost(String value) {
        String host = defaultText(value, "https://devapi.qweather.com");
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "https://" + host;
        }
        try {
            URI uri = URI.create(host);
            if (StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost())) {
                StringBuilder builder = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
                if (uri.getPort() > 0) {
                    builder.append(":").append(uri.getPort());
                }
                return trimTrailingSlash(builder.toString());
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the original value so the weather request returns a clear configuration error.
        }
        return trimTrailingSlash(host);
    }

    private String normalizeNmcWarningHost(String value) {
        String host = defaultText(value, DEFAULT_NMC_WARNING_HOST);
        if (!host.contains("nmc.cn")) {
            host = DEFAULT_NMC_WARNING_HOST;
        }
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "https://" + host;
        }
        try {
            URI uri = URI.create(host);
            if (StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost())) {
                StringBuilder builder = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
                if (uri.getPort() > 0) {
                    builder.append(":").append(uri.getPort());
                }
                return trimTrailingSlash(builder.toString());
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to the official NMC host.
        }
        return DEFAULT_NMC_WARNING_HOST;
    }

    private String normalizeQweatherWarningApiMode(String value) {
        String mode = normalizeCode(defaultText(value, "AUTO"));
        return switch (mode) {
            case "NMC", "WEATHER_ALERT", "V7_WARNING" -> mode;
            default -> "AUTO";
        };
    }

    private WeatherAlertCoordinates weatherAlertCoordinates(String location) {
        String raw = defaultText(location, "");
        String[] parts = raw.split(",");
        if (parts.length < 2) {
            throw new BizException(
                "DISASTER_QWEATHER_WARNING_LOCATION_INVALID",
                "新版天气预警 API 需要中心经纬度，格式为：经度,纬度"
            );
        }
        String longitude = normalizeCoordinate(parts[0]);
        String latitude = normalizeCoordinate(parts[1]);
        if (!StringUtils.hasText(longitude) || !StringUtils.hasText(latitude)) {
            throw new BizException(
                "DISASTER_QWEATHER_WARNING_LOCATION_INVALID",
                "新版天气预警 API 需要中心经纬度，格式为：经度,纬度"
            );
        }
        return new WeatherAlertCoordinates(latitude, longitude);
    }

    private String normalizeCoordinate(String value) {
        String text = defaultText(value, "");
        try {
            return new BigDecimal(text).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private boolean containsAny(String source, String... needles) {
        if (!StringUtils.hasText(source) || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (StringUtils.hasText(needle) && source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "-";
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record DefaultSetting(String key, String value, String group, String label, String type, int sortNo, String remark) {
    }

    private record WeatherPayload(
        JsonNode now,
        String nowRaw,
        JsonNode daily,
        String dailyRaw,
        JsonNode warning,
        String warningRaw,
        String warningError
    ) {
    }

    private record WeatherAlertCoordinates(String latitude, String longitude) {
    }

    private record WarningItem(String title, String typeName, String severity, String detail, String matchText) {

        private String summary() {
            return List.of(title, typeAndLevel(), detail).stream().filter(StringUtils::hasText).findFirst().orElse("");
        }

        private String typeAndLevel() {
            return (StringUtils.hasText(typeName) ? typeName : "")
                + (StringUtils.hasText(severity) ? " " + severity : "");
        }
    }

    private record WarningRule(String warningType, String warningColor, String guardLevel, String cardColor, boolean enabled) {
    }

    private record WetBulbRule(Double min, Double max, boolean includeMin, boolean includeMax, String advice, boolean enabled) {

        private boolean matches(double value) {
            if (min != null) {
                int compare = Double.compare(value, min);
                if (includeMin ? compare < 0 : compare <= 0) {
                    return false;
                }
            }
            if (max != null) {
                int compare = Double.compare(value, max);
                if (includeMax ? compare > 0 : compare >= 0) {
                    return false;
                }
            }
            return true;
        }
    }

    private record WarningEvaluation(String summary, String typeAndLevel, String guardLevel, String cardColor) {

        private boolean present() {
            return StringUtils.hasText(summary) || StringUtils.hasText(guardLevel);
        }

        private static WarningEvaluation empty() {
            return new WarningEvaluation("", "", "", "");
        }
    }

    private record WarningResolveNotice(String summary, String typeAndLevel, String matchText) {

        private boolean present() {
            return StringUtils.hasText(summary) || StringUtils.hasText(matchText);
        }

        private static WarningResolveNotice empty() {
            return new WarningResolveNotice("", "", "");
        }
    }

    private record MatchedWarning(WarningItem warning, WarningRule rule) {

        private String warningColor() {
            return rule.warningColor();
        }

        private String summary() {
            String warningText = firstNonBlank(warning.summary(), rule.warningType() + rule.warningColor());
            return warningText + "（" + rule.guardLevel() + "）";
        }

        private String typeAndLevel() {
            return rule.warningType() + " " + rule.warningColor() + " / " + rule.guardLevel();
        }

        private String firstNonBlank(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
            return "";
        }
    }

    private record FeishuSendTarget(String chatId, String groupName, boolean test) {
    }
}
