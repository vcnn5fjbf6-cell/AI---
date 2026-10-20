package com.company.procurement.modules.procurement.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.result.Result;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.FeishuMessageIntentResult;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiAnalysisResult;
import com.company.procurement.modules.procurement.assistant.client.DifyProcurementClient.ProcurementAiCandidateDraft;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.CandidateCommand;
import com.company.procurement.modules.procurement.assistant.client.ProcurementAiAnalyzer;
import com.company.procurement.modules.procurement.assistant.config.FeishuBotRuntimeConfig;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.company.procurement.modules.procurement.assistant.service.FeishuBotService;
import com.company.procurement.modules.procurement.assistant.service.ProcurementAiImageStoreService;
import com.company.procurement.modules.procurement.assistant.service.ProcurementAiService;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiCandidateVO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiConfirmResultVO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiSessionVO;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestItemVO;
import com.company.procurement.modules.supplier.service.SupplierFeishuNotificationService;
import com.company.procurement.modules.platform.aimessagelog.entity.ProcurementAiMessageLog;
import com.company.procurement.modules.platform.aimessagelog.service.ProcurementAiMessageLogService;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuBotServiceImpl implements FeishuBotService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final int FEISHU_ID_LOOKUP_BATCH_SIZE = 50;
    private static final int FEISHU_ID_LOOKUP_MAX_USERS = 1000;
    private static final String BOT_CARD_SOURCE = "procurement-ai-bot";
    private static final String BOT_CARD_VERSION = "v20260707.1";
    private static final String CARD_JSON_SCHEMA_VERSION = "2.0";
    private static final int BOT_CARD_DETAIL_ITEM_LIMIT = 10;
    private static final int FEISHU_EXCEL_MAX_ROWS_TO_AI = 120;
    private static final int FEISHU_EXCEL_MAX_COLUMNS_TO_AI = 12;
    private static final int FEISHU_EXCEL_TEXT_LIMIT = 12000;
    private static final int M3_DIRECT_IMAGE_MAX_BYTES = 6 * 1024 * 1024;
    private static final long PENDING_CANDIDATE_COMMAND_TTL_MS = 10 * 60 * 1000L;
    private static final String ACTION_PREVIEW_WAITING = "PREVIEW_WAITING";
    private static final String ACTION_CONFIRM_ALL = "CONFIRM_ALL";
    private static final String ACTION_CONFIRM_ONE = "CONFIRM_ONE";
    private static final String ACTION_CANCEL_ALL = "CANCEL_ALL";
    private static final String ACTION_CANCEL_ONE = "CANCEL_ONE";
    private static final String ACTION_TOGGLE_CANCEL_ITEM = "TOGGLE_CANCEL_ITEM";
    private static final String ACTION_ITEM_PAGE = "ITEM_PAGE";
    private static final Pattern CONFIRM_PATTERN = Pattern.compile("^\\s*确认(\\s+.*)?$");
    private static final Pattern UPDATE_PURPOSE_PATTERN = Pattern.compile(
        "^\\s*(?:把|将)?(?:第\\s*)?(\\d+)?\\s*(?:项|条|个)?(?:的)?\\s*用途\\s*(?:改成|改为|修改为|设为|是|为|：|:)?\\s*(.+?)\\s*$");
    private static final Pattern CANCEL_ITEM_PATTERN = Pattern.compile(
        "^\\s*(?:删除|取消|移除)\\s*(?:第\\s*)?(\\d+)\\s*(?:项|条|个)?.*$");
    private static final Pattern CANDIDATE_SELECTION_ONLY_PATTERN = Pattern.compile(
        "^\\s*(?:选|选择|就)?\\s*(?:第\\s*)?(\\d+)\\s*(?:项|条|号|个)?\\s*(?:吧|了|就行|可以|这个|那个)?\\s*$");
    private static final Pattern PROCUREMENT_FAST_PATH_PATTERN = Pattern.compile(
        "(采购|申购|购买|买|需要|我要|申请|补充|更换|下单|订购|用于)");
    private static final Pattern PRICE_FAST_PATH_PATTERN = Pattern.compile(
        "(?:(?:¥|￥)\\s*\\d+(?:\\.\\d+)?|(?:单价|价格|预算|参考价|报价)\\s*[:：]?\\s*(?:¥|￥)?\\s*\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?\\s*(?:元|块钱|rmb|RMB|cny|CNY))");
    private static final Pattern QUANTITY_FAST_PATH_PATTERN = Pattern.compile(
        "\\d+(?:\\.\\d+)?\\s*(?:个|件|台|套|支|条|箱|包|双|把|卷|瓶|盒|块|张|根|只|米|本|辆|片|副|枚)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\u3000<>\"'\\]\\[)）]+", Pattern.CASE_INSENSITIVE);
    private static final String FEISHU_EXCEL_PROMPT_PREFIX = "飞书Excel附件内容：";
    private static final Set<String> MESSAGE_TEXT_KEYS = Set.of(
        "text", "title", "name", "url", "href", "link", "preview_url", "description", "desc", "content",
        "un_escape_text", "summary"
    );

    private final FeishuBotRuntimeConfig botRuntimeConfig;
    private final DifyProcurementClient difyProcurementClient;
    private final ProcurementAiAnalyzer procurementAiAnalyzer;
    private final ProcurementAiService procurementAiService;
    private final ProcurementAiImageStoreService imageStoreService;
    private final ProcurementAiMessageLogService messageLogService;
    private final ImportFileLogService importFileLogService;
    private final SupplierFeishuNotificationService supplierFeishuNotificationService;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final RestClient restClient = RestClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedTenantAccessToken;
    private volatile long cachedTenantAccessTokenExpiresAt;
    private volatile String cachedTenantAccessTokenSignature;
    private final Map<String, PendingCandidateCommand> pendingCandidateCommands = new ConcurrentHashMap<>();

    private boolean isBotEnabled() {
        return botRuntimeConfig.current().enabled();
    }

    @Override
    public Object handleEvent(Map<String, Object> payload) {
        String challenge = text(payload.get("challenge"));
        if (StringUtils.hasText(challenge)) {
            return Map.of("challenge", challenge);
        }
        if (!isBotEnabled()) {
            return Result.fail("FEISHU_BOT_DISABLED", "Feishu bot is not enabled");
        }
        verifyToken(payload);
        return handleTrustedEvent(payload);
    }

    @Override
    public Object handleTrustedEvent(Map<String, Object> payload) {
        long startedAt = System.currentTimeMillis();
        BotMessage message = parseMessage(payload);
        if (isSelfOrBotMessage(message)) {
            return Result.ok();
        }
        if (isDuplicateMessage(message)) {
            return Result.ok();
        }
        Long messageLogId = safeRecordIncoming(payload, message);
        String intentCode = "UNKNOWN";
        String intentName = "\u672a\u8bc6\u522b";
        if (!isBotEnabled()) {
            safeFinishIgnored(messageLogId, "BOT_DISABLED", "\u673a\u5668\u4eba\u672a\u542f\u7528", "Feishu bot is not enabled", "", startedAt);
            return Result.ok();
        }
        if (!StringUtils.hasText(message.sourceUserKey())) {
            safeFinishIgnored(messageLogId, "SOURCE_USER_MISSING", "\u7f3a\u5c11\u6765\u6e90\u7528\u6237", "\u98de\u4e66\u4e8b\u4ef6\u7f3a\u5c11\u7528\u6237\u6807\u8bc6", "", startedAt);
            return Result.ok();
        }
        boolean supplierBindIntent = supplierFeishuNotificationService.isSupplierBindIntentMessage(message.text());
        if (supplierFeishuNotificationService.isSupplierBindMessage(message.text())) {
            try {
                String replyText = supplierFeishuNotificationService.bindSupplierFromMessage(
                    message.text(),
                    message.openId(),
                    message.userId(),
                    message.unionId(),
                    message.chatId(),
                    message.chatType(),
                    message.tenantKey(),
                    message.sourceUserKey(),
                    message.messageId()
                );
                sendTextToMessageTarget(message, replyText);
                safeFinishSuccess(messageLogId, "SUPPLIER_FEISHU_BIND", "供应商飞书绑定", replyText, replyText, null, startedAt);
                return Result.ok();
            } catch (BizException ex) {
                sendTextToMessageTarget(message, ex.getMessage());
                safeFinishFailed(messageLogId, "SUPPLIER_FEISHU_BIND", "供应商飞书绑定", ex.getCode(), ex.getMessage(), ex.getMessage(), startedAt);
                return Result.fail(ex.getCode(), ex.getMessage());
            }
        }
        try {
            String supplierQuoteReply = supplierFeishuNotificationService.tryImportSupplierQuoteFile(
                message.messageType(),
                message.imageKey(),
                message.fileName(),
                message.openId(),
                message.userId(),
                message.unionId(),
                message.chatId(),
                message.chatType(),
                message.tenantKey(),
                message.sourceUserKey(),
                message.messageId(),
                messageLogId
            );
            if (StringUtils.hasText(supplierQuoteReply)) {
                sendTextToMessageTarget(message, supplierQuoteReply);
                safeFinishSuccess(messageLogId, "SUPPLIER_QUOTE_FILE_IMPORT", "供应商报价附件导入",
                    supplierQuoteReply, supplierQuoteReply, null, startedAt);
                return Result.ok();
            }
        } catch (BizException ex) {
            sendTextToMessageTarget(message, ex.getMessage());
            safeFinishFailed(messageLogId, "SUPPLIER_QUOTE_FILE_IMPORT", "供应商报价附件导入",
                ex.getCode(), ex.getMessage(), ex.getMessage(), startedAt);
            return Result.fail(ex.getCode(), ex.getMessage());
        }

        AuthUser user = resolveBotUser(payload);
        if (user == null) {
            intentCode = supplierBindIntent ? "SUPPLIER_BIND_GUIDE" : "USER_UNBOUND";
            intentName = supplierBindIntent ? "供应商绑定引导" : "\u8d26\u53f7\u672a\u7ed1\u5b9a";
            String replyText = supplierBindIntent
                ? supplierFeishuNotificationService.supplierBindGuideMessage()
                : unboundUserGuideMessage();
            sendTextToMessageTarget(message, replyText);
            safeFinishIgnored(messageLogId, intentCode, intentName, "\u98de\u4e66\u8d26\u53f7\u672a\u7ed1\u5b9a\u7cfb\u7edf\u7528\u6237", replyText, startedAt);
            return Result.ok();
        }
        safeAttachUser(messageLogId, user);

        try {
            String messageText = firstText(message.text(), "");
            List<ProcurementAiSessionVO> waitingCommandSessions = procurementAiService.waitingSessions(message.sourceUserKey());
            if (waitingCommandSessions != null && !waitingCommandSessions.isEmpty()) {
                PendingCandidateCommand pendingConfirmation = pendingCandidateConfirmation(message.sourceUserKey());
                if (pendingConfirmation != null) {
                    Result<?> handledConfirmation = handlePendingCandidateConfirmation(pendingConfirmation, message,
                        user, messageLogId, startedAt, waitingCommandSessions);
                    if (handledConfirmation != null) {
                        return handledConfirmation;
                    }
                }
            }
            if (isSubmitConfirmMessage(messageText)) {
                if (waitingCommandSessions == null || waitingCommandSessions.isEmpty()) {
                    throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "没有待确认的采购候选");
                }
                return askCandidateCommandConfirmation("CONFIRM_ALL", null, message, messageLogId, startedAt,
                    null, waitingCommandSessions);
            }
            if (isSubmitPreviewMessage(messageText)) {
                intentCode = "PREVIEW_WAITING";
                intentName = "\u67e5\u770b\u5f85\u63d0\u4ea4\u5019\u9009";
                List<ProcurementAiSessionVO> sessions = procurementAiService.waitingSessions(message.sourceUserKey());
                String replyText = buildWaitingSummary(sessions);
                sendCardOrText(message.openId(), buildWaitingCard(sessions, message.sourceUserKey()), replyText);
                safeFinishSuccess(messageLogId, intentCode, intentName, replyText, "\u5f85\u63d0\u4ea4\u5019\u9009 " + (sessions == null ? 0 : sessions.size()) + " \u9879",
                    firstSession(sessions), null, startedAt);
                return Result.ok(sessions);
            }
            if (waitingCommandSessions != null && !waitingCommandSessions.isEmpty()) {
                Integer selectedItemNo = candidateSelectionNo(messageText);
                if (selectedItemNo != null) {
                    PendingCandidateCommand pendingCommand = takePendingCandidateCommand(message.sourceUserKey());
                    if (pendingCommand != null) {
                        Result<?> handledPendingCommand = handlePendingCandidateSelection(pendingCommand, selectedItemNo,
                            message, user, messageLogId, startedAt, waitingCommandSessions);
                        if (handledPendingCommand != null) {
                            return handledPendingCommand;
                        }
                    }
                    rememberPendingCandidateSelection(message.sourceUserKey(), selectedItemNo);
                    String replyText = "已收到第 " + selectedItemNo + " 项。请说明要执行的操作，例如：撤回第"
                        + selectedItemNo + "项、修改第" + selectedItemNo + "项数量，或确认第" + selectedItemNo + "项。";
                    sendText(message.openId(), replyText);
                    safeFinishSuccess(messageLogId, "CANDIDATE_SELECTION_CLARIFY", "候选序号待补充动作", replyText,
                        replyText, firstSession(waitingCommandSessions), selectedItemNo, startedAt);
                    return Result.ok();
                }
            }
            if (waitingCommandSessions != null && !waitingCommandSessions.isEmpty()
                && procurementAiAnalyzer.canCompleteTextWithM3()) {
                CandidateCommand command = interpretWaitingCandidateCommand(messageText, waitingCommandSessions, message.sourceUserKey());
                Result<?> handledCommand = handleWaitingCandidateCommand(command, message, user, messageLogId, startedAt,
                    waitingCommandSessions);
                if (handledCommand != null) {
                    return handledCommand;
                }
            }
            Matcher purposeMatcher = UPDATE_PURPOSE_PATTERN.matcher(messageText);
            if (purposeMatcher.matches()) {
                intentCode = "UPDATE_PURPOSE";
                intentName = "\u4fee\u6539\u7528\u9014";
                Integer itemNo = parseInt(purposeMatcher.group(1), null);
                String reason = firstText(purposeMatcher.group(2), "");
                ProcurementAiSessionVO updated = procurementAiService.updateWaitingCandidateReason(
                    message.sourceUserKey(), itemNo, reason, user);
                String replyText = "\u5df2\u4fee\u6539\u7528\u9014\uff1a\n" + buildSingleWaitingLine(itemNo == null ? 1 : itemNo, updated);
                sendText(message.openId(), replyText);
                safeFinishSuccess(messageLogId, intentCode, intentName, replyText, "\u4fee\u6539\u7528\u9014\uff1a" + reason, updated,
                    itemNo == null ? 1 : itemNo, startedAt);
                return Result.ok(updated);
            }
            Matcher cancelMatcher = CANCEL_ITEM_PATTERN.matcher(messageText);
            if (cancelMatcher.matches()) {
                Integer itemNo = parseInt(cancelMatcher.group(1), null);
                if (waitingCommandSessions == null || waitingCommandSessions.isEmpty()) {
                    throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "没有待确认的采购候选");
                }
                CandidateCommand command = new CandidateCommand("CANCEL_ITEM", itemNo, null, null, null,
                    "", null, "", "", "");
                return askCandidateCommandConfirmation("CANCEL_ITEM", command, message, messageLogId, startedAt,
                    itemNo, waitingCommandSessions);
            }
            if (isConfirmMessage(message.text())) {
                intentCode = "CONFIRM_ONE";
                intentName = "\u786e\u8ba4\u5355\u6761\u5019\u9009";
                ProcurementAiConfirmResultVO result = confirmFromMessage(message, user);
                String replyText = "\u5df2\u786e\u8ba4\u5019\u9009 " + result.getCandidateNo() + "\uff0c"
                    + requestRouteText(result.getRequest()) + "\uff0c"
                    + firstText(result.getPurchaseRequestNo(), result.getSessionNo());
                sendCardOrText(message.openId(), buildSubmittedCard(List.of(result)), replyText);
                safeFinishSuccess(messageLogId, intentCode, intentName, replyText, replyText, result, startedAt);
                return Result.ok(result);
            }

            ProcurementAiStartDTO dto = buildStartDTO(message, messageLogId, user);
            safeUpdateRawText(messageLogId, dto.getRawText());
            ProcurementAiSessionVO draft = procurementAiService.latestDraftSession(message.sourceUserKey(), user);
            if (isImageWithoutUserDemand(message)) {
                intentCode = "CLARIFY_DRAFT";
                intentName = "图片采购信息待补充";
                ImageOnlyDraft imageDraft = prepareImageOnlyDraft(dto, message.sourceUserKey());
                safeUpdateRawText(messageLogId, imageDraft.dto().getRawText());
                String replyText = imageDemandClarificationReply(imageDraft.analysis());
                sendText(message.openId(), replyText);
                ProcurementAiSessionVO draftSession = procurementAiService.saveDraft(imageDraft.dto(), user, "FEISHU_BOT",
                    message.sourceUserKey(), message.messageId(), replyText,
                    imageDraft.difyRequestJson(), imageDraft.difyResponseText(), imageDraft.difyParsedJson());
                safeFinishSuccess(messageLogId, intentCode, intentName, replyText,
                    imageOnlyDraftSummary(imageDraft.analysis()),
                    draftSession, null, startedAt);
                return Result.ok(draftSession);
            }
            ProcurementAiStartDTO intentDto = draft == null ? dto : mergeDraftInput(dto, draft);
            boolean skipIntentClassification = shouldSkipIntentClassification(message, intentDto);
            FeishuMessageIntentResult messageIntent = skipIntentClassification
                ? null
                : difyProcurementClient.classifyFeishuMessage(intentDto, message.sourceUserKey());
            boolean useM3ImageFallback = !skipIntentClassification && shouldUseM3ImageFallback(message, intentDto, messageIntent);
            if (!skipIntentClassification && !messageIntent.isProcurementDemand() && !useM3ImageFallback) {
                intentCode = firstText(messageIntent.getIntent(), "CHAT");
                intentName = firstText(messageIntent.getIntentName(), "闲聊");
                String replyText = messageIntent.isNeedClarification()
                    ? clarificationReply(messageIntent)
                    : firstText(messageIntent.getReply(), buildHelpReply());
                sendText(message.openId(), replyText);
                if (messageIntent.isNeedClarification()) {
                    ProcurementAiSessionVO draftSession = procurementAiService.saveDraft(dto, user, "FEISHU_BOT",
                        message.sourceUserKey(), message.messageId(), replyText,
                        messageIntent.getDifyRequestJson(), messageIntent.getDifyResponseText(), messageIntent.getDifyParsedJson());
                    safeFinishSuccess(messageLogId, "CLARIFY_DRAFT", "补充采购信息", replyText,
                        firstText(messageIntent.getClarificationQuestion(), messageIntent.getReply(), "采购信息待补充"),
                        draftSession, null, startedAt);
                    safeUpdateDifyDebug(messageLogId, messageIntent, null);
                    return Result.ok(draftSession);
                }
                safeFinishIgnored(messageLogId, intentCode, intentName,
                    firstText(messageIntent.getAiError(), "Dify 判断为非采购需求消息"), replyText, startedAt);
                safeUpdateDifyDebug(messageLogId, messageIntent, null);
                return Result.ok();
            }

            intentCode = "CREATE_CANDIDATE";
            intentName = "\u521b\u5efa\u91c7\u8d2d\u5019\u9009";
            ProcurementAiStartDTO sessionDto = draft == null ? dto : mergeDraftInput(dto, draft);
            ProcurementAiAnalysisResult imageDraftAnalysis = null;
            if (draft != null && ProcurementAiAnalyzer.isImageRecognitionDraft(sessionDto)) {
                imageDraftAnalysis = procurementAiAnalyzer.analyze(sessionDto, message.sourceUserKey());
                if (imageDraftAnalysis.getCandidates() == null || imageDraftAnalysis.getCandidates().isEmpty()) {
                    String replyText = firstText(imageDraftAnalysis.getAiSummary(), "请补充本次采购的数量和用途。");
                    sendText(message.openId(), replyText);
                    ProcurementAiSessionVO draftSession = procurementAiService.saveDraft(dto, user, "FEISHU_BOT",
                        message.sourceUserKey(), message.messageId(), replyText,
                        imageDraftAnalysis.getDifyRequestJson(), imageDraftAnalysis.getDifyResponseText(), imageDraftAnalysis.getDifyParsedJson());
                    safeFinishSuccess(messageLogId, "CLARIFY_DRAFT", "图片采购信息待补充", replyText,
                        replyText, draftSession, null, startedAt);
                    return Result.ok(draftSession);
                }
            }
            ProcurementAiSessionVO session = imageDraftAnalysis == null
                ? procurementAiService.startSession(sessionDto, user, "FEISHU_BOT", message.sourceUserKey(), message.messageId())
                : procurementAiService.startSession(sessionDto, imageDraftAnalysis, user, "FEISHU_BOT", message.sourceUserKey(), message.messageId());
            if (draft != null) {
                procurementAiService.markDraftConverted(draft.getId(), session.getId());
            }
            String replyText = buildCandidateReply(session);
            sendCardOrText(message.openId(), buildCandidateCard(session, message.sourceUserKey()), replyText);
            safeFinishSuccess(messageLogId, intentCode, intentName, replyText,
                firstText(session.getAiSummary(), session.getAiError(), session.getReqTitle()), session, 1, startedAt);
            safeUpdateDifyDebug(messageLogId, messageIntent, session);
            return Result.ok(session);
        } catch (BizException ex) {
            String replyText = ex.getMessage();
            sendText(message.openId(), replyText);
            safeFinishFailed(messageLogId, intentCode, intentName, ex.getCode(), ex.getMessage(), replyText, startedAt);
            return Result.fail(ex.getCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Feishu bot event failed", ex);
            String replyText = "\u91c7\u8d2d\u9700\u6c42\u5904\u7406\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u6216\u8054\u7cfb\u7ba1\u7406\u5458\u3002";
            sendText(message.openId(), replyText);
            safeFinishFailed(messageLogId, intentCode, intentName, "FEISHU_BOT_EVENT_FAILED", firstText(ex.getMessage(), ex.getClass().getName()), replyText, startedAt);
            return Result.fail("FEISHU_BOT_EVENT_FAILED", "Feishu bot event failed");
        }
    }

    @Override
    public AuthUser resolveBotUser(Map<String, Object> payload) {
        BotMessage message = parseMessage(payload);
        SysUser user = findBoundUser(message);
        if (user == null) {
            return null;
        }
        return toAuthUser(user);
    }

    private AuthUser toAuthUser(SysUser user) {
        return AuthUser.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .userType(user.getUserType())
            .regionId(user.getRegionId())
            .deptCode(user.getDeptCode())
            .deptName(user.getDeptName())
            .supplierId(user.getSupplierId())
            .dataScopeType(user.getDataScopeType())
            .roleCodes(userRoleMapper.selectRoleCodesByUserId(user.getId()))
            .build();
    }

    @Override
    public FeishuApprovalCardActionResult handleCardAction(Map<String, Object> value, Map<String, Object> formValue,
                                                           String openId, String userId, String unionId, String messageId) {
        if (!isBotEnabled()) {
            return FeishuApprovalCardActionResult.warning("飞书采购助手未启用");
        }
        if (!BOT_CARD_SOURCE.equals(text(value.get("source")))) {
            return FeishuApprovalCardActionResult.warning("暂不支持该卡片动作");
        }
        String action = text(value.get("action"));
        String valueSourceUserKey = text(value.get("sourceUserKey"));
        String operatorSourceUserKey = firstText(openId, userId, unionId);
        if (StringUtils.hasText(valueSourceUserKey) && !operatorMatches(valueSourceUserKey, openId, userId, unionId)) {
            return FeishuApprovalCardActionResult.warning("只能处理自己的采购候选");
        }
        String sourceUserKey = firstText(valueSourceUserKey, operatorSourceUserKey);
        if (!StringUtils.hasText(sourceUserKey)) {
            return FeishuApprovalCardActionResult.warning("缺少飞书用户标识，无法处理采购候选");
        }

        BotMessage message = new BotMessage(openId, userId, unionId, operatorSourceUserKey, messageId,
            "", null, "", "", "interactive", "", "", "", "card.action.trigger", "user");
        SysUser boundUser = findBoundUser(message);
        if (boundUser == null) {
            return FeishuApprovalCardActionResult.warning("你的飞书账号还没有绑定系统用户，请先联系管理员同步或维护邮箱、手机号。");
        }
        AuthUser user = toAuthUser(boundUser);
        try {
            List<String> cancelItemKeys = cancelItemKeys(value.get("cancelItemKeys"));
            if (ACTION_PREVIEW_WAITING.equals(action)) {
                List<ProcurementAiSessionVO> sessions = procurementAiService.waitingSessions(sourceUserKey);
                Integer itemPage = parseInt(text(value.get("itemPage")), 1);
                return FeishuApprovalCardActionResult.ok("已刷新待提交候选",
                    buildWaitingCard(sessions, sourceUserKey, itemPage, cancelItemKeys));
            }
            if (ACTION_ITEM_PAGE.equals(action)) {
                Integer itemPage = parseInt(text(value.get("itemPage")), 1);
                List<ProcurementAiSessionVO> sessions = procurementAiService.waitingSessions(sourceUserKey);
                return FeishuApprovalCardActionResult.ok("已切换明细页",
                    buildWaitingCard(sessions, sourceUserKey, itemPage, cancelItemKeys));
            }
            if (ACTION_CONFIRM_ALL.equals(action)) {
                List<ProcurementAiConfirmResultVO> results = procurementAiService.confirmAllWaiting(sourceUserKey, user, cancelItemKeys);
                return FeishuApprovalCardActionResult.ok("已提交采购申请", buildSubmittedCard(results));
            }
            if (ACTION_CANCEL_ALL.equals(action)) {
                procurementAiService.cancelAllWaiting(sourceUserKey, user);
                return FeishuApprovalCardActionResult.ok("已撤回全部待提交候选",
                    buildWaitingCard(procurementAiService.waitingSessions(sourceUserKey), sourceUserKey));
            }
            if (ACTION_CONFIRM_ONE.equals(action)) {
                String sessionNo = text(value.get("sessionNo"));
                Integer candidateNo = parseInt(text(value.get("candidateNo")), 1);
                ProcurementAiConfirmResultVO result = procurementAiService.confirmBySessionNo(sessionNo, candidateNo, user, sourceUserKey);
                return FeishuApprovalCardActionResult.ok("已提交采购申请", buildSubmittedCard(List.of(result)));
            }
            if (ACTION_TOGGLE_CANCEL_ITEM.equals(action) || ACTION_CANCEL_ONE.equals(action)) {
                String sessionNo = text(value.get("sessionNo"));
                Integer candidateNo = parseInt(text(value.get("candidateNo")), 1);
                Integer itemPage = parseInt(text(value.get("itemPage")), 1);
                String cancelItemKey = firstText(text(value.get("cancelItemKey")), candidateKey(sessionNo, candidateNo));
                List<String> selectedKeys = toggleCancelItemKey(cancelItemKeys, cancelItemKey);
                List<ProcurementAiSessionVO> sessions = procurementAiService.waitingSessions(sourceUserKey);
                boolean selected = selectedKeys.contains(cancelItemKey);
                return FeishuApprovalCardActionResult.ok(selected ? "已标记撤回采购候选" : "已恢复采购候选",
                    buildWaitingCard(sessions, sourceUserKey, itemPage, selectedKeys));
            }
            return FeishuApprovalCardActionResult.warning("暂不支持该采购助手动作");
        } catch (BizException ex) {
            return FeishuApprovalCardActionResult.warning(ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Handle Feishu procurement AI card action failed", ex);
            return FeishuApprovalCardActionResult.warning("采购助手卡片处理失败，请稍后重试或回到系统处理");
        }
    }

    private boolean operatorMatches(String sourceUserKey, String openId, String userId, String unionId) {
        return equalsText(sourceUserKey, openId) || equalsText(sourceUserKey, userId) || equalsText(sourceUserKey, unionId);
    }

    private ProcurementAiConfirmResultVO confirmFromMessage(BotMessage message, AuthUser user) {
        String text = firstText(message.text(), "");
        if (!CONFIRM_PATTERN.matcher(text).matches()) {
            throw new BizException("CONFIRM_FORMAT_INVALID", "请回复：确认 会话号 序号，例如：确认 PA202604290001 1");
        }
        String rest = text.replaceFirst("^\\s*确认\\s*", "").trim();
        String sessionNo = "";
        Integer candidateNo = null;
        if (StringUtils.hasText(rest)) {
            String[] parts = rest.split("\\s+");
            if (parts.length == 1 && parts[0].matches("\\d+")) {
                candidateNo = parseInt(parts[0], null);
            } else {
                sessionNo = parts[0];
                if (parts.length >= 2) {
                    candidateNo = parseInt(parts[1], null);
                }
            }
        }
        if (!StringUtils.hasText(sessionNo)) {
            ProcurementAiSessionVO latest = procurementAiService.latestWaitingSession(message.sourceUserKey());
            if (latest == null) {
                throw new BizException("PROCUREMENT_AI_SESSION_NOT_FOUND", "没有找到待确认的采购推荐");
            }
            sessionNo = latest.getSessionNo();
        }
        if (candidateNo == null) {
            candidateNo = 1;
        }
        return procurementAiService.confirmBySessionNo(sessionNo, candidateNo, user, message.sourceUserKey());
    }

    private CandidateCommand interpretWaitingCandidateCommand(String messageText,
                                                              List<ProcurementAiSessionVO> waitingSessions,
                                                              String sourceUserKey) {
        if (!StringUtils.hasText(messageText) || waitingSessions == null || waitingSessions.isEmpty()) {
            return null;
        }
        try {
            return procurementAiAnalyzer.interpretCandidateCommand(messageText, waitingSessions, sourceUserKey);
        } catch (RuntimeException ex) {
            log.warn("M3 candidate command analysis failed", ex);
            return null;
        }
    }

    private Result<?> handleWaitingCandidateCommand(CandidateCommand command,
                                                    BotMessage message,
                                                    AuthUser user,
                                                    Long messageLogId,
                                                    long startedAt,
                                                    List<ProcurementAiSessionVO> waitingSessions) {
        if (command == null || !StringUtils.hasText(command.action())) {
            return null;
        }
        PendingCandidateCommand pendingCommand = pendingCandidateCommand(message.sourceUserKey());
        command = mergePendingCandidateCommand(pendingCommand, command, message.text());
        String action = command.action();
        if ("CREATE_NEW_DEMAND".equals(action) || "NONE".equals(action)) {
            return null;
        }
        safeUpdateM3CommandDebug(messageLogId, command);
        if ("CLARIFY".equals(action)) {
            String pendingAction = pendingCandidateAction(command, message.text(), command.clarificationQuestion());
            if ("CANCEL_ALL".equals(pendingAction)) {
                return askCandidateCommandConfirmation(pendingAction, command, message, messageLogId, startedAt, null, waitingSessions);
            }
            String replyText = firstText(command.clarificationQuestion(), "请说明要修改哪一项候选，以及要改成什么。");
            if (StringUtils.hasText(pendingAction)) {
                rememberPendingCandidateCommand(message.sourceUserKey(), pendingAction, command);
            } else if (command.itemNo() != null && command.itemNo() > 0) {
                rememberPendingCandidateSelection(message.sourceUserKey(), command.itemNo());
            }
            sendText(message.openId(), replyText);
            safeFinishSuccess(messageLogId, "M3_CANDIDATE_CLARIFY", "候选自然语言澄清", replyText,
                replyText, firstSession(waitingSessions), null, startedAt);
            return Result.ok();
        }
        Integer itemNo = resolveCandidateCommandItemNo(command, waitingSessions);
        if (itemNo == null && ("UPDATE_QUANTITY".equals(action) || "UPDATE_PURPOSE".equals(action)
            || "CANCEL_ITEM".equals(action) || "CONFIRM_ONE".equals(action))) {
            rememberPendingCandidateCommand(message.sourceUserKey(), action, command);
            String replyText = "当前有多个待提交候选，请说明要处理第几项。";
            sendText(message.openId(), replyText);
            safeFinishSuccess(messageLogId, "M3_CANDIDATE_CLARIFY", "候选自然语言澄清", replyText,
                replyText, firstSession(waitingSessions), null, startedAt);
            return Result.ok();
        }
        if (isHighRiskCandidateAction(action)) {
            return askCandidateCommandConfirmation(action, command, message, messageLogId, startedAt, itemNo, waitingSessions);
        }
        return switch (action) {
            case "UPDATE_QUANTITY" -> handleM3UpdateQuantity(command, message, user, messageLogId, startedAt, itemNo);
            case "UPDATE_PURPOSE" -> handleM3UpdatePurpose(command, message, user, messageLogId, startedAt, itemNo);
            case "CANCEL_ITEM" -> handleM3CancelCandidate(message, user, messageLogId, startedAt, itemNo);
            case "CANCEL_ALL" -> handleM3CancelAllCandidates(message, user, messageLogId, startedAt);
            case "CONFIRM_ONE" -> handleM3ConfirmOne(message, user, messageLogId, startedAt, itemNo, waitingSessions);
            case "CONFIRM_ALL" -> handleM3ConfirmAll(message, user, messageLogId, startedAt);
            case "PREVIEW_WAITING" -> handleM3PreviewWaiting(message, messageLogId, startedAt);
            default -> null;
        };
    }

    private Result<?> askCandidateCommandConfirmation(String action,
                                                      CandidateCommand command,
                                                      BotMessage message,
                                                      Long messageLogId,
                                                      long startedAt,
                                                      Integer itemNo,
                                                      List<ProcurementAiSessionVO> waitingSessions) {
        rememberPendingCandidateConfirmation(message.sourceUserKey(), action, command, itemNo);
        String replyText = confirmationPrompt(action, itemNo, waitingSessions);
        sendText(message.openId(), replyText);
        safeFinishSuccess(messageLogId, "M3_CANDIDATE_CONFIRM_PENDING", "候选高风险动作待确认", replyText,
            replyText, firstSession(waitingSessions), itemNo, startedAt);
        return Result.ok();
    }

    private Result<?> handlePendingCandidateConfirmation(PendingCandidateCommand pendingCommand,
                                                         BotMessage message,
                                                         AuthUser user,
                                                         Long messageLogId,
                                                         long startedAt,
                                                         List<ProcurementAiSessionVO> waitingSessions) {
        if (pendingCommand == null || !pendingCommand.awaitingConfirmation()) {
            return null;
        }
        forgetPendingCandidateCommand(message.sourceUserKey());
        if (!isAffirmativeConfirmation(message.text())) {
            String replyText = "已取消本次" + candidateActionName(pendingCommand.action()) + "。";
            sendText(message.openId(), replyText);
            safeFinishSuccess(messageLogId, "M3_CANDIDATE_CONFIRM_CANCELLED", "候选高风险动作取消", replyText,
                replyText, firstSession(waitingSessions), pendingCommand.itemNo(), startedAt);
            return Result.ok();
        }
        CandidateCommand command = pendingCommand.toCommand(pendingCommand.itemNo());
        return switch (pendingCommand.action()) {
            case "CANCEL_ITEM" -> handleM3CancelCandidate(message, user, messageLogId, startedAt, pendingCommand.itemNo());
            case "CANCEL_ALL" -> handleM3CancelAllCandidates(message, user, messageLogId, startedAt);
            case "CONFIRM_ONE" -> handleM3ConfirmOne(message, user, messageLogId, startedAt, pendingCommand.itemNo(), waitingSessions);
            case "CONFIRM_ALL" -> handleM3ConfirmAll(message, user, messageLogId, startedAt);
            case "UPDATE_QUANTITY" -> handleM3UpdateQuantity(command, message, user, messageLogId, startedAt, pendingCommand.itemNo());
            case "UPDATE_PURPOSE" -> handleM3UpdatePurpose(command, message, user, messageLogId, startedAt, pendingCommand.itemNo());
            default -> null;
        };
    }

    private Result<?> handlePendingCandidateSelection(PendingCandidateCommand pendingCommand,
                                                      Integer itemNo,
                                                      BotMessage message,
                                                      AuthUser user,
                                                      Long messageLogId,
                                                      long startedAt,
                                                      List<ProcurementAiSessionVO> waitingSessions) {
        if (pendingCommand == null || itemNo == null || itemNo <= 0) {
            return null;
        }
        CandidateCommand command = pendingCommand.toCommand(itemNo);
        if (isHighRiskCandidateAction(pendingCommand.action())) {
            return askCandidateCommandConfirmation(pendingCommand.action(), command, message, messageLogId,
                startedAt, itemNo, waitingSessions);
        }
        return switch (pendingCommand.action()) {
            case "UPDATE_QUANTITY" -> handleM3UpdateQuantity(command, message, user, messageLogId, startedAt, itemNo);
            case "UPDATE_PURPOSE" -> handleM3UpdatePurpose(command, message, user, messageLogId, startedAt, itemNo);
            case "CANCEL_ITEM" -> handleM3CancelCandidate(message, user, messageLogId, startedAt, itemNo);
            case "CONFIRM_ONE" -> handleM3ConfirmOne(message, user, messageLogId, startedAt, itemNo, waitingSessions);
            default -> null;
        };
    }

    private CandidateCommand mergePendingCandidateCommand(PendingCandidateCommand pendingCommand,
                                                          CandidateCommand command,
                                                          String userText) {
        if (pendingCommand == null || pendingCommand.awaitingConfirmation() || pendingCommand.expired()
            || command == null || !StringUtils.hasText(command.action())) {
            return command;
        }
        String commandAction = normalizedCandidateSelectionAction(command.action());
        if ("CLARIFY".equals(command.action())) {
            commandAction = pendingCandidateAction(command, userText, command.clarificationQuestion());
        }
        String action = "SELECT_ITEM".equals(pendingCommand.action())
            ? commandAction
            : firstText(commandAction, pendingCommand.action());
        if (!StringUtils.hasText(action)) {
            return command;
        }
        return new CandidateCommand(
            action,
            command.itemNo() == null ? pendingCommand.itemNo() : command.itemNo(),
            command.quantity() == null ? pendingCommand.quantity() : command.quantity(),
            firstText(command.unit(), pendingCommand.unit()),
            firstText(command.purpose(), pendingCommand.purpose()),
            command.clarificationQuestion(),
            command.confidence(),
            command.requestJson(),
            command.responseText(),
            command.parsedJson()
        );
    }

    private void rememberPendingCandidateSelection(String sourceUserKey, Integer itemNo) {
        if (itemNo == null || itemNo <= 0) {
            return;
        }
        String normalizedKey = normalizedSourceUserKey(sourceUserKey);
        if (!StringUtils.hasText(normalizedKey)) {
            return;
        }
        pendingCandidateCommands.put(normalizedKey, new PendingCandidateCommand(
            "SELECT_ITEM",
            null,
            null,
            null,
            itemNo,
            false,
            System.currentTimeMillis() + PENDING_CANDIDATE_COMMAND_TTL_MS
        ));
    }

    private void rememberPendingCandidateCommand(String sourceUserKey, String action, CandidateCommand command) {
        String normalizedKey = normalizedSourceUserKey(sourceUserKey);
        String normalizedAction = normalizedCandidateSelectionAction(action);
        if (!StringUtils.hasText(normalizedKey) || !StringUtils.hasText(normalizedAction)) {
            return;
        }
        pendingCandidateCommands.put(normalizedKey, new PendingCandidateCommand(
            normalizedAction,
            command == null ? null : command.quantity(),
            command == null ? null : command.unit(),
            command == null ? null : command.purpose(),
            command == null ? null : command.itemNo(),
            false,
            System.currentTimeMillis() + PENDING_CANDIDATE_COMMAND_TTL_MS
        ));
    }

    private void rememberPendingCandidateConfirmation(String sourceUserKey, String action, CandidateCommand command,
                                                      Integer itemNo) {
        String normalizedKey = normalizedSourceUserKey(sourceUserKey);
        String normalizedAction = normalizedCandidateConfirmationAction(action);
        if (!StringUtils.hasText(normalizedKey) || !StringUtils.hasText(normalizedAction)) {
            return;
        }
        pendingCandidateCommands.put(normalizedKey, new PendingCandidateCommand(
            normalizedAction,
            command == null ? null : command.quantity(),
            command == null ? null : command.unit(),
            command == null ? null : command.purpose(),
            itemNo == null ? command == null ? null : command.itemNo() : itemNo,
            true,
            System.currentTimeMillis() + PENDING_CANDIDATE_COMMAND_TTL_MS
        ));
    }

    private PendingCandidateCommand pendingCandidateCommand(String sourceUserKey) {
        String normalizedKey = normalizedSourceUserKey(sourceUserKey);
        if (!StringUtils.hasText(normalizedKey)) {
            return null;
        }
        PendingCandidateCommand pendingCommand = pendingCandidateCommands.get(normalizedKey);
        if (pendingCommand == null) {
            return null;
        }
        if (pendingCommand.expired()) {
            pendingCandidateCommands.remove(normalizedKey);
            return null;
        }
        return pendingCommand.awaitingConfirmation() ? null : pendingCommand;
    }

    private PendingCandidateCommand takePendingCandidateCommand(String sourceUserKey) {
        String normalizedKey = normalizedSourceUserKey(sourceUserKey);
        if (!StringUtils.hasText(normalizedKey)) {
            return null;
        }
        PendingCandidateCommand pendingCommand = pendingCandidateCommands.remove(normalizedKey);
        if (pendingCommand == null || pendingCommand.expired() || pendingCommand.awaitingConfirmation()) {
            return null;
        }
        return pendingCommand;
    }

    private PendingCandidateCommand pendingCandidateConfirmation(String sourceUserKey) {
        String normalizedKey = normalizedSourceUserKey(sourceUserKey);
        if (!StringUtils.hasText(normalizedKey)) {
            return null;
        }
        PendingCandidateCommand pendingCommand = pendingCandidateCommands.get(normalizedKey);
        if (pendingCommand == null) {
            return null;
        }
        if (pendingCommand.expired()) {
            pendingCandidateCommands.remove(normalizedKey);
            return null;
        }
        return pendingCommand.awaitingConfirmation() ? pendingCommand : null;
    }

    private void forgetPendingCandidateCommand(String sourceUserKey) {
        String normalizedKey = normalizedSourceUserKey(sourceUserKey);
        if (StringUtils.hasText(normalizedKey)) {
            pendingCandidateCommands.remove(normalizedKey);
        }
    }

    private String normalizedSourceUserKey(String sourceUserKey) {
        return StringUtils.hasText(sourceUserKey) ? sourceUserKey.trim() : "";
    }

    private String pendingCandidateAction(CandidateCommand command, String userText, String replyText) {
        if (command != null) {
            String action = normalizedCandidateSelectionAction(command.action());
            if (StringUtils.hasText(action) && !"CLARIFY".equals(action)) {
                return action;
            }
        }
        String text = (firstText(userText, "") + "\n" + firstText(replyText, "") + "\n"
            + firstText(command == null ? null : command.clarificationQuestion(), "")).toLowerCase(Locale.ROOT);
        if (containsAny(text, "删除", "撤回", "取消", "不要", "删掉", "移除")) {
            if (containsAny(text, "全部", "全都", "所有", "清空", "都不要")) {
                return "CANCEL_ALL";
            }
            return "CANCEL_ITEM";
        }
        if (containsAny(text, "用途", "使用", "用于")) {
            return "UPDATE_PURPOSE";
        }
        if ((command == null ? null : command.quantity()) != null
            || containsAny(text, "数量", "改成", "改为", "换成")) {
            return "UPDATE_QUANTITY";
        }
        if (containsAny(text, "确认", "提交")) {
            return "CONFIRM_ONE";
        }
        return null;
    }

    private String normalizedCandidateSelectionAction(String action) {
        String value = firstText(action, "").trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "UPDATE_QUANTITY", "UPDATE_PURPOSE", "CANCEL_ITEM", "CONFIRM_ONE" -> value;
            default -> null;
        };
    }

    private String normalizedCandidateConfirmationAction(String action) {
        String value = firstText(action, "").trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "CANCEL_ITEM", "CANCEL_ALL", "CONFIRM_ONE", "CONFIRM_ALL",
                "UPDATE_QUANTITY", "UPDATE_PURPOSE" -> value;
            default -> null;
        };
    }

    private boolean isHighRiskCandidateAction(String action) {
        String value = firstText(action, "").trim().toUpperCase(Locale.ROOT);
        return "CANCEL_ITEM".equals(value) || "CANCEL_ALL".equals(value)
            || "CONFIRM_ONE".equals(value) || "CONFIRM_ALL".equals(value);
    }

    private String confirmationPrompt(String action, Integer itemNo, List<ProcurementAiSessionVO> waitingSessions) {
        String itemText = itemNo == null ? "" : "第 " + itemNo + " 项";
        return switch (firstText(action, "").trim().toUpperCase(Locale.ROOT)) {
            case "CANCEL_ITEM" -> "请再次确认：是否撤回" + itemText + "采购候选？回复“确认”后才会撤回，回复其他内容将取消本次撤回。";
            case "CANCEL_ALL" -> "请再次确认：是否撤回当前全部 " + activeCandidateCount(waitingSessions)
                + " 条采购候选？回复“确认”后才会撤回，回复其他内容将取消本次撤回。";
            case "CONFIRM_ONE" -> "请再次确认：是否提交" + itemText + "采购候选并生成采购申请？回复“确认”后才会提交，回复其他内容将取消本次提交。";
            case "CONFIRM_ALL" -> "请再次确认：是否提交当前全部 " + activeCandidateCount(waitingSessions)
                + " 条采购候选并生成采购申请？回复“确认”后才会提交，回复其他内容将取消本次提交。";
            default -> "请再次确认是否执行该操作。回复“确认”后才会执行，回复其他内容将取消本次操作。";
        };
    }

    private String candidateActionName(String action) {
        return switch (firstText(action, "").trim().toUpperCase(Locale.ROOT)) {
            case "CANCEL_ITEM", "CANCEL_ALL" -> "撤回";
            case "CONFIRM_ONE", "CONFIRM_ALL" -> "提交";
            case "UPDATE_QUANTITY" -> "修改数量";
            case "UPDATE_PURPOSE" -> "修改用途";
            default -> "操作";
        };
    }

    private boolean isAffirmativeConfirmation(String text) {
        String value = firstText(text, "").trim();
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.matches("^(确认|确定|是|是的|对|对的|可以|执行|继续|同意|ok|OK|Ok).*$");
    }

    static Integer candidateSelectionNo(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = CANDIDATE_SELECTION_ONLY_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        try {
            int value = Integer.parseInt(matcher.group(1));
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Result<?> handleM3UpdateQuantity(CandidateCommand command, BotMessage message, AuthUser user,
                                             Long messageLogId, long startedAt, Integer itemNo) {
        if (command.quantity() == null || command.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            rememberPendingCandidateCommand(message.sourceUserKey(), "UPDATE_QUANTITY",
                command == null ? null : new CandidateCommand("UPDATE_QUANTITY", itemNo,
                    command.quantity(), command.unit(), command.purpose(), command.clarificationQuestion(),
                    command.confidence(), command.requestJson(), command.responseText(), command.parsedJson()));
            String replyText = firstText(command.clarificationQuestion(), "请说明要改成多少数量。");
            sendText(message.openId(), replyText);
            safeFinishSuccess(messageLogId, "M3_UPDATE_QUANTITY_CLARIFY", "修改数量待补充", replyText, replyText, null, startedAt);
            return Result.ok();
        }
        ProcurementAiSessionVO updated = procurementAiService.updateWaitingCandidateQuantity(
            message.sourceUserKey(), itemNo, command.quantity(), command.unit(), user);
        forgetPendingCandidateCommand(message.sourceUserKey());
        String replyText = "已修改数量：\n" + buildSingleWaitingLine(itemNo, updated);
        sendText(message.openId(), replyText);
        safeFinishSuccess(messageLogId, "M3_UPDATE_QUANTITY", "修改数量", replyText,
            "修改数量：" + command.quantity().stripTrailingZeros().toPlainString() + firstText(command.unit(), ""),
            updated, itemNo, startedAt);
        return Result.ok(updated);
    }

    private Result<?> handleM3UpdatePurpose(CandidateCommand command, BotMessage message, AuthUser user,
                                            Long messageLogId, long startedAt, Integer itemNo) {
        if (!StringUtils.hasText(command.purpose())) {
            rememberPendingCandidateCommand(message.sourceUserKey(), "UPDATE_PURPOSE",
                command == null ? null : new CandidateCommand("UPDATE_PURPOSE", itemNo,
                    command.quantity(), command.unit(), command.purpose(), command.clarificationQuestion(),
                    command.confidence(), command.requestJson(), command.responseText(), command.parsedJson()));
            String replyText = firstText(command.clarificationQuestion(), "请说明新的采购用途。");
            sendText(message.openId(), replyText);
            safeFinishSuccess(messageLogId, "M3_UPDATE_PURPOSE_CLARIFY", "修改用途待补充", replyText, replyText, null, startedAt);
            return Result.ok();
        }
        ProcurementAiSessionVO updated = procurementAiService.updateWaitingCandidateReason(
            message.sourceUserKey(), itemNo, command.purpose(), user);
        forgetPendingCandidateCommand(message.sourceUserKey());
        String replyText = "已修改用途：\n" + buildSingleWaitingLine(itemNo, updated);
        sendText(message.openId(), replyText);
        safeFinishSuccess(messageLogId, "M3_UPDATE_PURPOSE", "修改用途", replyText,
            "修改用途：" + command.purpose(), updated, itemNo, startedAt);
        return Result.ok(updated);
    }

    private Result<?> handleM3CancelCandidate(BotMessage message, AuthUser user, Long messageLogId,
                                              long startedAt, Integer itemNo) {
        ProcurementAiSessionVO cancelled = procurementAiService.cancelWaitingSession(message.sourceUserKey(), itemNo, user);
        String replyText = "已撤回候选：" + firstText(firstCandidate(cancelled) == null ? null : firstCandidate(cancelled).getItemName(), cancelled.getReqTitle());
        sendText(message.openId(), replyText);
        safeFinishSuccess(messageLogId, "M3_CANCEL_ITEM", "撤回候选", replyText, replyText, cancelled, itemNo, startedAt);
        return Result.ok(cancelled);
    }

    private Result<?> handleM3CancelAllCandidates(BotMessage message, AuthUser user, Long messageLogId,
                                                  long startedAt) {
        int cancelledCount = activeCandidateCount(procurementAiService.waitingSessions(message.sourceUserKey()));
        List<ProcurementAiSessionVO> cancelled = procurementAiService.cancelAllWaiting(message.sourceUserKey(), user);
        String replyText = "已撤回全部待提交采购候选，共 " + cancelledCount + " 条。";
        sendText(message.openId(), replyText);
        safeFinishSuccess(messageLogId, "M3_CANCEL_ALL", "撤回全部候选", replyText,
            replyText, firstSession(cancelled), null, startedAt);
        return Result.ok(cancelled);
    }

    private Result<?> handleM3ConfirmOne(BotMessage message, AuthUser user, Long messageLogId,
                                         long startedAt, Integer itemNo,
                                         List<ProcurementAiSessionVO> waitingSessions) {
        ProcurementAiConfirmResultVO result = confirmWaitingCandidateAt(message, user, itemNo, waitingSessions);
        String replyText = "已确认候选 " + result.getCandidateNo() + "，"
            + requestRouteText(result.getRequest()) + "，"
            + firstText(result.getPurchaseRequestNo(), result.getSessionNo());
        sendCardOrText(message.openId(), buildSubmittedCard(List.of(result)), replyText);
        safeFinishSuccess(messageLogId, "M3_CONFIRM_ONE", "确认单条候选", replyText, replyText, result, startedAt);
        return Result.ok(result);
    }

    private Result<?> handleM3ConfirmAll(BotMessage message, AuthUser user, Long messageLogId, long startedAt) {
        List<ProcurementAiConfirmResultVO> results = procurementAiService.confirmAllWaiting(message.sourceUserKey(), user);
        String replyText = buildConfirmAllReply(results);
        sendCardOrText(message.openId(), buildSubmittedCard(results), replyText);
        safeFinishSuccess(messageLogId, "M3_CONFIRM_ALL", "确认提交全部", replyText,
            buildConfirmAllSummary(results), firstConfirmResult(results), startedAt);
        return Result.ok(results);
    }

    private Result<?> handleM3PreviewWaiting(BotMessage message, Long messageLogId, long startedAt) {
        List<ProcurementAiSessionVO> sessions = procurementAiService.waitingSessions(message.sourceUserKey());
        String replyText = buildWaitingSummary(sessions);
        sendCardOrText(message.openId(), buildWaitingCard(sessions, message.sourceUserKey()), replyText);
        safeFinishSuccess(messageLogId, "M3_PREVIEW_WAITING", "查看待提交候选", replyText,
            "待提交候选 " + (sessions == null ? 0 : sessions.size()) + " 项", firstSession(sessions), null, startedAt);
        return Result.ok(sessions);
    }

    private Integer resolveCandidateCommandItemNo(CandidateCommand command, List<ProcurementAiSessionVO> waitingSessions) {
        if (command.itemNo() != null && command.itemNo() > 0) {
            return command.itemNo();
        }
        return activeCandidateCount(waitingSessions) == 1 ? 1 : null;
    }

    private ProcurementAiConfirmResultVO confirmWaitingCandidateAt(BotMessage message, AuthUser user, Integer itemNo,
                                                                   List<ProcurementAiSessionVO> waitingSessions) {
        int targetNo = itemNo == null ? 1 : itemNo;
        int currentNo = 0;
        for (ProcurementAiSessionVO session : waitingSessions == null ? List.<ProcurementAiSessionVO>of() : waitingSessions) {
            for (ProcurementAiCandidateVO candidate : activeCandidates(session)) {
                currentNo++;
                if (currentNo == targetNo) {
                    return procurementAiService.confirmBySessionNo(session.getSessionNo(), candidate.getCandidateNo(),
                        user, message.sourceUserKey());
                }
            }
        }
        throw new BizException("PROCUREMENT_AI_CANDIDATE_NOT_FOUND", "候选物品不存在或已撤回");
    }

    private String buildCandidateReply(ProcurementAiSessionVO session) {
        StringBuilder builder = new StringBuilder();
        builder.append("已记录采购候选：").append(session.getSessionNo()).append("\n");
        String aiReviewOpinion = candidateAiReviewOpinion(session);
        if (StringUtils.hasText(aiReviewOpinion)) {
            builder.append("AI审核意见：").append(aiReviewOpinion).append("\n");
        }
        builder.append(session.getEmergency()
            ? "类型：紧急采购，确认后按现有紧急采购流程提交楼长审核。\n"
            : "类型：普通采购，确认后按现有普通采购流程提交楼长审核。\n");
        for (ProcurementAiCandidateVO candidate : session.getCandidates()) {
            builder.append(candidate.getCandidateNo()).append(". ")
                .append(candidate.getItemName());
            if (StringUtils.hasText(candidate.getSpecModel())) {
                builder.append(" / ").append(candidate.getSpecModel());
            }
            builder.append(" / ¥").append(candidate.getUnitPrice() == null ? "0.00" : candidate.getUnitPrice());
            if (StringUtils.hasText(candidate.getEcommercePlatform())) {
                builder.append(" / ").append(candidate.getEcommercePlatform());
            }
            if (StringUtils.hasText(candidate.getEcommerceUrl())) {
                builder.append("\n").append(candidate.getEcommerceUrl());
            }
            builder.append("\n");
        }
        builder.append("继续发送商品、图片或链接可继续记录。\n");
        builder.append("请在卡片上查看汇总或确认提交。");
        return builder.toString();
    }

    private String buildHelpReply() {
        return "你好，我是采购助手。\n"
            + "请直接发送要采购的物品、数量、用途、参考单价或预算、参考链接或图片，例如：\n"
            + "采购 2 个罗技无线鼠标，参考价 129 元/个，用于机房值班电脑备用\n"
            + "收到采购候选后，可在卡片上查看汇总并确认提交。";
    }

    private String buildWaitingSummary(List<ProcurementAiSessionVO> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "当前没有待确认的采购候选。你可以直接发送要采购的物品、图片或参考链接。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("当前待提交的采购候选共 ").append(sessions.size()).append(" 项：\n");
        for (int i = 0; i < sessions.size(); i++) {
            builder.append(buildSingleWaitingLine(i + 1, sessions.get(i))).append("\n");
        }
        builder.append("\n确认无误后，请在卡片上点击“确认全部提交”。\n");
        builder.append("确认后会按现有采购申请流程提交楼长审核；普通采购需楼长审核通过后才进入月度需求池。\n");
        builder.append("修改用途示例：把第2项用途改成机房巡检备用\n");
        builder.append("删除示例：删除第2项");
        return builder.toString();
    }

    private String buildSingleWaitingLine(Integer fallbackNo, ProcurementAiSessionVO session) {
        ProcurementAiCandidateVO candidate = firstCandidate(session);
        StringBuilder builder = new StringBuilder();
        builder.append(fallbackNo == null ? "-" : fallbackNo).append(". ");
        if (candidate == null) {
            builder.append(firstText(session == null ? null : session.getReqTitle(), "未识别物品"));
            return builder.toString();
        }
        if (session != null && session.getEmergency()) {
            builder.append("[紧急] ");
        }
        builder.append(firstText(candidate.getItemName(), "未识别物品"));
        if (StringUtils.hasText(candidate.getSpecModel())) {
            builder.append(" / ").append(candidate.getSpecModel());
        }
        builder.append(" / ").append(candidate.getQty() == null ? "1" : candidate.getQty().stripTrailingZeros().toPlainString())
            .append(firstText(candidate.getUnit(), "件"))
            .append(" / 参考价 ¥").append(candidate.getUnitPrice() == null ? "0.00" : candidate.getUnitPrice().toPlainString());
        if (StringUtils.hasText(candidate.getPurchaseReason())) {
            builder.append("\n   用途：").append(candidate.getPurchaseReason());
        }
        if (StringUtils.hasText(candidate.getEcommerceUrl())) {
            builder.append("\n   链接：").append(candidate.getEcommerceUrl());
        }
        return builder.toString();
    }

    private String buildConfirmAllReply(List<ProcurementAiConfirmResultVO> results) {
        StringBuilder builder = new StringBuilder();
        int requestCount = 0;
        int itemCount = 0;
        if (results != null) {
            for (ProcurementAiConfirmResultVO result : results) {
                if (result == null || result.getRequest() == null) {
                    continue;
                }
                requestCount++;
                itemCount += result.getRequest().getItems() == null ? 1 : result.getRequest().getItems().size();
            }
        }
        builder.append("已生成 ").append(requestCount).append(" 张采购申请，包含 ").append(itemCount).append(" 条明细：");
        if (results != null) {
            for (ProcurementAiConfirmResultVO result : results) {
                if (result == null || result.getRequest() == null) {
                    continue;
                }
                builder.append("\n")
                    .append(firstText(result.getPurchaseRequestNo(), result.getSessionNo()))
                    .append(" / ")
                    .append(requestRouteText(result.getRequest()))
                    .append(" / ")
                    .append(firstText(result.getRequest().getReqTitle(), result.getSessionNo()));
            }
        }
        return builder.toString();
    }

    private Map<String, Object> buildCandidateCard(ProcurementAiSessionVO session, String sourceUserKey) {
        Map<String, Object> card = baseCard("orange", "采购候选已记录", "采购候选已记录");
        List<Map<String, Object>> elements = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        appendMarkdownLine(summary, "卡片版本", BOT_CARD_VERSION, false);
        appendMarkdownLine(summary, "候选编号", session == null ? null : session.getSessionNo(), false);
        appendMarkdownLine(summary, "类型", session != null && Boolean.TRUE.equals(session.getEmergency()) ? "紧急采购" : "普通采购", false);
        appendMarkdownLine(summary, "流程", session != null && Boolean.TRUE.equals(session.getEmergency())
            ? "确认后按现有紧急采购流程提交楼长审核"
            : "确认后按现有普通采购流程提交楼长审核", false);
        appendMarkdownLine(summary, "AI审核意见", candidateAiReviewOpinion(session), true);
        elements.add(markdownElement(summary.toString()));
        elements.add(Map.of("tag", "hr"));
        appendCandidateRows(elements, session == null ? List.of() : List.of(session), sourceUserKey, 1, List.of());

        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(cardActionButton("查看汇总", "default", ACTION_PREVIEW_WAITING, sourceUserKey, null, null));
        List<ProcurementAiCandidateVO> candidates = activeCandidates(session);
        if (session != null && candidates.size() == 1) {
            actions.add(cardActionButton("确认本候选", "default", ACTION_CONFIRM_ONE, sourceUserKey, session.getSessionNo(), candidates.get(0).getCandidateNo()));
        }
        actions.add(cardActionButton("确认全部提交", "primary", ACTION_CONFIRM_ALL, sourceUserKey, null, null));
        elements.add(Map.of("tag", "hr"));
        elements.add(buttonRow(actions));
        elements.add(versionElement());
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private String candidateAiReviewOpinion(ProcurementAiSessionVO session) {
        if (session == null) {
            return "";
        }
        if (StringUtils.hasText(session.getAiSummary())) {
            return limit(ensureApprovalDecisionOpinion(session.getAiSummary()), 180);
        }
        return "建议通过：已按现有采购流程生成候选，请确认前核对商品名称、数量、用途、规格和参考价格。";
    }

    private String ensureApprovalDecisionOpinion(String opinion) {
        if (!StringUtils.hasText(opinion)) {
            return "";
        }
        String value = opinion.trim();
        if (hasApprovalDecisionPrefix(value)) {
            return value;
        }
        return approvalDecisionPrefixForText(value) + value;
    }

    private String approvalDecisionPrefixForText(String opinion) {
        if (opinion.contains("不通过") || opinion.contains("退回") || opinion.contains("驳回")) {
            return "建议退回：";
        }
        if (opinion.contains("补充") || opinion.contains("更正") || opinion.contains("缺失")
            || opinion.contains("不完整") || opinion.contains("未识别") || opinion.contains("小于等于 0")) {
            return "建议补充：";
        }
        return "建议通过：";
    }

    private boolean hasApprovalDecisionPrefix(String opinion) {
        if (!StringUtils.hasText(opinion)) {
            return false;
        }
        String value = opinion.trim();
        return value.startsWith("建议通过")
            || value.startsWith("建议补充")
            || value.startsWith("建议退回")
            || value.startsWith("建议驳回")
            || value.startsWith("建议不通过");
    }

    private Map<String, Object> buildWaitingCard(List<ProcurementAiSessionVO> sessions, String sourceUserKey) {
        return buildWaitingCard(sessions, sourceUserKey, 1);
    }

    private Map<String, Object> buildWaitingCard(List<ProcurementAiSessionVO> sessions, String sourceUserKey, Integer itemPage) {
        return buildWaitingCard(sessions, sourceUserKey, itemPage, List.of());
    }

    private Map<String, Object> buildWaitingCard(List<ProcurementAiSessionVO> sessions, String sourceUserKey,
                                                 Integer itemPage, List<String> selectedCancelItemKeys) {
        int itemCount = activeCandidateCount(sessions);
        int currentPage = normalizeCandidatePage(itemPage, itemCount);
        List<String> selectedKeys = cancelItemKeysOnActiveCandidates(selectedCancelItemKeys, sessions);
        Map<String, Object> card = baseCard("blue", "待提交采购候选", "待提交采购候选");
        List<Map<String, Object>> elements = new ArrayList<>();
        if (itemCount <= 0) {
            elements.add(markdownElement("**卡片版本**：" + markdownText(BOT_CARD_VERSION)
                + "\n当前没有待确认的采购候选。你可以直接发送要采购的物品、图片或参考链接。"));
            elements.add(versionElement());
            card.put("body", Map.of("elements", elements));
            return card;
        }
        StringBuilder summary = new StringBuilder();
        appendMarkdownLine(summary, "卡片版本", BOT_CARD_VERSION, false);
        appendMarkdownLine(summary, "待提交明细", itemCount + " 条", false);
        if (!selectedKeys.isEmpty()) {
            appendMarkdownLine(summary, "已标记撤回", selectedKeys.size() + " 条", false);
        }
        appendMarkdownLine(summary, "流程", "确认后会按现有采购申请流程提交楼长审核；普通采购需楼长审核通过后才进入月度需求池。", false);
        elements.add(markdownElement(summary.toString()));
        elements.add(Map.of("tag", "hr"));
        appendCandidateRows(elements, sessions, sourceUserKey, currentPage, selectedKeys);
        elements.add(Map.of("tag", "hr"));
        elements.add(buttonRow(List.of(
            cardActionButton("刷新汇总", "default", ACTION_PREVIEW_WAITING, sourceUserKey, null, null, currentPage, selectedKeys, null),
            cardActionButton("撤回全部", "danger", ACTION_CANCEL_ALL, sourceUserKey, null, null, currentPage, selectedKeys, null),
            cardActionButton("确认全部提交", "primary", ACTION_CONFIRM_ALL, sourceUserKey, null, null, currentPage, selectedKeys, null)
        )));
        elements.add(versionElement());
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private Map<String, Object> buildSubmittedCard(List<ProcurementAiConfirmResultVO> results) {
        Map<String, Object> card = baseCard("green", "采购申请已提交", "采购申请已提交");
        List<Map<String, Object>> elements = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            elements.add(markdownElement("**卡片版本**：" + markdownText(BOT_CARD_VERSION) + "\n没有生成采购申请。"));
            elements.add(versionElement());
            card.put("body", Map.of("elements", elements));
            return card;
        }
        StringBuilder summary = new StringBuilder();
        appendMarkdownLine(summary, "卡片版本", BOT_CARD_VERSION, false);
        for (ProcurementAiConfirmResultVO result : results) {
            PurchaseRequestVO request = result == null ? null : result.getRequest();
            if (request == null) {
                continue;
            }
            appendMarkdownLine(summary, "申请单号", firstText(result.getPurchaseRequestNo(), request.getReqNo(), result.getSessionNo()), false);
            appendMarkdownLine(summary, "申请标题", request.getReqTitle(), false);
            appendMarkdownLine(summary, "金额", moneyText(request.getTotalAmount()), false);
            appendMarkdownLine(summary, "明细数量", String.valueOf(request.getItems() == null ? 0 : request.getItems().size()), false);
            appendMarkdownLine(summary, "流程", requestRouteText(request), false);
        }
        elements.add(markdownElement(summary.toString()));
        List<PurchaseRequestItemVO> items = firstRequestItems(results);
        if (!items.isEmpty()) {
            elements.add(Map.of("tag", "hr"));
            elements.add(markdownElement(requestItemsMarkdown(items)));
        }
        elements.add(versionElement());
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private Map<String, Object> baseCard(String template, String title, String summary) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_JSON_SCHEMA_VERSION);
        card.put("config", cardConfig(summary));
        card.put("header", cardHeader(template, title));
        return card;
    }

    private Map<String, Object> cardConfig(String summary) {
        return Map.of(
            "enable_forward", false,
            "update_multi", true,
            "width_mode", "default",
            "summary", Map.of("content", firstText(summary, "采购助手") + " " + BOT_CARD_VERSION)
        );
    }

    private Map<String, Object> cardHeader(String template, String title) {
        return Map.of(
            "template", template,
            "title", plainText(title),
            "text_tag_list", List.of(Map.of(
                "tag", "text_tag",
                "text", plainText(BOT_CARD_VERSION),
                "color", "neutral"
            ))
        );
    }

    private Map<String, Object> cardActionButton(String text, String type, String action, String sourceUserKey,
                                                 String sessionNo, Integer candidateNo) {
        return cardActionButton(text, type, action, sourceUserKey, sessionNo, candidateNo, null, List.of(), null);
    }

    private Map<String, Object> cardActionButton(String text, String type, String action, String sourceUserKey,
                                                 String sessionNo, Integer candidateNo, Integer itemPage) {
        return cardActionButton(text, type, action, sourceUserKey, sessionNo, candidateNo, itemPage, List.of(), null);
    }

    private Map<String, Object> cardActionButton(String text, String type, String action, String sourceUserKey,
                                                 String sessionNo, Integer candidateNo, Integer itemPage,
                                                 List<String> cancelItemKeys, String cancelItemKey) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("source", BOT_CARD_SOURCE);
        value.put("action", action);
        if (StringUtils.hasText(sourceUserKey)) {
            value.put("sourceUserKey", sourceUserKey);
        }
        if (StringUtils.hasText(sessionNo)) {
            value.put("sessionNo", sessionNo);
        }
        if (candidateNo != null) {
            value.put("candidateNo", String.valueOf(candidateNo));
        }
        if (itemPage != null) {
            value.put("itemPage", String.valueOf(itemPage));
        }
        if (StringUtils.hasText(cancelItemKey)) {
            value.put("cancelItemKey", cancelItemKey);
        }
        List<String> stableCancelItemKeys = normalizedCancelItemKeys(cancelItemKeys);
        if (!stableCancelItemKeys.isEmpty()) {
            value.put("cancelItemKeys", stableCancelItemKeys);
        }
        value.put("cardVersion", BOT_CARD_VERSION);

        Map<String, Object> button = new LinkedHashMap<>();
        button.put("tag", "button");
        button.put("text", plainText(text));
        button.put("type", type);
        button.put("size", "medium");
        button.put("width", "default");
        button.put("behaviors", List.of(Map.of(
            "type", "callback",
            "value", value
        )));
        if (ACTION_CONFIRM_ALL.equals(action) || ACTION_CONFIRM_ONE.equals(action) || ACTION_CANCEL_ALL.equals(action)) {
            String confirmText = ACTION_CANCEL_ALL.equals(action)
                ? "确认撤回当前全部待提交采购候选？撤回后不会生成采购申请。"
                : ACTION_CONFIRM_ALL.equals(action)
                ? stableCancelItemKeys.isEmpty()
                ? "确认把当前所有待提交采购候选合并成一张采购申请？"
                : "确认提交未标记撤回的采购候选？已标记撤回的 " + stableCancelItemKeys.size() + " 条明细不会提交。"
                : "确认提交该采购候选？";
            button.put("confirm", Map.of(
                "title", plainText(text),
                "text", plainText(confirmText)
            ));
        }
        return button;
    }

    private Map<String, Object> candidatePageButton(String text, String sourceUserKey, int itemPage, boolean enabled) {
        return candidatePageButton(text, sourceUserKey, itemPage, enabled, List.of());
    }

    private Map<String, Object> candidatePageButton(String text, String sourceUserKey, int itemPage, boolean enabled,
                                                    List<String> cancelItemKeys) {
        Map<String, Object> button = cardActionButton(text, "default", ACTION_ITEM_PAGE, sourceUserKey, null, null,
            itemPage, cancelItemKeys, null);
        if (!enabled) {
            button.put("disabled", true);
        }
        return button;
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

    private Map<String, Object> markdownElement(String content) {
        return Map.of(
            "tag", "markdown",
            "content", firstText(content, "-")
        );
    }

    private void appendCandidateRows(List<Map<String, Object>> elements, List<ProcurementAiSessionVO> sessions,
                                     String sourceUserKey, Integer itemPage) {
        appendCandidateRows(elements, sessions, sourceUserKey, itemPage, List.of());
    }

    private void appendCandidateRows(List<Map<String, Object>> elements, List<ProcurementAiSessionVO> sessions,
                                     String sourceUserKey, Integer itemPage, List<String> selectedCancelItemKeys) {
        int total = activeCandidateCount(sessions);
        int pageCount = candidatePageCount(total);
        int currentPage = normalizeCandidatePage(itemPage, total);
        List<String> selectedKeys = cancelItemKeysOnActiveCandidates(selectedCancelItemKeys, sessions);
        int fromIndex = total <= 0 ? 0 : (currentPage - 1) * BOT_CARD_DETAIL_ITEM_LIMIT + 1;
        int toIndex = Math.min(total, currentPage * BOT_CARD_DETAIL_ITEM_LIMIT);
        elements.add(markdownElement(total > BOT_CARD_DETAIL_ITEM_LIMIT
            ? "**采购明细**（第 " + currentPage + "/" + pageCount + " 页，显示 " + fromIndex + "-" + toIndex + " 条，共 " + total + " 条）"
            : "**采购明细**"));
        int index = 1;
        if (sessions != null) {
            boolean reachedLimit = false;
            for (ProcurementAiSessionVO session : sessions) {
                for (ProcurementAiCandidateVO candidate : activeCandidates(session)) {
                    if (index < fromIndex) {
                        index++;
                        continue;
                    }
                    if (index > toIndex) {
                        reachedLimit = true;
                        break;
                    }
                    String candidateKey = candidateKey(session, candidate);
                    elements.add(candidateRow(index++, session, candidate, sourceUserKey, currentPage,
                        selectedKeys.contains(candidateKey), selectedKeys));
                }
                if (reachedLimit) {
                    break;
                }
            }
        }
        if (index == 1) {
            elements.add(markdownElement("-"));
        }
        if (total > BOT_CARD_DETAIL_ITEM_LIMIT) {
            elements.add(buttonRow(List.of(
                candidatePageButton("上一页", sourceUserKey, currentPage - 1, currentPage > 1, selectedKeys),
                candidatePageButton("下一页", sourceUserKey, currentPage + 1, currentPage < pageCount, selectedKeys)
            )));
        }
    }

    private Map<String, Object> candidateRow(int index, ProcurementAiSessionVO session, ProcurementAiCandidateVO candidate,
                                             String sourceUserKey) {
        return candidateRow(index, session, candidate, sourceUserKey, 1);
    }

    private Map<String, Object> candidateRow(int index, ProcurementAiSessionVO session, ProcurementAiCandidateVO candidate,
                                             String sourceUserKey, Integer itemPage) {
        return candidateRow(index, session, candidate, sourceUserKey, itemPage, false, List.of());
    }

    private Map<String, Object> candidateRow(int index, ProcurementAiSessionVO session, ProcurementAiCandidateVO candidate,
                                             String sourceUserKey, Integer itemPage, boolean selected,
                                             List<String> selectedCancelItemKeys) {
        Map<String, Object> leftColumn = new LinkedHashMap<>();
        leftColumn.put("tag", "column");
        leftColumn.put("width", "weighted");
        leftColumn.put("weight", 1);
        leftColumn.put("elements", List.of(markdownElement(candidateLineMarkdown(index, candidate, selected))));

        Map<String, Object> rightColumn = new LinkedHashMap<>();
        rightColumn.put("tag", "column");
        rightColumn.put("width", "auto");
        rightColumn.put("elements", List.of(cardActionButton(selected ? "恢复" : "撤回",
            selected ? "default" : "danger",
            ACTION_TOGGLE_CANCEL_ITEM,
            sourceUserKey,
            session == null ? null : session.getSessionNo(),
            candidate == null ? null : candidate.getCandidateNo(),
            itemPage,
            selectedCancelItemKeys,
            candidateKey(session, candidate))));

        return Map.of(
            "tag", "column_set",
            "flex_mode", "none",
            "horizontal_spacing", "default",
            "background_style", "default",
            "columns", List.of(leftColumn, rightColumn)
        );
    }

    private Map<String, Object> versionElement() {
        return markdownElement("卡片版本：" + BOT_CARD_VERSION);
    }

    private Map<String, Object> plainText(String content) {
        return Map.of("tag", "plain_text", "content", firstText(content, "-"));
    }

    private String requestItemsMarkdown(List<PurchaseRequestItemVO> items) {
        StringBuilder builder = new StringBuilder("**采购明细**");
        int limit = Math.min(items.size(), 10);
        for (int i = 0; i < limit; i++) {
            PurchaseRequestItemVO item = items.get(i);
            builder.append("\n").append(i + 1).append(". ")
                .append(markdownText(item.getItemName()));
            if (StringUtils.hasText(item.getReqType())) {
                builder.append(" / 类型：").append(markdownText(item.getReqType()));
            }
            if (StringUtils.hasText(item.getSpecModel())) {
                builder.append(" / ").append(markdownText(item.getSpecModel()));
            }
            builder.append(" / ")
                .append(decimalText(item.getQty())).append(markdownText(item.getUnit()))
                .append(" / ").append(moneyText(item.getLineAmount()));
            if (StringUtils.hasText(item.getPurpose())) {
                builder.append("\n   用途：").append(markdownText(item.getPurpose()));
            }
        }
        if (items.size() > limit) {
            builder.append("\n... 还有 ").append(items.size() - limit).append(" 条明细，请到系统查看完整数据。");
        }
        return builder.toString();
    }

    private String candidateLineMarkdown(int index, ProcurementAiCandidateVO candidate) {
        return candidateLineMarkdown(index, candidate, false);
    }

    private String candidateLineMarkdown(int index, ProcurementAiCandidateVO candidate, boolean selected) {
        StringBuilder builder = new StringBuilder();
        StringBuilder line = new StringBuilder();
        line.append(index).append(". ")
            .append(markdownText(firstText(candidate.getItemName(), "未识别物品")));
        if (StringUtils.hasText(candidate.getReqType())) {
            line.append(" / 类型：").append(markdownText(candidate.getReqType()));
        }
        if (StringUtils.hasText(candidate.getSpecModel())) {
            line.append(" / ").append(markdownText(candidate.getSpecModel()));
        }
        line.append(" / ")
            .append(decimalText(candidate.getQty())).append(markdownText(firstText(candidate.getUnit(), "件")))
            .append(" / 参考价 ").append(moneyText(candidate.getUnitPrice()));
        builder.append(selected ? strikeMarkdown(line.toString()) + "  **待撤回**" : line);
        if (StringUtils.hasText(candidate.getPurchaseReason())) {
            String purpose = "用途：" + markdownText(candidate.getPurchaseReason());
            builder.append("\n   ").append(selected ? strikeMarkdown(purpose) : purpose);
        }
        if (StringUtils.hasText(candidate.getEcommerceUrl())) {
            builder.append("\n   链接：[打开商品](").append(markdownUrl(candidate.getEcommerceUrl())).append(")");
        }
        return builder.toString();
    }

    private void appendMarkdownLine(StringBuilder builder, String label, String value, boolean skipBlank) {
        if (skipBlank && !StringUtils.hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append("**").append(label).append("**：").append(markdownText(value));
    }

    private List<ProcurementAiCandidateVO> activeCandidates(ProcurementAiSessionVO session) {
        if (session == null || session.getCandidates() == null) {
            return List.of();
        }
        return session.getCandidates().stream()
            .filter(candidate -> candidate != null && (!StringUtils.hasText(candidate.getStatus()) || "ACTIVE".equals(candidate.getStatus())))
            .toList();
    }

    private List<String> cancelItemKeys(Object rawValue) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectCancelItemKeys(rawValue, result);
        return List.copyOf(result);
    }

    private void collectCancelItemKeys(Object rawValue, Set<String> result) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectCancelItemKeys(item, result);
            }
            return;
        }
        String value = text(rawValue);
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String key : value.split(",")) {
            if (StringUtils.hasText(key)) {
                result.add(key.trim());
            }
        }
    }

    private List<String> normalizedCancelItemKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private List<String> cancelItemKeysOnActiveCandidates(List<String> keys, List<ProcurementAiSessionVO> sessions) {
        List<String> normalizedKeys = normalizedCancelItemKeys(keys);
        if (normalizedKeys.isEmpty()) {
            return List.of();
        }
        Set<String> activeKeys = activeCandidateKeys(sessions);
        return normalizedKeys.stream()
            .filter(activeKeys::contains)
            .toList();
    }

    private Set<String> activeCandidateKeys(List<ProcurementAiSessionVO> sessions) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (sessions == null) {
            return keys;
        }
        for (ProcurementAiSessionVO session : sessions) {
            for (ProcurementAiCandidateVO candidate : activeCandidates(session)) {
                String key = candidateKey(session, candidate);
                if (StringUtils.hasText(key)) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    private List<String> toggleCancelItemKey(List<String> selectedKeys, String candidateKey) {
        LinkedHashSet<String> result = new LinkedHashSet<>(normalizedCancelItemKeys(selectedKeys));
        if (StringUtils.hasText(candidateKey)) {
            if (!result.remove(candidateKey)) {
                result.add(candidateKey);
            }
        }
        return List.copyOf(result);
    }

    private String candidateKey(ProcurementAiSessionVO session, ProcurementAiCandidateVO candidate) {
        return candidateKey(session == null ? null : session.getSessionNo(),
            candidate == null ? null : candidate.getCandidateNo());
    }

    private String candidateKey(String sessionNo, Integer candidateNo) {
        if (!StringUtils.hasText(sessionNo)) {
            return "";
        }
        return sessionNo.trim() + "#" + (candidateNo == null ? 1 : candidateNo);
    }

    private int activeCandidateCount(List<ProcurementAiSessionVO> sessions) {
        if (sessions == null) {
            return 0;
        }
        return sessions.stream().mapToInt(session -> activeCandidates(session).size()).sum();
    }

    private int candidatePageCount(int total) {
        return Math.max(1, (Math.max(total, 0) + BOT_CARD_DETAIL_ITEM_LIMIT - 1) / BOT_CARD_DETAIL_ITEM_LIMIT);
    }

    private int normalizeCandidatePage(Integer itemPage, int total) {
        int page = itemPage == null ? 1 : itemPage;
        int pageCount = candidatePageCount(total);
        if (page < 1) {
            return 1;
        }
        return Math.min(page, pageCount);
    }

    private List<PurchaseRequestItemVO> firstRequestItems(List<ProcurementAiConfirmResultVO> results) {
        if (results == null) {
            return List.of();
        }
        for (ProcurementAiConfirmResultVO result : results) {
            if (result != null && result.getRequest() != null && result.getRequest().getItems() != null) {
                return result.getRequest().getItems();
            }
        }
        return List.of();
    }

    private String moneyText(java.math.BigDecimal value) {
        return "¥" + (value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private String decimalText(java.math.BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String markdownText(String value) {
        String text = firstText(value, "-");
        return text
            .replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("`", "\\`");
    }

    private String markdownUrl(String url) {
        String trimmed = firstText(url, "");
        if (!StringUtils.hasText(trimmed)) {
            return "";
        }
        if (!trimmed.regionMatches(true, 0, "http://", 0, 7)
            && !trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            trimmed = "https://" + trimmed;
        }
        return trimmed.replace(")", "%29").replace(" ", "%20");
    }

    private String strikeMarkdown(String value) {
        return "~~" + firstText(value, "-").replace("~", "") + "~~";
    }

    private ProcurementAiCandidateVO firstCandidate(ProcurementAiSessionVO session) {
        if (session == null || session.getCandidates() == null || session.getCandidates().isEmpty()) {
            return null;
        }
        return session.getCandidates().get(0);
    }

    private boolean isConfirmMessage(String text) {
        return StringUtils.hasText(text) && CONFIRM_PATTERN.matcher(text).matches();
    }

    private boolean isSubmitConfirmMessage(String text) {
        String normalized = normalizeCommand(text);
        return "确认提交".equals(normalized) || "确认全部提交".equals(normalized) || "确认提交全部".equals(normalized);
    }

    private boolean isSubmitPreviewMessage(String text) {
        String normalized = normalizeCommand(text);
        return "可以提交了".equals(normalized)
            || "可以提交".equals(normalized)
            || "提交吧".equals(normalized)
            || "提交".equals(normalized)
            || "查看草稿".equals(normalized)
            || "查看汇总".equals(normalized)
            || "汇总".equals(normalized)
            || "草稿".equals(normalized);
    }

    private boolean shouldSkipIntentClassification(BotMessage message, ProcurementAiStartDTO dto) {
        if (ProcurementAiAnalyzer.isImageRecognitionDraft(dto)) {
            return true;
        }
        if (message != null && isImageMessage(message) && procurementAiAnalyzer.canAnalyzeWithM3(dto)) {
            return true;
        }
        return hasClearProcurementText(dto == null ? null : dto.getRawText());
    }

    private boolean isImageWithoutUserDemand(BotMessage message) {
        return isImageWithoutUserDemandText(
            message == null ? null : message.messageType(),
            message == null ? null : message.text()
        );
    }

    static boolean isImageWithoutUserDemandText(String messageType, String text) {
        return "image".equalsIgnoreCase(messageType == null ? "" : messageType.trim())
            && !StringUtils.hasText(text);
    }

    private ImageOnlyDraft prepareImageOnlyDraft(ProcurementAiStartDTO dto, String sourceUserKey) {
        ProcurementAiAnalysisResult analysis = null;
        String requestJson = "";
        String responseText = "";
        String parsedJson = toSafeJson(Map.of(
            "source", "local_image_only_guard",
            "reason", "image_without_user_demand"
        ));
        if (procurementAiAnalyzer.canAnalyzeWithM3(dto)) {
            try {
                analysis = procurementAiAnalyzer.analyze(dto, sourceUserKey);
                dto.setRawText(imageRecognitionDraftText(dto.getRawText(), analysis));
                requestJson = firstText(analysis.getDifyRequestJson(), "");
                responseText = firstText(analysis.getDifyResponseText(), "");
                parsedJson = firstText(analysis.getDifyParsedJson(), parsedJson);
            } catch (RuntimeException ex) {
                log.warn("Analyze image-only procurement draft failed", ex);
                parsedJson = toSafeJson(Map.of(
                    "source", "local_image_only_guard",
                    "reason", "image_without_user_demand",
                    "analysisError", firstText(ex.getMessage(), ex.getClass().getName())
                ));
            }
        }
        return new ImageOnlyDraft(dto, analysis, requestJson, responseText, parsedJson);
    }

    static String imageDemandClarificationReply(ProcurementAiAnalysisResult analysis) {
        String prefix = imageRecognitionReplyPrefix(analysis);
        List<String> missingFields = imageDraftMissingFields(analysis);
        if (StringUtils.hasText(prefix)) {
            return prefix + "。请补充" + joinChinese(missingFields) + "；如识别有误，请直接更正。";
        }
        return "已收到图片，但未能稳定识别完整商品信息。请补充" + joinChinese(missingFields) + "；如识别有误，请直接更正。";
    }

    static String imageRecognitionDraftText(String rawText, ProcurementAiAnalysisResult analysis) {
        List<ProcurementAiCandidateDraft> candidates = recognizedImageCandidates(analysis);
        String baseText = firstText(rawText, "图片采购需求");
        if (candidates.isEmpty()) {
            return baseText;
        }
        StringBuilder builder = new StringBuilder(baseText);
        builder.append("\n图片识别结果（仅作草稿上下文，不代表用户已确认采购数量和用途）：");
        int maxRows = Math.min(candidates.size(), 5);
        for (int i = 0; i < maxRows; i++) {
            ProcurementAiCandidateDraft candidate = candidates.get(i);
            builder.append("\n").append(i + 1).append(". 商品名称：")
                .append(firstText(candidate.getItemName(), "未识别"));
            if (StringUtils.hasText(candidate.getSpecModel())) {
                builder.append("；规格型号：").append(candidate.getSpecModel().trim());
            }
            if (candidate.getUnitPrice() != null && candidate.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                builder.append("；参考单价：¥").append(formatMoney(candidate.getUnitPrice()));
            }
            if (StringUtils.hasText(candidate.getUnit())) {
                builder.append("；建议单位：").append(candidate.getUnit().trim());
            }
        }
        builder.append("\n待用户补充：采购数量、用途；如识别有误请直接更正。");
        return limit(builder.toString(), 1800);
    }

    private String imageOnlyDraftSummary(ProcurementAiAnalysisResult analysis) {
        if (recognizedImageCandidates(analysis).isEmpty()) {
            return "图片消息未包含用户文字，已转入待补充草稿";
        }
        return "图片消息已识别商品信息并转入待补充草稿，等待用户补充采购数量和用途";
    }

    private static String imageRecognitionReplyPrefix(ProcurementAiAnalysisResult analysis) {
        ProcurementAiCandidateDraft candidate = firstRecognizedImageCandidate(analysis);
        if (candidate == null || !StringUtils.hasText(candidate.getItemName())) {
            return "";
        }
        List<String> details = new ArrayList<>();
        if (StringUtils.hasText(candidate.getSpecModel())) {
            details.add("规格：" + candidate.getSpecModel().trim());
        }
        if (candidate.getUnitPrice() != null && candidate.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            details.add("参考单价：¥" + formatMoney(candidate.getUnitPrice()));
        }
        StringBuilder builder = new StringBuilder("已从图片识别到：").append(candidate.getItemName().trim());
        if (!details.isEmpty()) {
            builder.append("（").append(String.join("，", details)).append("）");
        }
        return builder.toString();
    }

    private static List<String> imageDraftMissingFields(ProcurementAiAnalysisResult analysis) {
        ProcurementAiCandidateDraft candidate = firstRecognizedImageCandidate(analysis);
        List<String> fields = new ArrayList<>();
        if (candidate == null || !StringUtils.hasText(candidate.getItemName())) {
            fields.add("商品名称");
        }
        if (candidate == null || !StringUtils.hasText(candidate.getSpecModel())) {
            fields.add("规格型号");
        }
        fields.add("采购数量");
        fields.add("用途");
        if (candidate == null || candidate.getUnitPrice() == null || candidate.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            fields.add("参考单价/预算");
        }
        return fields;
    }

    private static ProcurementAiCandidateDraft firstRecognizedImageCandidate(ProcurementAiAnalysisResult analysis) {
        List<ProcurementAiCandidateDraft> candidates = recognizedImageCandidates(analysis);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static List<ProcurementAiCandidateDraft> recognizedImageCandidates(ProcurementAiAnalysisResult analysis) {
        if (analysis == null || analysis.getCandidates() == null) {
            return List.of();
        }
        return analysis.getCandidates().stream()
            .filter(candidate -> candidate != null && StringUtils.hasText(candidate.getItemName()))
            .toList();
    }

    private static String joinChinese(List<String> values) {
        List<String> items = values == null ? List.of() : values.stream()
            .filter(StringUtils::hasText)
            .toList();
        if (items.isEmpty()) {
            return "采购数量和用途";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + "和" + items.get(1);
        }
        return String.join("、", items.subList(0, items.size() - 1)) + "和" + items.get(items.size() - 1);
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static boolean hasClearProcurementText(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String value = text.trim();
        if (value.startsWith(FEISHU_EXCEL_PROMPT_PREFIX)) {
            return false;
        }
        boolean hasProcurementSignal = PROCUREMENT_FAST_PATH_PATTERN.matcher(value).find();
        boolean hasPriceSignal = PRICE_FAST_PATH_PATTERN.matcher(value).find();
        boolean hasQuantitySignal = QUANTITY_FAST_PATH_PATTERN.matcher(value).find();
        boolean hasUrlSignal = URL_PATTERN.matcher(value).find();
        return hasPriceSignal && hasProcurementSignal && (hasQuantitySignal || hasUrlSignal);
    }

    private int resolveUrgencyLevel(String text) {
        String normalized = firstText(text, "").toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return 1;
        }
        if (normalized.contains("紧急") || normalized.contains("特急") || normalized.contains("急用")
            || normalized.contains("马上要") || normalized.contains("立即要") || normalized.contains("尽快采购")
            || normalized.contains("尽快买")) {
            return 2;
        }
        return 1;
    }

    private String normalizeCommand(String text) {
        return firstText(text, "").replaceAll("\\s+", "");
    }

    private boolean isSelfOrBotMessage(BotMessage message) {
        String senderType = firstText(message.senderType(), "").toLowerCase(Locale.ROOT);
        return "app".equals(senderType) || "bot".equals(senderType);
    }

    private boolean isDuplicateMessage(BotMessage message) {
        return StringUtils.hasText(message.messageId())
            && messageLogService.existsBySourceAndMessageId("FEISHU_BOT", message.messageId());
    }

    private String requestRouteText(PurchaseRequestVO request) {
        if (request != null && request.getUrgencyLevel() != null && request.getUrgencyLevel() >= 2) {
            return "紧急采购已按现有流程提交楼长审核";
        }
        return "普通采购已按现有流程提交楼长审核";
    }

    private Long safeRecordIncoming(Map<String, Object> payload, BotMessage message) {
        try {
            ProcurementAiMessageLog log = new ProcurementAiMessageLog();
            log.setSourceChannel("FEISHU_BOT");
            log.setEventType(limit(message.eventType(), 80));
            log.setMessageId(limit(message.messageId(), 128));
            log.setChatId(limit(message.chatId(), 128));
            log.setChatType(limit(message.chatType(), 32));
            log.setMessageType(limit(message.messageType(), 32));
            log.setSourceUserKey(limit(message.sourceUserKey(), 128));
            log.setSenderOpenId(limit(message.openId(), 128));
            log.setSenderUserId(limit(message.userId(), 128));
            log.setSenderUnionId(limit(message.unionId(), 128));
            log.setRawText(limit(message.text(), 4000));
            log.setRawContent(limit(message.rawContent(), 12000));
            log.setRawPayload(limit(toSafeJson(payload), 50000));
            log.setProcessStatus("RECEIVED");
            log.setOccurredAt(LocalDateTime.now());
            return messageLogService.record(log);
        } catch (RuntimeException ex) {
            log.warn("Record procurement AI message log failed", ex);
            return null;
        }
    }

    private ProcurementAiStartDTO buildStartDTO(BotMessage message, Long messageLogId, AuthUser user) {
        ProcurementAiStartDTO dto = new ProcurementAiStartDTO();
        String excelText = buildExcelAttachmentText(message, messageLogId, user);
        String rawText = firstText(message.text(), StringUtils.hasText(excelText) ? "我要采购 Excel 附件中的明细" : "\u56fe\u7247\u91c7\u8d2d\u9700\u6c42");
        if (StringUtils.hasText(excelText)) {
            rawText = rawText + "\n" + excelText;
        }
        dto.setRawText(rawText);
        dto.setImageKey(message.imageKey());
        ImageRelay imageRelay = relayImageMessage(message);
        dto.setImageUrl(imageRelay.publicUrl());
        dto.setImageDataUrl(imageRelay.dataUrl());
        dto.setReqType("\u5176\u4ed6");
        dto.setPlanMonth(LocalDate.now().toString().substring(0, 7));
        int urgencyLevel = resolveUrgencyLevel(message.text());
        dto.setUrgencyLevel(urgencyLevel);
        dto.setEmergency(urgencyLevel >= 2);
        return dto;
    }

    private String buildExcelAttachmentText(BotMessage message, Long messageLogId, AuthUser user) {
        if (message == null || !StringUtils.hasText(message.imageKey()) || !StringUtils.hasText(message.messageId())) {
            return "";
        }
        Map<String, Object> content = parseContent(message.rawContent());
        String fileName = firstText(
            text(content.get("file_name")),
            text(content.get("fileName")),
            text(content.get("name")),
            text(content.get("title"))
        );
        if (!isExcelFileName(fileName) || !"file".equalsIgnoreCase(firstText(message.messageType(), text(content.get("type"))))) {
            return "";
        }
        Long importLogId = null;
        try {
            byte[] fileBytes = downloadFeishuMessageFile(message, message.imageKey());
            if (fileBytes == null || fileBytes.length == 0) {
                return "";
            }
            importLogId = importFileLogService.recordFeishuImport(
                "FEISHU_EXCEL_ATTACHMENT",
                "飞书采购助手 Excel 附件",
                fileName,
                fileBytes,
                message.sourceUserKey(),
                message.messageId(),
                messageLogId,
                user
            );
            List<List<String>> table = ExcelImportUtils.readFirstSheet(fileName, fileBytes);
            int dataRows = dataRowCount(table);
            importFileLogService.markSuccess(importLogId, dataRows, dataRows, 0, "飞书采购助手读取 Excel 明细 " + dataRows + " 条");
            return excelTableToPrompt(fileName, table);
        } catch (BizException ex) {
            importFileLogService.markFailed(importLogId, ex);
            log.warn("Read Feishu Excel attachment failed, fileName={}", fileName, ex);
            return "飞书Excel附件读取失败：" + ex.getMessage() + "。请重新上传正确的 .xlsx/.xls 文件，或复制表格内容发送。";
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(importLogId, ex);
            log.warn("Download Feishu Excel attachment failed, fileName={}", fileName, ex);
            return "飞书Excel附件读取失败：系统未能下载或解析该文件。请重新上传 Excel，或复制表格内容发送。";
        }
    }

    private byte[] downloadFeishuMessageFile(BotMessage message, String fileKey) {
        return downloadFeishuMessageResource(message, fileKey, "file");
    }

    private byte[] downloadFeishuMessageImage(BotMessage message) {
        return downloadFeishuMessageResource(message, message.imageKey(), "image");
    }

    private byte[] downloadFeishuMessageResource(BotMessage message, String fileKey, String resourceType) {
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
            .pathSegment(message.messageId(), "resources", fileKey)
            .queryParam("type", resourceType)
            .build()
            .toUriString();
        return restClient.get()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
            .retrieve()
            .body(byte[].class);
    }

    private ImageRelay relayImageMessage(BotMessage message) {
        if (!isImageMessage(message) || !StringUtils.hasText(message.imageKey()) || !StringUtils.hasText(message.messageId())) {
            return ImageRelay.empty();
        }
        try {
            byte[] bytes = downloadFeishuMessageImage(message);
            return new ImageRelay(imageStoreService.store(bytes), imageDataUrl(bytes));
        } catch (RuntimeException ex) {
            log.warn("Relay Feishu procurement image failed, messageId={}", message.messageId(), ex);
            return ImageRelay.empty();
        }
    }

    private String imageDataUrl(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (bytes.length > M3_DIRECT_IMAGE_MAX_BYTES) {
            log.warn("Skip M3 direct image payload because image is too large, bytes={}", bytes.length);
            return "";
        }
        return "data:" + imageMediaType(bytes) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String imageMediaType(byte[] bytes) {
        if (bytes.length >= 8
            && (bytes[0] & 0xff) == 0x89
            && bytes[1] == 0x50
            && bytes[2] == 0x4e
            && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3
            && (bytes[0] & 0xff) == 0xff
            && (bytes[1] & 0xff) == 0xd8
            && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 6
            && bytes[0] == 'G'
            && bytes[1] == 'I'
            && bytes[2] == 'F') {
            return "image/gif";
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
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private boolean shouldUseM3ImageFallback(BotMessage message,
                                             ProcurementAiStartDTO dto,
                                             FeishuMessageIntentResult intent) {
        if (!isImageMessage(message) || !procurementAiAnalyzer.canAnalyzeWithM3(dto)) {
            return false;
        }
        if (intent == null) {
            return true;
        }
        return intent.isNeedClarification() || "AI意图判断失败".equals(intent.getIntentName());
    }

    private boolean isImageMessage(BotMessage message) {
        return message != null && "image".equalsIgnoreCase(firstText(message.messageType(), ""));
    }

    private String excelTableToPrompt(String fileName, List<List<String>> table) {
        if (table == null || table.isEmpty()) {
            return "飞书Excel附件内容：" + fileName + "\n未读取到有效表格行。";
        }
        List<List<String>> rows = table.stream()
            .filter(this::hasAnyCellText)
            .toList();
        if (rows.isEmpty()) {
            return "飞书Excel附件内容：" + fileName + "\n未读取到有效表格行。";
        }
        List<String> headers = rows.get(0);
        StringBuilder builder = new StringBuilder();
        builder.append("飞书Excel附件内容：").append(fileName).append("\n");
        builder.append("请按表格每一行识别采购明细；商品名称、数量、用途和大于 0 的参考单价/预算缺失时必须追问，不要生成候选。\n");
        builder.append("表头：").append(joinCells(headers, FEISHU_EXCEL_MAX_COLUMNS_TO_AI)).append("\n");
        int dataCount = 0;
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            if (dataCount >= FEISHU_EXCEL_MAX_ROWS_TO_AI) {
                break;
            }
            List<String> row = rows.get(rowIndex);
            if (!hasAnyCellText(row)) {
                continue;
            }
            builder.append("第").append(++dataCount).append("行：");
            int maxColumns = Math.min(Math.max(headers.size(), row.size()), FEISHU_EXCEL_MAX_COLUMNS_TO_AI);
            boolean appended = false;
            for (int columnIndex = 0; columnIndex < maxColumns; columnIndex++) {
                String value = cell(row, columnIndex);
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                if (appended) {
                    builder.append("；");
                }
                builder.append(firstText(cell(headers, columnIndex), "第" + (columnIndex + 1) + "列"))
                    .append("=")
                    .append(value);
                appended = true;
            }
            builder.append("\n");
            if (builder.length() >= FEISHU_EXCEL_TEXT_LIMIT) {
                builder.append("表格内容较多，已截断，请到系统查看或重新上传拆分文件。\n");
                break;
            }
        }
        int remaining = Math.max(0, rows.size() - 1 - dataCount);
        if (remaining > 0) {
            builder.append("还有 ").append(remaining).append(" 行未展开，请到系统查看完整表格或拆分发送。\n");
        }
        return limit(builder.toString(), FEISHU_EXCEL_TEXT_LIMIT);
    }

    private int dataRowCount(List<List<String>> table) {
        if (table == null || table.size() <= 1) {
            return 0;
        }
        return (int) table.stream()
            .skip(1)
            .filter(this::hasAnyCellText)
            .count();
    }

    private boolean isExcelFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".xlsx") || normalized.endsWith(".xls");
    }

    private boolean hasAnyCellText(List<String> row) {
        return row != null && row.stream().anyMatch(StringUtils::hasText);
    }

    private String joinCells(List<String> row, int maxColumns) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(row.size(), maxColumns); i++) {
            String value = cell(row, i);
            values.add(StringUtils.hasText(value) ? value : "第" + (i + 1) + "列");
        }
        return String.join(" | ", values);
    }

    private String cell(List<String> row, int index) {
        return row == null || index < 0 || index >= row.size() ? "" : firstText(row.get(index), "");
    }

    private ProcurementAiStartDTO mergeDraftInput(ProcurementAiStartDTO current, ProcurementAiSessionVO draft) {
        if (draft == null) {
            return current;
        }
        ProcurementAiStartDTO merged = new ProcurementAiStartDTO();
        String draftText = firstText(draft.getRawText(), "");
        String currentText = firstText(current == null ? null : current.getRawText(), "");
        merged.setRawText(firstText(
            "历史待补充采购需求：\n" + draftText + "\n本次用户补充：\n" + currentText,
            currentText,
            draftText
        ));
        merged.setImageKey(firstText(current == null ? null : current.getImageKey(), draft.getImageKey()));
        merged.setImageUrl(firstText(current == null ? null : current.getImageUrl(), draft.getImageUrl()));
        merged.setImageDataUrl(current == null ? null : current.getImageDataUrl());
        merged.setReqTitle(firstText(draft.getReqTitle(), current == null ? null : current.getReqTitle(), currentText));
        merged.setReqType(firstText(nonDefaultReqType(current == null ? null : current.getReqType()), draft.getReqType(), current == null ? null : current.getReqType(), "服务/费用/低值易耗等"));
        merged.setPlanMonth(firstText(current == null ? null : current.getPlanMonth(), draft.getPlanMonth()));
        merged.setDeptCode(firstText(current == null ? null : current.getDeptCode(), draft.getDeptCode()));
        merged.setDeptName(firstText(current == null ? null : current.getDeptName(), draft.getDeptName()));
        merged.setBudgetCode(firstText(current == null ? null : current.getBudgetCode(), draft.getBudgetCode()));
        int currentUrgency = current == null || current.getUrgencyLevel() == null ? 1 : current.getUrgencyLevel();
        int draftUrgency = draft.getUrgencyLevel() == null ? 1 : draft.getUrgencyLevel();
        merged.setUrgencyLevel(Math.max(currentUrgency, draftUrgency));
        merged.setEmergency(Boolean.TRUE.equals(current == null ? null : current.getEmergency()) || Boolean.TRUE.equals(draft.getEmergency()));
        merged.setNeedDate(firstText(current == null ? null : current.getNeedDate(), draft.getNeedDate() == null ? null : draft.getNeedDate().toString()));
        merged.setRemark(firstText(current == null ? null : current.getRemark(), draft.getAiSummary()));
        return merged;
    }

    private String nonDefaultReqType(String reqType) {
        if (!StringUtils.hasText(reqType) || "其他".equals(reqType.trim()) || "服务/费用/低值易耗等".equals(reqType.trim())) {
            return "";
        }
        return reqType;
    }

    private String clarificationReply(FeishuMessageIntentResult intent) {
        return firstText(
            intent == null ? null : intent.getClarificationQuestion(),
            intent == null ? null : intent.getReply(),
            "这条采购信息还不完整，请补充商品名称、数量、用途、规格和大于 0 的参考单价或预算。"
        );
    }

    private void safeAttachUser(Long logId, AuthUser user) {
        if (logId == null || user == null) {
            return;
        }
        ProcurementAiMessageLog update = new ProcurementAiMessageLog();
        update.setId(logId);
        update.setApplicantUserId(user.getUserId());
        update.setUsername(limit(user.getUsername(), 64));
        update.setRealName(limit(user.getRealName(), 128));
        safeUpdateLog(update);
    }

    private void safeUpdateRawText(Long logId, String rawText) {
        if (logId == null || !StringUtils.hasText(rawText)) {
            return;
        }
        ProcurementAiMessageLog update = new ProcurementAiMessageLog();
        update.setId(logId);
        update.setRawText(limit(rawText, 4000));
        safeUpdateLog(update);
    }

    private void safeFinishSuccess(Long logId, String intentCode, String intentName, String replyText,
                                   String resultSummary, ProcurementAiConfirmResultVO result, long startedAt) {
        ProcurementAiMessageLog update = resultUpdate(logId, intentCode, intentName, "SUCCESS", replyText, resultSummary, startedAt);
        fillConfirmResult(update, result);
        safeUpdateLog(update);
    }

    private void safeFinishSuccess(Long logId, String intentCode, String intentName, String replyText,
                                   String resultSummary, ProcurementAiSessionVO session, Integer candidateNo, long startedAt) {
        ProcurementAiMessageLog update = resultUpdate(logId, intentCode, intentName, "SUCCESS", replyText, resultSummary, startedAt);
        fillSession(update, session, candidateNo);
        safeUpdateLog(update);
    }

    private void safeFinishIgnored(Long logId, String intentCode, String intentName, String errorMessage,
                                   String replyText, long startedAt) {
        ProcurementAiMessageLog update = resultUpdate(logId, intentCode, intentName, "IGNORED", replyText, errorMessage, startedAt);
        update.setErrorMessage(limit(errorMessage, 4000));
        safeUpdateLog(update);
    }

    private void safeFinishFailed(Long logId, String intentCode, String intentName, String errorCode,
                                  String errorMessage, String replyText, long startedAt) {
        ProcurementAiMessageLog update = resultUpdate(logId, intentCode, intentName, "FAILED", replyText, errorMessage, startedAt);
        update.setErrorCode(limit(errorCode, 128));
        update.setErrorMessage(limit(errorMessage, 4000));
        safeUpdateLog(update);
    }

    private void safeUpdateDifyDebug(Long logId, FeishuMessageIntentResult intent, ProcurementAiSessionVO session) {
        if (logId == null) {
            return;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> parsed = new LinkedHashMap<>();
        if (intent != null) {
            putIfDebug(request, "intent", intent.getDifyRequestJson());
            putIfDebug(response, "intent", intent.getDifyResponseText());
            putIfDebug(parsed, "intent", intent.getDifyParsedJson());
        }
        if (session != null) {
            putIfDebug(request, "candidate", session.getDifyRequestJson());
            putIfDebug(response, "candidate", session.getDifyResponseText());
            putIfDebug(parsed, "candidate", session.getDifyParsedJson());
        }
        if (request.isEmpty() && response.isEmpty() && parsed.isEmpty()) {
            return;
        }
        ProcurementAiMessageLog update = new ProcurementAiMessageLog();
        update.setId(logId);
        update.setDifyRequestJson(limit(toSafeJson(request), 50000));
        update.setDifyResponseText(limit(toSafeJson(response), 50000));
        update.setDifyParsedJson(limit(toSafeJson(parsed), 50000));
        safeUpdateLog(update);
    }

    private void safeUpdateM3CommandDebug(Long logId, CandidateCommand command) {
        if (logId == null || command == null) {
            return;
        }
        ProcurementAiMessageLog update = new ProcurementAiMessageLog();
        update.setId(logId);
        update.setDifyRequestJson(limit(command.requestJson(), 50000));
        update.setDifyResponseText(limit(command.responseText(), 50000));
        update.setDifyParsedJson(limit(command.parsedJson(), 50000));
        safeUpdateLog(update);
    }

    private void putIfDebug(Map<String, Object> target, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        target.put(key, parseDebugJson(value));
    }

    private Object parseDebugJson(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (RuntimeException | JsonProcessingException ex) {
            return value;
        }
    }

    private ProcurementAiMessageLog resultUpdate(Long logId, String intentCode, String intentName, String status,
                                                 String replyText, String resultSummary, long startedAt) {
        ProcurementAiMessageLog update = new ProcurementAiMessageLog();
        update.setId(logId);
        update.setIntentCode(limit(intentCode, 64));
        update.setIntentName(limit(intentName, 128));
        update.setProcessStatus(status);
        update.setReplyText(limit(replyText, 4000));
        update.setResultSummary(limit(resultSummary, 4000));
        update.setDurationMs(Math.max(0L, System.currentTimeMillis() - startedAt));
        return update;
    }

    private void fillSession(ProcurementAiMessageLog update, ProcurementAiSessionVO session, Integer candidateNo) {
        if (update == null || session == null) {
            return;
        }
        update.setSessionId(session.getId());
        update.setSessionNo(limit(session.getSessionNo(), 64));
        update.setCandidateNo(candidateNo);
        update.setPurchaseRequestId(session.getPurchaseRequestId());
    }

    private void fillConfirmResult(ProcurementAiMessageLog update, ProcurementAiConfirmResultVO result) {
        if (update == null || result == null) {
            return;
        }
        update.setSessionId(result.getSessionId());
        update.setSessionNo(limit(result.getSessionNo(), 64));
        update.setCandidateNo(result.getCandidateNo());
        update.setPurchaseRequestId(result.getPurchaseRequestId());
        update.setPurchaseRequestNo(limit(result.getPurchaseRequestNo(), 64));
        PurchaseRequestVO request = result.getRequest();
        if (request != null) {
            update.setResultSummary(limit(firstText(update.getResultSummary(),
                firstText(result.getPurchaseRequestNo(), request.getReqNo(), result.getSessionNo())
                    + " / " + firstText(request.getReqTitle(), result.getSessionNo())), 4000));
        }
    }

    private void safeUpdateLog(ProcurementAiMessageLog update) {
        if (update == null || update.getId() == null) {
            return;
        }
        try {
            messageLogService.updateResult(update);
        } catch (RuntimeException ex) {
            log.warn("Update procurement AI message log failed", ex);
        }
    }

    private ProcurementAiConfirmResultVO firstConfirmResult(List<ProcurementAiConfirmResultVO> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.stream().filter(item -> item != null).findFirst().orElse(null);
    }

    private ProcurementAiSessionVO firstSession(List<ProcurementAiSessionVO> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        return sessions.stream().filter(item -> item != null).findFirst().orElse(null);
    }

    private String buildConfirmAllSummary(List<ProcurementAiConfirmResultVO> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ProcurementAiConfirmResultVO result : results) {
            if (result == null || result.getRequest() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(firstText(result.getPurchaseRequestNo(), result.getSessionNo()))
                .append(" / ")
                .append(firstText(result.getRequest().getReqTitle(), result.getSessionNo()));
        }
        return builder.toString();
    }

    private String toSafeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(sanitizeForLog(value));
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    private Object sanitizeForLog(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> target = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                target.put(key, isSensitiveKey(key) ? "***" : sanitizeForLog(entry.getValue()));
            }
            return target;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> target = new ArrayList<>();
            for (Object item : iterable) {
                target.add(sanitizeForLog(item));
            }
            return target;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("authorization")
            || normalized.contains("encrypt_key");
    }

    private SysUser findBoundUser(BotMessage message) {
        SysUser user = findUserByFeishuIds(message);
        if (user != null) {
            return user;
        }
        for (String username : distinct(message.userId(), message.openId(), message.unionId())) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
            if (user != null) {
                return user;
            }
        }
        for (String employeeNo : distinct(message.userId())) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeNo, employeeNo)
                .last("LIMIT 1"));
            if (user != null) {
                return user;
            }
        }
        for (String token : distinct(message.userId(), message.openId(), message.unionId())) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .like(SysUser::getRemark, token)
                .last("LIMIT 1"));
            if (user != null) {
                return user;
            }
        }
        user = findUserByConfiguredEmailOrMobile(message);
        return user == null ? findUserByFeishuProfile(message) : user;
    }

    private SysUser findUserByFeishuIds(BotMessage message) {
        for (String userId : distinct(message.userId())) {
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getFeishuUserId, userId)
                .last("LIMIT 1"));
            if (user != null) {
                return user;
            }
        }
        for (String openId : distinct(message.openId())) {
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getFeishuOpenId, openId)
                .last("LIMIT 1"));
            if (user != null) {
                return user;
            }
        }
        for (String unionId : distinct(message.unionId())) {
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getFeishuUnionId, unionId)
                .last("LIMIT 1"));
            if (user != null) {
                return user;
            }
        }
        return null;
    }

    private SysUser findUserByConfiguredEmailOrMobile(BotMessage message) {
        if (!StringUtils.hasText(message.userId())
            && !StringUtils.hasText(message.openId())
            && !StringUtils.hasText(message.unionId())) {
            return null;
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .and(wrapper -> wrapper
                .isNotNull(SysUser::getEmail)
                .ne(SysUser::getEmail, "")
                .or()
                .isNotNull(SysUser::getMobile)
                .ne(SysUser::getMobile, ""))
            .last("LIMIT " + FEISHU_ID_LOOKUP_MAX_USERS));
        if (users.isEmpty()) {
            return null;
        }
        for (FeishuIdCandidate idCandidate : feishuIdCandidates(message)) {
            for (int from = 0; from < users.size(); from += FEISHU_ID_LOOKUP_BATCH_SIZE) {
                List<SysUser> batch = users.subList(from, Math.min(from + FEISHU_ID_LOOKUP_BATCH_SIZE, users.size()));
                SysUser matched = findUserByBatchGetId(message, idCandidate, batch);
                if (matched != null) {
                    return matched;
                }
            }
        }
        return null;
    }

    private SysUser findUserByBatchGetId(BotMessage message, FeishuIdCandidate idCandidate, List<SysUser> users) {
        Map<String, SysUser> emailUsers = new LinkedHashMap<>();
        Map<String, SysUser> mobileUsers = new LinkedHashMap<>();
        for (SysUser user : users) {
            if (StringUtils.hasText(user.getEmail())) {
                emailUsers.put(user.getEmail().trim().toLowerCase(Locale.ROOT), user);
            }
            for (String mobile : mobileCandidates(user.getMobile())) {
                mobileUsers.put(mobile, user);
            }
        }
        if (emailUsers.isEmpty() && mobileUsers.isEmpty()) {
            return null;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (!emailUsers.isEmpty()) {
            body.put("emails", new ArrayList<>(emailUsers.keySet()));
        }
        if (!mobileUsers.isEmpty()) {
            body.put("mobiles", new ArrayList<>(mobileUsers.keySet()));
        }

        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        try {
            String url = UriComponentsBuilder.fromUriString(config.userBatchGetIdUrl())
                .queryParam("user_id_type", idCandidate.idType())
                .build()
                .toUriString();
            Map<String, Object> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
            Map<String, Object> data = unwrapFeishuData(response);
            for (Map<String, Object> item : objectList(firstObject(data.get("user_list"), data.get("items"), data.get("users")))) {
                if (!matchesBatchGetId(message, idCandidate, item)) {
                    continue;
                }
                SysUser matched = userByReturnedContact(item, emailUsers, mobileUsers);
                if (matched != null) {
                    bindFeishuIdsIfNeeded(matched, profileFromBatchGetId(item, message, idCandidate));
                    return matched;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Resolve Feishu bot sender by configured email/mobile failed, idType={}", idCandidate.idType(), ex);
        }
        return null;
    }

    private boolean matchesBatchGetId(BotMessage message, FeishuIdCandidate idCandidate, Map<String, Object> item) {
        String returnedPrimaryId = firstText(text(item.get("user_id")), text(item.get("id")));
        String expectedPrimaryId = messageIdByType(message, idCandidate.idType());
        return equalsText(returnedPrimaryId, expectedPrimaryId)
            || equalsText(text(item.get("open_id")), message.openId())
            || equalsText(text(item.get("union_id")), message.unionId());
    }

    private SysUser userByReturnedContact(Map<String, Object> item, Map<String, SysUser> emailUsers, Map<String, SysUser> mobileUsers) {
        for (String email : distinct(text(item.get("email")), text(item.get("enterprise_email"))) ) {
            SysUser user = emailUsers.get(email.toLowerCase(Locale.ROOT));
            if (user != null) {
                return user;
            }
        }
        for (String mobile : mobileCandidates(text(item.get("mobile")))) {
            SysUser user = mobileUsers.get(mobile);
            if (user != null) {
                return user;
            }
        }
        return null;
    }

    private FeishuBotUserProfile profileFromBatchGetId(Map<String, Object> item, BotMessage message, FeishuIdCandidate idCandidate) {
        String returnedPrimaryId = firstText(text(item.get("user_id")), text(item.get("id")));
        String userId = "user_id".equals(idCandidate.idType()) ? firstText(returnedPrimaryId, message.userId()) : message.userId();
        String openId = "open_id".equals(idCandidate.idType()) ? firstText(returnedPrimaryId, message.openId()) : firstText(text(item.get("open_id")), message.openId());
        String unionId = "union_id".equals(idCandidate.idType()) ? firstText(returnedPrimaryId, message.unionId()) : firstText(text(item.get("union_id")), message.unionId());
        String email = firstText(text(item.get("enterprise_email")), text(item.get("email")));
        String mobile = normalizeMobile(text(item.get("mobile")));
        String employeeNo = text(item.get("employee_no"));
        return new FeishuBotUserProfile(userId, openId, unionId, email, mobile, employeeNo);
    }

    private SysUser findUserByFeishuProfile(BotMessage message) {
        FeishuBotUserProfile profile = fetchFeishuUserProfile(message);
        if (profile == null) {
            return null;
        }
        SysUser user = null;
        if (StringUtils.hasText(profile.email())) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, profile.email().trim())
                .last("LIMIT 1"));
        }
        if (user == null) {
            for (String mobile : mobileCandidates(profile.mobile())) {
                user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getMobile, mobile)
                    .last("LIMIT 1"));
                if (user != null) {
                    break;
                }
            }
        }
        if (user == null && StringUtils.hasText(profile.employeeNo())) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeNo, profile.employeeNo().trim())
                .last("LIMIT 1"));
        }
        if (user != null) {
            bindFeishuIdsIfNeeded(user, profile);
        }
        return user;
    }

    private FeishuBotUserProfile fetchFeishuUserProfile(BotMessage message) {
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        for (FeishuIdCandidate candidate : feishuIdCandidates(message)) {
            try {
                String detailUrl = String.format(config.userDetailUrl(), candidate.id());
                String url = UriComponentsBuilder.fromUriString(detailUrl)
                    .queryParam("user_id_type", candidate.idType())
                    .build()
                    .toUriString();
                Map<String, Object> response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                    .retrieve()
                    .body(MAP_TYPE);
                Map<String, Object> data = unwrapFeishuData(response);
                Map<String, Object> user = mapValue(firstObject(data.get("user"), data));
                FeishuBotUserProfile profile = toFeishuBotUserProfile(user, message);
                if (profile.hasContactIdentity()) {
                    return profile;
                }
            } catch (RuntimeException ex) {
                log.warn("Resolve Feishu bot sender profile failed, idType={}, id={}",
                    candidate.idType(), candidate.id(), ex);
            }
        }
        return null;
    }

    private List<FeishuIdCandidate> feishuIdCandidates(BotMessage message) {
        List<FeishuIdCandidate> candidates = new ArrayList<>();
        if (StringUtils.hasText(message.userId())) {
            candidates.add(new FeishuIdCandidate("user_id", message.userId().trim()));
        }
        if (StringUtils.hasText(message.openId())) {
            candidates.add(new FeishuIdCandidate("open_id", message.openId().trim()));
        }
        if (StringUtils.hasText(message.unionId())) {
            candidates.add(new FeishuIdCandidate("union_id", message.unionId().trim()));
        }
        return candidates;
    }

    private FeishuBotUserProfile toFeishuBotUserProfile(Map<String, Object> user, BotMessage message) {
        String userId = firstText(text(user.get("user_id")), text(user.get("id")), message.userId());
        String openId = firstText(text(user.get("open_id")), message.openId());
        String unionId = firstText(text(user.get("union_id")), message.unionId());
        String email = firstText(text(user.get("enterprise_email")), text(user.get("email")));
        String mobile = normalizeMobile(text(user.get("mobile")));
        String employeeNo = text(user.get("employee_no"));
        return new FeishuBotUserProfile(userId, openId, unionId, email, mobile, employeeNo);
    }

    private Map<String, Object> unwrapFeishuData(Map<String, Object> response) {
        if (response == null) {
            return new HashMap<>();
        }
        String responseCode = text(response.get("code"));
        if (StringUtils.hasText(responseCode) && !"0".equals(responseCode)) {
            throw new BizException("FEISHU_BOT_USER_LOOKUP_FAILED",
                firstText(text(response.get("msg")), text(response.get("message")), "获取飞书用户信息失败"));
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> target = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    target.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return target;
        }
        return response;
    }

    private Object firstObject(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            Map<String, Object> map = mapValue(item);
            if (!map.isEmpty()) {
                result.add(map);
            }
        }
        return result;
    }

    private String messageIdByType(BotMessage message, String idType) {
        return switch (idType) {
            case "open_id" -> message.openId();
            case "union_id" -> message.unionId();
            default -> message.userId();
        };
    }

    private boolean equalsText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equals(right.trim());
    }

    private void bindFeishuIdsIfNeeded(SysUser user, FeishuBotUserProfile profile) {
        boolean changed = false;
        if (StringUtils.hasText(profile.userId()) && !profile.userId().equals(user.getFeishuUserId())) {
            user.setFeishuUserId(profile.userId());
            changed = true;
        }
        if (StringUtils.hasText(profile.openId()) && !profile.openId().equals(user.getFeishuOpenId())) {
            user.setFeishuOpenId(profile.openId());
            changed = true;
        }
        if (StringUtils.hasText(profile.unionId()) && !profile.unionId().equals(user.getFeishuUnionId())) {
            user.setFeishuUnionId(profile.unionId());
            changed = true;
        }
        if (changed) {
            userMapper.updateById(user);
        }
    }

    private String normalizeMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "";
        }
        return mobile.replace(" ", "").replace("-", "").trim();
    }

    private List<String> mobileCandidates(String mobile) {
        String normalized = normalizeMobile(mobile);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);
        if (normalized.matches("1\\d{10}")) {
            candidates.add("+86" + normalized);
            candidates.add("86" + normalized);
        }
        if (normalized.startsWith("+86") && normalized.length() > 3) {
            candidates.add(normalized.substring(3));
        }
        if (normalized.startsWith("86") && normalized.length() == 13) {
            candidates.add(normalized.substring(2));
        }
        return candidates.stream().distinct().toList();
    }

    private void verifyToken(Map<String, Object> payload) {
        String verificationToken = botRuntimeConfig.current().verificationToken();
        if (!StringUtils.hasText(verificationToken)) {
            throw new BizException("FEISHU_BOT_TOKEN_MISSING", "Feishu bot verification token is not configured");
        }
        String token = firstText(text(payload.get("token")), text(mapValue(payload.get("header")).get("token")));
        if (!verificationToken.equals(token)) {
            throw new BizException("FEISHU_BOT_TOKEN_INVALID", "Feishu bot token invalid");
        }
    }

    private BotMessage parseMessage(Map<String, Object> payload) {
        Map<String, Object> header = mapValue(payload.get("header"));
        Map<String, Object> event = mapValue(payload.get("event"));
        Map<String, Object> message = mapValue(event.get("message"));
        Map<String, Object> sender = mapValue(event.get("sender"));
        Map<String, Object> senderId = mapValue(sender.get("sender_id"));
        String rawContent = text(message.get("content"));
        Map<String, Object> content = parseContent(rawContent);

        String openId = firstText(text(senderId.get("open_id")), text(senderId.get("openId")), text(event.get("open_id")));
        String userId = firstText(text(senderId.get("user_id")), text(senderId.get("userId")), text(event.get("user_id")));
        String unionId = firstText(text(senderId.get("union_id")), text(senderId.get("unionId")), text(event.get("union_id")));
        String text = buildMessageText(content, message, payload);
        String imageKey = firstText(text(content.get("image_key")), text(content.get("file_key")), text(message.get("image_key")));
        String fileName = firstText(text(content.get("file_name")), text(content.get("fileName")), text(content.get("name")),
            text(message.get("file_name")), text(message.get("fileName")), text(message.get("name")));
        String messageId = firstText(text(message.get("message_id")), text(event.get("message_id")), text(payload.get("message_id")));
        String messageType = firstText(text(message.get("message_type")), text(message.get("messageType")));
        String chatId = firstText(text(message.get("chat_id")), text(message.get("chatId")));
        String chatType = firstText(text(message.get("chat_type")), text(message.get("chatType")));
        String eventType = firstText(text(header.get("event_type")), text(header.get("eventType")), text(payload.get("event_type")));
        String senderType = firstText(text(sender.get("sender_type")), text(sender.get("senderType")));
        String tenantKey = firstText(text(sender.get("tenant_key")), text(sender.get("tenantKey")),
            text(header.get("tenant_key")), text(header.get("tenantKey")), text(payload.get("tenant_key")));
        String sourceUserKey = firstText(openId, userId, unionId);
        return new BotMessage(openId, userId, unionId, sourceUserKey, messageId, text, imageKey,
            fileName, rawContent, messageType, chatId, chatType, tenantKey, eventType, senderType);
    }

    private String buildMessageText(Map<String, Object> content, Map<String, Object> message, Map<String, Object> payload) {
        String baseText = firstText(text(content.get("text")), text(message.get("text")), text(payload.get("text")));
        List<String> snippets = new ArrayList<>();
        collectMessageSnippets(content, snippets, 0);

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectUrls(baseText, urls);
        for (String snippet : snippets) {
            collectUrls(snippet, urls);
        }

        LinkedHashSet<String> previewTexts = new LinkedHashSet<>();
        for (String snippet : snippets) {
            String normalized = cleanupPreviewText(snippet);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.equals(baseText) || (StringUtils.hasText(baseText) && baseText.contains(normalized))) {
                continue;
            }
            if (looksLikeTechnicalToken(normalized)) {
                continue;
            }
            previewTexts.add(limit(normalized, 240));
        }

        StringBuilder builder = new StringBuilder(firstText(baseText, firstText(previewTexts.toArray(String[]::new))));
        if (!urls.isEmpty() || !previewTexts.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("飞书链接/预览信息：");
            int titleCount = 0;
            for (String previewText : previewTexts) {
                if (++titleCount > 3) {
                    break;
                }
                builder.append("\n标题：").append(previewText);
            }
            int urlCount = 0;
            for (String url : urls) {
                if (++urlCount > 5) {
                    break;
                }
                builder.append("\nURL：").append(url);
            }
        }
        return builder.toString();
    }

    private void collectMessageSnippets(Object value, List<String> snippets, int depth) {
        if (value == null || depth > 8) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim().toLowerCase(Locale.ROOT);
                Object child = entry.getValue();
                if (child instanceof String stringValue) {
                    if (isMessageTextKey(key) || containsUrl(stringValue)) {
                        snippets.add(stringValue);
                    }
                } else {
                    collectMessageSnippets(child, snippets, depth + 1);
                }
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectMessageSnippets(item, snippets, depth + 1);
            }
            return;
        }
        if (value instanceof String stringValue && containsUrl(stringValue)) {
            snippets.add(stringValue);
        }
    }

    private boolean isMessageTextKey(String key) {
        return MESSAGE_TEXT_KEYS.contains(key) || key.endsWith("_url") || key.contains("href") || key.contains("link");
    }

    private boolean containsUrl(String value) {
        return StringUtils.hasText(value) && URL_PATTERN.matcher(value).find();
    }

    private void collectUrls(String value, Set<String> urls) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(value);
        while (matcher.find()) {
            String url = trimUrl(matcher.group());
            if (StringUtils.hasText(url)) {
                urls.add(url);
            }
        }
    }

    private String cleanupPreviewText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = URL_PATTERN.matcher(value.trim()).replaceAll(" ").replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("^[：:，,。；;、\\s]+|[：:，,。；;、\\s]+$", "");
        return normalized;
    }

    private String trimUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("[，。；;、)）\\]}]+$", "");
    }

    private boolean looksLikeTechnicalToken(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.trim();
        return normalized.length() > 1200
            || normalized.matches("(?i)^(img|file|om|ou|on|cli|msg)_[a-z0-9_-]{12,}$")
            || normalized.matches("(?i)^[a-z0-9_-]{24,}$");
    }

    private Map<String, Object> parseContent(String content) {
        if (!StringUtils.hasText(content)) {
            return new HashMap<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(content, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            return Map.of("text", content);
        }
    }

    private void sendCardOrText(String openId, Map<String, Object> card, String fallbackText) {
        if (!sendCard(openId, card)) {
            sendText(openId, fallbackText);
        }
    }

    private boolean sendCard(String openId, Map<String, Object> card) {
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!StringUtils.hasText(openId) || !config.enabled() || card == null || card.isEmpty()) {
            return false;
        }
        try {
            String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
                .queryParam("receive_id_type", "open_id")
                .build()
                .toUriString();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receive_id", openId);
            body.put("msg_type", "interactive");
            body.put("content", objectMapper.writeValueAsString(card));
            restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
            return true;
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Send Feishu bot card failed", ex);
            return false;
        }
    }

    private void sendText(String openId, String text) {
        sendText("open_id", openId, text);
    }

    private String unboundUserGuideMessage() {
        return "你的飞书账号还没有绑定系统用户。\n"
            + "如果你是内部员工，请联系管理员同步飞书组织或维护邮箱、手机号。\n"
            + "如果你是外部供应商，请发送：绑定 供应商编码\n"
            + "例如：绑定 SUP0001\n"
            + "不知道供应商编码时，请联系采购管理员。";
    }

    private void sendTextToMessageTarget(BotMessage message, String text) {
        if (message != null && StringUtils.hasText(message.chatId())
            && !"p2p".equalsIgnoreCase(firstText(message.chatType(), ""))) {
            sendText("chat_id", message.chatId(), text);
            return;
        }
        sendText(message == null ? "" : message.openId(), text);
    }

    private void sendText(String receiveIdType, String receiveId, String text) {
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!StringUtils.hasText(receiveId) || !config.enabled()) {
            return;
        }
        try {
            String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
                .queryParam("receive_id_type", receiveIdType)
                .build()
                .toUriString();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receive_id", receiveId);
            body.put("msg_type", "text");
            body.put("content", "{\"text\":\"" + escapeJson(text) + "\"}");
            restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
        } catch (RuntimeException ex) {
            log.warn("Send Feishu bot text failed", ex);
        }
    }

    private String resolveTenantAccessToken(FeishuBotRuntimeConfig.Snapshot config) {
        long now = System.currentTimeMillis();
        String signature = config.tenantTokenSignature();
        if (StringUtils.hasText(cachedTenantAccessToken)
            && cachedTenantAccessTokenExpiresAt > now
            && signature.equals(cachedTenantAccessTokenSignature)) {
            return cachedTenantAccessToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedTenantAccessToken)
                && cachedTenantAccessTokenExpiresAt > now
                && signature.equals(cachedTenantAccessTokenSignature)) {
                return cachedTenantAccessToken;
            }
            if (!StringUtils.hasText(config.appId()) || !StringUtils.hasText(config.appSecret())) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "请先配置飞书 App ID 和 App Secret");
            }
            Map<String, Object> body = Map.of(
                "app_id", config.appId(),
                "app_secret", config.appSecret()
            );
            try {
                Map<String, Object> response = restClient.post()
                    .uri(config.tenantAccessTokenUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
                Map<String, Object> data = mapValue(response == null ? null : response.get("data"));
                validateFeishuResponse(response);
                String token = firstText(
                    text(response == null ? null : response.get("tenant_access_token")),
                    text(data.get("tenant_access_token"))
                );
                if (!StringUtils.hasText(token)) {
                    throw new BizException("FEISHU_TENANT_TOKEN_FAILED", missingTenantAccessTokenMessage(response));
                }
                Object expireValue = data.containsKey("expire") ? data.get("expire") : response == null ? null : response.get("expire");
                long expireSeconds = numberValue(expireValue, 7200L);
                cachedTenantAccessToken = token;
                cachedTenantAccessTokenExpiresAt = now + Math.max(60L, expireSeconds - 60L) * 1000L;
                cachedTenantAccessTokenSignature = signature;
                return token;
            } catch (RestClientResponseException ex) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", feishuHttpErrorMessage(ex));
            } catch (RestClientException ex) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "获取飞书租户访问凭证失败：" + firstText(ex.getMessage(), "网络异常"));
            }
        }
    }

    private void validateFeishuResponse(Map<String, Object> response) {
        if (response == null) {
            throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "飞书响应为空，未获取到 tenant_access_token");
        }
        String code = text(response.get("code"));
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BizException("FEISHU_TENANT_TOKEN_FAILED",
                firstText(text(response.get("msg")), text(response.get("message")), "获取飞书租户访问凭证失败"));
        }
    }

    private String missingTenantAccessTokenMessage(Map<String, Object> response) {
        if (response == null) {
            return "飞书响应为空，未获取到 tenant_access_token";
        }
        return "飞书响应缺少 tenant_access_token："
            + firstText(text(response.get("msg")), text(response.get("message")), text(response.get("code")), "请检查飞书 App ID/App Secret");
    }

    private String feishuHttpErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return StringUtils.hasText(body)
            ? "获取飞书租户访问凭证失败：" + limit(body, 300)
            : "获取飞书租户访问凭证失败：" + firstText(ex.getMessage(), "HTTP " + ex.getStatusCode().value());
    }

    private List<String> distinct(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value) && !result.contains(value.trim())) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private Integer parseInt(String value, Integer defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long numberValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String textValue = text(value);
        if (!StringUtils.hasText(textValue)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(textValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new HashMap<>();
        }
        Map<String, Object> target = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                target.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return target;
    }

    private String escapeJson(String value) {
        return firstText(value, "")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record PendingCandidateCommand(String action, BigDecimal quantity, String unit, String purpose,
                                           Integer itemNo, boolean awaitingConfirmation, long expiresAt) {

        private boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }

        private CandidateCommand toCommand(Integer selectedItemNo) {
            return new CandidateCommand(action, selectedItemNo == null ? itemNo : selectedItemNo,
                quantity, unit, purpose, "", null, "", "", "");
        }
    }

    private record BotMessage(String openId, String userId, String unionId, String sourceUserKey, String messageId,
                              String text, String imageKey, String fileName, String rawContent, String messageType,
                              String chatId, String chatType, String tenantKey, String eventType, String senderType) {
    }

    private record ImageRelay(String publicUrl, String dataUrl) {

        private static ImageRelay empty() {
            return new ImageRelay("", "");
        }
    }

    private record ImageOnlyDraft(ProcurementAiStartDTO dto, ProcurementAiAnalysisResult analysis,
                                  String difyRequestJson, String difyResponseText, String difyParsedJson) {
    }

    private record FeishuIdCandidate(String idType, String id) {
    }

    private record FeishuBotUserProfile(String userId, String openId, String unionId, String email, String mobile,
                                        String employeeNo) {

        private boolean hasContactIdentity() {
            return StringUtils.hasText(email)
                || StringUtils.hasText(mobile)
                || StringUtils.hasText(employeeNo);
        }
    }
}
