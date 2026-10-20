package com.company.procurement.modules.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.procurement.assistant.config.FeishuBotRuntimeConfig;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierFeishuBinding;
import com.company.procurement.modules.supplier.entity.SupplierFeishuQuoteNotice;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.dto.SupplierAwardConfirmDTO;
import com.company.procurement.modules.supplier.dto.SupplierAwardWaiveDTO;
import com.company.procurement.modules.supplier.dto.SupplierFeishuQuoteNoticeQueryDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteItemSubmitDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteSubmitDTO;
import com.company.procurement.modules.supplier.mapper.SupplierFeishuBindingMapper;
import com.company.procurement.modules.supplier.mapper.SupplierFeishuQuoteNoticeMapper;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierFeishuNotificationService;
import com.company.procurement.modules.supplier.service.SupplierQuoteService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.supplier.vo.SupplierFeishuQuoteNoticeVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteVO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class SupplierFeishuNotificationServiceImpl implements SupplierFeishuNotificationService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final String CARD_VERSION = "v20260812.1";
    private static final String CARD_SCHEMA_VERSION = "2.0";
    private static final String CARD_SOURCE = "supplier-quote-notice";
    private static final String NOTICE_TYPE_QUOTE_TASK = "QUOTE_TASK";
    private static final String NOTICE_TYPE_AWARD_CONFIRM = "AWARD_CONFIRM";
    private static final String ACTION_DOWNLOAD_QUOTE_TEMPLATE = "DOWNLOAD_QUOTE_TEMPLATE";
    private static final String ACTION_DOWNLOAD_AWARD_LIST = "DOWNLOAD_AWARD_LIST";
    private static final String ACTION_CONFIRM_AWARD = "CONFIRM_AWARD";
    private static final String ACTION_WAIVE_AWARD = "WAIVE_AWARD";
    private static final String QUOTE_ITEM_STATUS_ACTIVE = "ACTIVE";
    private static final int DEFAULT_QUOTE_LEAD_TIME_DAYS = 7;
    private static final List<String> YES_NO_OPTIONS = List.of("是", "否");
    private static final List<String> PRODUCT_CONDITION_OPTIONS = List.of("全新", "二手");
    private static final String QUOTE_TEMPLATE_NOTICE = "请分别选择原商品和替换商品成色；选择二手时必须填写二手商品说明（来源、使用年限、翻新或拆机情况）；交期天数默认 7 天，可按实际情况修改。报价完成后将该 Excel 直接发回机器人。";
    private static final Long ADMIN_COPY_BINDING_ID = 0L;
    private static final Long ADMIN_FALLBACK_BINDING_ID = -1L;
    private static final String NATIONAL_DEMAND_AGGREGATE_REASON = "需求池全国月度汇总生成";
    private static final Pattern BIND_PATTERN = Pattern.compile(
        "^\\s*(?:.*?\\s*)?(?:供应商\\s*)?(?:绑定|綁定|bind)\\s*[:：]?\\s*([A-Za-z0-9_-]{2,64})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BIND_INTENT_PATTERN = Pattern.compile(
        ".*(?:绑定|綁定|bind).*",
        Pattern.CASE_INSENSITIVE
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SupplierMapper supplierMapper;
    private final SupplierFeishuBindingMapper bindingMapper;
    private final SupplierFeishuQuoteNoticeMapper noticeMapper;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final ProcurementSettingMapper settingMapper;
    private final EcommerceUrlService ecommerceUrlService;
    private final SupplierQuoteService supplierQuoteService;
    private final TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    private final ImportFileLogService importFileLogService;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final FeishuBotRuntimeConfig botRuntimeConfig;
    private final RestClient restClient = RestClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedTenantAccessToken;
    private volatile long cachedTenantAccessTokenExpiresAt;
    private volatile String cachedTenantAccessTokenSignature;

    private record ReceiveTarget(String receiveIdType, String receiveId) {
    }

    @Override
    public boolean isSupplierBindMessage(String text) {
        return StringUtils.hasText(text) && BIND_PATTERN.matcher(text.trim()).matches();
    }

    @Override
    public boolean isSupplierBindIntentMessage(String text) {
        return StringUtils.hasText(text) && BIND_INTENT_PATTERN.matcher(text.trim()).matches();
    }

    @Override
    public String supplierBindGuideMessage() {
        return "外部供应商绑定方式：\n"
            + "1. 请先向采购管理员确认供应商编码，例如 SUP0001。\n"
            + "2. 在当前机器人单聊或外部群发送：绑定 SUP0001\n"
            + "3. 绑定成功后，后续报价任务会推送到当前单聊或外部群；报价仍通过系统“供应商报价”流程提交。\n"
            + "如果不知道供应商编码，请联系采购管理员。";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bindSupplierFromMessage(
        String text,
        String openId,
        String userId,
        String unionId,
        String chatId,
        String chatType,
        String tenantKey,
        String sourceUserKey,
        String messageId
    ) {
        String supplierCode = parseSupplierCode(text);
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
            .eq(Supplier::getSupplierCode, supplierCode)
            .last("LIMIT 1"));
        if (supplier == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "未找到供应商编码：" + supplierCode);
        }
        if (!isSupplierUsable(supplier, LocalDateTime.now())) {
            throw new BizException("SUPPLIER_NOT_ACTIVE", "当前供应商未启用、未准入或已被限制报价，请联系采购管理员");
        }

        String receiveIdType = resolveReceiveIdType(chatId, chatType);
        String receiveId = "chat_id".equals(receiveIdType) ? chatId : openId;
        if (!StringUtils.hasText(receiveId)) {
            throw new BizException("FEISHU_SUPPLIER_RECEIVE_ID_MISSING", "飞书消息缺少可绑定的 open_id 或 chat_id");
        }

        SupplierFeishuBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<SupplierFeishuBinding>()
            .eq(SupplierFeishuBinding::getSupplierId, supplier.getId())
            .eq(SupplierFeishuBinding::getReceiveIdType, receiveIdType)
            .eq(SupplierFeishuBinding::getReceiveId, receiveId)
            .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (binding == null) {
            binding = new SupplierFeishuBinding();
            binding.setSupplierId(supplier.getId());
            binding.setReceiveIdType(receiveIdType);
            binding.setReceiveId(receiveId);
        }
        binding.setSupplierCode(supplier.getSupplierCode());
        binding.setSupplierName(supplier.getSupplierName());
        binding.setBindStatus(SupplierFeishuBinding.STATUS_BOUND);
        binding.setOpenId(openId);
        binding.setUserId(userId);
        binding.setUnionId(unionId);
        binding.setChatId(chatId);
        binding.setChatType(chatType);
        binding.setTenantKey(tenantKey);
        binding.setSourceUserKey(sourceUserKey);
        binding.setLastMessageId(messageId);
        binding.setBoundAt(now);
        binding.setDisabledAt(null);
        binding.setRemark("飞书机器人消息绑定");
        if (binding.getId() == null) {
            bindingMapper.insert(binding);
        } else {
            bindingMapper.updateById(binding);
        }

        return "已绑定供应商：" + supplier.getSupplierName()
            + "（" + supplier.getSupplierCode() + "）。后续可报价任务会推送到当前"
            + ("chat_id".equals(receiveIdType) ? "外部群" : "单聊")
            + "，报价仍通过原“供应商报价”流程提交。";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String tryImportSupplierQuoteFile(
        String messageType,
        String fileKey,
        String fileName,
        String openId,
        String userId,
        String unionId,
        String chatId,
        String chatType,
        String tenantKey,
        String sourceUserKey,
        String messageId,
        Long messageLogId
    ) {
        if (!isExcelFileMessage(messageType, fileKey, fileName)) {
            return "";
        }
        SupplierFeishuBinding binding = findBoundSupplier(openId, userId, unionId, chatId, chatType, sourceUserKey);
        if (binding == null) {
            if (looksLikeSupplierInquiryFile(fileName)) {
                throw new BizException("SUPPLIER_FEISHU_BIND_REQUIRED", supplierBindGuideMessage());
            }
            return "";
        }
        Supplier supplier = supplierMapper.selectById(binding.getSupplierId());
        if (!isSupplierUsable(supplier, LocalDateTime.now())) {
            throw new BizException("SUPPLIER_NOT_ACTIVE", "当前供应商未启用、未准入或已被限制报价，请联系采购管理员");
        }
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            throw new BizException("FEISHU_BOT_DISABLED", "飞书机器人未启用，无法下载报价附件");
        }
        byte[] content = downloadFeishuMessageFile(config, messageId, fileKey);
        AuthUser supplierUser = buildSupplierAuthUser(supplier, binding);
        Long importLogId = importFileLogService.recordFeishuImport(
            "SUPPLIER_QUOTE_FILE_IMPORT",
            "供应商报价附件导入",
            firstText(fileName, "供应商报价.xlsx"),
            content,
            sourceUserKey,
            messageId,
            messageLogId,
            supplierUser
        );
        try {
            SupplierQuoteSubmitDTO dto = parseSupplierQuoteFile(fileName, content, supplier);
            AuthUser previous = AuthContextHolder.get();
            try {
                AuthContextHolder.set(supplierUser);
                SupplierQuoteVO quote = supplierQuoteService.submitQuote(dto);
                String summary = "已导入供应商报价：" + firstText(quote == null ? null : quote.getQuoteNo(), "")
                    + "，明细 " + dto.getItems().size() + " 条";
                importFileLogService.markSuccess(importLogId, dto.getItems().size(), dto.getItems().size(), 0, summary);
                PurchaseRequest request = purchaseRequestMapper.selectById(dto.getPurchaseRequestId());
                return "已导入报价单："
                    + firstText(request == null ? null : request.getReqNo(), String.valueOf(dto.getPurchaseRequestId()))
                    + "\n供应商：" + firstText(supplier.getSupplierName(), supplier.getSupplierCode())
                    + "\n明细：" + dto.getItems().size() + " 条"
                    + "\n后续会继续进入现有报价、比价和中标确认流程。";
            } finally {
                if (previous == null) {
                    AuthContextHolder.clear();
                } else {
                    AuthContextHolder.set(previous);
                }
            }
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(importLogId, ex);
            throw ex;
        }
    }

    @Override
    @Scheduled(fixedDelay = 60_000L)
    public void pushPendingQuoteNotifications() {
        boolean supplierNoticeEnabled = quoteNoticeEnabled();
        boolean adminCopyEnabled = adminCardCopyEnabled();
        if (!supplierNoticeEnabled && !adminCopyEnabled) {
            return;
        }
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return;
        }
        List<PurchaseRequest> requests = purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_QUOTING)
            .orderByDesc(PurchaseRequest::getUpdatedAt)
            .last("LIMIT " + quoteNoticeScanBatchSize()));
        LocalDateTime now = LocalDateTime.now();
        for (PurchaseRequest request : requests) {
            try {
                pushQuoteNotificationsForRequest(request, config, now, supplierNoticeEnabled, adminCopyEnabled);
            } catch (RuntimeException ex) {
                log.warn("Push supplier quote notice failed, requestId={}", request.getId(), ex);
            }
        }
    }

    @Override
    public void pushQuoteNotificationsForRequest(Long purchaseRequestId) {
        if (purchaseRequestId == null) {
            return;
        }
        boolean supplierNoticeEnabled = quoteNoticeEnabled();
        boolean adminCopyEnabled = adminCardCopyEnabled();
        if (!supplierNoticeEnabled && !adminCopyEnabled) {
            return;
        }
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null || !ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())) {
            return;
        }
        try {
            pushQuoteNotificationsForRequest(
                request,
                config,
                LocalDateTime.now(),
                supplierNoticeEnabled,
                adminCopyEnabled
            );
        } catch (RuntimeException ex) {
            log.warn("Push supplier quote notice failed, requestId={}", purchaseRequestId, ex);
        }
    }

    @Override
    @Scheduled(fixedDelay = 60_000L, initialDelay = 15_000L)
    public void pushPendingAwardNotifications() {
        boolean supplierNoticeEnabled = awardNoticeEnabled();
        boolean adminCopyEnabled = adminCardCopyEnabled();
        if (!supplierNoticeEnabled && !adminCopyEnabled) {
            return;
        }
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return;
        }
        List<SupplierQuote> quotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getSupplierConfirmStatus, ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING)
            .orderByDesc(SupplierQuote::getUpdatedAt)
            .last("LIMIT " + quoteNoticeScanBatchSize()));
        LocalDateTime now = LocalDateTime.now();
        for (SupplierQuote quote : quotes) {
            try {
                pushAwardNotificationsForQuote(quote, config, now, supplierNoticeEnabled, adminCopyEnabled);
            } catch (RuntimeException ex) {
                log.warn("Push supplier award notice failed, quoteId={}", quote.getId(), ex);
            }
        }
    }

    @Override
    public FeishuApprovalCardActionResult sendSupplierQuoteNoticeTest(String receiveIdType, String receiveId) {
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return FeishuApprovalCardActionResult.warning("请先启用飞书采购AI助手并配置 App ID/App Secret");
        }
        String normalizedType = normalizeReceiveIdType(receiveIdType);
        String targetReceiveId = firstText(receiveId, "");
        if (!StringUtils.hasText(targetReceiveId)) {
            ReceiveTarget adminTarget = resolveDefaultAdminReceiveTarget();
            if (adminTarget == null) {
                return FeishuApprovalCardActionResult.warning("请输入测试接收 ID，或先维护管理员邮箱/飞书 ID");
            }
            normalizedType = adminTarget.receiveIdType();
            targetReceiveId = adminTarget.receiveId();
        }
        PurchaseRequest request = buildQuoteNoticeTestRequest();
        Supplier supplier = buildQuoteNoticeTestSupplier();
        List<PurchaseRequestItem> items = buildQuoteNoticeTestItems();

        try {
            sendInteractiveMessage(config, normalizedType, targetReceiveId.trim(), buildSupplierQuoteNoticeCard(request, supplier, items));
            return FeishuApprovalCardActionResult.ok("供应商报价通知样卡已发送");
        } catch (RuntimeException ex) {
            log.warn("Send supplier quote notice test failed", ex);
            return FeishuApprovalCardActionResult.warning("发送供应商报价通知样卡失败：" + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    @Override
    public PageResult<SupplierFeishuQuoteNoticeVO> pageQuoteNotices(SupplierFeishuQuoteNoticeQueryDTO queryDTO) {
        LambdaQueryWrapper<SupplierFeishuQuoteNotice> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getPurchaseRequestId() != null) {
            wrapper.eq(SupplierFeishuQuoteNotice::getPurchaseRequestId, queryDTO.getPurchaseRequestId());
        }
        if (queryDTO.getSupplierId() != null) {
            wrapper.eq(SupplierFeishuQuoteNotice::getSupplierId, queryDTO.getSupplierId());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(SupplierFeishuQuoteNotice::getStatus, queryDTO.getStatus().trim());
        }
        if (StringUtils.hasText(queryDTO.getNoticeType())) {
            wrapper.eq(SupplierFeishuQuoteNotice::getNoticeType, normalizeNoticeType(queryDTO.getNoticeType()));
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            Set<Long> requestIds = findNoticeKeywordRequestIds(keyword);
            Set<Long> supplierIds = findNoticeKeywordSupplierIds(keyword);
            wrapper.and(scope -> scope
                .like(SupplierFeishuQuoteNotice::getRequestNo, keyword)
                .or()
                .like(SupplierFeishuQuoteNotice::getNoticeType, keyword)
                .or()
                .like(SupplierFeishuQuoteNotice::getReceiveId, keyword)
                .or()
                .like(SupplierFeishuQuoteNotice::getLastError, keyword)
                .or()
                .like(SupplierFeishuQuoteNotice::getCardVersion, keyword)
                .or(!requestIds.isEmpty(), condition -> condition.in(SupplierFeishuQuoteNotice::getPurchaseRequestId, requestIds))
                .or(!supplierIds.isEmpty(), condition -> condition.in(SupplierFeishuQuoteNotice::getSupplierId, supplierIds)));
        }
        wrapper.orderByDesc(SupplierFeishuQuoteNotice::getUpdatedAt);
        Page<SupplierFeishuQuoteNotice> page = noticeMapper.selectPage(
            new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()),
            wrapper
        );
        List<SupplierFeishuQuoteNoticeVO> records = toNoticeVOs(page.getRecords());
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public FeishuApprovalCardActionResult resendQuoteNotice(Long noticeId) {
        SupplierFeishuQuoteNotice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            return FeishuApprovalCardActionResult.warning("供应商报价卡片通知不存在");
        }
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return FeishuApprovalCardActionResult.warning("请先启用飞书采购AI助手并配置 App ID/App Secret");
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(notice.getPurchaseRequestId());
        Supplier supplier = supplierMapper.selectById(notice.getSupplierId());
        if (request == null || supplier == null) {
            return FeishuApprovalCardActionResult.warning("采购申请或供应商不存在");
        }
        if (!StringUtils.hasText(notice.getReceiveIdType()) || !StringUtils.hasText(notice.getReceiveId())) {
            return FeishuApprovalCardActionResult.warning("通知记录缺少接收方式，不能重发");
        }
        if ("admin_user".equalsIgnoreCase(firstText(notice.getReceiveIdType(), ""))) {
            return FeishuApprovalCardActionResult.warning("这条是管理员接收 ID 缺失的占位日志，请先维护管理员邮箱或飞书 open_id/user_id/union_id");
        }
        String noticeType = normalizeNoticeType(notice.getNoticeType());
        Map<String, Object> card;
        if (NOTICE_TYPE_AWARD_CONFIRM.equals(noticeType)) {
            SupplierQuote quote = findSupplierQuote(request.getId(), supplier.getId());
            if (quote == null) {
                return FeishuApprovalCardActionResult.warning("供应商报价记录不存在");
            }
            List<SupplierQuoteItem> items = loadAwardedQuoteItems(quote.getId());
            if (items.isEmpty()) {
                return FeishuApprovalCardActionResult.warning("供应商没有待确认的中标明细");
            }
            card = buildSupplierAwardConfirmationCard(request, supplier, quote, items);
        } else {
            List<PurchaseRequestItem> items = loadActiveItems(request.getId(), supplier.getId());
            if (items.isEmpty()) {
                return FeishuApprovalCardActionResult.warning("采购申请没有可报价明细");
            }
            card = buildSupplierQuoteNoticeCard(request, supplier, items);
        }
        try {
            String messageId = sendInteractiveMessage(
                config,
                notice.getReceiveIdType(),
                notice.getReceiveId(),
                card
            );
            notice.setMessageId(messageId);
            notice.setStatus(SupplierFeishuQuoteNotice.STATUS_SENT);
            notice.setSentAt(LocalDateTime.now());
            notice.setRetryCount(0);
            notice.setLastError("");
            notice.setCardVersion(CARD_VERSION);
            noticeMapper.updateById(notice);
            return FeishuApprovalCardActionResult.ok(NOTICE_TYPE_AWARD_CONFIRM.equals(noticeType)
                ? "供应商中标确认卡片已重新发送"
                : "供应商报价任务卡片已重新发送");
        } catch (RuntimeException ex) {
            notice.setStatus(SupplierFeishuQuoteNotice.STATUS_FAILED);
            notice.setRetryCount((notice.getRetryCount() == null ? 0 : notice.getRetryCount()) + 1);
            notice.setLastError(limit(firstText(ex.getMessage(), ex.getClass().getSimpleName()), 1000));
            notice.setCardVersion(CARD_VERSION);
            noticeMapper.updateById(notice);
            return FeishuApprovalCardActionResult.warning("重新发送失败：" + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    @Override
    public FeishuApprovalCardActionResult handleQuoteCardAction(
        Map<String, Object> value,
        Map<String, Object> formValue,
        String openId,
        String userId,
        String unionId,
        String messageId
    ) {
        String action = firstText(text(value == null ? null : value.get("action")), "");
        if (ACTION_DOWNLOAD_AWARD_LIST.equals(action)) {
            return handleAwardDownloadAction(value, messageId, openId);
        }
        if (ACTION_CONFIRM_AWARD.equals(action) || ACTION_WAIVE_AWARD.equals(action)) {
            return handleAwardCardAction(action, value, formValue, messageId, openId);
        }
        if (!ACTION_DOWNLOAD_QUOTE_TEMPLATE.equals(action)) {
            return FeishuApprovalCardActionResult.warning("暂不支持该供应商报价动作");
        }
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return FeishuApprovalCardActionResult.warning("请先启用飞书采购AI助手并配置 App ID/App Secret");
        }
        try {
            SupplierFeishuQuoteNotice notice = findQuoteNoticeForAction(value, messageId);
            PurchaseRequest request;
            Supplier supplier;
            List<PurchaseRequestItem> items;
            String receiveIdType;
            String receiveId;
            if (notice == null && isTestQuoteTemplateAction(value)) {
                request = buildQuoteNoticeTestRequest();
                supplier = buildQuoteNoticeTestSupplier();
                items = buildQuoteNoticeTestItems();
                receiveIdType = "open_id";
                receiveId = firstText(openId, "");
            } else if (notice != null) {
                request = purchaseRequestMapper.selectById(notice.getPurchaseRequestId());
                supplier = supplierMapper.selectById(notice.getSupplierId());
                items = loadActiveItems(notice.getPurchaseRequestId(), notice.getSupplierId());
                receiveIdType = notice.getReceiveIdType();
                receiveId = notice.getReceiveId();
            } else {
                return FeishuApprovalCardActionResult.warning("供应商报价卡片通知不存在，请在系统里重新发送卡片");
            }
            if (request == null || supplier == null) {
                return FeishuApprovalCardActionResult.warning("采购申请或供应商不存在");
            }
            if (items == null || items.isEmpty()) {
                return FeishuApprovalCardActionResult.warning("采购申请没有可报价明细");
            }
            if (!StringUtils.hasText(receiveId) || "admin_user".equalsIgnoreCase(firstText(receiveIdType, ""))) {
                return FeishuApprovalCardActionResult.warning("当前卡片缺少有效接收目标，不能发送询价单");
            }
            ExportFile file = buildQuoteTemplateFile(request, supplier, items);
            String fileKey = uploadFeishuFile(config, file);
            sendFileMessage(config, receiveIdType, receiveId, fileKey);
            return FeishuApprovalCardActionResult.ok("询价单已发送，请下载填写后把 Excel 发回机器人");
        } catch (RuntimeException ex) {
            log.warn("Download supplier inquiry sheet failed", ex);
            return FeishuApprovalCardActionResult.warning("下载询价单失败：" + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    private FeishuApprovalCardActionResult handleAwardDownloadAction(Map<String, Object> value,
                                                                     String messageId,
                                                                     String openId) {
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return FeishuApprovalCardActionResult.warning("请先启用飞书采购AI助手并配置 App ID/App Secret");
        }
        try {
            SupplierFeishuQuoteNotice notice = findQuoteNoticeForAction(value, messageId);
            if (notice == null || !NOTICE_TYPE_AWARD_CONFIRM.equalsIgnoreCase(firstText(notice.getNoticeType(), NOTICE_TYPE_QUOTE_TASK))) {
                return FeishuApprovalCardActionResult.warning("供应商中标确认卡片通知不存在，请在系统里重新发送卡片");
            }
            PurchaseRequest request = purchaseRequestMapper.selectById(notice.getPurchaseRequestId());
            Supplier supplier = supplierMapper.selectById(notice.getSupplierId());
            if (request == null || supplier == null) {
                return FeishuApprovalCardActionResult.warning("采购申请或供应商不存在");
            }
            sendAwardListFile(config, notice, request, supplier, openId);
            return FeishuApprovalCardActionResult.ok("中标清单已发送，请下载查看");
        } catch (RuntimeException ex) {
            log.warn("Download supplier award list failed", ex);
            return FeishuApprovalCardActionResult.warning("下载中标清单失败：" + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    private FeishuApprovalCardActionResult handleAwardCardAction(String action,
                                                                 Map<String, Object> value,
                                                                 Map<String, Object> formValue,
                                                                 String messageId,
                                                                 String openId) {
        try {
            SupplierFeishuQuoteNotice notice = findQuoteNoticeForAction(value, messageId);
            if (notice == null || !NOTICE_TYPE_AWARD_CONFIRM.equalsIgnoreCase(firstText(notice.getNoticeType(), NOTICE_TYPE_QUOTE_TASK))) {
                return FeishuApprovalCardActionResult.warning("供应商中标确认卡片通知不存在，请在系统里重新发送卡片");
            }
            PurchaseRequest request = purchaseRequestMapper.selectById(notice.getPurchaseRequestId());
            Supplier supplier = supplierMapper.selectById(notice.getSupplierId());
            SupplierQuote quote = findSupplierQuote(notice.getPurchaseRequestId(), notice.getSupplierId());
            if (request == null || supplier == null || quote == null) {
                return FeishuApprovalCardActionResult.warning("采购申请、供应商或报价记录不存在");
            }
            List<SupplierQuoteItem> originalAwardItems = loadAwardedQuoteItems(quote.getId());
            if (!ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING.equals(quote.getSupplierConfirmStatus())) {
                return FeishuApprovalCardActionResult.ok(
                    "该中标确认已处理",
                    buildSupplierAwardProcessedCard(
                        request,
                        supplier,
                        quote,
                        originalAwardItems,
                        supplierConfirmStatusLabel(quote.getSupplierConfirmStatus()),
                        supplierConfirmStatusTemplate(quote.getSupplierConfirmStatus())
                    )
                );
            }
            AuthUser previous = AuthContextHolder.get();
            try {
                AuthContextHolder.set(buildSupplierAuthUser(supplier, loadBinding(notice.getBindingId())));
                if (ACTION_CONFIRM_AWARD.equals(action)) {
                    SupplierAwardConfirmDTO dto = new SupplierAwardConfirmDTO();
                    dto.setConfirmRemark("飞书卡片确认中标");
                    supplierQuoteService.confirmAward(request.getId(), dto);
                } else if (ACTION_WAIVE_AWARD.equals(action)) {
                    String remark = formText(formValue, "awardWaiveRemark");
                    if (!StringUtils.hasText(remark)) {
                        return FeishuApprovalCardActionResult.warning("请填写放弃中标原因");
                    }
                    SupplierAwardWaiveDTO dto = new SupplierAwardWaiveDTO();
                    dto.setWaiveRemark(remark);
                    supplierQuoteService.waiveAward(request.getId(), dto);
                } else {
                    return FeishuApprovalCardActionResult.warning("暂不支持该供应商中标动作");
                }
            } finally {
                if (previous == null) {
                    AuthContextHolder.clear();
                } else {
                    AuthContextHolder.set(previous);
                }
            }
            SupplierQuote updatedQuote = findSupplierQuote(notice.getPurchaseRequestId(), notice.getSupplierId());
            List<SupplierQuoteItem> items = ACTION_WAIVE_AWARD.equals(action)
                ? originalAwardItems
                : (updatedQuote == null ? List.of() : loadAwardedQuoteItems(updatedQuote.getId()));
            String result = ACTION_CONFIRM_AWARD.equals(action) ? "已确认中标" : "已放弃中标";
            if (ACTION_CONFIRM_AWARD.equals(action)) {
                result = appendAwardListAutoSendResult(result, notice, request, supplier, openId);
            }
            return FeishuApprovalCardActionResult.ok(
                result,
                buildSupplierAwardProcessedCard(
                    request,
                    supplier,
                    updatedQuote == null ? quote : updatedQuote,
                    items,
                    result,
                    ACTION_CONFIRM_AWARD.equals(action) ? "green" : "red"
                )
            );
        } catch (RuntimeException ex) {
            log.warn("Handle supplier award card action failed", ex);
            return FeishuApprovalCardActionResult.warning("处理中标确认失败：" + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    private String appendAwardListAutoSendResult(String result,
                                                 SupplierFeishuQuoteNotice notice,
                                                 PurchaseRequest request,
                                                 Supplier supplier,
                                                 String openId) {
        FeishuBotRuntimeConfig.Snapshot config = botRuntimeConfig.current();
        if (!config.enabled()) {
            return result + "（飞书机器人未启用，未自动发送中标清单）";
        }
        try {
            sendAwardListFile(config, notice, request, supplier, openId);
            return result + "，中标清单已发送";
        } catch (RuntimeException ex) {
            log.warn("Auto send supplier award list failed", ex);
            return result + "（中标清单自动发送失败，可点击“下载中标清单”重试）";
        }
    }

    private void sendAwardListFile(FeishuBotRuntimeConfig.Snapshot config,
                                   SupplierFeishuQuoteNotice notice,
                                   PurchaseRequest request,
                                   Supplier supplier,
                                   String openId) {
        SupplierQuote quote = findSupplierQuote(notice.getPurchaseRequestId(), notice.getSupplierId());
        if (quote == null || !ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(quote.getSupplierConfirmStatus())) {
            throw new BizException("SUPPLIER_AWARD_CONFIRM_REQUIRED", "供应商确认中标后才能下载中标清单");
        }
        String receiveIdType = notice.getReceiveIdType();
        String receiveId = notice.getReceiveId();
        if (!StringUtils.hasText(receiveId) || "admin_user".equalsIgnoreCase(firstText(receiveIdType, ""))) {
            receiveIdType = "open_id";
            receiveId = firstText(openId, "");
        }
        if (!StringUtils.hasText(receiveId)) {
            throw new BizException("FEISHU_RECEIVE_ID_MISSING", "当前卡片缺少有效接收目标，不能发送中标清单");
        }

        AuthUser previous = AuthContextHolder.get();
        ExportFile file;
        try {
            AuthContextHolder.set(buildSupplierAuthUser(supplier, loadBinding(notice.getBindingId())));
            file = supplierQuoteService.exportAward(request.getId());
        } finally {
            if (previous == null) {
                AuthContextHolder.clear();
            } else {
                AuthContextHolder.set(previous);
            }
        }
        String fileKey = uploadFeishuFile(config, file);
        sendFileMessage(config, receiveIdType, receiveId, fileKey);
    }

    @Transactional(rollbackFor = Exception.class)
    protected void pushQuoteNotificationsForRequest(PurchaseRequest request,
                                                    FeishuBotRuntimeConfig.Snapshot config,
                                                    LocalDateTime now,
                                                    boolean supplierNoticeEnabled,
                                                    boolean adminCopyEnabled) {
        List<PurchaseRequestItem> allActiveItems = loadActiveItems(request.getId());
        if (allActiveItems.isEmpty()) {
            return;
        }
        List<SysUser> adminRecipients = adminCopyEnabled ? adminCopyRecipients() : List.of();
        for (Supplier supplier : eligibleSuppliers(request, now)) {
            List<PurchaseRequestItem> items = loadActiveItems(request.getId(), supplier.getId());
            if (items.isEmpty()) {
                continue;
            }
            List<SupplierFeishuBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<SupplierFeishuBinding>()
                .eq(SupplierFeishuBinding::getSupplierId, supplier.getId())
                .eq(SupplierFeishuBinding::getBindStatus, SupplierFeishuBinding.STATUS_BOUND)
                .orderByDesc(SupplierFeishuBinding::getBoundAt));
            Map<String, Object> card = buildSupplierQuoteNoticeCard(request, supplier, items);
            if (supplierNoticeEnabled) {
                boolean pushedSupplierTarget = false;
                for (SupplierFeishuBinding binding : bindings) {
                    if (!StringUtils.hasText(binding.getReceiveIdType()) || !StringUtils.hasText(binding.getReceiveId())) {
                        continue;
                    }
                    pushedSupplierTarget = true;
                    pushQuoteNoticeToTarget(request, supplier, binding.getId(), binding.getReceiveIdType(), binding.getReceiveId(), card, config);
                }
                if (!pushedSupplierTarget) {
                    ReceiveTarget adminTarget = resolveDefaultAdminReceiveTarget();
                    if (adminTarget != null) {
                        pushQuoteNoticeToTarget(request, supplier, ADMIN_FALLBACK_BINDING_ID, adminTarget.receiveIdType(), adminTarget.receiveId(), card, config);
                    }
                }
            }
            for (SysUser admin : adminRecipients) {
                ReceiveTarget target = resolveAdminReceiveTarget(admin);
                if (target == null) {
                    ensureFailedAdminNotice(NOTICE_TYPE_QUOTE_TASK, request, supplier, admin, missingAnyReceiveIdMessage(displayName(admin)));
                    continue;
                }
                pushQuoteNoticeToTarget(request, supplier, ADMIN_COPY_BINDING_ID, target.receiveIdType(), target.receiveId(), card, config);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void pushAwardNotificationsForQuote(SupplierQuote quote,
                                                  FeishuBotRuntimeConfig.Snapshot config,
                                                  LocalDateTime now,
                                                  boolean supplierNoticeEnabled,
                                                  boolean adminCopyEnabled) {
        if (quote == null || quote.getPurchaseRequestId() == null || quote.getSupplierId() == null) {
            return;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(quote.getPurchaseRequestId());
        if (request == null || !ProcurementWorkflow.LEADER_CONFIRM_STATUS_CONFIRMED.equals(request.getLeaderConfirmStatus())) {
            return;
        }
        Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
        if (supplier == null) {
            return;
        }
        List<SupplierQuoteItem> items = loadAwardedQuoteItems(quote.getId());
        if (items.isEmpty()) {
            return;
        }
        Map<String, Object> card = buildSupplierAwardConfirmationCard(request, supplier, quote, items);
        if (supplierNoticeEnabled) {
            List<SupplierFeishuBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<SupplierFeishuBinding>()
                .eq(SupplierFeishuBinding::getSupplierId, supplier.getId())
                .eq(SupplierFeishuBinding::getBindStatus, SupplierFeishuBinding.STATUS_BOUND)
                .orderByDesc(SupplierFeishuBinding::getBoundAt));
            boolean pushedSupplierTarget = false;
            for (SupplierFeishuBinding binding : bindings) {
                if (!StringUtils.hasText(binding.getReceiveIdType()) || !StringUtils.hasText(binding.getReceiveId())) {
                    continue;
                }
                pushedSupplierTarget = true;
                pushSupplierNoticeToTarget(
                    NOTICE_TYPE_AWARD_CONFIRM,
                    request,
                    supplier,
                    binding.getId(),
                    binding.getReceiveIdType(),
                    binding.getReceiveId(),
                    card,
                    config
                );
            }
            if (!pushedSupplierTarget) {
                ReceiveTarget adminTarget = resolveDefaultAdminReceiveTarget();
                if (adminTarget != null) {
                    pushSupplierNoticeToTarget(
                        NOTICE_TYPE_AWARD_CONFIRM,
                        request,
                        supplier,
                        ADMIN_FALLBACK_BINDING_ID,
                        adminTarget.receiveIdType(),
                        adminTarget.receiveId(),
                        card,
                        config
                    );
                }
            }
        }
        if (adminCopyEnabled) {
            for (SysUser admin : adminCopyRecipients()) {
                ReceiveTarget target = resolveAdminReceiveTarget(admin);
                if (target == null) {
                    ensureFailedAdminNotice(NOTICE_TYPE_AWARD_CONFIRM, request, supplier, admin, missingAnyReceiveIdMessage(displayName(admin)));
                    continue;
                }
                pushSupplierNoticeToTarget(
                    NOTICE_TYPE_AWARD_CONFIRM,
                    request,
                    supplier,
                    ADMIN_COPY_BINDING_ID,
                    target.receiveIdType(),
                    target.receiveId(),
                    card,
                    config
                );
            }
        }
    }

    private void pushQuoteNoticeToTarget(PurchaseRequest request,
                                         Supplier supplier,
                                         Long bindingId,
                                         String receiveIdType,
                                         String receiveId,
                                         Map<String, Object> card,
                                         FeishuBotRuntimeConfig.Snapshot config) {
        pushSupplierNoticeToTarget(NOTICE_TYPE_QUOTE_TASK, request, supplier, bindingId, receiveIdType, receiveId, card, config);
    }

    private void pushSupplierNoticeToTarget(String noticeType,
                                            PurchaseRequest request,
                                            Supplier supplier,
                                            Long bindingId,
                                            String receiveIdType,
                                            String receiveId,
                                            Map<String, Object> card,
                                            FeishuBotRuntimeConfig.Snapshot config) {
        SupplierFeishuQuoteNotice notice = ensureNotice(noticeType, request, supplier, bindingId, receiveIdType, receiveId);
        if (SupplierFeishuQuoteNotice.STATUS_SENT.equals(notice.getStatus())) {
            return;
        }
        boolean failedWithOldCardVersion = SupplierFeishuQuoteNotice.STATUS_FAILED.equals(notice.getStatus())
            && !CARD_VERSION.equals(firstText(notice.getCardVersion(), ""));
        if ((notice.getRetryCount() == null ? 0 : notice.getRetryCount()) >= 3 && !failedWithOldCardVersion) {
            return;
        }
        if (failedWithOldCardVersion) {
            notice.setRetryCount(0);
        }
        try {
            String messageId = sendInteractiveMessage(config, receiveIdType, receiveId, card);
            notice.setStatus(SupplierFeishuQuoteNotice.STATUS_SENT);
            notice.setMessageId(messageId);
            notice.setLastError("");
            notice.setSentAt(LocalDateTime.now());
            notice.setCardVersion(CARD_VERSION);
            noticeMapper.updateById(notice);
        } catch (RuntimeException ex) {
            notice.setStatus(SupplierFeishuQuoteNotice.STATUS_FAILED);
            notice.setRetryCount((notice.getRetryCount() == null ? 0 : notice.getRetryCount()) + 1);
            notice.setLastError(limit(firstText(ex.getMessage(), ex.getClass().getSimpleName()), 1000));
            notice.setCardVersion(CARD_VERSION);
            noticeMapper.updateById(notice);
        }
    }

    private SupplierFeishuQuoteNotice ensureNotice(String noticeType,
                                                   PurchaseRequest request,
                                                   Supplier supplier,
                                                   Long bindingId,
                                                   String receiveIdType,
                                                   String receiveId) {
        Long normalizedBindingId = bindingId == null ? ADMIN_COPY_BINDING_ID : bindingId;
        String normalizedNoticeType = normalizeNoticeType(noticeType);
        LambdaQueryWrapper<SupplierFeishuQuoteNotice> wrapper = new LambdaQueryWrapper<SupplierFeishuQuoteNotice>()
            .eq(SupplierFeishuQuoteNotice::getPurchaseRequestId, request.getId())
            .eq(SupplierFeishuQuoteNotice::getSupplierId, supplier.getId())
            .eq(SupplierFeishuQuoteNotice::getNoticeType, normalizedNoticeType)
            .eq(SupplierFeishuQuoteNotice::getReceiveIdType, receiveIdType)
            .eq(SupplierFeishuQuoteNotice::getReceiveId, receiveId)
            .eq(SupplierFeishuQuoteNotice::getBindingId, normalizedBindingId);
        SupplierFeishuQuoteNotice notice = noticeMapper.selectOne(wrapper.last("LIMIT 1"));
        if (notice != null) {
            return notice;
        }
        notice = new SupplierFeishuQuoteNotice();
        notice.setPurchaseRequestId(request.getId());
        notice.setRequestNo(request.getReqNo());
        notice.setSupplierId(supplier.getId());
        notice.setNoticeType(normalizedNoticeType);
        notice.setBindingId(normalizedBindingId);
        notice.setReceiveIdType(receiveIdType);
        notice.setReceiveId(receiveId);
        notice.setStatus(SupplierFeishuQuoteNotice.STATUS_PENDING);
        notice.setRetryCount(0);
        notice.setCardVersion(CARD_VERSION);
        noticeMapper.insert(notice);
        return notice;
    }

    private void ensureFailedAdminNotice(String noticeType, PurchaseRequest request, Supplier supplier, SysUser admin, String message) {
        if (request == null || supplier == null || admin == null || admin.getId() == null) {
            return;
        }
        SupplierFeishuQuoteNotice notice = ensureNotice(
            noticeType,
            request,
            supplier,
            ADMIN_COPY_BINDING_ID,
            "admin_user",
            "sys_user:" + admin.getId()
        );
        notice.setStatus(SupplierFeishuQuoteNotice.STATUS_FAILED);
        notice.setRetryCount((notice.getRetryCount() == null ? 0 : notice.getRetryCount()) + 1);
        notice.setLastError(limit(message, 1000));
        notice.setCardVersion(CARD_VERSION);
        noticeMapper.updateById(notice);
    }

    private List<SupplierFeishuQuoteNoticeVO> toNoticeVOs(List<SupplierFeishuQuoteNotice> notices) {
        if (notices == null || notices.isEmpty()) {
            return List.of();
        }
        Set<Long> requestIds = notices.stream()
            .map(SupplierFeishuQuoteNotice::getPurchaseRequestId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> supplierIds = notices.stream()
            .map(SupplierFeishuQuoteNotice::getSupplierId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, PurchaseRequest> requestMap = requestIds.isEmpty()
            ? Map.of()
            : purchaseRequestMapper.selectBatchIds(requestIds).stream()
                .collect(Collectors.toMap(PurchaseRequest::getId, item -> item, (a, b) -> a));
        Map<Long, Supplier> supplierMap = supplierIds.isEmpty()
            ? Map.of()
            : supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, item -> item, (a, b) -> a));
        return notices.stream()
            .map(notice -> toNoticeVO(notice, requestMap.get(notice.getPurchaseRequestId()), supplierMap.get(notice.getSupplierId())))
            .toList();
    }

    private SupplierFeishuQuoteNoticeVO toNoticeVO(SupplierFeishuQuoteNotice notice, PurchaseRequest request, Supplier supplier) {
        SupplierFeishuQuoteNoticeVO vo = new SupplierFeishuQuoteNoticeVO();
        vo.setId(notice.getId());
        vo.setPurchaseRequestId(notice.getPurchaseRequestId());
        vo.setRequestNo(firstText(notice.getRequestNo(), request == null ? null : request.getReqNo()));
        vo.setRequestTitle(request == null ? null : request.getReqTitle());
        vo.setSupplierId(notice.getSupplierId());
        vo.setSupplierCode(supplier == null ? null : supplier.getSupplierCode());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());
        vo.setNoticeType(firstText(notice.getNoticeType(), NOTICE_TYPE_QUOTE_TASK));
        vo.setBindingId(notice.getBindingId());
        vo.setReceiveIdType(notice.getReceiveIdType());
        vo.setReceiveId(notice.getReceiveId());
        vo.setStatus(notice.getStatus());
        vo.setMessageId(notice.getMessageId());
        vo.setLastError(notice.getLastError());
        vo.setSentAt(notice.getSentAt());
        vo.setRetryCount(notice.getRetryCount());
        vo.setCardVersion(notice.getCardVersion());
        vo.setCreatedAt(notice.getCreatedAt());
        vo.setUpdatedAt(notice.getUpdatedAt());
        return vo;
    }

    private Set<Long> findNoticeKeywordRequestIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Set.of();
        }
        return purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
                .like(PurchaseRequest::getReqNo, keyword)
                .or()
                .like(PurchaseRequest::getReqTitle, keyword)
                .last("LIMIT 50"))
            .stream()
            .map(PurchaseRequest::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Set<Long> findNoticeKeywordSupplierIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Set.of();
        }
        return supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .like(Supplier::getSupplierName, keyword)
                .or()
                .like(Supplier::getSupplierCode, keyword)
                .last("LIMIT 50"))
            .stream()
            .map(Supplier::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private List<Supplier> eligibleSuppliers(PurchaseRequest request, LocalDateTime now) {
        if (isDirectAwardRequest(request)) {
            Supplier supplier = supplierMapper.selectById(request.getDirectAwardSupplierId());
            return isSupplierEligibleForRequest(supplier, request, now) ? List.of(supplier) : List.of();
        }
        Map<Long, Supplier> supplierMap = new LinkedHashMap<>();
        supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getStatus, "ACTIVE")
                .eq(Supplier::getOnboardingStatus, "APPROVED")
                .orderByAsc(Supplier::getSupplierCode))
            .stream()
            .filter(supplier -> isSupplierEligibleForRequest(supplier, request, now))
            .forEach(supplier -> supplierMap.put(supplier.getId(), supplier));
        temporarySupplierRecommendationService.eligibleTemporarySuppliers(request.getId(), now)
            .forEach(supplier -> supplierMap.putIfAbsent(supplier.getId(), supplier));
        return new ArrayList<>(supplierMap.values());
    }

    private boolean isSupplierEligibleForRequest(Supplier supplier, PurchaseRequest request, LocalDateTime now) {
        if (!isSupplierUsable(supplier, now)) {
            return false;
        }
        if (isDirectAwardRequest(request)) {
            return Objects.equals(supplier.getId(), request.getDirectAwardSupplierId());
        }
        return supplier.getRegionId() == null
            || Objects.equals(supplier.getRegionId(), request.getRegionId())
            || isNationalDemandAggregate(request);
    }

    private boolean isSupplierUsable(Supplier supplier, LocalDateTime now) {
        if (supplier == null) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(firstText(supplier.getStatus(), ""))) {
            return false;
        }
        if (supplier.getBlacklistFlag() != null && supplier.getBlacklistFlag() == 1) {
            return false;
        }
        if (supplier.getBidSuspendedUntil() != null && supplier.getBidSuspendedUntil().isAfter(now)) {
            return false;
        }
        if (temporarySupplierRecommendationService.isTemporarySupplier(supplier)) {
            return temporarySupplierRecommendationService.hasAnyActiveRecommendation(supplier.getId());
        }
        if (!"APPROVED".equalsIgnoreCase(firstText(supplier.getOnboardingStatus(), ""))) {
            return false;
        }
        return true;
    }

    private List<PurchaseRequestItem> loadActiveItems(Long requestId) {
        return purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, requestId)
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .orderByAsc(PurchaseRequestItem::getLineNo));
    }

    private List<PurchaseRequestItem> loadActiveItems(Long requestId, Long supplierId) {
        return temporarySupplierRecommendationService.filterScopedItems(requestId, supplierId, loadActiveItems(requestId));
    }

    private List<SupplierQuoteItem> loadAwardedQuoteItems(Long quoteId) {
        if (quoteId == null) {
            return List.of();
        }
        return supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, quoteId)
            .eq(SupplierQuoteItem::getAwarded, 1)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .orderByAsc(SupplierQuoteItem::getLineNo));
    }

    private boolean isExcelFileMessage(String messageType, String fileKey, String fileName) {
        if (!StringUtils.hasText(fileKey)) {
            return false;
        }
        String normalizedType = firstText(messageType, "").trim().toLowerCase(Locale.ROOT);
        String normalizedName = firstText(fileName, "").trim().toLowerCase(Locale.ROOT);
        return "file".equals(normalizedType)
            || normalizedName.endsWith(".xlsx")
            || normalizedName.endsWith(".xls");
    }

    private boolean looksLikeSupplierInquiryFile(String fileName) {
        String normalized = firstText(fileName, "").trim();
        return normalized.contains("询价单") && (normalized.endsWith(".xlsx") || normalized.endsWith(".xls"));
    }

    private SupplierFeishuBinding findBoundSupplier(
        String openId,
        String userId,
        String unionId,
        String chatId,
        String chatType,
        String sourceUserKey
    ) {
        List<LambdaQueryWrapper<SupplierFeishuBinding>> queries = new ArrayList<>();
        if (StringUtils.hasText(chatId) && !"p2p".equalsIgnoreCase(firstText(chatType, ""))) {
            queries.add(boundSupplierQuery().eq(SupplierFeishuBinding::getReceiveIdType, "chat_id")
                .eq(SupplierFeishuBinding::getReceiveId, chatId));
        }
        if (StringUtils.hasText(openId)) {
            queries.add(boundSupplierQuery().eq(SupplierFeishuBinding::getReceiveIdType, "open_id")
                .eq(SupplierFeishuBinding::getReceiveId, openId));
            queries.add(boundSupplierQuery().eq(SupplierFeishuBinding::getOpenId, openId));
        }
        if (StringUtils.hasText(userId)) {
            queries.add(boundSupplierQuery().eq(SupplierFeishuBinding::getUserId, userId));
        }
        if (StringUtils.hasText(unionId)) {
            queries.add(boundSupplierQuery().eq(SupplierFeishuBinding::getUnionId, unionId));
        }
        if (StringUtils.hasText(sourceUserKey)) {
            queries.add(boundSupplierQuery().eq(SupplierFeishuBinding::getSourceUserKey, sourceUserKey));
        }
        for (LambdaQueryWrapper<SupplierFeishuBinding> query : queries) {
            SupplierFeishuBinding binding = bindingMapper.selectOne(query.orderByDesc(SupplierFeishuBinding::getUpdatedAt).last("LIMIT 1"));
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private LambdaQueryWrapper<SupplierFeishuBinding> boundSupplierQuery() {
        return new LambdaQueryWrapper<SupplierFeishuBinding>()
            .eq(SupplierFeishuBinding::getBindStatus, SupplierFeishuBinding.STATUS_BOUND);
    }

    private byte[] downloadFeishuMessageFile(FeishuBotRuntimeConfig.Snapshot config, String messageId, String fileKey) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(fileKey)) {
            throw new BizException("FEISHU_FILE_RESOURCE_MISSING", "飞书文件消息缺少 message_id 或 file_key，无法下载附件");
        }
        try {
            byte[] content = restClient.get()
                .uri(messageResourceUrl(config, messageId, fileKey))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
                .retrieve()
                .body(byte[].class);
            if (content == null || content.length == 0) {
                throw new BizException("FEISHU_FILE_EMPTY", "飞书报价附件为空");
            }
            return content;
        } catch (RestClientResponseException ex) {
            throw new BizException("FEISHU_FILE_DOWNLOAD_FAILED",
                "下载飞书报价附件失败：" + limit(ex.getResponseBodyAsString(), 300));
        } catch (RestClientException ex) {
            throw new BizException("FEISHU_FILE_DOWNLOAD_FAILED",
                "下载飞书报价附件失败：" + firstText(ex.getMessage(), "网络异常"));
        }
    }

    private String messageResourceUrl(FeishuBotRuntimeConfig.Snapshot config, String messageId, String fileKey) {
        return UriComponentsBuilder.fromUriString(openApiBase(config))
            .pathSegment("im", "v1", "messages", messageId, "resources", fileKey)
            .queryParam("type", "file")
            .build()
            .toUriString();
    }

    private String openApiBase(FeishuBotRuntimeConfig.Snapshot config) {
        String messageUrl = firstText(config.messageSendUrl(), "https://open.feishu.cn/open-apis/im/v1/messages").replaceAll("\\?.*$", "");
        int index = messageUrl.indexOf("/im/v1/messages");
        if (index > 0) {
            return messageUrl.substring(0, index);
        }
        return "https://open.feishu.cn/open-apis";
    }

    private AuthUser buildSupplierAuthUser(Supplier supplier, SupplierFeishuBinding binding) {
        return AuthUser.builder()
            .userId(0L)
            .username("feishu-supplier:" + firstText(supplier.getSupplierCode(), String.valueOf(supplier.getId())))
            .realName(firstText(supplier.getSupplierName(), supplier.getSupplierCode()))
            .userType(DataScopeHelper.USER_TYPE_SUPPLIER)
            .regionId(supplier.getRegionId())
            .supplierId(supplier.getId())
            .dataScopeType(DataScopeHelper.SCOPE_SELF)
            .roleCodes(List.of("SUPPLIER"))
            .build();
    }

    private SupplierQuoteSubmitDTO parseSupplierQuoteFile(String fileName, byte[] content, Supplier supplier) {
        List<Map<String, String>> rows = ExcelImportUtils.toHeaderMaps(ExcelImportUtils.readFirstSheet(fileName, content));
        if (rows.isEmpty()) {
            throw new BizException("SUPPLIER_QUOTE_FILE_EMPTY", "报价附件没有可导入的明细");
        }
        PurchaseRequest request = resolvePurchaseRequestFromQuoteFile(fileName, rows);
        validateQuoteFileSupplier(rows, supplier);
        List<PurchaseRequestItem> requestItems = loadActiveItems(request.getId(), supplier.getId());
        Map<Long, PurchaseRequestItem> itemById = requestItems.stream()
            .filter(item -> item.getId() != null)
            .collect(Collectors.toMap(PurchaseRequestItem::getId, item -> item, (a, b) -> a));
        Map<Integer, PurchaseRequestItem> itemByLineNo = requestItems.stream()
            .filter(item -> item.getLineNo() != null)
            .collect(Collectors.toMap(PurchaseRequestItem::getLineNo, item -> item, (a, b) -> a));
        Set<Long> seenItemIds = new LinkedHashSet<>();
        List<SupplierQuoteItemSubmitDTO> quoteItems = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if (isBlankQuoteRow(row)) {
                continue;
            }
            PurchaseRequestItem requestItem = resolveRequestItem(row, itemById, itemByLineNo);
            if (requestItem == null) {
                throw new BizException("SUPPLIER_QUOTE_ITEM_NOT_MATCHED",
                    "报价附件中存在无法匹配的采购明细，请确认未删除系统字段或行号");
            }
            if (!seenItemIds.add(requestItem.getId())) {
                throw new BizException("SUPPLIER_QUOTE_ITEM_DUPLICATED", "报价附件存在重复明细：" + requestItem.getLineNo());
            }
            SupplierQuoteItemSubmitDTO itemDTO = new SupplierQuoteItemSubmitDTO();
            itemDTO.setPurchaseRequestItemId(requestItem.getId());
            itemDTO.setWaived(parseBoolean(rowValue(row, "是否放弃报价", "放弃报价", "waived")));
            itemDTO.setOriginalCondition(rowValue(row, "原商品成色", "商品成色", "originalCondition"));
            itemDTO.setOriginalSecondHandRemark(rowValue(row, "原商品二手说明", "二手商品说明", "originalSecondHandRemark"));
            itemDTO.setReplacementFlag(parseBoolean(rowValue(row, "是否替换商品", "替换商品", "replacementFlag")));
            itemDTO.setReplacementItemName(rowValue(row, "替换商品名称", "替换名称", "replacementItemName"));
            itemDTO.setReplacementSpecModel(rowValue(row, "替换规格型号", "替换规格", "replacementSpecModel"));
            itemDTO.setReplacementEcommerceUrl(rowValue(row, "替换商品链接", "替换链接", "replacementEcommerceUrl"));
            itemDTO.setReplacementUnitPrice(parseDecimal(rowValue(row, "替换商品单价", "替换单价", "replacementUnitPrice")));
            itemDTO.setReplacementReason(rowValue(row, "替换原因", "replacementReason"));
            itemDTO.setReplacementCondition(rowValue(row, "替换商品成色", "replacementCondition"));
            itemDTO.setReplacementSecondHandRemark(rowValue(row, "替换商品二手说明", "replacementSecondHandRemark"));
            itemDTO.setUnitPrice(parseDecimal(rowValue(row, "报价单价", "单价", "unitPrice")));
            itemDTO.setLeadTimeDays(parseQuoteLeadTimeDays(row));
            itemDTO.setQuoteRemark(rowValue(row, "报价备注", "备注", "quoteRemark"));
            quoteItems.add(itemDTO);
        }
        if (quoteItems.isEmpty()) {
            throw new BizException("SUPPLIER_QUOTE_FILE_EMPTY", "报价附件没有可导入的明细");
        }
        SupplierQuoteSubmitDTO dto = new SupplierQuoteSubmitDTO();
        dto.setPurchaseRequestId(request.getId());
        dto.setQuoteRemark("飞书机器人导入：" + firstText(fileName, "供应商报价附件"));
        dto.setItems(quoteItems);
        return dto;
    }

    private PurchaseRequest resolvePurchaseRequestFromQuoteFile(String fileName, List<Map<String, String>> rows) {
        Long requestId = firstLongFromRows(rows, "采购申请ID", "申请ID", "purchaseRequestId");
        if (requestId != null) {
            PurchaseRequest request = purchaseRequestMapper.selectById(requestId);
            if (request != null) {
                return request;
            }
        }
        String requestNo = "";
        for (Map<String, String> row : rows) {
            requestNo = firstText(requestNo, rowValue(row, "申请单号", "采购申请单号", "requestNo"));
            if (StringUtils.hasText(requestNo)) {
                break;
            }
        }
        requestNo = firstText(requestNo, requestNoFromFileName(fileName));
        if (StringUtils.hasText(requestNo)) {
            PurchaseRequest request = purchaseRequestMapper.selectOne(new LambdaQueryWrapper<PurchaseRequest>()
                .eq(PurchaseRequest::getReqNo, requestNo)
                .last("LIMIT 1"));
            if (request != null) {
                return request;
            }
        }
        throw new BizException("SUPPLIER_QUOTE_REQUEST_NOT_FOUND", "无法从报价附件识别采购申请，请使用系统生成的询价单填写后回传");
    }

    private void validateQuoteFileSupplier(List<Map<String, String>> rows, Supplier supplier) {
        Long supplierId = firstLongFromRows(rows, "供应商ID", "supplierId");
        if (supplierId != null && !Objects.equals(supplierId, supplier.getId())) {
            throw new BizException("SUPPLIER_QUOTE_SUPPLIER_MISMATCH", "报价附件供应商与当前飞书绑定供应商不一致");
        }
        for (Map<String, String> row : rows) {
            String supplierCode = rowValue(row, "供应商编码", "supplierCode");
            if (StringUtils.hasText(supplierCode) && !supplierCode.equalsIgnoreCase(firstText(supplier.getSupplierCode(), ""))) {
                throw new BizException("SUPPLIER_QUOTE_SUPPLIER_MISMATCH", "报价附件供应商编码与当前飞书绑定供应商不一致");
            }
        }
    }

    private PurchaseRequestItem resolveRequestItem(
        Map<String, String> row,
        Map<Long, PurchaseRequestItem> itemById,
        Map<Integer, PurchaseRequestItem> itemByLineNo
    ) {
        Long itemId = parseLongText(rowValue(row, "采购明细ID", "明细ID", "purchaseRequestItemId"));
        if (itemId != null && itemById.containsKey(itemId)) {
            return itemById.get(itemId);
        }
        Integer lineNo = parseInteger(rowValue(row, "行号", "lineNo"));
        return lineNo == null ? null : itemByLineNo.get(lineNo);
    }

    private Long firstLongFromRows(List<Map<String, String>> rows, String... headers) {
        for (Map<String, String> row : rows) {
            Long value = parseLongText(rowValue(row, headers));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String requestNoFromFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        Matcher matcher = Pattern.compile("询价单-?([^./\\\\]+)", Pattern.CASE_INSENSITIVE).matcher(fileName);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private boolean isBlankQuoteRow(Map<String, String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        return row.entrySet().stream()
            .filter(entry -> !List.of(
                ExcelImportUtils.normalizeHeader("报价说明")
            ).contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .noneMatch(StringUtils::hasText);
    }

    private String rowValue(Map<String, String> row, String... headers) {
        if (row == null || headers == null) {
            return "";
        }
        for (String header : headers) {
            String value = row.get(ExcelImportUtils.normalizeHeader(header));
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private int parseQuoteLeadTimeDays(Map<String, String> row) {
        Integer leadTimeDays = parseInteger(rowValue(row, "交期天数", "交期", "leadTimeDays"));
        return leadTimeDays == null ? DEFAULT_QUOTE_LEAD_TIME_DAYS : leadTimeDays;
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("是")
            || normalized.equals("true")
            || normalized.equals("yes")
            || normalized.equals("y")
            || normalized.equals("1")
            || normalized.contains("放弃")
            || normalized.contains("替换");
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(cleanNumber(value)).intValue();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long parseLongText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(cleanNumber(value)).longValue();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(cleanNumber(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String cleanNumber(String value) {
        return firstText(value, "")
            .replace(",", "")
            .replace("￥", "")
            .replace("¥", "")
            .replace("元", "")
            .trim();
    }

    private SupplierFeishuQuoteNotice findQuoteNoticeForAction(Map<String, Object> value, String messageId) {
        if (StringUtils.hasText(messageId)) {
            SupplierFeishuQuoteNotice notice = noticeMapper.selectOne(new LambdaQueryWrapper<SupplierFeishuQuoteNotice>()
                .eq(SupplierFeishuQuoteNotice::getMessageId, messageId)
                .last("LIMIT 1"));
            if (notice != null) {
                return notice;
            }
        }
        Long purchaseRequestId = longValue(value == null ? null : value.get("purchaseRequestId"));
        Long supplierId = longValue(value == null ? null : value.get("supplierId"));
        if (purchaseRequestId == null || supplierId == null || purchaseRequestId <= 0 || supplierId <= 0) {
            return null;
        }
        String noticeType = normalizeNoticeType(text(value == null ? null : value.get("noticeType")));
        return noticeMapper.selectOne(new LambdaQueryWrapper<SupplierFeishuQuoteNotice>()
            .eq(SupplierFeishuQuoteNotice::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierFeishuQuoteNotice::getSupplierId, supplierId)
            .eq(SupplierFeishuQuoteNotice::getNoticeType, noticeType)
            .orderByDesc(SupplierFeishuQuoteNotice::getUpdatedAt)
            .last("LIMIT 1"));
    }

    private SupplierQuote findSupplierQuote(Long purchaseRequestId, Long supplierId) {
        if (purchaseRequestId == null || supplierId == null) {
            return null;
        }
        return supplierQuoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getSupplierId, supplierId)
            .last("LIMIT 1"));
    }

    private SupplierFeishuBinding loadBinding(Long bindingId) {
        if (bindingId == null || bindingId <= 0) {
            return null;
        }
        return bindingMapper.selectById(bindingId);
    }

    private String supplierConfirmStatusLabel(String status) {
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(status)) {
            return "已确认中标";
        }
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED.equals(status)) {
            return "已放弃中标";
        }
        return "已处理";
    }

    private String supplierConfirmStatusTemplate(String status) {
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(status)) {
            return "green";
        }
        if (ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED.equals(status)) {
            return "red";
        }
        return "grey";
    }

    private boolean isTestQuoteTemplateAction(Map<String, Object> value) {
        Long purchaseRequestId = longValue(value == null ? null : value.get("purchaseRequestId"));
        return purchaseRequestId != null && purchaseRequestId == 0;
    }

    private PurchaseRequest buildQuoteNoticeTestRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(0L);
        request.setReqNo("PTCG-SUPPLIER-TEST");
        request.setReqTitle("供应商报价任务通知样卡");
        request.setReqReason("供应商报价通知测试");
        request.setStatus(ProcurementWorkflow.STATUS_QUOTING);
        request.setQuoteCountdownDueAt(LocalDateTime.now().plusDays(3));
        return request;
    }

    private Supplier buildQuoteNoticeTestSupplier() {
        Supplier supplier = new Supplier();
        supplier.setId(0L);
        supplier.setSupplierCode("SUP-TEST");
        supplier.setSupplierName("测试供应商");
        return supplier;
    }

    private List<PurchaseRequestItem> buildQuoteNoticeTestItems() {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setLineNo(1);
        item.setItemCode("TEST-001");
        item.setReqType("服务/费用/低值易耗等");
        item.setItemName("测试商品");
        item.setSpecModel("规格型号");
        item.setQty(BigDecimal.ONE);
        item.setUnit("件");
        item.setPurpose("报价通知样卡");
        return List.of(item);
    }

    private ExportFile buildQuoteTemplateFile(PurchaseRequest request, Supplier supplier, List<PurchaseRequestItem> items) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
            "行号（勿改）",
            "物料编码（勿改）",
            "商品名称（勿改）",
            "规格型号（勿改）",
            "电商链接（勿改）",
            "收货城市（勿改）",
            "数量（勿改）",
            "单位（勿改）",
            "是否放弃报价（可填）",
            "原商品成色（可填）",
            "原商品二手说明（可填）",
            "报价单价（可填）",
            "交期天数（可填，默认7天）",
            "报价备注（可填）",
            "是否替换商品（可填）",
            "替换商品名称（替换时必填）",
            "替换规格型号（替换时必填）",
            "替换商品链接（替换时必填）",
            "替换商品成色（可填）",
            "替换商品二手说明（可填）",
            "替换商品单价（可填）",
            "替换原因（替换时必填）",
            "报价说明（勿改）",
            "申请单号（勿改）",
            "采购申请ID（勿改）",
            "采购明细ID（勿改）",
            "供应商ID（勿改）",
            "供应商编码（勿改）"
        ));
        for (PurchaseRequestItem item : items) {
            rows.add(List.of(
                firstText(text(item.getLineNo()), ""),
                firstText(item.getItemCode(), ""),
                firstText(item.getItemName(), ""),
                firstText(item.getSpecModel(), ""),
                quoteTemplateEcommerceUrl(item),
                firstText(item.getReceivingCity(), ""),
                quantityText(item.getQty()),
                firstText(item.getUnit(), ""),
                "否",
                "全新",
                "",
                "",
                String.valueOf(DEFAULT_QUOTE_LEAD_TIME_DAYS),
                "",
                "否",
                "",
                "",
                "",
                "全新",
                "",
                "",
                "",
                QUOTE_TEMPLATE_NOTICE,
                firstText(request.getReqNo(), ""),
                firstText(text(request.getId()), ""),
                firstText(text(item.getId()), ""),
                firstText(text(supplier == null ? null : supplier.getId()), ""),
                firstText(supplier == null ? null : supplier.getSupplierCode(), "")
            ));
        }
        String fileName = sanitizeFileName("询价单-" + firstText(request.getReqNo(), "采购申请") + ".xlsx");
        return new ExportFile(
            fileName,
            ExcelExportUtils.XLSX_CONTENT_TYPE,
            ExcelExportUtils.toXlsxBytes("询价单", rows, quoteTemplateWorkbookOptions(rows.get(0)))
        );
    }

    private ExcelExportUtils.WorkbookOptions quoteTemplateWorkbookOptions(List<String> headers) {
        ExcelExportUtils.Builder builder = ExcelExportUtils.WorkbookOptions.builder()
            .freezeHeader(true)
            .autoFilter(true);
        for (String header : List.of("是否放弃报价", "是否替换商品")) {
            addQuoteTemplateDropdown(builder, headers, header, YES_NO_OPTIONS);
            addQuoteTemplateNote(builder, headers, header, "可填写：请从下拉中选择“是”或“否”。");
        }
        for (String header : List.of("原商品成色", "替换商品成色")) {
            addQuoteTemplateDropdown(builder, headers, header, PRODUCT_CONDITION_OPTIONS);
            addQuoteTemplateNote(builder, headers, header, "可填写：请从下拉中选择“全新”或“二手”。");
        }
        for (String header : List.of("原商品二手说明", "报价单价", "报价备注", "替换商品二手说明")) {
            addQuoteTemplateNote(builder, headers, header, "可填写：供应商报价时填写。");
        }
        for (String header : List.of("替换商品名称", "替换规格型号", "替换商品链接", "替换原因")) {
            addQuoteTemplateNote(builder, headers, header, "选择“是否替换商品=是”时必填。");
        }
        addQuoteTemplateNote(builder, headers, "替换商品单价", "选择替换商品时必填且必须大于 0。");
        addQuoteTemplateNote(builder, headers, "交期天数", "默认 7 天，可按实际交货周期填写正整数。");
        for (String header : List.of("行号", "物料编码", "商品名称", "规格型号", "电商链接", "收货城市", "数量", "单位", "报价说明", "申请单号", "采购申请ID", "采购明细ID", "供应商ID", "供应商编码")) {
            addQuoteTemplateNote(builder, headers, header, "勿改：系统生成字段，回传时用于匹配采购明细。");
        }
        addQuoteTemplateHyperlink(builder, headers, "电商链接");
        addQuoteTemplateHyperlink(builder, headers, "替换商品链接");
        return builder.build();
    }

    private String quoteTemplateEcommerceUrl(PurchaseRequestItem item) {
        if (item == null || !StringUtils.hasText(item.getEcommerceUrl())) {
            return "";
        }
        try {
            return ecommerceUrlService.normalizeForLine(item.getEcommerceUrl(), item.getLineNo() == null ? 1 : item.getLineNo());
        } catch (BizException ex) {
            log.warn("Supplier quote template contains invalid ecommerce link, itemId={}, code={}", item.getId(), ex.getCode());
            return "链接无效，请联系采购人";
        }
    }

    private void addQuoteTemplateDropdown(ExcelExportUtils.Builder builder, List<String> headers, String header, List<String> options) {
        int index = quoteTemplateColumnIndex(headers, header);
        if (index >= 0) {
            builder.dropdown(index, options);
        }
    }

    private void addQuoteTemplateNote(ExcelExportUtils.Builder builder, List<String> headers, String header, String note) {
        int index = quoteTemplateColumnIndex(headers, header);
        if (index >= 0) {
            builder.note(0, index, note);
        }
    }

    private void addQuoteTemplateHyperlink(ExcelExportUtils.Builder builder, List<String> headers, String header) {
        int index = quoteTemplateColumnIndex(headers, header);
        if (index >= 0) {
            builder.hyperlinkColumns(index);
        }
    }

    private int quoteTemplateColumnIndex(List<String> headers, String header) {
        String normalized = ExcelImportUtils.normalizeHeader(header);
        for (int index = 0; index < headers.size(); index++) {
            if (Objects.equals(ExcelImportUtils.normalizeHeader(headers.get(index)), normalized)) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Object> buildSupplierQuoteNoticeCard(PurchaseRequest request, Supplier supplier, List<PurchaseRequestItem> items) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_SCHEMA_VERSION);
        String title = isDirectAwardRequest(request) ? "定向报价任务" : "供应商报价任务";
        card.put("config", cardConfig(title));
        card.put("header", Map.of(
            "template", isDirectAwardRequest(request) ? "orange" : "blue",
            "title", plainText(title),
            "text_tag_list", List.of(Map.of(
                "tag", "text_tag",
                "text", plainText(CARD_VERSION),
                "color", "neutral"
            ))
        ));
        List<Object> elements = new ArrayList<>();
        elements.add(markdownElement(buildSupplierQuoteNoticeSummary(request, supplier, items)));
        elements.add(Map.of("tag", "hr"));
        elements.add(callbackButton("下载询价单", "primary", ACTION_DOWNLOAD_QUOTE_TEMPLATE, request, supplier));
        elements.add(markdownElement("卡片版本：" + CARD_VERSION));
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private String buildSupplierQuoteNoticeSummary(PurchaseRequest request, Supplier supplier, List<PurchaseRequestItem> items) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "卡片版本", CARD_VERSION);
        appendLine(builder, "供应商", firstText(supplier == null ? null : supplier.getSupplierName(), "-"));
        appendLine(builder, "申请单号", firstText(request.getReqNo(), "-"));
        appendLine(builder, "申请标题", firstText(request.getReqTitle(), "-"));
        appendLine(builder, "报价截止", supplierQuoteDeadlineText(request));
        appendLine(builder, "报价类型", supplierQuoteTypeLabel(request));
        builder.append("\n**采购明细**");
        int limit = Math.min(items == null ? 0 : items.size(), 8);
        for (int i = 0; i < limit; i++) {
            PurchaseRequestItem item = items.get(i);
            builder.append("\n")
                .append(i + 1)
                .append(". ")
                .append(markdownText(firstText(item.getItemName(), "-")))
                .append(" / 类型：")
                .append(markdownText(firstText(item.getReqType(), "-")))
                .append(" / ")
                .append(markdownText(firstText(item.getSpecModel(), "-")))
                .append(" / ")
                .append(quantityText(item.getQty()))
                .append(markdownText(firstText(item.getUnit(), "")));
            if (StringUtils.hasText(item.getPurpose())) {
                builder.append(" / 用途：").append(markdownText(item.getPurpose()));
            }
        }
        if (items != null && items.size() > limit) {
            builder.append("\n还有 ").append(items.size() - limit).append(" 条明细，请进入系统查看完整清单。");
        }
        builder.append("\n\n点击“下载询价单”后，机器人会把 Excel 询价单发送到当前会话；请填写后直接把 Excel 发回机器人，系统会继续执行现有报价、比价和中标确认流程。");
        return builder.toString();
    }

    private String supplierQuoteDeadlineText(PurchaseRequest request) {
        LocalDateTime deadline = supplierQuoteDeadlineAt(request);
        return deadline == null ? "待确认" : deadline.format(DATE_TIME_FORMATTER);
    }

    private LocalDateTime supplierQuoteDeadlineAt(PurchaseRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getQuoteCountdownDueAt() != null) {
            return request.getQuoteCountdownDueAt();
        }
        LocalDateTime openedAt = quoteOpenedAt(request);
        return openedAt == null ? null : openedAt.plusDays(quoteOpenDays());
    }

    private LocalDateTime quoteOpenedAt(PurchaseRequest request) {
        if (request == null) {
            return null;
        }
        return request.getApprovedAt() == null ? request.getUpdatedAt() : request.getApprovedAt();
    }

    private int quoteOpenDays() {
        String value = settingValue("quote_open_days");
        if (!StringUtils.hasText(value)) {
            return 7;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 7;
        }
    }

    private String supplierQuoteTypeLabel(PurchaseRequest request) {
        String quoteMode = isDirectAwardRequest(request) ? "定向报价" : "公开询价";
        return quoteMode + " / " + awardStrategyLabel(request == null ? null : request.getAwardStrategy());
    }

    private String awardStrategyLabel(String strategy) {
        return ProcurementWorkflow.isItemLowestAwardStrategy(strategy) ? "分项最低中标" : "总价最低中标";
    }

    private Map<String, Object> buildSupplierAwardConfirmationCard(PurchaseRequest request,
                                                                    Supplier supplier,
                                                                    SupplierQuote quote,
                                                                    List<SupplierQuoteItem> items) {
        return buildSupplierAwardCard(request, supplier, quote, items, false, "供应商中标确认", "orange", "");
    }

    private Map<String, Object> buildSupplierAwardProcessedCard(PurchaseRequest request,
                                                                Supplier supplier,
                                                                SupplierQuote quote,
                                                                List<SupplierQuoteItem> items,
                                                                String resultText,
                                                                String template) {
        return buildSupplierAwardCard(request, supplier, quote, items, true, resultText, template, resultText);
    }

    private Map<String, Object> buildSupplierAwardCard(PurchaseRequest request,
                                                       Supplier supplier,
                                                       SupplierQuote quote,
                                                       List<SupplierQuoteItem> items,
                                                       boolean resolved,
                                                       String title,
                                                       String template,
                                                       String resultText) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", CARD_SCHEMA_VERSION);
        card.put("config", cardConfig(title));
        card.put("header", Map.of(
            "template", template,
            "title", plainText(title),
            "text_tag_list", List.of(Map.of(
                "tag", "text_tag",
                "text", plainText(CARD_VERSION),
                "color", "neutral"
            ))
        ));
        List<Object> elements = new ArrayList<>();
        elements.add(markdownElement(buildSupplierAwardSummary(request, supplier, quote, items)));
        elements.add(Map.of("tag", "hr"));
        if (resolved) {
            elements.add(markdownElement("**处理结果**：" + markdownText(resultText)));
            elements.add(buttonRow(List.of(
                supplierActionButton(
                    "下载中标清单",
                    "primary",
                    ACTION_DOWNLOAD_AWARD_LIST,
                    request,
                    supplier,
                    "发送 " + firstText(request == null ? null : request.getReqNo(), "该申请") + " 的中标清单到当前会话？",
                    false)
            )));
        } else {
            elements.add(buttonRow(List.of(
                supplierActionButton(
                    "确认中标",
                    "primary",
                    ACTION_CONFIRM_AWARD,
                    request,
                    supplier,
                    "确认接受 " + firstText(request == null ? null : request.getReqNo(), "该申请") + " 的中标明细？",
                    false)
            )));
            List<Map<String, Object>> waiveFormElements = new ArrayList<>();
            waiveFormElements.add(markdownElement("**放弃中标**：请填写原因，系统会自动按原报价规则递补下一名供应商。"));
            waiveFormElements.add(awardWaiveReasonInput());
            waiveFormElements.add(buttonRow(List.of(
                supplierActionButton(
                    "放弃中标",
                    "danger",
                    ACTION_WAIVE_AWARD,
                    request,
                    supplier,
                    "确认放弃 " + firstText(request == null ? null : request.getReqNo(), "该申请") + " 的中标资格？",
                    true)
            )));
            elements.add(Map.of(
                "tag", "form",
                "name", "supplierAwardWaiveForm",
                "elements", waiveFormElements
            ));
        }
        elements.add(markdownElement("卡片版本：" + CARD_VERSION));
        card.put("body", Map.of("elements", elements));
        return card;
    }

    private String buildSupplierAwardSummary(PurchaseRequest request,
                                             Supplier supplier,
                                             SupplierQuote quote,
                                             List<SupplierQuoteItem> items) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "卡片版本", CARD_VERSION);
        appendLine(builder, "供应商", firstText(supplier == null ? null : supplier.getSupplierName(), "-"));
        appendLine(builder, "申请单号", firstText(request == null ? null : request.getReqNo(), "-"));
        appendLine(builder, "申请标题", firstText(request == null ? null : request.getReqTitle(), "-"));
        appendLine(builder, "中标金额", moneyText(sumQuoteItemAmount(items)));
        appendLine(builder, "确认截止", quote == null || quote.getSupplierConfirmDueAt() == null
            ? "-"
            : quote.getSupplierConfirmDueAt().format(DATE_TIME_FORMATTER));
        builder.append("\n**中标明细**");
        int limit = Math.min(items == null ? 0 : items.size(), 10);
        for (int i = 0; i < limit; i++) {
            SupplierQuoteItem item = items.get(i);
            builder.append("\n")
                .append(i + 1)
                .append(". ")
                .append(markdownText(displayItemName(item)))
                .append(" / ")
                .append(markdownText(displaySpecModel(item)))
                .append(" / ")
                .append(quantityText(item.getQty()))
                .append(markdownText(firstText(item.getUnit(), "")))
                .append(" / ")
                .append(moneyText(displayLineAmount(item)));
            if (StringUtils.hasText(item.getDeliveryAddress())) {
                builder.append(" / 送达：").append(markdownText(item.getDeliveryAddress()));
            }
        }
        if (items != null && items.size() > limit) {
            builder.append("\n还有 ").append(items.size() - limit).append(" 条中标明细，请在系统或中标清单中查看。");
        }
        return builder.toString();
    }

    private String sendInteractiveMessage(FeishuBotRuntimeConfig.Snapshot config, String receiveIdType, String receiveId, Map<String, Object> card) {
        String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
            .queryParam("receive_id_type", normalizeReceiveIdType(receiveIdType))
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

    private String uploadFeishuFile(FeishuBotRuntimeConfig.Snapshot config, ExportFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file_type", "xls");
        body.add("file_name", file.fileName());
        body.add("file", new ByteArrayResource(file.content()) {
            @Override
            public String getFilename() {
                return file.fileName();
            }
        });
        Map<String, Object> response = restClient.post()
            .uri(fileUploadUrl(config))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(MAP_TYPE);
        validateFeishuResponse(response);
        Map<String, Object> data = mapValue(response == null ? null : response.get("data"));
        String fileKey = firstText(text(data.get("file_key")), text(data.get("fileKey")), text(response == null ? null : response.get("file_key")));
        if (!StringUtils.hasText(fileKey)) {
            throw new BizException("FEISHU_FILE_UPLOAD_FAILED", "飞书响应缺少 file_key");
        }
        return fileKey;
    }

    private void sendFileMessage(FeishuBotRuntimeConfig.Snapshot config, String receiveIdType, String receiveId, String fileKey) {
        String url = UriComponentsBuilder.fromUriString(config.messageSendUrl())
            .queryParam("receive_id_type", normalizeReceiveIdType(receiveIdType))
            .build()
            .toUriString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "file");
        body.put("content", toJson(Map.of("file_key", fileKey)));
        Map<String, Object> response = restClient.post()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken(config))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE);
        validateFeishuResponse(response);
    }

    private String fileUploadUrl(FeishuBotRuntimeConfig.Snapshot config) {
        String messageUrl = firstText(config.messageSendUrl(), "https://open.feishu.cn/open-apis/im/v1/messages").replaceAll("\\?.*$", "");
        if (messageUrl.endsWith("/messages")) {
            return messageUrl.substring(0, messageUrl.length() - "/messages".length()) + "/files";
        }
        return "https://open.feishu.cn/open-apis/im/v1/files";
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
                cachedTenantAccessTokenExpiresAt = now + Math.max(60L, expireSeconds - 60L) * 1000L;
                cachedTenantAccessTokenSignature = signature;
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
            throw new BizException("FEISHU_RESPONSE_EMPTY", "飞书响应为空");
        }
        String code = text(response.get("code"));
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BizException("FEISHU_RESPONSE_FAILED",
                firstText(text(response.get("msg")), text(response.get("message")), "飞书接口返回失败"));
        }
    }

    private boolean quoteNoticeEnabled() {
        return boolSetting("feishu_supplier_quote_notice_enabled", false);
    }

    private boolean awardNoticeEnabled() {
        return boolSetting("feishu_supplier_award_notice_enabled", true);
    }

    private boolean adminCardCopyEnabled() {
        return boolSetting("feishu_admin_card_copy_enabled", false);
    }

    private int quoteNoticeScanBatchSize() {
        return intSetting("feishu_supplier_quote_notice_scan_batch_size", 50, 10, 500);
    }

    private List<SysUser> adminCopyRecipients() {
        if (!adminCardCopyEnabled()) {
            return List.of();
        }
        String configured = settingValue("feishu_admin_card_copy_usernames");
        List<SysUser> candidates = StringUtils.hasText(configured)
            ? configuredAdminCopyUsers(configured)
            : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        Set<Long> seen = new LinkedHashSet<>();
        List<SysUser> recipients = new ArrayList<>();
        for (SysUser user : candidates) {
            if (user == null || user.getId() == null || !Integer.valueOf(1).equals(user.getStatus()) || !isSystemAdminUser(user)) {
                continue;
            }
            if (seen.add(user.getId())) {
                recipients.add(user);
            }
        }
        return recipients;
    }

    private List<SysUser> configuredAdminCopyUsers(String value) {
        List<SysUser> users = new ArrayList<>();
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
        return Pattern.compile("[,，；\\s]+")
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

    private ReceiveTarget resolveAdminReceiveTarget(SysUser user) {
        String preferredType = normalizeReceiveIdType(firstText(settingValue("feishu_approval_card_receive_id_type"), "email"));
        String receiveId = resolveFeishuReceiveId(user, preferredType);
        if (StringUtils.hasText(receiveId)) {
            return new ReceiveTarget(preferredType, receiveId);
        }
        for (String fallbackType : List.of("user_id", "open_id", "union_id", "email")) {
            if (fallbackType.equals(preferredType)) {
                continue;
            }
            receiveId = resolveFeishuReceiveId(user, fallbackType);
            if (StringUtils.hasText(receiveId)) {
                return new ReceiveTarget(fallbackType, receiveId);
            }
        }
        return null;
    }

    private ReceiveTarget resolveDefaultAdminReceiveTarget() {
        for (SysUser admin : adminDefaultRecipients()) {
            ReceiveTarget target = resolveAdminReceiveTarget(admin);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private List<SysUser> adminDefaultRecipients() {
        String configured = settingValue("feishu_admin_card_copy_usernames");
        List<SysUser> candidates = StringUtils.hasText(configured)
            ? configuredAdminCopyUsers(configured)
            : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        Set<Long> seen = new LinkedHashSet<>();
        List<SysUser> recipients = new ArrayList<>();
        for (SysUser user : candidates) {
            if (user == null || user.getId() == null || !Integer.valueOf(1).equals(user.getStatus()) || !isSystemAdminUser(user)) {
                continue;
            }
            if (seen.add(user.getId())) {
                recipients.add(user);
            }
        }
        return recipients;
    }

    private String resolveFeishuReceiveId(SysUser user, String receiveIdType) {
        if (user == null) {
            return "";
        }
        return switch (normalizeReceiveIdType(receiveIdType)) {
            case "user_id" -> firstText(user.getFeishuUserId(), "");
            case "union_id" -> firstText(user.getFeishuUnionId(), "");
            case "email" -> firstText(user.getEmail(), "");
            default -> firstText(user.getFeishuOpenId(), "");
        };
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return "管理员";
        }
        return firstText(user.getRealName(), user.getUsername(), "管理员");
    }

    private String missingAnyReceiveIdMessage(String subject) {
        return subject + "未维护邮箱或飞书 open_id/user_id/union_id";
    }

    private boolean boolSetting(String key, boolean defaultValue) {
        String value = settingValue(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim());
    }

    private int intSetting(String key, int defaultValue, int min, int max) {
        try {
            int value = Integer.parseInt(firstText(settingValue(key), "").trim());
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private String settingValue(String key) {
        ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, key)
            .last("LIMIT 1"));
        return setting == null ? "" : setting.getSettingValue();
    }

    private String parseSupplierCode(String text) {
        Matcher matcher = BIND_PATTERN.matcher(firstText(text, "").trim());
        if (!matcher.matches()) {
            throw new BizException("SUPPLIER_BIND_FORMAT_INVALID", "请按“绑定 供应商编码”格式发送，例如：绑定 SUP0001");
        }
        return matcher.group(1).trim().toUpperCase();
    }

    private String resolveReceiveIdType(String chatId, String chatType) {
        if (StringUtils.hasText(chatId) && !"p2p".equalsIgnoreCase(firstText(chatType, ""))) {
            return "chat_id";
        }
        return "open_id";
    }

    private String normalizeReceiveIdType(String receiveIdType) {
        String value = firstText(receiveIdType, "open_id").trim().toLowerCase(Locale.ROOT);
        return List.of("open_id", "user_id", "union_id", "email", "chat_id").contains(value) ? value : "open_id";
    }

    private String normalizeNoticeType(String noticeType) {
        String value = firstText(noticeType, NOTICE_TYPE_QUOTE_TASK).trim().toUpperCase(Locale.ROOT);
        return NOTICE_TYPE_AWARD_CONFIRM.equals(value) ? NOTICE_TYPE_AWARD_CONFIRM : NOTICE_TYPE_QUOTE_TASK;
    }

    private boolean isDirectAwardRequest(PurchaseRequest request) {
        return request != null
            && request.getDirectAwardSupplierId() != null
            && request.getDirectAwardApplied() != null
            && request.getDirectAwardApplied() == 1;
    }

    private boolean isNationalDemandAggregate(PurchaseRequest request) {
        return request != null && NATIONAL_DEMAND_AGGREGATE_REASON.equals(request.getReqReason());
    }

    private Map<String, Object> plainText(String content) {
        return Map.of("tag", "plain_text", "content", firstText(content, ""));
    }

    private Map<String, Object> cardConfig(String summary) {
        return Map.of(
            "enable_forward", false,
            "update_multi", true,
            "width_mode", "default",
            "summary", Map.of("content", summary + " " + CARD_VERSION)
        );
    }

    private Map<String, Object> callbackButton(String text, String type, String action, PurchaseRequest request, Supplier supplier) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("source", CARD_SOURCE);
        value.put("noticeType", NOTICE_TYPE_QUOTE_TASK);
        value.put("action", action);
        value.put("purchaseRequestId", String.valueOf(request == null ? 0L : request.getId()));
        value.put("supplierId", String.valueOf(supplier == null ? 0L : supplier.getId()));
        value.put("cardVersion", CARD_VERSION);
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
        String reqNo = firstText(request == null ? null : request.getReqNo(), "该报价任务");
        button.put("confirm", Map.of(
            "title", plainText(text),
            "text", plainText("下载 " + reqNo + " 的供应商询价单？")
        ));
        return button;
    }

    private Map<String, Object> supplierActionButton(String text,
                                                     String type,
                                                     String action,
                                                     PurchaseRequest request,
                                                     Supplier supplier,
                                                     String confirmText,
                                                     boolean submitForm) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("source", CARD_SOURCE);
        value.put("noticeType", NOTICE_TYPE_AWARD_CONFIRM);
        value.put("action", action);
        value.put("purchaseRequestId", String.valueOf(request == null ? 0L : request.getId()));
        value.put("supplierId", String.valueOf(supplier == null ? 0L : supplier.getId()));
        value.put("cardVersion", CARD_VERSION);
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
        if (submitForm) {
            button.put("name", action);
            button.put("form_action_type", "submit");
        }
        button.put("confirm", Map.of(
            "title", plainText(text),
            "text", plainText(firstText(confirmText, text + "？"))
        ));
        return button;
    }

    private Map<String, Object> awardWaiveReasonInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tag", "input");
        input.put("name", "awardWaiveRemark");
        input.put("required", true);
        input.put("input_type", "text");
        input.put("max_length", 200);
        input.put("label", plainText("放弃原因"));
        input.put("placeholder", plainText("填写放弃中标原因"));
        return input;
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
        return Map.of("tag", "markdown", "content", firstText(content, ""));
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        builder.append("**").append(markdownText(label)).append("**：").append(markdownText(firstText(value, "-"))).append("\n");
    }

    private String markdownText(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private String quantityText(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private BigDecimal sumQuoteItemAmount(List<SupplierQuoteItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
            .map(this::displayLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String moneyText(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return "¥" + value.stripTrailingZeros().toPlainString();
    }

    private String displayItemName(SupplierQuoteItem item) {
        if (replacementApproved(item)) {
            return firstText(item.getReplacementItemName(), item.getItemName(), "-");
        }
        return firstText(item == null ? null : item.getItemName(), "-");
    }

    private String displaySpecModel(SupplierQuoteItem item) {
        if (replacementApproved(item)) {
            return firstText(item.getReplacementSpecModel(), item.getSpecModel(), "-");
        }
        return firstText(item == null ? null : item.getSpecModel(), "-");
    }

    private BigDecimal displayLineAmount(SupplierQuoteItem item) {
        if (replacementApproved(item)) {
            return item.getReplacementLineAmount() == null ? item.getLineAmount() : item.getReplacementLineAmount();
        }
        return item == null ? null : item.getLineAmount();
    }

    private boolean replacementApproved(SupplierQuoteItem item) {
        return item != null
            && item.getReplacementFlag() != null
            && item.getReplacementFlag() == 1
            && "APPROVED".equalsIgnoreCase(firstText(item.getReplacementReviewStatus(), ""));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException("FEISHU_CARD_JSON_FAILED", "飞书卡片内容序列化失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    Map.Entry::getValue,
                    (left, right) -> left,
                    LinkedHashMap::new
                ));
        }
        return new LinkedHashMap<>();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String formText(Map<String, Object> formValue, String name) {
        if (formValue == null || !StringUtils.hasText(name)) {
            return "";
        }
        Object value = formValue.get(name);
        if (value instanceof Map<?, ?> map) {
            return firstText(text(map.get("value")), text(map.get("text")), text(map.get("content")));
        }
        return text(value);
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
        try {
            return Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = text(value).trim();
            return StringUtils.hasText(text) ? Long.parseLong(text) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String sanitizeFileName(String value) {
        String fileName = firstText(value, "询价单.xlsx").replaceAll("[\\\\/:*?\"<>|]", "_");
        return fileName.length() <= 120 ? fileName : fileName.substring(0, 120);
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
