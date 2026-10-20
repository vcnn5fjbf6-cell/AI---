import type { PageQuery } from "./common";

export interface DisasterWeatherSettingsVO {
  enabled: string;
  qweatherKeyMasked: string;
  qweatherHost: string;
  qweatherWarningApiMode: string;
  qweatherWarningHost: string;
  qweatherWarningTokenMasked: string;
  cityCode: string;
  location: string;
  centerAddress: string;
  pushCity: string;
  pushGroup: string;
  dutyChatId: string;
  testChatId: string;
  dailyPushEnabled: string;
  weeklyPushEnabled: string;
  warningPushEnabled: string;
  warningResolvePushEnabled: string;
  newDeliveryPromotionEnabled: string;
  rainySeasonEnabled: string;
  rainySeasonMonths: string;
  warningRulesJson: string;
  wetBulbRulesJson: string;
  wetBulbNote: string;
}

export interface DisasterWeatherSettingsForm {
  enabled: string;
  qweatherKey?: string;
  qweatherHost: string;
  qweatherWarningApiMode: string;
  qweatherWarningHost: string;
  qweatherWarningToken?: string;
  cityCode: string;
  location: string;
  centerAddress: string;
  pushCity: string;
  pushGroup: string;
  dutyChatId: string;
  testChatId: string;
  dailyPushEnabled: string;
  weeklyPushEnabled: string;
  warningPushEnabled: string;
  warningResolvePushEnabled: string;
  newDeliveryPromotionEnabled: string;
  rainySeasonEnabled: string;
  rainySeasonMonths: string;
  warningRulesJson?: string;
  wetBulbRulesJson?: string;
  wetBulbNote?: string;
}

export interface DisasterWarningRule {
  type: string;
  color: string;
  guard: string;
  card: string;
  enabled: boolean;
}

export interface DisasterWetBulbRule {
  min: number | null;
  max: number | null;
  includeMin: boolean;
  includeMax: boolean;
  advice: string;
  enabled: boolean;
}

export interface DisasterWeatherRecordVO {
  id: number;
  queryRecordId: string;
  recordType: string;
  pushCity: string;
  pushGroup: string;
  cityCode: string;
  todayWeatherInfo: string;
  warningInfo: string;
  warningTypeExtract: string;
  guardTag: string;
  guardAlertTag: string;
  wetBulbAdvice: string;
  guardSteps: string;
  relativeHumidity: number | null;
  saturationTemp: number | null;
  dryBulbTemp: number | null;
  wetBulbTemp: number | null;
  wetBulbInfo: string;
  realTimePrecipitation: number | null;
  manualTrigger: number;
  sendStatus: string;
  messageId: string;
  lastError: string;
  lastPushTime: string | null;
  warningFetchTime: string | null;
  createdAt: string;
  updatedAt: string;
  forecastDays: string[];
}

export interface DisasterWeatherOverviewVO {
  settings: DisasterWeatherSettingsVO;
  latestRecord: DisasterWeatherRecordVO | null;
}

export interface DisasterWeatherRecordQuery extends PageQuery {
  recordType?: string;
  sendStatus?: string;
  guardLevel?: string;
  keyword?: string;
}

export interface DisasterWeatherTriggerForm {
  recordType: "DAILY" | "WEEKLY" | "WARNING";
  sendToFeishu: boolean;
  testCardColor?: "red" | "orange" | "yellow" | "blue" | "green";
}
