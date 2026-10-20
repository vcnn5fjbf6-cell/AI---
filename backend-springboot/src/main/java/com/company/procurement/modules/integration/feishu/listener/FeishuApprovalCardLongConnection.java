package com.company.procurement.modules.integration.feishu.listener;

import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig.Snapshot;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.procurement.assistant.service.FeishuBotService;
import com.company.procurement.modules.supplier.service.SupplierFeishuNotificationService;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.CallBackAction;
import com.lark.oapi.event.cardcallback.model.CallBackCard;
import com.lark.oapi.event.cardcallback.model.CallBackContext;
import com.lark.oapi.event.cardcallback.model.CallBackOperator;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerData;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.P2MessageReadV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.UserId;
import com.lark.oapi.ws.Client;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuApprovalCardLongConnection {

    private final FeishuApprovalCardRuntimeConfig runtimeConfig;
    private final FeishuApprovalCardService approvalCardService;
    private final FeishuBotService feishuBotService;
    private final SupplierFeishuNotificationService supplierFeishuNotificationService;

    private Client client;
    private String activeSignature;
    private boolean missingConfigLogged;
    private boolean missingTokenLogged;

    @EventListener(ApplicationReadyEvent.class)
    public void startOnReady() {
        refreshConnection();
    }

    @Scheduled(fixedDelayString = "${feishu.approval-card.connection-refresh-delay-ms:30000}")
    public synchronized void refreshConnection() {
        Snapshot config = runtimeConfig.current();
        if (!config.enabled() || !config.longConnectionEnabled()) {
            closeClient();
            missingConfigLogged = false;
            missingTokenLogged = false;
            return;
        }
        if (!StringUtils.hasText(config.appId()) || !StringUtils.hasText(config.appSecret())) {
            if (!missingConfigLogged) {
                log.warn("Feishu approval card long connection is enabled but app id or app secret is missing");
                missingConfigLogged = true;
            }
            closeClient();
            missingTokenLogged = false;
            return;
        }
        if (!StringUtils.hasText(config.verificationToken())) {
            if (!missingTokenLogged) {
                log.warn("Feishu approval card long connection is enabled but verification token is missing");
                missingTokenLogged = true;
            }
        }
        String signature = config.longConnectionSignature();
        if (client != null && signature.equals(activeSignature)) {
            missingConfigLogged = false;
            return;
        }
        closeClient();
        EventDispatcher dispatcher = EventDispatcher.newBuilder(
                defaultText(config.verificationToken()),
                defaultText(config.encryptKey()))
            .onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                @Override
                public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
                    return handleActionTrigger(event);
                }
            })
            .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                @Override
                public void handle(P2MessageReceiveV1 event) {
                    handleBotMessageReceive(event);
                }
            })
            .onP2MessageReadV1(new ImService.P2MessageReadV1Handler() {
                @Override
                public void handle(P2MessageReadV1 event) {
                    // The app may subscribe to read receipts. They are irrelevant for procurement actions.
                }
            })
            .build();
        client = new Client.Builder(config.appId(), config.appSecret())
            .eventHandler(dispatcher)
            .autoReconnect(true)
            .build();
        client.start();
        activeSignature = signature;
        missingConfigLogged = false;
        missingTokenLogged = false;
        log.info("Feishu approval card long connection started");
    }

    @PreDestroy
    public synchronized void close() {
        closeClient();
    }

    private void closeClient() {
        if (client != null) {
            client.close();
            client = null;
            activeSignature = null;
        }
    }

    private P2CardActionTriggerResponse handleActionTrigger(P2CardActionTrigger trigger) {
        P2CardActionTriggerData event = trigger == null ? null : trigger.getEvent();
        CallBackAction action = event == null ? null : event.getAction();
        Map<String, Object> value = action == null || action.getValue() == null ? Map.of() : action.getValue();
        Map<String, Object> formValue = action == null || action.getFormValue() == null ? Map.of() : action.getFormValue();
        CallBackOperator operator = event == null ? null : event.getOperator();
        CallBackContext context = event == null ? null : event.getContext();
        String actionName = action == null ? "" : text(action.getName());
        String source = firstTextValue(value.get("source"));
        String actionCode = firstTextValue(value.get("action"), "submitRejectItem".equals(actionName) ? "REJECT_ITEM" : "");
        if (!StringUtils.hasText(actionCode)) {
            return new P2CardActionTriggerResponse();
        }
        if ("procurement-ai-bot".equals(source)) {
            FeishuApprovalCardActionResult result = feishuBotService.handleCardAction(
                value,
                formValue,
                operator == null ? null : operator.getOpenId(),
                operator == null ? null : operator.getUserId(),
                operator == null ? null : operator.getUnionId(),
                context == null ? null : context.getOpenMessageId()
            );
            return toCardActionResponse(result, "AI 助手处理完成");
        }
        if ("supplier-quote-notice".equals(source)) {
            FeishuApprovalCardActionResult result = supplierFeishuNotificationService.handleQuoteCardAction(
                value,
                formValue,
                operator == null ? null : operator.getOpenId(),
                operator == null ? null : operator.getUserId(),
                operator == null ? null : operator.getUnionId(),
                context == null ? null : context.getOpenMessageId()
            );
            return toCardActionResponse(result, "供应商报价处理完成");
        }
        Object rejectItemIdValue = firstAvailable(
            value.get("rejectItemId"),
            findNamedValue(formValue, "rejectItemId"));
        Object rejectItemIdsValue = firstAvailable(
            value.get("rejectItemIds"),
            findNamedValue(formValue, "rejectItemIds"));
        Object rejectReasonValue = firstAvailable(value.get("rejectReason"), findNamedValue(formValue, "rejectReason"));
        Object approvalCommentValue = firstAvailable(
            value.get("approvalComment"),
            findNamedValue(formValue, "approvalComment"));

        FeishuApprovalCardActionResult result = approvalCardService.handleCardAction(
            FeishuApprovalCardActionResult.ActionRequest.builder()
                .action(actionCode)
                .approvalTaskId(longValue(value.get("approvalTaskId")))
                .purchaseRequestId(longValue(value.get("purchaseRequestId")))
                .rejectItemId(longValue(rejectItemIdValue))
                .rejectItemIds(longValues(rejectItemIdsValue))
                .rejectReason(textValue(rejectReasonValue))
                .itemPage(intValue(value.get("itemPage")))
                .approvalComment(textValue(approvalCommentValue))
                .cardToken(text(value.get("cardToken")))
                .openId(operator == null ? null : operator.getOpenId())
                .userId(operator == null ? null : operator.getUserId())
                .unionId(operator == null ? null : operator.getUnionId())
                .messageId(context == null ? null : context.getOpenMessageId())
                .build()
        );

        return toCardActionResponse(result, "审批处理完成");
    }

    private P2CardActionTriggerResponse toCardActionResponse(FeishuApprovalCardActionResult result, String defaultMessage) {
        CallBackToast toast = new CallBackToast();
        toast.setType(defaultText(result.getToastType(), "warning"));
        toast.setContent(defaultText(result.getMessage(), defaultMessage));

        P2CardActionTriggerResponse response = new P2CardActionTriggerResponse();
        response.setToast(toast);
        if (result.getCardData() != null) {
            CallBackCard card = new CallBackCard();
            card.setType(defaultText(result.getCardType(), "raw"));
            card.setData(result.getCardData());
            response.setCard(card);
        }
        return response;
    }

    private void handleBotMessageReceive(P2MessageReceiveV1 receiveEvent) {
        try {
            feishuBotService.handleTrustedEvent(toBotPayload(receiveEvent));
        } catch (RuntimeException ex) {
            log.warn("Feishu bot message receive event failed", ex);
        }
    }

    private Map<String, Object> toBotPayload(P2MessageReceiveV1 receiveEvent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> header = new LinkedHashMap<>();
        Map<String, Object> event = new LinkedHashMap<>();
        Map<String, Object> sender = new LinkedHashMap<>();
        Map<String, Object> senderId = new LinkedHashMap<>();
        Map<String, Object> messageMap = new LinkedHashMap<>();

        header.put("event_type", "im.message.receive_v1");

        EventSender eventSender = receiveEvent == null || receiveEvent.getEvent() == null ? null : receiveEvent.getEvent().getSender();
        UserId userId = eventSender == null ? null : eventSender.getSenderId();
        senderId.put("open_id", userId == null ? null : userId.getOpenId());
        senderId.put("user_id", userId == null ? null : userId.getUserId());
        senderId.put("union_id", userId == null ? null : userId.getUnionId());
        sender.put("sender_id", senderId);
        sender.put("sender_type", eventSender == null ? null : eventSender.getSenderType());
        sender.put("tenant_key", eventSender == null ? null : eventSender.getTenantKey());

        EventMessage eventMessage = receiveEvent == null || receiveEvent.getEvent() == null ? null : receiveEvent.getEvent().getMessage();
        messageMap.put("message_id", eventMessage == null ? null : eventMessage.getMessageId());
        messageMap.put("message_type", eventMessage == null ? null : eventMessage.getMessageType());
        messageMap.put("chat_id", eventMessage == null ? null : eventMessage.getChatId());
        messageMap.put("chat_type", eventMessage == null ? null : eventMessage.getChatType());
        messageMap.put("content", eventMessage == null ? null : eventMessage.getContent());

        event.put("sender", sender);
        event.put("message", messageMap);
        payload.put("header", header);
        payload.put("event", event);
        return payload;
    }

    private Object findNamedValue(Object container, String name) {
        if (container instanceof Map<?, ?> map) {
            if (map.containsKey(name)) {
                return map.get(name);
            }
            for (Object item : map.values()) {
                Object found = findNamedValue(item, name);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (container instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Object found = findNamedValue(item, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Object firstAvailable(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Map<?, ?> map) {
            Long result = longValue(map.get("value"));
            if (result != null) {
                return result;
            }
            result = longValue(map.get("option"));
            if (result != null) {
                return result;
            }
            return longValue(map.get("text"));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Long result = longValue(item);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<Long> longValues(Object value) {
        List<Long> results = new ArrayList<>();
        collectLongValues(value, results);
        return results.stream().distinct().toList();
    }

    private void collectLongValues(Object value, List<Long> results) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            results.add(number.longValue());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            collectLongValues(map.get("value"), results);
            collectLongValues(map.get("values"), results);
            collectLongValues(map.get("option"), results);
            collectLongValues(map.get("options"), results);
            collectLongValues(map.get("selected_value"), results);
            collectLongValues(map.get("selected_values"), results);
            collectLongValues(map.get("selected_option"), results);
            collectLongValues(map.get("selected_options"), results);
            collectLongValues(map.get("text"), results);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectLongValues(item, results);
            }
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.contains(",")) {
            for (String item : text.split(",")) {
                collectLongValues(item, results);
            }
            return;
        }
        try {
            results.add(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
        }
    }

    private Long firstLongValue(Object... values) {
        for (Object value : values) {
            Long result = longValue(value);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        Long result = longValue(value);
        if (result == null) {
            return null;
        }
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return result.intValue();
    }

    private String firstTextValue(Object... values) {
        for (Object value : values) {
            String result = textValue(value);
            if (StringUtils.hasText(result)) {
                return result;
            }
        }
        return "";
    }

    private String textValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return firstTextValue(map.get("value"), map.get("text"), map.get("content"));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String result = textValue(item);
                if (StringUtils.hasText(result)) {
                    return result;
                }
            }
            return "";
        }
        return text(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultText(String value) {
        return defaultText(value, "");
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
