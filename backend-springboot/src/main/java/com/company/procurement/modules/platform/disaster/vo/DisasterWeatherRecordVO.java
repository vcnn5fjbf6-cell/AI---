package com.company.procurement.modules.platform.disaster.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DisasterWeatherRecordVO {

    private Long id;
    private String queryRecordId;
    private String recordType;
    private String pushCity;
    private String pushGroup;
    private String cityCode;
    private String todayWeatherInfo;
    private String warningInfo;
    private String warningTypeExtract;
    private String guardTag;
    private String guardAlertTag;
    private String wetBulbAdvice;
    private String guardSteps;
    private BigDecimal relativeHumidity;
    private BigDecimal saturationTemp;
    private BigDecimal dryBulbTemp;
    private BigDecimal wetBulbTemp;
    private String wetBulbInfo;
    private BigDecimal realTimePrecipitation;
    private Integer manualTrigger;
    private String sendStatus;
    private String messageId;
    private String lastError;
    private LocalDateTime lastPushTime;
    private LocalDateTime warningFetchTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> forecastDays;
}
