package com.company.procurement.modules.platform.disaster.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("disaster_weather_record")
public class DisasterWeatherRecord extends BaseEntity {

    @TableField("query_record_id")
    private String queryRecordId;

    @TableField("record_type")
    private String recordType;

    @TableField("push_city")
    private String pushCity;

    @TableField("last_push_time")
    private LocalDateTime lastPushTime;

    @TableField("warning_fetch_time")
    private LocalDateTime warningFetchTime;

    @TableField("push_group")
    private String pushGroup;

    @TableField("important_event_group")
    private String importantEventGroup;

    @TableField("room_manager")
    private String roomManager;

    @TableField("push_description")
    private String pushDescription;

    @TableField("pushed")
    private Integer pushed;

    @TableField("guard_level3_accepted")
    private Integer guardLevel3Accepted;

    @TableField("guard_level2_accepted")
    private Integer guardLevel2Accepted;

    @TableField("guard_level1_accepted")
    private Integer guardLevel1Accepted;

    @TableField("city_code")
    private String cityCode;

    @TableField("created_person")
    private String createdPerson;

    @TableField("weather_raw_json")
    private String weatherRawJson;

    @TableField("today_weather_json")
    private String todayWeatherJson;

    @TableField("today_weather_info")
    private String todayWeatherInfo;

    @TableField("warning_raw_json")
    private String warningRawJson;

    @TableField("warning_info")
    private String warningInfo;

    @TableField("warning_type_extract")
    private String warningTypeExtract;

    @TableField("guard_tag")
    private String guardTag;

    @TableField("guard_alert_tag")
    private String guardAlertTag;

    @TableField("wet_bulb_advice")
    private String wetBulbAdvice;

    @TableField("guard_steps")
    private String guardSteps;

    @TableField("relative_humidity")
    private BigDecimal relativeHumidity;

    @TableField("saturation_temp")
    private BigDecimal saturationTemp;

    @TableField("dry_bulb_temp")
    private BigDecimal dryBulbTemp;

    @TableField("wet_bulb_temp")
    private BigDecimal wetBulbTemp;

    @TableField("wet_bulb_info")
    private String wetBulbInfo;

    @TableField("tomorrow_weather")
    private String tomorrowWeather;

    @TableField("day2_weather")
    private String day2Weather;

    @TableField("day3_weather")
    private String day3Weather;

    @TableField("day4_weather")
    private String day4Weather;

    @TableField("day5_weather")
    private String day5Weather;

    @TableField("day6_weather")
    private String day6Weather;

    @TableField("day7_weather")
    private String day7Weather;

    @TableField("day8_weather")
    private String day8Weather;

    @TableField("day9_weather")
    private String day9Weather;

    @TableField("day10_weather")
    private String day10Weather;

    @TableField("day11_weather")
    private String day11Weather;

    @TableField("day12_weather")
    private String day12Weather;

    @TableField("day13_weather")
    private String day13Weather;

    @TableField("day14_weather")
    private String day14Weather;

    @TableField("real_time_precipitation")
    private BigDecimal realTimePrecipitation;

    @TableField("manual_trigger")
    private Integer manualTrigger;

    @TableField("send_status")
    private String sendStatus;

    @TableField("message_id")
    private String messageId;

    @TableField("last_error")
    private String lastError;
}
