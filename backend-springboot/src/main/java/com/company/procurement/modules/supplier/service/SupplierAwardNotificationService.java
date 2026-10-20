package com.company.procurement.modules.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAwardNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProcurementSettingMapper settingMapper;
    private final SupplierMapper supplierMapper;

    public int supplierConfirmWorkdays() {
        return intSetting("supplier_award_confirm_workdays", 4, 1, 30);
    }

    public LocalDateTime supplierConfirmDueAt(LocalDateTime startAt) {
        return addBusinessDays(startAt == null ? LocalDateTime.now() : startAt, supplierConfirmWorkdays());
    }

    public LocalDateTime addBusinessDays(LocalDateTime startAt, int businessDays) {
        LocalDateTime result = startAt == null ? LocalDateTime.now() : startAt;
        int targetDays = Math.max(1, businessDays);
        int added = 0;
        while (added < targetDays) {
            result = result.plusDays(1);
            if (isBusinessDay(result.toLocalDate())) {
                added++;
            }
        }
        return result;
    }

    public boolean sendAwardConfirmationEmail(PurchaseRequest request, SupplierQuote quote, LocalDateTime dueAt) {
        Map<String, String> settings = settingMap();
        if (!booleanSetting(settings, "mail_enabled", false)) {
            return false;
        }
        String host = settings.get("mail_smtp_host");
        String from = settings.get("mail_smtp_from");
        if (!StringUtils.hasText(host) || !StringUtils.hasText(from)) {
            log.warn("Skip supplier award mail because SMTP host or sender is empty");
            return false;
        }
        Supplier supplier = quote == null ? null : supplierMapper.selectById(quote.getSupplierId());
        String recipient = supplier == null ? "" : supplier.getContactEmail();
        if (!StringUtils.hasText(recipient)) {
            log.warn("Skip supplier award mail because supplier contact email is empty, quoteId={}", quote == null ? null : quote.getId());
            return false;
        }

        try {
            JavaMailSenderImpl sender = buildMailSender(settings, host);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from.trim());
            message.setTo(recipient.trim());
            message.setSubject("采购中标确认通知 - " + safe(request == null ? null : request.getReqNo()));
            message.setText(buildMailText(request, supplier, dueAt));
            sender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send supplier award mail, quoteId={}, reason={}", quote == null ? null : quote.getId(), ex.getMessage());
            return false;
        }
    }

    private JavaMailSenderImpl buildMailSender(Map<String, String> settings, String host) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(intSetting(settings, "mail_smtp_port", 25, 1, 65535));
        if (StringUtils.hasText(settings.get("mail_smtp_username"))) {
            sender.setUsername(settings.get("mail_smtp_username").trim());
        }
        if (StringUtils.hasText(settings.get("mail_smtp_password"))) {
            sender.setPassword(settings.get("mail_smtp_password"));
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(sender.getUsername())));
        props.put("mail.smtp.starttls.enable", String.valueOf(booleanSetting(settings, "mail_smtp_starttls", false)));
        props.put("mail.smtp.ssl.enable", String.valueOf(booleanSetting(settings, "mail_smtp_ssl", false)));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private String buildMailText(PurchaseRequest request, Supplier supplier, LocalDateTime dueAt) {
        return """
            %s：

            您有一条采购申请已中标，请登录企业采购平台，在“工作台”或“采购管理 / 供应商报价”中确认中标。

            申请单号：%s
            申请标题：%s
            确认截止：%s（%d 个工作日，节假日不计入）

            超过截止时间未确认的，系统会自动视为放弃，并按中标规则递补下一名供应商。
            """.formatted(
            safe(supplier == null ? null : supplier.getSupplierName()),
            safe(request == null ? null : request.getReqNo()),
            safe(request == null ? null : request.getReqTitle()),
            dueAt == null ? "-" : dueAt.format(DATE_TIME_FORMATTER),
            supplierConfirmWorkdays()
        );
    }

    private boolean isBusinessDay(LocalDate date) {
        Set<LocalDate> extraWorkdays = dateSetSetting("supplier_confirm_extra_workdays");
        if (extraWorkdays.contains(date)) {
            return true;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        return !dateSetSetting("supplier_confirm_holidays").contains(date);
    }

    private Set<LocalDate> dateSetSetting(String key) {
        String value = settingMap().get(key);
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split("[,;，；\\s]+"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(this::parseDate)
            .filter(date -> date != null)
            .collect(Collectors.toSet());
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int intSetting(String key, int defaultValue, int min, int max) {
        return intSetting(settingMap(), key, defaultValue, min, max);
    }

    private int intSetting(Map<String, String> settings, String key, int defaultValue, int min, int max) {
        try {
            int value = Integer.parseInt(safe(settings.get(key)).trim());
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean booleanSetting(Map<String, String> settings, String key, boolean defaultValue) {
        String value = settings.get(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim());
    }

    private Map<String, String> settingMap() {
        return settingMapper.selectList(new LambdaQueryWrapper<ProcurementSetting>()
                .eq(ProcurementSetting::getIsDeleted, 0))
            .stream()
            .collect(Collectors.toMap(
                ProcurementSetting::getSettingKey,
                setting -> safe(setting.getSettingValue()),
                (left, right) -> left
            ));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
