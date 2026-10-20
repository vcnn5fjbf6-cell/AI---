package com.company.procurement.modules.integration.feishu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.auth.service.AuthUserResolver;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig.Snapshot;
import com.company.procurement.modules.integration.feishu.dto.FeishuApprovalCardNotificationQueryDTO;
import com.company.procurement.modules.integration.feishu.entity.FeishuApprovalCardNotification;
import com.company.procurement.modules.integration.feishu.mapper.FeishuApprovalCardNotificationMapper;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardNotificationVO;
import com.company.procurement.modules.procurement.assistant.entity.ProcurementAiSession;
import com.company.procurement.modules.procurement.assistant.mapper.ProcurementAiSessionMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalActionDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalItemDecisionDTO;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService.ItemReview;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuApprovalCardServiceImpl implements FeishuApprovalCardService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
    };
    private static final Pattern FEISHU_ID_PATTERN =
        Pattern.compile("(?i)(user_id|open_id|union_id|email)\\s*=\\s*([^;\\s]+)");
    private static final Pattern AI_REVIEW_REMARK_PATTERN =
        Pattern.compile("AI审核意见\\s*[:：]\\s*([^\\r\\n]+)");
    private static final Pattern AI_SESSION_NO_PATTERN =
        Pattern.compile("\\bPA[0-9A-Z]{8,}\\b", Pattern.CASE_INSENSITIVE);
    private static final String ACTION_REJECT_ITEM = "REJECT_ITEM";
    private static final String ACTION_TOGGLE_REJECT_ITEM = "TOGGLE_REJECT_ITEM";
    private static final String ACTION_ITEM_PAGE = "ITEM_PAGE";
    private static final String ACTION_APPROVE_REPLACEMENT = "APPROVE_REPLACEMENT";
    private static final String ACTION_REJECT_REPLACEMENT = "REJECT_REPLACEMENT";
    private static final String CARD_JSON_SCHEMA_VERSION = "2.0";
    private static final String CARD_TEMPLATE_VERSION = "v20260826.4";
    private static final int CARD_ITEM_PAGE_SIZE = 10;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FeishuApprovalCardRuntimeConfig runtimeConfig;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final ProcurementAiSessionMapper procurementAiSessionMapper;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final FeishuApprovalCardNotificationMapper notificationMapper;
    private final AuthUserResolver authUserResolver;
    private final WorkflowService workflowService;
    private final OverPriceReviewService overPriceReviewService;
    private final FeishuDemandSubmitterResolver demandSubmitterResolver;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder().build();
    private final SecureRandom secureRandom = new SecureRandom();

    private volatile String cachedTenantAccessToken;
    private volatile long cachedTenantAccessTokenExpiresAt;
    private volatile String cachedTenantAccessTokenSignature;

    private record ReceiveTarget(String receiveIdType, String receiveId) {
    }

    private record AdminReceiveTarget(SysUser user, ReceiveTarget target) {
    }

    @Override
    public void pushPendingApprovalCards() {
        Snapshot config = runtimeConfig.current();
        if (!config.enabled() || !config.heartbeatEnabled()) {
            return;
        }
        if (!StringUtils.hasText(config.appId()) || !StringUtils.hasText(config.appSecret())) {
            log.warn("Feishu approval card is enabled but app id or app secret is missing");
            return;
        }

        List<ApprovalTask> tasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getStatus, ApprovalTaskService.STATUS_PENDING)
            .isNotNull(ApprovalTask::getPurchaseRequestId)
            .orderByAsc(ApprovalTask::getCreatedAt)
            .orderByAsc(ApprovalTask::getId)
            .last("LIMIT " + Math.max(1, config.batchSize())));
        for (ApprovalTask task : tasks) {
            try {
                pushOneTask(task, config);
            } catch (RuntimeException ex) {
                log.warn("Push Feishu approval card failed, taskId={}", task == null ? null : task.getId(), ex);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeishuApprovalCardActionResult handleCardAction(FeishuApprovalCardActionResult.ActionRequest request) {
        Snapshot config = runtimeConfig.current();
        if (!config.enabled()) {
            return FeishuApprovalCardActionResult.warning("飞书审批卡片未启用");
        }
        String action = normalizeAction(request.getAction());
        if (!ProcurementWorkflow.ACTION_APPROVE.equals(action)
            && !ProcurementWorkflow.ACTION_RETURN.equals(action)
            && !ACTION_REJECT_ITEM.equals(action)
            && !ACTION_TOGGLE_REJECT_ITEM.equals(action)
            && !ACTION_ITEM_PAGE.equals(action)
            && !ACTION_APPROVE_REPLACEMENT.equals(action)
            && !ACTION_REJECT_REPLACEMENT.equals(action)) {
            return FeishuApprovalCardActionResult.warning("暂不支持该审批动作");
        }

        FeishuApprovalCardNotification notification = findNotification(request);
        if (notification == null) {
            return FeishuApprovalCardActionResult.warning("审批卡片已失效，请回到系统处理");
        }
        if (FeishuApprovalCardNotification.STATUS_APPROVED.equals(notification.getStatus())
            || FeishuApprovalCardNotification.STATUS_RETURNED.equals(notification.getStatus())) {
            return FeishuApprovalCardActionResult.ok("该审批任务已经处理");
        }
        ApprovalTask task = approvalTaskMapper.selectById(notification.getApprovalTaskId());
        if (task == null || !ApprovalTaskService.STATUS_PENDING.equals(task.getStatus())) {
            closeNotification(notification, request.getMessageId(), "审批任务已处理");
            return FeishuApprovalCardActionResult.ok("该审批任务已经处理");
        }
        if (!Objects.equals(task.getPurchaseRequestId(), notification.getPurchaseRequestId())) {
            return FeishuApprovalCardActionResult.warning("审批任务数据不一致，请回到系统处理");
        }

        SysUser operator = resolveOperator(request, notification);
        if (operator == null) {
            return FeishuApprovalCardActionResult.warning("无法确认当前飞书账号对应的系统审批人，请检查审批人邮箱或飞书 ID 绑定");
        }
        if (!canOperateNotification(operator, notification)) {
            return FeishuApprovalCardActionResult.warning("当前飞书账号不是该审批任务处理人");
        }
        SysUser cardApprover = approvalDisplayUser(notification, task, operator);

        PurchaseRequest purchaseRequest = purchaseRequestMapper.selectById(notification.getPurchaseRequestId());
        if (purchaseRequest == null) {
            return FeishuApprovalCardActionResult.warning("采购申请不存在");
        }
        List<Long> rejectItemIds = rejectItemIds(request);
        if (ACTION_TOGGLE_REJECT_ITEM.equals(action) || ACTION_ITEM_PAGE.equals(action)) {
            List<PurchaseRequestItem> items = cardItems(task, purchaseRequest);
            List<Long> selectedIds = rejectItemIdsOnActiveItems(request.getRejectItemIds(), items);
            if (ACTION_TOGGLE_REJECT_ITEM.equals(action) && request.getRejectItemId() != null) {
                selectedIds = toggleSelectedItem(selectedIds, request.getRejectItemId(), items);
            }
            int itemPage = normalizeItemPage(request.getItemPage(), items.size());
            Map<String, Object> updatedCard = buildApprovalCard(
                notification,
                task,
                purchaseRequest,
                cardApprover,
                false,
                null,
                config,
                itemPage,
                selectedIds);
            return FeishuApprovalCardActionResult.ok(
                ACTION_TOGGLE_REJECT_ITEM.equals(action) ? "已更新待驳回明细" : "已切换明细页",
                updatedCard);
        }
        if (ACTION_APPROVE_REPLACEMENT.equals(action) || ACTION_REJECT_REPLACEMENT.equals(action)) {
            String approvalComment = firstText(
                request.getApprovalComment(),
                ACTION_APPROVE_REPLACEMENT.equals(action) ? "同意替换商品" : "不同意替换商品"
            );
            if (ACTION_REJECT_REPLACEMENT.equals(action) && Objects.equals(approvalComment, "同意替换商品")) {
                approvalComment = "不同意替换商品";
            }
            AuthUser previous = AuthContextHolder.get();
            try {
                AuthContextHolder.set(authUserResolver.buildAuthUser(operator));
                workflowService.confirmReplacementItems(
                    notification.getPurchaseRequestId(),
                    ACTION_APPROVE_REPLACEMENT.equals(action),
                    approvalComment
                );
            } catch (BizException ex) {
                return FeishuApprovalCardActionResult.warning(ex.getMessage());
            } finally {
                if (previous == null) {
                    AuthContextHolder.clear();
                } else {
                    AuthContextHolder.set(previous);
                }
            }
            PurchaseRequest refreshedRequest = purchaseRequestMapper.selectById(notification.getPurchaseRequestId());
            notification.setStatus(FeishuApprovalCardNotification.STATUS_APPROVED);
            notification.setAction(action);
            notification.setActedAt(LocalDateTime.now());
            notification.setMessageId(firstText(notification.getMessageId(), request.getMessageId()));
            notificationMapper.updateById(notification);
            Map<String, Object> resolvedCard = buildApprovalCard(
                notification,
                task,
                refreshedRequest == null ? purchaseRequest : refreshedRequest,
                cardApprover,
                true,
                action,
                config);
            patchActionResultCard(notification, resolvedCard, config);
            if (refreshedRequest == null
                || !Objects.equals(refreshedRequest.getCurrentNodeNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW)) {
                closeSiblingNotifications(notification, task, refreshedRequest == null ? purchaseRequest : refreshedRequest, cardApprover, action, config);
            }
            return FeishuApprovalCardActionResult.ok(actionResultMessage(action), resolvedCard);
        }
        if (ACTION_REJECT_ITEM.equals(action) && rejectItemIds.isEmpty()) {
            return FeishuApprovalCardActionResult.warning("请选择至少一条要驳回的明细");
        }
        if (ACTION_REJECT_ITEM.equals(action) && !StringUtils.hasText(request.getRejectReason())) {
            return FeishuApprovalCardActionResult.warning("请填写驳回原因");
        }
        String approvalComment = resolveApprovalComment(action, request);
        if (!StringUtils.hasText(approvalComment)) {
            return FeishuApprovalCardActionResult.warning("请填写审批意见");
        }

        AuthUser previous = AuthContextHolder.get();
        try {
            AuthContextHolder.set(authUserResolver.buildAuthUser(operator));
            WorkflowApprovalActionDTO dto = new WorkflowApprovalActionDTO();
            dto.setAction(ACTION_REJECT_ITEM.equals(action) ? ProcurementWorkflow.ACTION_APPROVE : action);
            dto.setApprovalComment(approvalComment);
            if (ACTION_REJECT_ITEM.equals(action)) {
                List<WorkflowApprovalItemDecisionDTO> itemDecisions = new java.util.ArrayList<>();
                for (Long itemId : rejectItemIds) {
                    WorkflowApprovalItemDecisionDTO itemDecision = new WorkflowApprovalItemDecisionDTO();
                    itemDecision.setItemId(itemId);
                    itemDecision.setRejected(true);
                    itemDecision.setRejectReason(request.getRejectReason());
                    itemDecisions.add(itemDecision);
                }
                dto.setItemDecisions(itemDecisions);
            }
            workflowService.approveRequest(notification.getPurchaseRequestId(), dto);
        } catch (BizException ex) {
            return FeishuApprovalCardActionResult.warning(ex.getMessage());
        } finally {
            if (previous == null) {
                AuthContextHolder.clear();
            } else {
                AuthContextHolder.set(previous);
            }
        }

        PurchaseRequest refreshedRequest = purchaseRequestMapper.selectById(notification.getPurchaseRequestId());
        notifyApplicantApprovalResult(
            refreshedRequest == null ? purchaseRequest : refreshedRequest,
            operator,
            action,
            approvalComment,
            rejectItemIds,
            config
        );

        notification.setStatus(ProcurementWorkflow.ACTION_APPROVE.equals(action)
            ? FeishuApprovalCardNotification.STATUS_APPROVED
            : ACTION_REJECT_ITEM.equals(action)
                ? FeishuApprovalCardNotification.STATUS_APPROVED
                : FeishuApprovalCardNotification.STATUS_RETURNED);
        notification.setAction(action);
        notification.setActedAt(LocalDateTime.now());
        notification.setMessageId(firstText(notification.getMessageId(), request.getMessageId()));
        notificationMapper.updateById(notification);
        Map<String, Object> resolvedCard = buildApprovalCard(notification, task, purchaseRequest, cardApprover, true, action, config);
        patchActionResultCard(notification, resolvedCard, config);
        closeSiblingNotifications(notification, task, purchaseRequest, cardApprover, action, config);
        return FeishuApprovalCardActionResult.ok(actionResultMessage(action), resolvedCard);
    }

    @Override
    public FeishuApprovalCardActionResult sendTestCardToUser(Long userId) {
        Snapshot config = runtimeConfig.current();
        if (!StringUtils.hasText(config.appId()) || !StringUtils.hasText(config.appSecret())) {
            return FeishuApprovalCardActionResult.warning("请先配置飞书 App ID 和 App Secret");
        }
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            return FeishuApprovalCardActionResult.warning("当前系统账号不存在");
        }
        boolean hasTestRecipient = StringUtils.hasText(config.testRecipientEmail());
        String receiveIdType = hasTestRecipient ? "email" : normalizeReceiveIdType(config.receiveIdType());
        ReceiveTarget target = hasTestRecipient
            ? new ReceiveTarget(receiveIdType, config.testRecipientEmail().trim())
            : resolveFeishuReceiveTarget(user, receiveIdType, true);
        SysUser displayUser = user;
        if (target == null) {
            AdminReceiveTarget adminTarget = resolveDefaultAdminReceiveTarget(config);
            if (adminTarget == null) {
                return FeishuApprovalCardActionResult.warning(missingReceiveIdMessage("当前账号", receiveIdType)
                    + "，且管理员接收目标未维护");
            }
            target = adminTarget.target();
            displayUser = adminTarget.user();
        }
        try {
            sendTestCard(config, displayUser, target.receiveIdType(), target.receiveId());
            if (hasTestRecipient) {
                return FeishuApprovalCardActionResult.ok("测试卡片已发送给测试接收邮箱（email）");
            }
            return FeishuApprovalCardActionResult.ok("测试卡片已发送给 " + displayName(displayUser) + "（" + target.receiveIdType() + "）");
        } catch (RuntimeException ex) {
            log.warn("Send Feishu approval test card failed, userId={}", userId, ex);
            return FeishuApprovalCardActionResult.warning(limit(ex.getMessage(), 200));
        }
    }

    @Override
    public FeishuApprovalCardActionResult sendBusinessNotification(Long userId, String title, String content) {
        Snapshot config = runtimeConfig.current();
        if (!config.enabled()) {
            return FeishuApprovalCardActionResult.warning("飞书通知未启用");
        }
        if (userId == null) {
            return FeishuApprovalCardActionResult.warning("通知接收人不能为空");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return FeishuApprovalCardActionResult.warning("通知接收人不存在");
        }
        String receiveIdType = normalizeReceiveIdType(config.receiveIdType());
        ReceiveTarget target = resolveFeishuReceiveTarget(user, receiveIdType, true);
        if (target == null) {
            return FeishuApprovalCardActionResult.warning(missingAnyReceiveIdMessage(displayName(user)));
        }
        try {
            String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
                .queryParam("receive_id_type", target.receiveIdType())
                .build()
                .toUriString();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receive_id", target.receiveId());
            body.put("msg_type", "interactive");
            body.put("content", toJson(buildBusinessNotificationCard(title, content)));
            Map<String, Object> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
            validateFeishuResponse(response);
            Map<String, Object> data = mapValue(response == null ? null : response.get("data"));
            return FeishuApprovalCardActionResult.ok(firstText(
                text(data.get("message_id")),
                text(data.get("messageId")),
                "通知已发送"
            ));
        } catch (RuntimeException ex) {
            log.warn("Send generic Feishu business notification failed, userId={}", userId, ex);
            return FeishuApprovalCardActionResult.warning(firstText(ex.getMessage(), "飞书通知发送失败"));
        }
    }

    private Map<String, Object> buildBusinessNotificationCard(String title, String content) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_JSON_SCHEMA_VERSION);
        card.put("header", Map.of(
            "template", "blue",
            "title", Map.of("tag", "plain_text", "content", firstText(title, "业务通知"))
        ));
        card.put("body", Map.of(
            "elements", List.of(Map.of(
                "tag", "markdown",
                "content", firstText(content, "请登录管理平台查看详情")
            ))
        ));
        return card;
    }

    @Override
    public PageResult<FeishuApprovalCardNotificationVO> pageNotifications(FeishuApprovalCardNotificationQueryDTO queryDTO) {
        FeishuApprovalCardNotificationQueryDTO effectiveQuery = queryDTO == null
            ? new FeishuApprovalCardNotificationQueryDTO()
            : queryDTO;
        LambdaQueryWrapper<FeishuApprovalCardNotification> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(effectiveQuery.getStatus())) {
            wrapper.eq(FeishuApprovalCardNotification::getStatus, effectiveQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (effectiveQuery.getApprovalTaskId() != null) {
            wrapper.eq(FeishuApprovalCardNotification::getApprovalTaskId, effectiveQuery.getApprovalTaskId());
        }
        if (effectiveQuery.getPurchaseRequestId() != null) {
            wrapper.eq(FeishuApprovalCardNotification::getPurchaseRequestId, effectiveQuery.getPurchaseRequestId());
        }
        applyNotificationKeyword(wrapper, effectiveQuery.getKeyword());
        wrapper.orderByDesc(FeishuApprovalCardNotification::getUpdatedAt)
            .orderByDesc(FeishuApprovalCardNotification::getId);

        Page<FeishuApprovalCardNotification> page = notificationMapper.selectPage(
            new Page<>(Math.max(1, effectiveQuery.getPageNo()), Math.max(1, effectiveQuery.getPageSize())),
            wrapper
        );
        List<FeishuApprovalCardNotification> notifications = page.getRecords();
        Set<Long> requestIds = notifications.stream()
            .map(FeishuApprovalCardNotification::getPurchaseRequestId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> taskIds = notifications.stream()
            .map(FeishuApprovalCardNotification::getApprovalTaskId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> userIds = notifications.stream()
            .map(FeishuApprovalCardNotification::getApproverUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        notifications.stream()
            .map(FeishuApprovalCardNotification::getRecipientUserId)
            .filter(Objects::nonNull)
            .forEach(userIds::add);
        Map<Long, PurchaseRequest> requestMap = requestIds.isEmpty()
            ? Map.of()
            : purchaseRequestMapper.selectBatchIds(requestIds).stream()
                .collect(Collectors.toMap(PurchaseRequest::getId, item -> item, (left, right) -> left));
        Map<Long, ApprovalTask> taskMap = taskIds.isEmpty()
            ? Map.of()
            : approvalTaskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(ApprovalTask::getId, item -> item, (left, right) -> left));
        Map<Long, SysUser> userMap = userIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, item -> item, (left, right) -> left));
        List<FeishuApprovalCardNotificationVO> records = notifications.stream()
            .map(notification -> toNotificationVO(notification, requestMap, taskMap, userMap))
            .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeishuApprovalCardActionResult resendNotification(Long notificationId) {
        Snapshot config = runtimeConfig.current();
        if (!config.enabled()) {
            return FeishuApprovalCardActionResult.warning("飞书审批卡片未启用");
        }
        if (!StringUtils.hasText(config.appId()) || !StringUtils.hasText(config.appSecret())) {
            return FeishuApprovalCardActionResult.warning("请先配置飞书 App ID 和 App Secret");
        }
        FeishuApprovalCardNotification notification = notificationId == null ? null : notificationMapper.selectById(notificationId);
        if (notification == null) {
            return FeishuApprovalCardActionResult.warning("卡片通知记录不存在");
        }
        ApprovalTask task = notification.getApprovalTaskId() == null ? null : approvalTaskMapper.selectById(notification.getApprovalTaskId());
        if (task == null) {
            notification.setStatus(FeishuApprovalCardNotification.STATUS_FAILED);
            notification.setLastError("审批任务不存在，不能重新发送");
            notificationMapper.updateById(notification);
            return FeishuApprovalCardActionResult.warning("审批任务不存在，不能重新发送");
        }
        if (!ApprovalTaskService.STATUS_PENDING.equals(task.getStatus())) {
            closeNotification(notification, null, "审批任务已处理");
            return FeishuApprovalCardActionResult.warning("审批任务已处理，不能重新发送");
        }

        notification.setStatus(FeishuApprovalCardNotification.STATUS_PENDING);
        notification.setAction(null);
        notification.setActedAt(null);
        notification.setMessageId(null);
        notification.setLastError("");
        notification.setCardToken(randomToken());
        notification.setRetryCount(0);
        notificationMapper.updateById(notification);

        pushOneTask(task, config);
        FeishuApprovalCardNotification refreshed = notificationMapper.selectById(notificationId);
        if (refreshed != null && FeishuApprovalCardNotification.STATUS_SENT.equals(refreshed.getStatus())) {
            return FeishuApprovalCardActionResult.ok("审批卡片已重新发送给 " + displayReceiveTarget(refreshed));
        }
        return FeishuApprovalCardActionResult.warning(limit(firstText(refreshed == null ? null : refreshed.getLastError(), "审批卡片重新发送失败"), 200));
    }

    private void applyNotificationKeyword(LambdaQueryWrapper<FeishuApprovalCardNotification> wrapper, String keyword) {
        String text = trimToNull(keyword);
        if (!StringUtils.hasText(text)) {
            return;
        }
        Set<Long> matchedRequestIds = findNotificationKeywordRequestIds(text);
        Set<Long> matchedUserIds = findNotificationKeywordUserIds(text);
        long numericId = numberValue(text, -1);
        wrapper.and(query -> {
            query.like(FeishuApprovalCardNotification::getReceiveId, text)
                .or()
                .like(FeishuApprovalCardNotification::getMessageId, text)
                .or()
                .like(FeishuApprovalCardNotification::getLastError, text);
            if (numericId > 0) {
                query.or()
                    .eq(FeishuApprovalCardNotification::getId, numericId)
                    .or()
                    .eq(FeishuApprovalCardNotification::getApprovalTaskId, numericId)
                    .or()
                    .eq(FeishuApprovalCardNotification::getPurchaseRequestId, numericId);
            }
            if (!matchedRequestIds.isEmpty()) {
                query.or().in(FeishuApprovalCardNotification::getPurchaseRequestId, matchedRequestIds);
            }
            if (!matchedUserIds.isEmpty()) {
                query.or().in(FeishuApprovalCardNotification::getApproverUserId, matchedUserIds)
                    .or().in(FeishuApprovalCardNotification::getRecipientUserId, matchedUserIds);
            }
        });
    }

    private Set<Long> findNotificationKeywordRequestIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Set.of();
        }
        List<PurchaseRequest> requests = purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
            .select(PurchaseRequest::getId)
            .and(query -> query.like(PurchaseRequest::getReqNo, keyword)
                .or()
                .like(PurchaseRequest::getReqTitle, keyword)));
        return requests.stream()
            .map(PurchaseRequest::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<Long> findNotificationKeywordUserIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Set.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getId)
            .and(query -> query.like(SysUser::getRealName, keyword)
                .or()
                .like(SysUser::getUsername, keyword)
                .or()
                .like(SysUser::getEmail, keyword)
                .or()
                .like(SysUser::getMobile, keyword)));
        return users.stream()
            .map(SysUser::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
    }

    private FeishuApprovalCardNotificationVO toNotificationVO(FeishuApprovalCardNotification notification,
                                                              Map<Long, PurchaseRequest> requestMap,
                                                              Map<Long, ApprovalTask> taskMap,
                                                              Map<Long, SysUser> userMap) {
        FeishuApprovalCardNotificationVO vo = new FeishuApprovalCardNotificationVO();
        vo.setId(notification.getId());
        vo.setApprovalTaskId(notification.getApprovalTaskId());
        vo.setPurchaseRequestId(notification.getPurchaseRequestId());
        PurchaseRequest request = requestMap.get(notification.getPurchaseRequestId());
        if (request != null) {
            vo.setRequestNo(request.getReqNo());
            vo.setRequestTitle(request.getReqTitle());
        }
        ApprovalTask task = taskMap.get(notification.getApprovalTaskId());
        if (task != null) {
            vo.setTaskLevelName(task.getLevelName());
            vo.setTaskStatus(task.getStatus());
        }
        SysUser approver = userMap.get(notification.getApproverUserId());
        vo.setApproverUserId(notification.getApproverUserId());
        if (approver != null) {
            vo.setApproverName(displayName(approver));
            vo.setApproverEmail(approver.getEmail());
        }
        Long recipientUserId = notification.getRecipientUserId() == null
            ? notification.getApproverUserId()
            : notification.getRecipientUserId();
        SysUser recipient = userMap.get(recipientUserId);
        vo.setRecipientUserId(recipientUserId);
        if (recipient != null) {
            vo.setRecipientName(displayName(recipient));
        }
        vo.setReceiveIdType(notification.getReceiveIdType());
        vo.setReceiveId(notification.getReceiveId());
        vo.setMessageId(notification.getMessageId());
        vo.setStatus(notification.getStatus());
        vo.setAction(notification.getAction());
        vo.setRetryCount(notification.getRetryCount());
        vo.setLastError(notification.getLastError());
        vo.setSentAt(notification.getSentAt());
        vo.setActedAt(notification.getActedAt());
        vo.setCreatedAt(notification.getCreatedAt());
        vo.setUpdatedAt(notification.getUpdatedAt());
        return vo;
    }

    private String displayReceiveTarget(FeishuApprovalCardNotification notification) {
        Long recipientUserId = notification.getRecipientUserId() == null
            ? notification.getApproverUserId()
            : notification.getRecipientUserId();
        SysUser recipient = recipientUserId == null ? null : userMapper.selectById(recipientUserId);
        if (recipient != null) {
            return displayName(recipient) + "（" + firstText(notification.getReceiveIdType(), "-") + "）";
        }
        return firstText(notification.getReceiveId(), "审批人");
    }

    private void pushOneTask(ApprovalTask task, Snapshot config) {
        if (task == null || task.getId() == null) {
            return;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(task.getPurchaseRequestId());
        SysUser approver = task.getCandidateUserId() == null ? null : userMapper.selectById(task.getCandidateUserId());
        if (request == null || approver == null) {
            FeishuApprovalCardNotification notification = notificationMapper.selectOne(new LambdaQueryWrapper<FeishuApprovalCardNotification>()
                .eq(FeishuApprovalCardNotification::getApprovalTaskId, task.getId())
                .last("LIMIT 1"));
            markFailed(notification, task, null, null, "采购申请或审批人不存在", config);
            return;
        }
        pushOneTaskToRecipient(task, request, approver, approver, config);
        for (SysUser admin : adminCopyRecipients(config, approver)) {
            pushOneTaskToRecipient(task, request, approver, admin, config);
        }
    }

    private void pushOneTaskToRecipient(ApprovalTask task,
                                        PurchaseRequest request,
                                        SysUser approver,
                                        SysUser recipient,
                                        Snapshot config) {
        if (task == null || recipient == null || recipient.getId() == null) {
            return;
        }
        FeishuApprovalCardNotification notification = findNotificationForRecipient(task.getId(), recipient.getId());
        if (notification != null && shouldSkip(notification)) {
            return;
        }
        String preferredReceiveIdType = normalizeReceiveIdType(config.receiveIdType());
        boolean adminCopy = approver != null
            && !Objects.equals(recipient.getId(), approver.getId())
            && isSystemAdminUser(recipient);
        ReceiveTarget target = resolveFeishuReceiveTarget(recipient, preferredReceiveIdType, adminCopy);
        boolean adminFallback = false;
        if (target == null && !adminCopy) {
            AdminReceiveTarget adminTarget = resolveDefaultAdminReceiveTarget(config);
            target = adminTarget == null ? null : adminTarget.target();
            adminFallback = target != null;
        }
        if (notification != null && retryExceeded(notification, config) && !adminCopy && !adminFallback) {
            return;
        }
        if (target == null) {
            String message = adminCopy
                ? missingAnyReceiveIdMessage(displayName(recipient))
                : missingReceiveIdMessage(displayName(recipient), preferredReceiveIdType);
            markFailed(notification, task, approver, recipient, message, config);
            return;
        }

        if (notification == null) {
            notification = newNotification(task, approver, recipient, target.receiveIdType(), target.receiveId());
            notificationMapper.insert(notification);
        } else {
            notification.setApproverUserId(approver.getId());
            notification.setRecipientUserId(recipient.getId());
            notification.setReceiveIdType(target.receiveIdType());
            notification.setReceiveId(target.receiveId());
            notification.setStatus(FeishuApprovalCardNotification.STATUS_PENDING);
            notificationMapper.updateById(notification);
        }

        try {
            String messageId = sendApprovalCard(notification, task, request, approver, config);
            notification.setMessageId(messageId);
            notification.setStatus(FeishuApprovalCardNotification.STATUS_SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setLastError("");
            notificationMapper.updateById(notification);
        } catch (RuntimeException ex) {
            notification.setStatus(FeishuApprovalCardNotification.STATUS_FAILED);
            notification.setRetryCount((notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1);
            notification.setLastError(limit(ex.getMessage(), 1000));
            notificationMapper.updateById(notification);
            log.warn("Send Feishu approval card failed, taskId={}, requestId={}", task.getId(), task.getPurchaseRequestId(), ex);
        }
    }

    private FeishuApprovalCardNotification findNotificationForRecipient(Long approvalTaskId, Long recipientUserId) {
        if (approvalTaskId == null || recipientUserId == null) {
            return null;
        }
        return notificationMapper.selectOne(new LambdaQueryWrapper<FeishuApprovalCardNotification>()
            .eq(FeishuApprovalCardNotification::getApprovalTaskId, approvalTaskId)
            .and(wrapper -> wrapper.eq(FeishuApprovalCardNotification::getRecipientUserId, recipientUserId)
                .or(child -> child.isNull(FeishuApprovalCardNotification::getRecipientUserId)
                    .eq(FeishuApprovalCardNotification::getApproverUserId, recipientUserId)))
            .last("LIMIT 1"));
    }

    private boolean shouldSkip(FeishuApprovalCardNotification notification) {
        return FeishuApprovalCardNotification.STATUS_SENT.equals(notification.getStatus())
            || FeishuApprovalCardNotification.STATUS_APPROVED.equals(notification.getStatus())
            || FeishuApprovalCardNotification.STATUS_RETURNED.equals(notification.getStatus())
            || FeishuApprovalCardNotification.STATUS_CLOSED.equals(notification.getStatus());
    }

    private boolean retryExceeded(FeishuApprovalCardNotification notification, Snapshot config) {
        return FeishuApprovalCardNotification.STATUS_FAILED.equals(notification.getStatus())
            && notification.getRetryCount() != null
            && notification.getRetryCount() >= Math.max(1, config.maxRetryCount());
    }

    private List<SysUser> adminCopyRecipients(Snapshot config, SysUser approver) {
        if (config == null || !config.adminCardCopyEnabled()) {
            return List.of();
        }
        List<SysUser> candidates = StringUtils.hasText(config.adminCardCopyUsernames())
            ? configuredAdminCopyUsers(config.adminCardCopyUsernames())
            : allSystemAdminUsers();
        Set<Long> seen = new LinkedHashSet<>();
        List<SysUser> recipients = new java.util.ArrayList<>();
        for (SysUser user : candidates) {
            if (user == null || user.getId() == null || !isActiveUser(user) || !isSystemAdminUser(user)) {
                continue;
            }
            if (seen.add(user.getId())) {
                recipients.add(user);
            }
        }
        return recipients;
    }

    private AdminReceiveTarget resolveDefaultAdminReceiveTarget(Snapshot config) {
        String preferredReceiveIdType = normalizeReceiveIdType(config == null ? null : config.receiveIdType());
        for (SysUser admin : adminDefaultRecipients(config)) {
            ReceiveTarget target = resolveFeishuReceiveTarget(admin, preferredReceiveIdType, true);
            if (target != null) {
                return new AdminReceiveTarget(admin, target);
            }
        }
        return null;
    }

    private List<SysUser> adminDefaultRecipients(Snapshot config) {
        List<SysUser> candidates = config != null && StringUtils.hasText(config.adminCardCopyUsernames())
            ? configuredAdminCopyUsers(config.adminCardCopyUsernames())
            : allSystemAdminUsers();
        Set<Long> seen = new LinkedHashSet<>();
        List<SysUser> recipients = new java.util.ArrayList<>();
        for (SysUser user : candidates) {
            if (user == null || user.getId() == null || !isActiveUser(user) || !isSystemAdminUser(user)) {
                continue;
            }
            if (seen.add(user.getId())) {
                recipients.add(user);
            }
        }
        return recipients;
    }

    private List<SysUser> configuredAdminCopyUsers(String value) {
        List<SysUser> users = new java.util.ArrayList<>();
        for (String token : splitAdminCopyTokens(value)) {
            SysUser user = findUserByAdminCopyToken(token);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    private List<String> splitAdminCopyTokens(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Pattern.compile("[,，;；\\s]+")
            .splitAsStream(value.trim())
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private SysUser findUserByAdminCopyToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.matches("\\d+")) {
            SysUser byId = userMapper.selectById(Long.parseLong(normalized));
            if (byId != null) {
                return byId;
            }
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, 1)
            .and(wrapper -> wrapper.eq(SysUser::getUsername, normalized)
                .or()
                .eq(SysUser::getEmployeeNo, normalized)
                .or()
                .eq(SysUser::getEmail, normalized))
            .last("LIMIT 1"));
    }

    private List<SysUser> allSystemAdminUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, 1)
            .orderByAsc(SysUser::getId));
    }

    private boolean isActiveUser(SysUser user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus());
    }

    private boolean isSystemAdminUser(SysUser user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(user.getId());
        AuthUser authUser = AuthUser.builder()
            .userId(user.getId())
            .userType(user.getUserType())
            .roleCodes(roleCodes == null ? List.of() : roleCodes)
            .build();
        return DataScopeHelper.isSystemAdmin(authUser);
    }

    private FeishuApprovalCardNotification newNotification(ApprovalTask task,
                                                           SysUser approver,
                                                           SysUser recipient,
                                                           String receiveIdType,
                                                           String receiveId) {
        FeishuApprovalCardNotification notification = new FeishuApprovalCardNotification();
        notification.setApprovalTaskId(task.getId());
        notification.setPurchaseRequestId(task.getPurchaseRequestId());
        notification.setApproverUserId(approver == null ? task.getCandidateUserId() : approver.getId());
        notification.setRecipientUserId(recipient == null ? notification.getApproverUserId() : recipient.getId());
        notification.setSubmitRound(task.getSubmitRound() == null ? 1 : task.getSubmitRound());
        notification.setReceiveIdType(receiveIdType);
        notification.setReceiveId(receiveId);
        notification.setCardToken(randomToken());
        notification.setStatus(FeishuApprovalCardNotification.STATUS_PENDING);
        notification.setRetryCount(0);
        return notification;
    }

    private void markFailed(FeishuApprovalCardNotification notification,
                            ApprovalTask task,
                            SysUser approver,
                            SysUser recipient,
                            String message,
                            Snapshot config) {
        if (notification == null && task != null) {
            notification = new FeishuApprovalCardNotification();
            notification.setApprovalTaskId(task.getId());
            notification.setPurchaseRequestId(task.getPurchaseRequestId());
            notification.setApproverUserId(approver == null ? task.getCandidateUserId() : approver.getId());
            notification.setRecipientUserId(recipient == null ? notification.getApproverUserId() : recipient.getId());
            notification.setSubmitRound(task.getSubmitRound() == null ? 1 : task.getSubmitRound());
            notification.setReceiveIdType(normalizeReceiveIdType(config.receiveIdType()));
            notification.setCardToken(randomToken());
            notification.setRetryCount(0);
            notification.setStatus(FeishuApprovalCardNotification.STATUS_FAILED);
            notification.setLastError(limit(message, 1000));
            notificationMapper.insert(notification);
            return;
        }
        if (notification != null) {
            notification.setStatus(FeishuApprovalCardNotification.STATUS_FAILED);
            notification.setRetryCount((notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1);
            notification.setLastError(limit(message, 1000));
            notificationMapper.updateById(notification);
        }
    }

    private String sendApprovalCard(FeishuApprovalCardNotification notification,
                                    ApprovalTask task,
                                    PurchaseRequest request,
                                    SysUser approver,
                                    Snapshot config) {
        String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
            .queryParam("receive_id_type", notification.getReceiveIdType())
            .build()
            .toUriString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", notification.getReceiveId());
        body.put("msg_type", "interactive");
        body.put("content", toJson(buildApprovalCard(notification, task, request, approver, false, null, config)));
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

    private void sendTestCard(Snapshot config, SysUser user, String receiveIdType, String receiveId) {
        String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
            .queryParam("receive_id_type", receiveIdType)
            .build()
            .toUriString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "interactive");
        body.put("content", toJson(buildTestCard(user, receiveIdType, config)));
        Map<String, Object> response = restClient.post()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE);
        validateFeishuResponse(response);
    }

    private void notifyApplicantApprovalResult(PurchaseRequest request,
                                               SysUser operator,
                                               String action,
                                               String approvalComment,
                                               List<Long> rejectItemIds,
                                               Snapshot config) {
        if (request == null || request.getApplicantUserId() == null) {
            return;
        }
        SysUser applicant = userMapper.selectById(request.getApplicantUserId());
        if (applicant == null) {
            return;
        }
        String receiveIdType = normalizeReceiveIdType(config.receiveIdType());
        String receiveId = resolveFeishuReceiveId(applicant, receiveIdType);
        if (!StringUtils.hasText(receiveId)) {
            log.warn("Skip Feishu approval result notification: applicant receive id missing, requestId={}, applicantId={}, type={}",
                request.getId(), applicant.getId(), receiveIdType);
            return;
        }
        try {
            String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
                .queryParam("receive_id_type", receiveIdType)
                .build()
                .toUriString();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receive_id", receiveId);
            body.put("msg_type", "interactive");
            body.put("content", toJson(buildApplicantResultCard(request, operator, action, approvalComment, rejectItemIds, config)));
            Map<String, Object> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
            validateFeishuResponse(response);
        } catch (RuntimeException ex) {
            log.warn("Send Feishu approval result notification failed, requestId={}, applicantId={}",
                request.getId(), applicant.getId(), ex);
        }
    }

    private void patchActionResultCard(FeishuApprovalCardNotification notification,
                                       Map<String, Object> card,
                                       Snapshot config) {
        if (!StringUtils.hasText(notification.getMessageId())) {
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                "content", toJson(card)
            );
            Map<String, Object> response = restClient.patch()
                .uri(String.format(config.messagePatchUrl(), notification.getMessageId()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
            validateFeishuResponse(response);
        } catch (RuntimeException ex) {
            log.warn("Patch Feishu approval card failed, notificationId={}", notification.getId(), ex);
        }
    }

    private void closeSiblingNotifications(FeishuApprovalCardNotification handled,
                                           ApprovalTask task,
                                           PurchaseRequest request,
                                           SysUser approver,
                                           String action,
                                           Snapshot config) {
        if (handled == null || handled.getApprovalTaskId() == null) {
            return;
        }
        List<FeishuApprovalCardNotification> siblings = notificationMapper.selectList(new LambdaQueryWrapper<FeishuApprovalCardNotification>()
            .eq(FeishuApprovalCardNotification::getApprovalTaskId, handled.getApprovalTaskId())
            .ne(FeishuApprovalCardNotification::getId, handled.getId())
            .in(FeishuApprovalCardNotification::getStatus, List.of(
                FeishuApprovalCardNotification.STATUS_PENDING,
                FeishuApprovalCardNotification.STATUS_SENT,
                FeishuApprovalCardNotification.STATUS_FAILED
            )));
        for (FeishuApprovalCardNotification sibling : siblings) {
            sibling.setStatus(ProcurementWorkflow.ACTION_RETURN.equals(action)
                ? FeishuApprovalCardNotification.STATUS_RETURNED
                : FeishuApprovalCardNotification.STATUS_APPROVED);
            sibling.setAction(action);
            sibling.setActedAt(LocalDateTime.now());
            sibling.setLastError("同一审批任务已由其他卡片处理");
            notificationMapper.updateById(sibling);
            patchActionResultCard(sibling, buildApprovalCard(sibling, task, request, approver, true, action, config), config);
        }
    }

    private Map<String, Object> buildApprovalCard(FeishuApprovalCardNotification notification,
                                                  ApprovalTask task,
                                                  PurchaseRequest request,
                                                  SysUser approver,
                                                  boolean resolved,
                                                  String resolvedAction,
                                                  Snapshot config) {
        return buildApprovalCard(notification, task, request, approver, resolved, resolvedAction, config, 1, List.of());
    }

    private Map<String, Object> buildApprovalCard(FeishuApprovalCardNotification notification,
                                                  ApprovalTask task,
                                                  PurchaseRequest request,
                                                  SysUser approver,
                                                  boolean resolved,
                                                  String resolvedAction,
                                                  Snapshot config,
                                                  Integer itemPage,
                                                  List<Long> selectedRejectItemIds) {
        if (isReplacementConfirmationCard(task, request)) {
            return buildReplacementConfirmationCard(notification, task, request, approver, resolved, resolvedAction, config);
        }
        boolean overPriceReviewCard = isOverPriceReviewCard(task, request);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_JSON_SCHEMA_VERSION);
        String title = overPriceReviewCard
            ? (resolved ? "楼长超价复核已处理" : "楼长超价复核待处理")
            : (resolved ? "采购审批已处理" : "采购审批待处理");
        card.put("config", cardConfig(title, true));
        card.put("header", cardHeader(resolved ? "green" : "orange", title));
        List<PurchaseRequestItem> items = cardItems(task, request);
        FeishuDemandSubmitterResolver.SubmitterContext submitters = demandSubmitterResolver.resolve(request, items);
        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        elements.add(markdownElement(approvalSummaryMarkdown(request, task, approver, notification, submitters)));
        Map<Long, ItemReview> overPriceReviews = overPriceReviewCard
            ? overPriceReviewService.reviewByRequestItemId(request)
            : Map.of();
        if (!items.isEmpty()) {
            elements.add(Map.of("tag", "hr"));
            elements.addAll(itemDetailElements(
                items,
                normalizeItemPage(itemPage, items.size()),
                selectedRejectItemIds,
                notification,
                request,
                !resolved && canRejectSingleItem(task, request),
                overPriceReviews,
                submitters));
        }
        elements.add(Map.of("tag", "hr"));
        if (resolved) {
            elements.add(markdownElement("**处理结果**：" + actionLabel(resolvedAction)));
        } else {
            boolean invalidItemAmount = hasInvalidItemAmount(items);
            if (invalidItemAmount) {
                elements.add(markdownElement("**金额校验**：存在单价或金额小于等于 0 的明细，不能直接同意；请退回整单或单项驳回后让申请人补充价格。"));
            }
            if (canRejectSingleItem(task, request) && !items.isEmpty()) {
                List<Long> selectedIds = rejectItemIdsOnActiveItems(selectedRejectItemIds, items);
                List<Map<String, Object>> rejectFormElements = new java.util.ArrayList<>();
                rejectFormElements.add(markdownElement(overPriceReviewCard
                    ? "**超价项决策**：标记需要驳回的超价商品；未标记商品将按通过继续流转。"
                    : "**单项驳回**：点击明细右侧“驳回本项”标记待驳回；选错后点“恢复”。"));
                rejectFormElements.add(markdownElement("**已标记待驳回**：" + selectedIds.size() + " 条"));
                rejectFormElements.add(rejectReasonInput());
                rejectFormElements.add(actionButton(
                    overPriceReviewCard ? "驳回已标记超价项" : "提交单项驳回",
                    "danger",
                    ACTION_REJECT_ITEM,
                    notification,
                    request,
                    rejectConfirmText(request, selectedIds, items),
                    normalizeItemPage(itemPage, items.size()),
                    null,
                    selectedIds,
                    true));
                elements.add(Map.of(
                    "tag", "form",
                    "name", "rejectItemForm",
                    "elements", rejectFormElements
                ));
                elements.add(Map.of("tag", "hr"));
            }
            List<Map<String, Object>> actions = new java.util.ArrayList<>();
            Map<String, Object> approveButton = actionButton(
                overPriceReviewCard ? "通过超价项" : "同意全部",
                "primary",
                ProcurementWorkflow.ACTION_APPROVE,
                notification,
                request
            );
            if (invalidItemAmount) {
                approveButton.put("disabled", true);
                approveButton.put("text", plainText("同意全部（需补价）"));
            }
            actions.add(approveButton);
            if (!overPriceReviewCard) {
                actions.add(actionButton("退回整单", "danger", ProcurementWorkflow.ACTION_RETURN, notification, request));
            }
            String systemActionUrl = systemActionUrl(request, config);
            if (StringUtils.hasText(systemActionUrl)) {
                actions.add(linkButton("系统处理", systemActionUrl));
            }
            List<Map<String, Object>> approvalFormElements = new java.util.ArrayList<>();
            approvalFormElements.add(approvalCommentInput(overPriceReviewCard));
            approvalFormElements.add(buttonRow(actions));
            elements.add(Map.of(
                "tag", "form",
                "name", "approvalActionForm",
                "elements", approvalFormElements
            ));
        }
        elements.add(versionElement());
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private boolean isReplacementConfirmationCard(ApprovalTask task, PurchaseRequest request) {
        return (task != null && Objects.equals(task.getLevelNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW))
            || (request != null && Objects.equals(request.getCurrentNodeNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW));
    }

    private Map<String, Object> buildReplacementConfirmationCard(FeishuApprovalCardNotification notification,
                                                                 ApprovalTask task,
                                                                 PurchaseRequest request,
                                                                 SysUser approver,
                                                                 boolean resolved,
                                                                 String resolvedAction,
                                                                 Snapshot config) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_JSON_SCHEMA_VERSION);
        String title = resolved ? "替换商品确认已处理" : "替换商品待确认";
        card.put("config", cardConfig(title, true));
        card.put("header", cardHeader(resolved ? "green" : "orange", title));

        List<PurchaseRequestItem> requestItems = activeItems(request);
        FeishuDemandSubmitterResolver.SubmitterContext submitters = demandSubmitterResolver.resolve(request, requestItems);
        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        elements.add(markdownElement(replacementSummaryMarkdown(request, task, approver, notification, submitters)));
        List<SupplierQuoteItem> replacementItems = awardedReplacementItems(request == null ? null : request.getId());
        if (!replacementItems.isEmpty()) {
            elements.add(Map.of("tag", "hr"));
            elements.add(markdownElement("**替换商品明细**"));
            Map<Long, SupplierQuote> quoteById = supplierQuotesById(replacementItems);
            Map<Long, Supplier> supplierById = suppliersByQuote(quoteById);
            List<SupplierQuoteItem> visibleItems = replacementItems.size() > CARD_ITEM_PAGE_SIZE
                ? replacementItems.subList(0, CARD_ITEM_PAGE_SIZE)
                : replacementItems;
            int index = 1;
            for (SupplierQuoteItem item : visibleItems) {
                elements.add(markdownElement(replacementItemMarkdown(index++, item, quoteById, supplierById, true, submitters)));
            }
            if (replacementItems.size() > visibleItems.size()) {
                elements.add(markdownElement("还有 " + (replacementItems.size() - visibleItems.size()) + " 条替换商品未展示，请在系统里查看完整明细。"));
            }
        }
        elements.add(Map.of("tag", "hr"));
        if (resolved) {
            elements.add(markdownElement("**处理结果**：" + actionLabel(resolvedAction)));
        } else {
            List<Map<String, Object>> actions = new java.util.ArrayList<>();
            actions.add(actionButton(
                "同意替换",
                "primary",
                ACTION_APPROVE_REPLACEMENT,
                notification,
                request,
                replacementConfirmText(request, replacementItems, true, submitters)));
            actions.add(actionButton(
                "不同意替换",
                "danger",
                ACTION_REJECT_REPLACEMENT,
                notification,
                request,
                replacementConfirmText(request, replacementItems, false, submitters)));
            String systemActionUrl = systemActionUrl(request, config);
            if (StringUtils.hasText(systemActionUrl)) {
                actions.add(linkButton("系统处理", systemActionUrl));
            }
            List<Map<String, Object>> formElements = new java.util.ArrayList<>();
            formElements.add(replacementConfirmInput());
            formElements.add(buttonRow(actions));
            elements.add(Map.of(
                "tag", "form",
                "name", "replacementConfirmForm",
                "elements", formElements
            ));
        }
        elements.add(versionElement());
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private String replacementSummaryMarkdown(PurchaseRequest request,
                                               ApprovalTask task,
                                               SysUser approver,
                                               FeishuApprovalCardNotification notification,
                                               FeishuDemandSubmitterResolver.SubmitterContext submitters) {
        StringBuilder builder = new StringBuilder();
        appendMarkdownLine(builder, "卡片版本", CARD_TEMPLATE_VERSION, false);
        appendMarkdownLine(builder, "申请单号", request == null ? null : request.getReqNo(), false);
        appendMarkdownLine(builder, "申请标题", request == null ? null : request.getReqTitle(), false);
        appendMarkdownLine(builder, "需求提交人", submitters == null ? null : submitters.summary(), false);
        appendMarkdownLine(builder, "提交时间", formatDateTime(request == null ? null : request.getSubmittedAt()), false);
        appendMarkdownLine(builder, totalAmountLabel(request), money(request == null ? null : request.getTotalAmount()), false);
        appendActingMarkdownLine(builder, request);
        appendMarkdownLine(builder, "当前节点", task == null ? null : task.getLevelName(), true);
        appendMarkdownLine(builder, "确认人", displayName(approver), true);
        SysUser recipient = cardRecipient(notification);
        if (recipient != null && approver != null && !Objects.equals(recipient.getId(), approver.getId())) {
            appendMarkdownLine(builder, "卡片接收人", displayName(recipient) + "（管理员抄送）", true);
        }
        appendMarkdownLine(builder, "处理规则", "同意后按替换商品继续；不同意或超过 2 天未确认，则按原商品报价继续。", false);
        return builder.toString();
    }

    private List<SupplierQuoteItem> awardedReplacementItems(Long purchaseRequestId) {
        if (purchaseRequestId == null) {
            return List.of();
        }
        return supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuoteItem::getStatus, "ACTIVE")
            .eq(SupplierQuoteItem::getAwarded, 1)
            .eq(SupplierQuoteItem::getReplacementFlag, 1)
            .orderByAsc(SupplierQuoteItem::getLineNo)
            .orderByAsc(SupplierQuoteItem::getId));
    }

    private Map<Long, SupplierQuote> supplierQuotesById(List<SupplierQuoteItem> replacementItems) {
        List<Long> quoteIds = replacementItems == null ? List.of() : replacementItems.stream()
            .map(SupplierQuoteItem::getQuoteId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (quoteIds.isEmpty()) {
            return Map.of();
        }
        return supplierQuoteMapper.selectBatchIds(quoteIds).stream()
            .collect(Collectors.toMap(SupplierQuote::getId, quote -> quote, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, Supplier> suppliersByQuote(Map<Long, SupplierQuote> quoteById) {
        List<Long> supplierIds = quoteById == null ? List.of() : quoteById.values().stream()
            .map(SupplierQuote::getSupplierId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(supplierIds).stream()
            .collect(Collectors.toMap(Supplier::getId, supplier -> supplier, (left, right) -> left, LinkedHashMap::new));
    }

    private String replacementItemMarkdown(int index,
                                           SupplierQuoteItem item,
                                           Map<Long, SupplierQuote> quoteById,
                                           Map<Long, Supplier> supplierById,
                                           boolean includeLink,
                                           FeishuDemandSubmitterResolver.SubmitterContext submitters) {
        SupplierQuote quote = item == null ? null : quoteById.get(item.getQuoteId());
        Supplier supplier = quote == null ? null : supplierById.get(quote.getSupplierId());
        StringBuilder builder = new StringBuilder();
        builder.append(index).append(". **原商品**：")
            .append(markdownText(item == null ? null : item.getItemName()));
        if (item != null && StringUtils.hasText(item.getSpecModel())) {
            builder.append(" / ").append(markdownText(item.getSpecModel()));
        }
        if (item != null) {
            builder.append(" / ").append(decimal(item.getQty()))
                .append(markdownText(item.getUnit()))
                .append(" / 原报价 ").append(money(item.getLineAmount()));
            builder.append("\n   原商品成色：").append(productConditionLabel(item.getOriginalCondition()))
                .append("；资产判定：").append(assetClassificationLabel(item.getOriginalAssetClassification()));
            if (item.getOriginalSpecialApprovalRequired() != null && item.getOriginalSpecialApprovalRequired() == 1) {
                builder.append("；**需特殊审批**");
            }
            if (StringUtils.hasText(item.getOriginalAssetCategoryCode())
                && StringUtils.hasText(item.getOriginalAssetNameCode())) {
                builder.append("（目录 ")
                    .append(markdownText(item.getOriginalAssetCategoryCode()))
                    .append("/")
                    .append(markdownText(item.getOriginalAssetNameCode()))
                    .append(" ")
                    .append(markdownText(item.getOriginalAssetCatalogName()))
                    .append("）");
            }
        }
        builder.append("\n   **替换为**：")
            .append(markdownText(firstText(item == null ? null : item.getReplacementItemName(), item == null ? null : item.getItemName())));
        if (item != null && StringUtils.hasText(item.getReplacementSpecModel())) {
            builder.append(" / ").append(markdownText(item.getReplacementSpecModel()));
        }
        if (item != null) {
            builder.append(" / 替换报价 ").append(money(firstAmount(item.getReplacementLineAmount(), item.getLineAmount())));
            builder.append("\n   替换商品成色：").append(productConditionLabel(item.getReplacementCondition()))
                .append("；资产判定：").append(assetClassificationLabel(item.getReplacementAssetClassification()));
            if (item.getReplacementSpecialApprovalRequired() != null && item.getReplacementSpecialApprovalRequired() == 1) {
                builder.append("；**需特殊审批**");
            }
            if (StringUtils.hasText(item.getReplacementAssetCategoryCode())
                && StringUtils.hasText(item.getReplacementAssetNameCode())) {
                builder.append("（目录 ")
                    .append(markdownText(item.getReplacementAssetCategoryCode()))
                    .append("/")
                    .append(markdownText(item.getReplacementAssetNameCode()))
                    .append(" ")
                    .append(markdownText(item.getReplacementAssetCatalogName()))
                    .append("）");
            }
            if (item.getConditionMismatch() != null && item.getConditionMismatch() == 1) {
                builder.append("；**成色声明与 M3 复核不一致，需人工确认**");
            }
        }
        if (includeLink && item != null && StringUtils.hasText(item.getReplacementEcommerceUrl())) {
            builder.append("\n   替换链接：[打开商品](").append(markdownUrl(item.getReplacementEcommerceUrl())).append(")");
        }
        builder.append("\n   供应商：")
            .append(markdownText(supplier == null ? null : firstText(supplier.getSupplierShortName(), supplier.getSupplierName())));
        if (item != null && StringUtils.hasText(item.getReplacementReason())) {
            builder.append("；替换原因：").append(markdownText(item.getReplacementReason()));
        }
        if (item != null && StringUtils.hasText(item.getReplacementReviewStatus())) {
            builder.append("；确认状态：").append(markdownText(replacementReviewStatusLabel(item.getReplacementReviewStatus())));
        }
        if (submitters != null && submitters.hasMultipleSubmitters() && item != null) {
            builder.append("\n   需求提交人：")
                .append(markdownText(submitters.itemSubmitter(item.getPurchaseRequestItemId())));
        }
        return builder.toString();
    }

    private Map<String, Object> replacementConfirmInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tag", "input");
        input.put("name", "approvalComment");
        input.put("required", true);
        input.put("input_type", "text");
        input.put("max_length", 200);
        input.put("default_value", "同意替换商品");
        input.put("label", plainText("确认意见"));
        input.put("placeholder", plainText("同意可保留默认；不同意请改写原因"));
        return input;
    }

    private String replacementConfirmText(PurchaseRequest request,
                                          List<SupplierQuoteItem> items,
                                          boolean approved,
                                          FeishuDemandSubmitterResolver.SubmitterContext submitters) {
        StringBuilder builder = new StringBuilder();
        builder.append(approved ? "确认同意替换商品？" : "确认不同意替换商品？")
            .append("\n申请单号：").append(firstText(request == null ? null : request.getReqNo(), "-"))
            .append("\n\n替换明细：");
        Map<Long, SupplierQuote> quoteById = supplierQuotesById(items);
        Map<Long, Supplier> supplierById = suppliersByQuote(quoteById);
        List<SupplierQuoteItem> visibleItems = items == null ? List.of()
            : (items.size() > CARD_ITEM_PAGE_SIZE ? items.subList(0, CARD_ITEM_PAGE_SIZE) : items);
        int index = 1;
        for (SupplierQuoteItem item : visibleItems) {
            builder.append("\n").append(replacementItemMarkdown(index++, item, quoteById, supplierById, false, submitters));
        }
        if (items != null && items.size() > visibleItems.size()) {
            builder.append("\n还有 ").append(items.size() - visibleItems.size()).append(" 条未展示，请在系统里查看完整明细。");
        }
        if (index == 1) {
            builder.append("\n-");
        }
        return limit(builder.toString(), 1200);
    }

    private String replacementReviewStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "PENDING" -> "待确认";
            case "APPROVED" -> "已同意替换";
            case "REJECTED" -> "已不同意替换";
            case "AUTO_REJECTED" -> "超时按不同意替换";
            default -> status;
        };
    }

    private String productConditionLabel(String condition) {
        if ("NEW".equalsIgnoreCase(firstText(condition, ""))) {
            return "全新";
        }
        if ("USED".equalsIgnoreCase(firstText(condition, ""))) {
            return "二手";
        }
        return "待确认";
    }

    private String assetClassificationLabel(String classification) {
        return switch (firstText(classification, "")) {
            case "ASSET" -> "固定资产";
            case "LOW_VALUE" -> "低值易耗";
            case "SPECIAL_APPROVAL" -> "固定资产";
            case "NOT_ASSET" -> "低值易耗";
            case "PENDING" -> "M3 复核中";
            case "MANUAL_REVIEW" -> "待人工确认";
            default -> "待确认";
        };
    }

    private BigDecimal firstAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> buildTestCard(SysUser user, String receiveIdType, Snapshot config) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_JSON_SCHEMA_VERSION);
        card.put("config", cardConfig("飞书审批卡片测试", false));
        card.put("header", cardHeader("blue", "飞书审批卡片测试"));
        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        String systemActionUrl = systemActionUrl(null, config);
        elements.add(markdownElement(
            "**接收账号**：" + markdownText(displayName(user)) + "\n"
                + "**接收 ID 类型**：" + markdownText(receiveIdType) + "\n"
                + "**系统地址**：" + markdownText(firstText(config.appBaseUrl(), "未配置")) + "\n"
                + "**系统处理入口**：" + markdownText(firstText(systemActionUrl, "未配置")) + "\n"
                + "**测试结果**：如果你能看到这张卡片，说明机器人消息发送和账号绑定已打通。"));
        if (StringUtils.hasText(systemActionUrl)) {
            elements.add(Map.of("tag", "hr"));
            elements.add(linkButton("打开系统", systemActionUrl));
        }
        elements.add(versionElement());
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private Map<String, Object> buildApplicantResultCard(PurchaseRequest request,
                                                         SysUser operator,
                                                         String action,
                                                         String approvalComment,
                                                         List<Long> rejectItemIds,
                                                         Snapshot config) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_JSON_SCHEMA_VERSION);
        String title = "采购审批结果通知";
        card.put("config", cardConfig(title, false));
        card.put("header", cardHeader(resultHeaderColor(action), title));
        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        StringBuilder summary = new StringBuilder();
        appendMarkdownLine(summary, "申请单号", request == null ? null : request.getReqNo(), false);
        appendMarkdownLine(summary, "申请标题", request == null ? null : request.getReqTitle(), false);
        appendActingMarkdownLine(summary, request);
        appendMarkdownLine(summary, "AI审核意见", aiReviewOpinion(request), true);
        appendMarkdownLine(summary, "审批结果", actionLabel(action), false);
        appendMarkdownLine(summary, "处理人", displayName(operator), true);
        appendMarkdownLine(summary, "审批意见", approvalComment, true);
        appendMarkdownLine(summary, "当前状态", requestStatusLabel(request), true);
        elements.add(markdownElement(summary.toString()));
        String rejectedDetails = rejectedItemsMarkdown(rejectItemIds);
        if (StringUtils.hasText(rejectedDetails)) {
            elements.add(Map.of("tag", "hr"));
            elements.add(markdownElement(rejectedDetails));
        }
        if (StringUtils.hasText(config.appBaseUrl())) {
            elements.add(Map.of("tag", "hr"));
            elements.add(linkButton("查看申请", approvalUrl(request, config)));
        }
        elements.add(versionElement());
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private String resultHeaderColor(String action) {
        if (ProcurementWorkflow.ACTION_RETURN.equals(action)) {
            return "red";
        }
        if (ACTION_REJECT_ITEM.equals(action)) {
            return "orange";
        }
        if (ACTION_REJECT_REPLACEMENT.equals(action)) {
            return "orange";
        }
        return "green";
    }

    private String requestStatusLabel(PurchaseRequest request) {
        if (request == null) {
            return "";
        }
        String status = firstText(request.getStatus(), request.getApprovalStatus());
        return switch (status) {
            case ProcurementWorkflow.STATUS_PENDING_APPROVAL -> "审批中";
            case ProcurementWorkflow.STATUS_DEMAND_POOL -> "进入需求池";
            case ProcurementWorkflow.STATUS_QUOTING -> "供应商报价中";
            case ProcurementWorkflow.STATUS_RETURNED -> "已退回";
            case ProcurementWorkflow.STATUS_REJECTED -> "已驳回";
            case ProcurementWorkflow.STATUS_TERMINATED -> "已终止";
            case ProcurementWorkflow.STATUS_APPROVED -> "已通过";
            default -> status;
        };
    }

    private String rejectedItemsMarkdown(List<Long> rejectItemIds) {
        if (rejectItemIds == null || rejectItemIds.isEmpty()) {
            return "";
        }
        List<PurchaseRequestItem> items = purchaseRequestItemMapper.selectBatchIds(rejectItemIds);
        if (items == null || items.isEmpty()) {
            return "";
        }
        Map<Long, PurchaseRequestItem> itemMap = items.stream()
            .filter(item -> item.getId() != null)
            .collect(Collectors.toMap(PurchaseRequestItem::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        StringBuilder builder = new StringBuilder("**本次驳回明细**");
        int index = 1;
        for (Long itemId : rejectItemIds) {
            PurchaseRequestItem item = itemMap.get(itemId);
            if (item == null) {
                continue;
            }
            appendConfirmItem(builder, index++, item);
        }
        return index > 1 ? builder.toString() : "";
    }

    private Map<String, Object> cardConfig(String summary, boolean updateMulti) {
        return Map.of(
            "enable_forward", false,
            "update_multi", updateMulti,
            "width_mode", "default",
            "summary", Map.of("content", summary + " " + CARD_TEMPLATE_VERSION)
        );
    }

    private Map<String, Object> cardHeader(String template, String title) {
        return Map.of(
            "template", template,
            "title", plainText(title),
            "text_tag_list", List.of(Map.of(
                "tag", "text_tag",
                "text", plainText(CARD_TEMPLATE_VERSION),
                "color", "neutral"
            ))
        );
    }

    private Map<String, Object> markdownElement(String content) {
        return Map.of(
            "tag", "markdown",
            "content", firstText(content, "-")
        );
    }

    private Map<String, Object> versionElement() {
        return markdownElement("卡片版本：" + CARD_TEMPLATE_VERSION);
    }

    private Map<String, Object> plainText(String content) {
        return Map.of("tag", "plain_text", "content", firstText(content, "-"));
    }

    private String approvalSummaryMarkdown(PurchaseRequest request,
                                           ApprovalTask task,
                                           SysUser approver,
                                           FeishuApprovalCardNotification notification,
                                           FeishuDemandSubmitterResolver.SubmitterContext submitters) {
        StringBuilder builder = new StringBuilder();
        appendMarkdownLine(builder, "卡片版本", CARD_TEMPLATE_VERSION, false);
        appendMarkdownLine(builder, "申请单号", request == null ? null : request.getReqNo(), false);
        appendMarkdownLine(builder, "申请标题", request == null ? null : request.getReqTitle(), false);
        appendMarkdownLine(builder, "需求提交人", submitters == null ? null : submitters.summary(), false);
        appendMarkdownLine(builder, totalAmountLabel(request), money(request == null ? null : request.getTotalAmount()), false);
        appendActingMarkdownLine(builder, request);
        appendMarkdownLine(builder, "申请原因", request == null ? null : request.getReqReason(), true);
        appendMarkdownLine(builder, "AI审核意见", aiReviewOpinion(request), true);
        appendMarkdownLine(builder, "当前节点", task == null ? null : task.getLevelName(), true);
        if (isOverPriceReviewCard(task, request)) {
            appendMarkdownLine(builder, "复核时限", overPriceReviewDueText(task), false);
        }
        appendMarkdownLine(builder, "审批人", displayName(approver), true);
        SysUser recipient = cardRecipient(notification);
        if (recipient != null && approver != null && !Objects.equals(recipient.getId(), approver.getId())) {
            appendMarkdownLine(builder, "卡片接收人", displayName(recipient) + "（管理员抄送）", true);
        }
        return builder.toString();
    }

    private String overPriceReviewDueText(ApprovalTask task) {
        if (task == null || task.getDueAt() == null) {
            return "24小时内完成";
        }
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(now, task.getDueAt());
        if (duration.isNegative() || duration.isZero()) {
            long overdueMinutes = Math.max(0, Math.abs(duration.toMinutes()));
            return "已超时 " + durationText(overdueMinutes) + "；截止 " + task.getDueAt().format(DATE_TIME_FORMATTER);
        }
        return "剩余 " + durationText(duration.toMinutes()) + "；截止 " + task.getDueAt().format(DATE_TIME_FORMATTER);
    }

    private String durationText(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + "小时" + minutes + "分";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_FORMATTER);
    }

    private SysUser cardRecipient(FeishuApprovalCardNotification notification) {
        if (notification == null || notification.getRecipientUserId() == null) {
            return null;
        }
        return userMapper.selectById(notification.getRecipientUserId());
    }

    private String aiReviewOpinion(PurchaseRequest request) {
        if (request == null) {
            return "";
        }
        String direct = firstText(request.getAiReviewOpinion(), extractAiReviewOpinion(request.getRemark()));
        if (StringUtils.hasText(direct)) {
            return limit(ensureApprovalDecisionOpinion(direct), 260);
        }
        if (request.getId() != null) {
            String byRequestId = aiReviewOpinionByRequestId(request.getId());
            if (StringUtils.hasText(byRequestId)) {
                return limit(ensureApprovalDecisionOpinion(byRequestId), 260);
            }
        }
        List<String> sessionNos = aiSessionNos(request.getRemark());
        if (sessionNos.isEmpty()) {
            return "";
        }
        return ensureApprovalDecisionOpinion(aiReviewOpinionBySessionNos(sessionNos));
    }

    private String aiReviewOpinionByRequestId(Long requestId) {
        List<ProcurementAiSession> sessions = procurementAiSessionMapper.selectList(
            new LambdaQueryWrapper<ProcurementAiSession>()
                .eq(ProcurementAiSession::getPurchaseRequestId, requestId));
        return aiReviewOpinionFromSessions(sessions);
    }

    private String aiReviewOpinionBySessionNos(List<String> sessionNos) {
        if (sessionNos == null || sessionNos.isEmpty()) {
            return "";
        }
        List<ProcurementAiSession> sessions = procurementAiSessionMapper.selectList(
            new LambdaQueryWrapper<ProcurementAiSession>()
                .in(ProcurementAiSession::getSessionNo, sessionNos));
        return aiReviewOpinionFromSessions(sessions);
    }

    private String aiReviewOpinionFromSessions(List<ProcurementAiSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "";
        }
        String opinion = sessions.stream()
            .map(ProcurementAiSession::getAiSummary)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .limit(3)
            .reduce((left, right) -> left + "；" + right)
            .orElse("");
        return limit(ensureApprovalDecisionOpinion(opinion), 260);
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

    private String extractAiReviewOpinion(String remark) {
        if (!StringUtils.hasText(remark)) {
            return "";
        }
        Matcher matcher = AI_REVIEW_REMARK_PATTERN.matcher(remark);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private List<String> aiSessionNos(String remark) {
        if (!StringUtils.hasText(remark)) {
            return List.of();
        }
        LinkedHashSet<String> sessionNos = new LinkedHashSet<>();
        Matcher matcher = AI_SESSION_NO_PATTERN.matcher(remark);
        while (matcher.find()) {
            sessionNos.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(sessionNos);
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

    private void appendActingMarkdownLine(StringBuilder builder, PurchaseRequest request) {
        if (request == null || request.getActingUserId() == null) {
            return;
        }
        String actingUser = firstText(request.getActingUserName(), String.valueOf(request.getActingUserId()));
        String dept = firstText(request.getDeptName(), request.getDeptCode(), "-");
        String reason = StringUtils.hasText(request.getActingReason()) ? "；原因：" + request.getActingReason().trim() : "";
        appendMarkdownLine(builder, "管理员代办", actingUser + " 代申请人选择部门 " + dept + reason, false);
    }

    private List<Map<String, Object>> itemDetailElements(List<PurchaseRequestItem> items,
                                                         int page,
                                                         List<Long> selectedRejectItemIds,
                                                         FeishuApprovalCardNotification notification,
                                                         PurchaseRequest request,
                                                         boolean interactive,
                                                         Map<Long, ItemReview> overPriceReviews,
                                                         FeishuDemandSubmitterResolver.SubmitterContext submitters) {
        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        int total = items == null ? 0 : items.size();
        int pageCount = pageCount(total);
        int currentPage = normalizeItemPage(page, total);
        int start = (currentPage - 1) * CARD_ITEM_PAGE_SIZE;
        int end = Math.min(start + CARD_ITEM_PAGE_SIZE, total);
        List<Long> selectedIds = rejectItemIdsOnActiveItems(selectedRejectItemIds, items);
        elements.add(markdownElement("**采购明细**（第 " + currentPage + "/" + pageCount + " 页，共 " + total + " 条）"));
        for (int i = start; i < end; i++) {
            PurchaseRequestItem item = items.get(i);
            boolean selected = item != null && selectedIds.contains(item.getId());
            elements.add(itemRow(
                i + 1,
                item,
                selected,
                interactive,
                notification,
                request,
                currentPage,
                selectedIds,
                overPriceReviews == null ? null : overPriceReviews.get(item == null ? null : item.getId()),
                submitters
            ));
        }
        if (pageCount > 1 && interactive) {
            elements.add(buttonRow(List.of(
                pageButton("上一页", notification, request, Math.max(1, currentPage - 1), selectedIds, currentPage > 1),
                pageButton("下一页", notification, request, Math.min(pageCount, currentPage + 1), selectedIds, currentPage < pageCount)
            )));
        } else if (pageCount > 1) {
            elements.add(markdownElement("还有更多明细，请到系统查看完整数据。"));
        }
        return elements;
    }

    private Map<String, Object> itemRow(int index,
                                        PurchaseRequestItem item,
                                        boolean selected,
                                        boolean interactive,
                                        FeishuApprovalCardNotification notification,
                                        PurchaseRequest request,
                                        int page,
                                        List<Long> selectedRejectItemIds,
                                        ItemReview overPriceReview,
                                        FeishuDemandSubmitterResolver.SubmitterContext submitters) {
        Map<String, Object> leftColumn = new LinkedHashMap<>();
        leftColumn.put("tag", "column");
        leftColumn.put("width", "weighted");
        leftColumn.put("weight", 1);
        leftColumn.put("elements", List.of(markdownElement(itemMarkdown(index, item, selected, request, overPriceReview, submitters))));

        List<Map<String, Object>> columns = new java.util.ArrayList<>();
        columns.add(leftColumn);
        if (interactive && item != null && item.getId() != null) {
            Map<String, Object> rightColumn = new LinkedHashMap<>();
            rightColumn.put("tag", "column");
            rightColumn.put("width", "auto");
            rightColumn.put("elements", List.of(toggleRejectItemButton(
                selected ? "恢复" : "驳回本项",
                selected ? "default" : "danger",
                notification,
                request,
                page,
                item.getId(),
                selectedRejectItemIds)));
            columns.add(rightColumn);
        }
        return Map.of(
            "tag", "column_set",
            "flex_mode", "none",
            "horizontal_spacing", "default",
            "background_style", "default",
            "columns", columns
        );
    }

    private String itemMarkdown(int index,
                                PurchaseRequestItem item,
                                boolean selected,
                                PurchaseRequest request,
                                ItemReview overPriceReview,
                                FeishuDemandSubmitterResolver.SubmitterContext submitters) {
        StringBuilder builder = new StringBuilder();
        StringBuilder line = new StringBuilder();
        line.append(index).append(". ").append(markdownText(item == null ? null : item.getItemName()));
        if (item != null && StringUtils.hasText(item.getReqType())) {
            line.append(" / 类型：").append(markdownText(item.getReqType()));
        }
        if (item != null && StringUtils.hasText(item.getSpecModel())) {
            line.append(" / ").append(markdownText(item.getSpecModel()));
        }
        if (item != null) {
            line.append(" / ")
                .append(decimal(item.getQty())).append(markdownText(item.getUnit()))
                .append(" / ").append(lineAmountLabel(request)).append("：").append(money(item.getLineAmount()));
        }
        builder.append(selected ? strikeMarkdown(line.toString()) + "  **待驳回**" : line);
        if (usesSupplierQuotePrice(request) && item != null && item.getReferenceLineAmount() != null) {
            builder.append("\n   原参考金额：").append(money(item.getReferenceLineAmount()));
        }
        if (overPriceReview != null && overPriceReview.reviewRequired()) {
            builder.append("\n   **超价：单价差 ")
                .append(money(overPriceReview.unitDifference()))
                .append("/").append(markdownText(item == null ? null : item.getUnit()))
                .append("；合计差 ").append(money(overPriceReview.lineDifference()))
                .append("；+").append(decimal(overPriceReview.ratePercent())).append("%**");
            builder.append("\n   中标单位：").append(markdownText(overPriceReview.supplierName()));
            builder.append("；参考单价：").append(money(overPriceReview.referenceUnitPrice()));
            builder.append("；中标单价：").append(money(overPriceReview.awardedUnitPrice()));
            builder.append("\n   提交者：").append(markdownText(overPriceReview.applicantName()));
            if (StringUtils.hasText(overPriceReview.applicantDeptName())) {
                builder.append("（").append(markdownText(overPriceReview.applicantDeptName())).append("）");
            }
            if (StringUtils.hasText(overPriceReview.sourceRequestNo())) {
                builder.append("；来源工单：").append(markdownText(overPriceReview.sourceRequestNo()));
            }
        }
        if (item != null && StringUtils.hasText(item.getPurpose())) {
            String purpose = "用途：" + markdownText(item.getPurpose());
            builder.append("\n   ").append(selected ? strikeMarkdown(purpose) : purpose);
        }
        if (submitters != null && submitters.hasMultipleSubmitters() && item != null) {
            builder.append("\n   需求提交人：").append(markdownText(submitters.itemSubmitter(item.getId())));
        }
        if (item != null && StringUtils.hasText(item.getEcommerceUrl())) {
            builder.append("\n   链接：[打开商品](").append(markdownUrl(item.getEcommerceUrl())).append(")");
        }
        return builder.toString();
    }

    private String totalAmountLabel(PurchaseRequest request) {
        return usesSupplierQuotePrice(request) ? "供应商报价总额" : "参考总金额";
    }

    private String lineAmountLabel(PurchaseRequest request) {
        return usesSupplierQuotePrice(request) ? "供应商报价" : "参考金额";
    }

    private boolean usesSupplierQuotePrice(PurchaseRequest request) {
        return request != null && ProcurementWorkflow.PRICE_SOURCE_SUPPLIER_QUOTE.equals(request.getPriceSource());
    }

    private boolean hasInvalidItemAmount(List<PurchaseRequestItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        return items.stream().anyMatch(item -> item != null
            && (item.getQty() == null
            || item.getQty().compareTo(BigDecimal.ZERO) <= 0
            || item.getUnitPrice() == null
            || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0
            || item.getLineAmount() == null
            || item.getLineAmount().compareTo(BigDecimal.ZERO) <= 0));
    }

    private String strikeMarkdown(String value) {
        return "~~" + firstText(value, "-").replace("~", "") + "~~";
    }

    private int pageCount(int total) {
        return Math.max(1, (Math.max(0, total) + CARD_ITEM_PAGE_SIZE - 1) / CARD_ITEM_PAGE_SIZE);
    }

    private int normalizeItemPage(Integer page, int total) {
        int pageCount = pageCount(total);
        int value = page == null ? 1 : page;
        if (value < 1) {
            return 1;
        }
        return Math.min(value, pageCount);
    }

    private List<Long> rejectItemIdsOnActiveItems(List<Long> itemIds, List<PurchaseRequestItem> items) {
        if (itemIds == null || itemIds.isEmpty() || items == null || items.isEmpty()) {
            return List.of();
        }
        Set<Long> activeIds = items.stream()
            .filter(item -> item != null && item.getId() != null)
            .map(PurchaseRequestItem::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return itemIds.stream()
            .filter(Objects::nonNull)
            .filter(activeIds::contains)
            .distinct()
            .toList();
    }

    private List<Long> toggleSelectedItem(List<Long> selectedIds, Long itemId, List<PurchaseRequestItem> items) {
        if (itemId == null || items == null || items.stream().noneMatch(item -> item != null && Objects.equals(item.getId(), itemId))) {
            return selectedIds == null ? List.of() : selectedIds;
        }
        LinkedHashSet<Long> result = selectedIds == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(selectedIds);
        if (!result.remove(itemId)) {
            result.add(itemId);
        }
        return List.copyOf(result);
    }

    private Map<String, Object> actionButton(String text,
                                             String type,
                                             String action,
                                             FeishuApprovalCardNotification notification,
                                             PurchaseRequest request) {
        return actionButton(text, type, action, notification, request, null);
    }

    private Map<String, Object> actionButton(String text,
                                             String type,
                                             String action,
                                             FeishuApprovalCardNotification notification,
                                             PurchaseRequest request,
                                             String confirmTextOverride) {
        return actionButton(text, type, action, notification, request, confirmTextOverride, null, null, List.of(), true);
    }

    private Map<String, Object> actionButton(String text,
                                             String type,
                                             String action,
                                             FeishuApprovalCardNotification notification,
                                             PurchaseRequest request,
                                             String confirmTextOverride,
                                             Integer itemPage,
                                             Long rejectItemId,
                                             List<Long> rejectItemIds,
                                             boolean confirmEnabled) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("source", "enterprise-procurement");
        value.put("action", action);
        value.put("approvalTaskId", String.valueOf(notification.getApprovalTaskId()));
        value.put("purchaseRequestId", String.valueOf(notification.getPurchaseRequestId()));
        value.put("cardToken", notification.getCardToken());
        value.put("cardVersion", CARD_TEMPLATE_VERSION);
        if (itemPage != null) {
            value.put("itemPage", itemPage);
        }
        if (rejectItemId != null) {
            value.put("rejectItemId", rejectItemId);
        }
        if (rejectItemIds != null && !rejectItemIds.isEmpty()) {
            value.put("rejectItemIds", rejectItemIds.stream().map(String::valueOf).toList());
        }
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
        if (ProcurementWorkflow.ACTION_APPROVE.equals(action)
            || ProcurementWorkflow.ACTION_RETURN.equals(action)
            || ACTION_REJECT_ITEM.equals(action)
            || ACTION_APPROVE_REPLACEMENT.equals(action)
            || ACTION_REJECT_REPLACEMENT.equals(action)) {
            button.put("name", formSubmitName(action));
            button.put("form_action_type", "submit");
        }
        if (!confirmEnabled) {
            return button;
        }
        String confirmTarget = ACTION_REJECT_ITEM.equals(action)
            ? "商品信息"
            : (ACTION_APPROVE_REPLACEMENT.equals(action) || ACTION_REJECT_REPLACEMENT.equals(action))
                ? "替换商品"
                : "采购申请";
        String reqNo = firstText(request == null ? null : request.getReqNo(), "该申请");
        String confirmText = ACTION_REJECT_ITEM.equals(action)
            ? "确认提交 " + reqNo + " 的单项驳回商品信息？"
            : "确认" + text + " " + reqNo + "？";
        button.put("confirm", Map.of(
            "title", plainText(text + confirmTarget),
            "text", plainText(firstText(confirmTextOverride, confirmText))
        ));
        return button;
    }

    private Map<String, Object> toggleRejectItemButton(String text,
                                                       String type,
                                                       FeishuApprovalCardNotification notification,
                                                       PurchaseRequest request,
                                                       int itemPage,
                                                       Long rejectItemId,
                                                       List<Long> rejectItemIds) {
        return actionButton(text, type, ACTION_TOGGLE_REJECT_ITEM, notification, request, null, itemPage, rejectItemId, rejectItemIds, false);
    }

    private Map<String, Object> pageButton(String text,
                                           FeishuApprovalCardNotification notification,
                                           PurchaseRequest request,
                                           int itemPage,
                                           List<Long> rejectItemIds,
                                           boolean enabled) {
        Map<String, Object> button = actionButton(text, "default", ACTION_ITEM_PAGE, notification, request, null, itemPage, null, rejectItemIds, false);
        if (!enabled) {
            button.put("disabled", true);
        }
        return button;
    }

    private String formSubmitName(String action) {
        if (ProcurementWorkflow.ACTION_APPROVE.equals(action)) {
            return "submitApprove";
        }
        if (ProcurementWorkflow.ACTION_RETURN.equals(action)) {
            return "submitReturn";
        }
        if (ACTION_APPROVE_REPLACEMENT.equals(action)) {
            return "submitApproveReplacement";
        }
        if (ACTION_REJECT_REPLACEMENT.equals(action)) {
            return "submitRejectReplacement";
        }
        return "submitRejectItem";
    }

    private String rejectConfirmText(PurchaseRequest request, List<Long> selectedItemIds, List<PurchaseRequestItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("确认提交 ")
            .append(firstText(request == null ? null : request.getReqNo(), "该申请"))
            .append(" 的单项驳回？\n")
            .append("将驳回卡片中已标记的明细，未标记明细按通过继续流转。\n\n")
            .append("本次驳回明细：");
        Map<Long, PurchaseRequestItem> itemMap = items == null ? Map.of() : items.stream()
            .filter(item -> item != null && item.getId() != null)
            .collect(Collectors.toMap(PurchaseRequestItem::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
        List<Long> ids = selectedItemIds == null ? List.of() : selectedItemIds;
        int index = 1;
        for (Long itemId : ids) {
            PurchaseRequestItem item = itemMap.get(itemId);
            if (item != null) {
                appendConfirmItem(builder, index++, item);
            }
        }
        if (index == 1) {
            builder.append("\n-");
        }
        return limit(builder.toString(), 1200);
    }

    private void appendConfirmItem(StringBuilder builder, int index, PurchaseRequestItem item) {
        builder.append("\n").append(index).append(". ").append(markdownText(item == null ? null : item.getItemName()));
        if (item != null && StringUtils.hasText(item.getSpecModel())) {
            builder.append(" / ").append(markdownText(item.getSpecModel()));
        }
        if (item != null) {
            builder.append(" / ")
                .append(decimal(item.getQty())).append(markdownText(item.getUnit()))
                .append(" / ").append(money(item.getLineAmount()));
            if (StringUtils.hasText(item.getPurpose())) {
                builder.append(" 用途：").append(markdownText(item.getPurpose()));
            }
        }
    }

    private Map<String, Object> rejectReasonInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tag", "input");
        input.put("name", "rejectReason");
        input.put("required", true);
        input.put("input_type", "text");
        input.put("max_length", 200);
        input.put("label", plainText("驳回原因"));
        input.put("placeholder", plainText("填写驳回原因，所选明细共用该原因"));
        return input;
    }

    private Map<String, Object> approvalCommentInput(boolean overPriceReview) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tag", "input");
        input.put("name", "approvalComment");
        input.put("required", true);
        input.put("input_type", "text");
        input.put("max_length", 200);
        if (!overPriceReview) {
            input.put("default_value", "同意");
        }
        input.put("label", plainText(overPriceReview ? "价格合理原因" : "审批意见"));
        input.put("placeholder", plainText(overPriceReview
            ? "通过超价商品时填写市场价格、规格变化或紧急采购等具体原因"
            : "填写审批意见；同意默认“同意”，退回请改成退回原因"));
        return input;
    }

    private Map<String, Object> linkButton(String text, String url) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("tag", "button");
        button.put("text", plainText(text));
        button.put("type", "default");
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

    private String markdownUrl(String url) {
        return normalizedUrl(url).replace(")", "%29").replace(" ", "%20");
    }

    private String normalizedUrl(String url) {
        String trimmed = firstText(url, "").trim();
        if (!StringUtils.hasText(trimmed)) {
            return "";
        }
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)
            || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private Map<String, Object> buttonRow(List<Map<String, Object>> buttons) {
        List<Map<String, Object>> columns = new java.util.ArrayList<>();
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

    private String approvalUrl(PurchaseRequest request, Snapshot config) {
        if (!StringUtils.hasText(config.appBaseUrl())) {
            return firstText(config.approvalPath(), "/procurement/approval");
        }
        String base = config.appBaseUrl().replaceAll("/+$", "");
        String path = StringUtils.hasText(config.approvalPath()) ? config.approvalPath() : "/procurement/approval";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String url = base + path;
        if (request != null && request.getId() != null) {
            url += "?requestId=" + request.getId();
        }
        return url;
    }

    private String systemActionUrl(PurchaseRequest request, Snapshot config) {
        if (StringUtils.hasText(config.systemActionUrl())) {
            return normalizedUrl(config.systemActionUrl());
        }
        if (StringUtils.hasText(config.appBaseUrl())) {
            return approvalUrl(request, config);
        }
        return "";
    }

    private FeishuApprovalCardNotification findNotification(FeishuApprovalCardActionResult.ActionRequest request) {
        if (request.getApprovalTaskId() == null || !StringUtils.hasText(request.getCardToken())) {
            return null;
        }
        return notificationMapper.selectOne(new LambdaQueryWrapper<FeishuApprovalCardNotification>()
            .eq(FeishuApprovalCardNotification::getApprovalTaskId, request.getApprovalTaskId())
            .eq(FeishuApprovalCardNotification::getCardToken, request.getCardToken())
            .last("LIMIT 1"));
    }

    private boolean canOperateNotification(SysUser operator, FeishuApprovalCardNotification notification) {
        if (operator == null || operator.getId() == null || notification == null) {
            return false;
        }
        if (Objects.equals(operator.getId(), notification.getApproverUserId())) {
            return true;
        }
        Long recipientUserId = notification.getRecipientUserId();
        if (Objects.equals(operator.getId(), recipientUserId) && isSystemAdminUser(operator)) {
            return true;
        }
        if (!isSystemAdminUser(operator)) {
            return false;
        }
        String receiveIdType = normalizeReceiveIdType(notification.getReceiveIdType());
        String receiveId = trimToNull(notification.getReceiveId());
        String operatorReceiveId = resolveFeishuReceiveId(operator, receiveIdType);
        if (!StringUtils.hasText(receiveId) || !StringUtils.hasText(operatorReceiveId)) {
            return false;
        }
        return "email".equals(receiveIdType)
            ? receiveId.equalsIgnoreCase(operatorReceiveId.trim())
            : receiveId.equals(operatorReceiveId.trim());
    }

    private SysUser approvalDisplayUser(FeishuApprovalCardNotification notification, ApprovalTask task, SysUser fallback) {
        Long approverUserId = notification == null ? null : notification.getApproverUserId();
        if (approverUserId == null && task != null) {
            approverUserId = task.getCandidateUserId();
        }
        SysUser approver = approverUserId == null ? null : userMapper.selectById(approverUserId);
        return approver == null ? fallback : approver;
    }

    private SysUser resolveOperator(FeishuApprovalCardActionResult.ActionRequest request,
                                    FeishuApprovalCardNotification notification) {
        SysUser boundUser = resolveOperatorByBoundFeishuId(request);
        if (boundUser != null) {
            return boundUser;
        }
        return resolveOperatorByNotificationRecipient(request, notification);
    }

    private SysUser resolveOperatorByBoundFeishuId(FeishuApprovalCardActionResult.ActionRequest request) {
        for (String token : List.of(request.getOpenId(), request.getUserId(), request.getUnionId())) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            SysUser byFeishuId = findUserByFeishuToken(token.trim());
            if (byFeishuId != null) {
                return byFeishuId;
            }
            SysUser byUsername = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, token.trim())
                .last("LIMIT 1"));
            if (byUsername != null) {
                return byUsername;
            }
            SysUser byRemark = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .like(SysUser::getRemark, token.trim())
                .last("LIMIT 1"));
            if (byRemark != null) {
                return byRemark;
            }
        }
        return null;
    }

    private SysUser resolveOperatorByNotificationRecipient(FeishuApprovalCardActionResult.ActionRequest request,
                                                           FeishuApprovalCardNotification notification) {
        if (notification == null) {
            return null;
        }
        Long recipientUserId = notification.getRecipientUserId() == null
            ? notification.getApproverUserId()
            : notification.getRecipientUserId();
        if (recipientUserId == null) {
            return null;
        }
        SysUser recipient = userMapper.selectById(recipientUserId);
        if (recipient == null || hasFeishuIdConflict(recipient, request)) {
            return null;
        }
        String receiveIdType = normalizeReceiveIdType(notification.getReceiveIdType());
        String receiveId = trimToNull(notification.getReceiveId());
        if (!StringUtils.hasText(receiveId)) {
            return null;
        }
        if ("email".equals(receiveIdType)) {
            if (!receiveId.equalsIgnoreCase(trimToNull(recipient.getEmail()))) {
                return null;
            }
            backfillFeishuIds(recipient, request);
            return recipient;
        }
        String operatorToken = switch (receiveIdType) {
            case "user_id" -> request.getUserId();
            case "union_id" -> request.getUnionId();
            default -> request.getOpenId();
        };
        if (StringUtils.hasText(operatorToken) && receiveId.equals(operatorToken.trim())) {
            backfillFeishuIds(recipient, request);
            return recipient;
        }
        return null;
    }

    private boolean hasFeishuIdConflict(SysUser user, FeishuApprovalCardActionResult.ActionRequest request) {
        return idConflict(user.getFeishuOpenId(), request.getOpenId())
            || idConflict(user.getFeishuUserId(), request.getUserId())
            || idConflict(user.getFeishuUnionId(), request.getUnionId());
    }

    private boolean idConflict(String stored, String received) {
        return StringUtils.hasText(stored)
            && StringUtils.hasText(received)
            && !stored.trim().equals(received.trim());
    }

    private void backfillFeishuIds(SysUser user, FeishuApprovalCardActionResult.ActionRequest request) {
        if (user == null || user.getId() == null) {
            return;
        }
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, user.getId());
        boolean changed = false;
        if (!StringUtils.hasText(user.getFeishuOpenId()) && StringUtils.hasText(request.getOpenId())) {
            wrapper.set(SysUser::getFeishuOpenId, request.getOpenId().trim());
            changed = true;
        }
        if (!StringUtils.hasText(user.getFeishuUserId()) && StringUtils.hasText(request.getUserId())) {
            wrapper.set(SysUser::getFeishuUserId, request.getUserId().trim());
            changed = true;
        }
        if (!StringUtils.hasText(user.getFeishuUnionId()) && StringUtils.hasText(request.getUnionId())) {
            wrapper.set(SysUser::getFeishuUnionId, request.getUnionId().trim());
            changed = true;
        }
        if (changed) {
            userMapper.update(null, wrapper);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private SysUser findUserByFeishuToken(String token) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getFeishuOpenId, token)
            .or()
            .eq(SysUser::getFeishuUserId, token)
            .or()
            .eq(SysUser::getFeishuUnionId, token)
            .last("LIMIT 1"));
    }

    private ReceiveTarget resolveFeishuReceiveTarget(SysUser user, String preferredReceiveIdType, boolean allowFallback) {
        String receiveIdType = normalizeReceiveIdType(preferredReceiveIdType);
        String receiveId = resolveFeishuReceiveId(user, receiveIdType);
        if (StringUtils.hasText(receiveId)) {
            return new ReceiveTarget(receiveIdType, receiveId);
        }
        if (!allowFallback) {
            return null;
        }
        for (String fallbackType : List.of("user_id", "open_id", "union_id", "email")) {
            if (fallbackType.equals(receiveIdType)) {
                continue;
            }
            receiveId = resolveFeishuReceiveId(user, fallbackType);
            if (StringUtils.hasText(receiveId)) {
                return new ReceiveTarget(fallbackType, receiveId);
            }
        }
        return null;
    }

    private String resolveFeishuReceiveId(SysUser user, String receiveIdType) {
        if (user == null) {
            return "";
        }
        String boundId = switch (receiveIdType) {
            case "user_id" -> user.getFeishuUserId();
            case "union_id" -> user.getFeishuUnionId();
            case "email" -> user.getEmail();
            default -> user.getFeishuOpenId();
        };
        if (StringUtils.hasText(boundId)) {
            return boundId.trim();
        }
        Map<String, String> ids = parseFeishuIds(user == null ? null : user.getRemark());
        String receiveId = ids.get(receiveIdType);
        if (StringUtils.hasText(receiveId)) {
            return receiveId;
        }
        String username = user == null ? null : user.getUsername();
        if (looksLikeFeishuId(username, receiveIdType)) {
            return username;
        }
        return "";
    }

    private Map<String, String> parseFeishuIds(String remark) {
        Map<String, String> ids = new LinkedHashMap<>();
        if (!StringUtils.hasText(remark)) {
            return ids;
        }
        Matcher matcher = FEISHU_ID_PATTERN.matcher(remark);
        while (matcher.find()) {
            ids.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
        }
        return ids;
    }

    private boolean looksLikeFeishuId(String value, String receiveIdType) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if ("open_id".equals(receiveIdType)) {
            return trimmed.startsWith("ou_");
        }
        if ("union_id".equals(receiveIdType)) {
            return trimmed.startsWith("on_");
        }
        if ("email".equals(receiveIdType)) {
            return trimmed.contains("@");
        }
        return true;
    }

    private void closeNotification(FeishuApprovalCardNotification notification, String messageId, String reason) {
        notification.setStatus(FeishuApprovalCardNotification.STATUS_CLOSED);
        notification.setMessageId(firstText(notification.getMessageId(), messageId));
        notification.setLastError(reason);
        notificationMapper.updateById(notification);
    }

    private String resolveTenantAccessToken(Snapshot config) {
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
                validateFeishuResponse(response);
                Map<String, Object> data = mapValue(response == null ? null : response.get("data"));
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
                cachedTenantAccessTokenSignature = signature;
                cachedTenantAccessTokenExpiresAt = now + Math.max(60L, expireSeconds - 60L) * 1000L;
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
            throw new BizException("FEISHU_API_FAILED", "Feishu response is empty");
        }
        String codeText = firstText(text(response.get("code")), text(response.get("Code")));
        if (!StringUtils.hasText(codeText) || "0".equals(codeText)) {
            return;
        }
        throw new BizException("FEISHU_API_FAILED", firstText(text(response.get("msg")), text(response.get("message")), "Feishu API failed"));
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException("JSON_SERIALIZE_FAILED", "Failed to build Feishu card");
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

    private String resolveApprovalComment(String action, FeishuApprovalCardActionResult.ActionRequest request) {
        String comment = request == null ? "" : firstText(request.getApprovalComment());
        if (!StringUtils.hasText(comment) && ACTION_REJECT_ITEM.equals(action)) {
            comment = request == null ? "" : firstText(request.getRejectReason());
        }
        if (!StringUtils.hasText(comment) && ProcurementWorkflow.ACTION_APPROVE.equals(action)) {
            return "同意";
        }
        return comment;
    }

    private String normalizeAction(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private List<PurchaseRequestItem> activeItems(PurchaseRequest request) {
        if (request == null || request.getId() == null) {
            return List.of();
        }
        return purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .orderByAsc(PurchaseRequestItem::getLineNo)
            .orderByAsc(PurchaseRequestItem::getId));
    }

    private List<PurchaseRequestItem> cardItems(ApprovalTask task, PurchaseRequest request) {
        List<PurchaseRequestItem> items = activeItems(request);
        if (!isOverPriceReviewCard(task, request)) {
            return items;
        }
        Set<Long> reviewItemIds = overPriceReviewService.requiredReviewItemIds(request);
        return items.stream()
            .filter(item -> item != null && item.getId() != null && reviewItemIds.contains(item.getId()))
            .toList();
    }

    private boolean isOverPriceReviewCard(ApprovalTask task, PurchaseRequest request) {
        if (task != null && ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW.equals(task.getLevelCode())) {
            return true;
        }
        return task == null
            && request != null
            && Objects.equals(request.getCurrentNodeNo(), ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW)
            && ProcurementWorkflow.PRICE_SOURCE_SUPPLIER_QUOTE.equals(request.getPriceSource())
            && overPriceReviewService.requiresReview(request);
    }

    private boolean canRejectSingleItem(ApprovalTask task, PurchaseRequest request) {
        Integer taskLevelNo = task == null ? null : task.getLevelNo();
        if (taskLevelNo != null && taskLevelNo >= ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW) {
            return true;
        }
        Integer requestNodeNo = request == null ? null : request.getCurrentNodeNo();
        if (requestNodeNo != null && requestNodeNo >= ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW) {
            return true;
        }
        String levelName = task == null ? null : task.getLevelName();
        return StringUtils.hasText(levelName)
            && (levelName.contains("楼长")
            || levelName.contains("经理")
            || levelName.contains("区域")
            || levelName.contains("总监")
            || levelName.contains("复审"));
    }

    private String normalizeReceiveIdType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "email";
        if ("email".equals(normalized) || "user_id".equals(normalized) || "union_id".equals(normalized)) {
            return normalized;
        }
        if ("open_id".equals(normalized)) {
            return normalized;
        }
        return "email";
    }

    private String missingReceiveIdMessage(String subject, String receiveIdType) {
        if ("email".equals(receiveIdType)) {
            return subject + "邮箱为空或未维护";
        }
        return subject + "未绑定飞书 " + receiveIdType;
    }

    private String missingAnyReceiveIdMessage(String subject) {
        return subject + "未维护邮箱或飞书 open_id/user_id/union_id";
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String actionLabel(String action) {
        if (ProcurementWorkflow.ACTION_APPROVE.equals(action)) {
            return "已同意";
        }
        if (ProcurementWorkflow.ACTION_RETURN.equals(action)) {
            return "已退回";
        }
        if (ACTION_REJECT_ITEM.equals(action)) {
            return "已提交单项驳回，其余明细按通过处理";
        }
        if (ACTION_APPROVE_REPLACEMENT.equals(action)) {
            return "已同意替换商品";
        }
        if (ACTION_REJECT_REPLACEMENT.equals(action)) {
            return "已不同意替换商品";
        }
        return firstText(action, "-");
    }

    private String actionResultMessage(String action) {
        if (ProcurementWorkflow.ACTION_APPROVE.equals(action)) {
            return "已同意全部";
        }
        if (ACTION_REJECT_ITEM.equals(action)) {
            return "已提交单项驳回";
        }
        if (ACTION_APPROVE_REPLACEMENT.equals(action)) {
            return "已同意替换商品";
        }
        if (ACTION_REJECT_REPLACEMENT.equals(action)) {
            return "已不同意替换商品";
        }
        return "已退回整单";
    }

    private List<Long> rejectItemIds(FeishuApprovalCardActionResult.ActionRequest request) {
        if (request == null) {
            return List.of();
        }
        List<Long> ids = new java.util.ArrayList<>();
        if (request.getRejectItemIds() != null) {
            ids.addAll(request.getRejectItemIds());
        }
        if (request.getRejectItemId() != null) {
            ids.add(request.getRejectItemId());
        }
        return ids.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private String money(BigDecimal value) {
        return "¥" + (value == null ? "0.00" : value.toPlainString());
    }

    private String decimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return "";
        }
        return firstText(user.getRealName(), user.getUsername(), String.valueOf(user.getId()));
    }

    private String markdownText(String value) {
        return firstText(value, "-").replace("\n", " ");
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
