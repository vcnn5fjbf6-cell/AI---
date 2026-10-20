package com.company.procurement.modules.platform.disaster.dto;

import lombok.Data;

@Data
public class DisasterWeatherSettingsDTO {

    private String enabled;

    private String qweatherKey;

    private String qweatherHost;

    private String qweatherWarningApiMode;

    private String qweatherWarningHost;

    private String qweatherWarningToken;

    private String cityCode;

    private String location;

    private String centerAddress;

    private String pushCity;

    private String pushGroup;

    private String dutyChatId;

    private String testChatId;

    private String dailyPushEnabled;

    private String weeklyPushEnabled;

    private String warningPushEnabled;

    private String warningResolvePushEnabled;

    private String newDeliveryPromotionEnabled;

    private String rainySeasonEnabled;

    private String rainySeasonMonths;

    private String warningRulesJson;

    private String wetBulbRulesJson;

    private String wetBulbNote;
}
