package com.company.procurement.modules.procurement.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.export.PdfExportUtils;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.order.dto.AfterSaleApprovalDTO;
import com.company.procurement.modules.procurement.order.dto.AfterSaleCreateDTO;
import com.company.procurement.modules.procurement.order.dto.OrderInvoiceReviewDTO;
import com.company.procurement.modules.procurement.order.dto.OrderReceiptDTO;
import com.company.procurement.modules.procurement.order.dto.ProcurementOrderPageQueryDTO;
import com.company.procurement.modules.procurement.order.entity.ProcurementAfterSale;
import com.company.procurement.modules.procurement.order.mapper.ProcurementAfterSaleMapper;
import com.company.procurement.modules.procurement.order.mapper.ProcurementOrderQueryMapper;
import com.company.procurement.modules.procurement.order.service.ProcurementOrderService;
import com.company.procurement.modules.procurement.order.service.OrderInvoiceService;
import com.company.procurement.modules.procurement.order.support.SupplierSoldOrderAccess;
import com.company.procurement.modules.procurement.order.vo.AfterSaleVO;
import com.company.procurement.modules.procurement.order.vo.OrderInvoiceSubmissionVO;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.support.SupplierQuoteAccess;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ProcurementOrderServiceImpl extends ServiceImpl<ProcurementAfterSaleMapper, ProcurementAfterSale> implements ProcurementOrderService {

    private static final String TYPE_REPLACE_ITEM = "REPLACE_ITEM";
    private static final String TYPE_NO_REASON_RETURN = "NO_REASON_RETURN";
    private static final String TYPE_RETURN_EXCHANGE = "RETURN_EXCHANGE";
    private static final String RECEIPT_STATUS_PENDING = "PENDING";
    private static final String RECEIPT_STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROPOSAL_PENDING = "PROPOSAL_PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final BigDecimal AFTER_SALE_UNIT_PRICE_INCREASE_LIMIT = new BigDecimal("400.00");
    private static final int INTERNAL_ORDER_SCAN_BATCH_SIZE = 200;
    private static final DateTimeFormatter AFTER_SALE_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String AFTER_SALE_KEYWORD_ORDER_EXISTS_SQL = """
        SELECT 1
        FROM supplier_quote_item sqi
        JOIN supplier_quote sq ON sq.id = sqi.quote_id AND sq.is_deleted = 0
        JOIN purchase_request pr ON pr.id = sqi.purchase_request_id AND pr.is_deleted = 0
        LEFT JOIN purchase_request_item pri ON pri.id = sqi.purchase_request_item_id AND pri.is_deleted = 0
        LEFT JOIN purchase_request src_pr ON src_pr.id = COALESCE(pri.source_request_id, pr.id) AND src_pr.is_deleted = 0
        LEFT JOIN supplier s ON s.id = sq.supplier_id AND s.is_deleted = 0
        WHERE sqi.id = procurement_after_sale.quote_item_id
          AND sqi.is_deleted = 0
          AND (
            CONCAT(pr.req_no, '-', LPAD(COALESCE(sqi.line_no, 0), 2, '0')) LIKE CONCAT('%', {0}, '%')
            OR pr.req_no LIKE CONCAT('%', {0}, '%')
            OR pr.req_title LIKE CONCAT('%', {0}, '%')
            OR src_pr.req_no LIKE CONCAT('%', {0}, '%')
            OR src_pr.req_title LIKE CONCAT('%', {0}, '%')
            OR sqi.item_name LIKE CONCAT('%', {0}, '%')
            OR sqi.item_code LIKE CONCAT('%', {0}, '%')
            OR s.supplier_name LIKE CONCAT('%', {0}, '%')
          )
        """;

    private final ProcurementOrderQueryMapper orderQueryMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final ProcurementSettingMapper procurementSettingMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final SysUserMapper userMapper;
    private final OrderInvoiceService orderInvoiceService;

    @Override
    public PageResult<ProcurementOrderVO> pageMyOrders(ProcurementOrderPageQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ScopeFilter scope = resolveOrderScope(authUser);
        long pageNo = Math.max(1, queryDTO.getPageNo());
        long pageSize = Math.max(1, queryDTO.getPageSize());
        long offset = (pageNo - 1) * pageSize;
        String keyword = normalizeBlank(queryDTO.getKeyword());
        String status = normalizeBlank(queryDTO.getStatus());
        long total;
        List<ProcurementOrderVO> records;
        if (scope.internalUserId() != null) {
            InternalOrderSlice visible = scanInternalOrders(
                    keyword,
                    status,
                    null,
                    scope.internalUserId(),
                    authUser,
                    offset,
                    pageSize);
            total = visible.total();
            records = visible.records();
        } else {
            total = orderQueryMapper.countOrderRows(keyword, status, null, scope.supplierId());
            records = total == 0
                ? List.of()
                : orderQueryMapper.selectOrderRows(keyword, status, null, scope.supplierId(), null, offset, pageSize);
            records.forEach(order -> decorateOrder(order, authUser));
        }
        return PageResult.of(pageNo, pageSize, total, records);
    }

    @Override
    public ProcurementOrderVO orderDetail(Long quoteItemId) {
        ProcurementOrderVO order = requireOrder(quoteItemId);
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureCanViewOrder(authUser, order);
        decorateOrder(order, authUser);
        return order;
    }

    @Override
    public ExportFile exportOrder(Long quoteItemId) {
        ProcurementOrderVO order = orderDetail(quoteItemId);
        if (!Boolean.TRUE.equals(order.getCanExportOrder())) {
            throw new BizException("FORBIDDEN", "当前账号不能导出该订单");
        }
        String fileName = "操作订单-" + sanitizeFileName(order.getOrderNo()) + ".pdf";
        return buildOrderPdf(fileName, List.of(order), includeQuoteComparison());
    }

    @Override
    public List<ProcurementOrderVO> listMyOrdersByRequestNo(String requestNo) {
        String normalized = normalizeBlank(requestNo);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException("REQUEST_NO_REQUIRED", "集采单号不能为空");
        }
        AuthUser authUser = AuthContextHolder.requireUser();
        ScopeFilter scope = resolveOrderScope(authUser);
        List<ProcurementOrderVO> records;
        if (scope.internalUserId() != null) {
            records = scanInternalOrders(
                    null,
                    null,
                    normalized,
                    scope.internalUserId(),
                    authUser,
                    0,
                    Long.MAX_VALUE)
                .records();
        } else {
            long total = orderQueryMapper.countOrderRows(null, null, normalized, scope.supplierId());
            if (total == 0) {
                return List.of();
            }
            records = orderQueryMapper.selectOrderRows(
                null,
                null,
                normalized,
                scope.supplierId(),
                null,
                0,
                total
            );
            records.forEach(order -> decorateOrder(order, authUser));
        }
        if (records.isEmpty()) {
            return List.of();
        }
        return records;
    }

    @Override
    public ExportFile exportOrdersByRequestNo(String requestNo, String deptName, Long supplierId) {
        List<ProcurementOrderVO> orders = listMyOrdersByRequestNo(requestNo);
        if (orders.isEmpty()) {
            throw new BizException("ORDER_GROUP_NOT_FOUND", "未找到该集采单下可查看的订单");
        }
        AuthUser authUser = AuthContextHolder.requireUser();
        List<ProcurementOrderVO> exportableOrders = filterArchiveOrders(orders, deptName, supplierId).stream()
            .filter(order -> Boolean.TRUE.equals(order.getCanExportOrder()))
            .toList();
        if (exportableOrders.isEmpty()) {
            throw new BizException("FORBIDDEN", "当前账号不能导出该供应商归档，或归档中没有可导出订单");
        }
        exportableOrders.forEach(order -> ensureCanViewOrder(authUser, order));
        String groupNo = safe(exportableOrders.get(0).getAggregateReqNo());
        String archiveName = StringUtils.hasText(groupNo) ? groupNo : requestNo;
        if (StringUtils.hasText(deptName)) {
            archiveName += "-" + deptName.trim();
        }
        if (supplierId != null && StringUtils.hasText(exportableOrders.get(0).getSupplierName())) {
            archiveName += "-" + exportableOrders.get(0).getSupplierName();
        }
        String fileName = "操作订单-" + sanitizeFileName(archiveName) + ".pdf";
        return buildOrderPdf(fileName, exportableOrders, includeQuoteComparison());
    }

    static List<ProcurementOrderVO> filterArchiveOrders(List<ProcurementOrderVO> orders,
                                                        String deptName,
                                                        Long supplierId) {
        String normalizedDeptName = StringUtils.hasText(deptName) ? deptName.trim() : null;
        return orders.stream()
            .filter(order -> !StringUtils.hasText(normalizedDeptName)
                || normalizedDeptName.equals(StringUtils.hasText(order.getDeptName()) ? order.getDeptName().trim() : null))
            .filter(order -> supplierId == null || supplierId.equals(order.getSupplierId()))
            .toList();
    }

    @Override
    public List<OrderInvoiceSubmissionVO> listInvoices(Long quoteItemId, String documentType) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return orderInvoiceService.list(requireOrder(quoteItemId), authUser, documentType);
    }

    @Override
    public OrderInvoiceSubmissionVO uploadInvoice(Long quoteItemId,
                                                  String documentType,
                                                  List<MultipartFile> files,
                                                  String invoiceNo,
                                                  LocalDate invoiceDate,
                                                  BigDecimal totalAmount,
                                                  String invoiceType,
                                                  String remark) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return orderInvoiceService.upload(
            requireOrder(quoteItemId),
            authUser,
            documentType,
            files,
            invoiceNo,
            invoiceDate,
            totalAmount,
            invoiceType,
            remark
        );
    }

    @Override
    public OrderInvoiceSubmissionVO acceptInvoice(Long quoteItemId, Long submissionId, OrderInvoiceReviewDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return orderInvoiceService.accept(
            requireOrder(quoteItemId),
            submissionId,
            authUser,
            dto == null ? null : dto.getRemark()
        );
    }

    @Override
    public OrderInvoiceSubmissionVO returnInvoice(Long quoteItemId, Long submissionId, OrderInvoiceReviewDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return orderInvoiceService.returnForResubmission(
            requireOrder(quoteItemId),
            submissionId,
            authUser,
            dto == null ? null : dto.getRemark()
        );
    }

    @Override
    public OrderInvoiceService.AttachmentResource loadInvoiceAttachment(Long quoteItemId, Long attachmentId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return orderInvoiceService.loadAttachment(requireOrder(quoteItemId), attachmentId, authUser);
    }

    private boolean includeQuoteComparison() {
        AuthUser authUser = AuthContextHolder.requireUser();
        return !DataScopeHelper.isSupplierUser(authUser) || SupplierQuoteAccess.canViewAllSubmissions(authUser);
    }

    private ExportFile buildOrderPdf(String fileName, List<ProcurementOrderVO> orders, boolean includeComparison) {
        if (orders == null || orders.isEmpty()) {
            throw new BizException("ORDER_NOT_FOUND", "订单不存在");
        }
        List<PurchaseRequest> requests = relatedRequests(orders);
        Map<Long, PurchaseRequest> requestMap = mapRequests(requests);
        Map<Long, SupplierQuote> quoteMap = relatedQuotes(orders);
        QuoteComparison comparison = includeComparison ? quoteComparison(orders) : QuoteComparison.empty();
        List<ApprovalRecord> approvalRecords = approvalRecords(requestMap.keySet());
        Map<Long, SysUser> userMap = relatedUsers(approvalRecords, requests);

        String groupNo = displayGroupNo(orders);
        String title = "采购操作订单 - " + groupNo;
        List<PdfExportUtils.PdfBlock> blocks = new ArrayList<>();
        if (includeComparison) {
            blocks.add(new PdfExportUtils.PdfBlock("采购结论", decisionMetrics(orders, comparison), List.of(), List.of()));
        }
        blocks.add(new PdfExportUtils.PdfBlock("订单概览", overviewRows(orders, requests), List.of()));
        List<List<String>> delegationRows = delegationRows(requests, userMap);
        if (!delegationRows.isEmpty()) {
            blocks.add(new PdfExportUtils.PdfBlock("管理员代办审计", List.of(), List.of(new PdfExportUtils.PdfTable(
                "代用户选择记录",
                List.of("申请单号", "申请人", "代办管理员", "选择部门", "原因"),
                delegationRows,
                new float[] {1.2f, 1.2f, 1.2f, 2.6f, 3.0f}
            ))));
        }
        if (includeComparison) {
            blocks.add(new PdfExportUtils.PdfBlock("商品比价信息", List.of(), List.of(new PdfExportUtils.PdfTable(
                "比价矩阵（横排商品，竖排供应商报价）",
                comparison.headers(),
                comparison.rows(),
                comparison.widths(),
                List.of(),
                List.of(),
                comparison.highlightedCells()
            ))));
        }
        blocks.add(new PdfExportUtils.PdfBlock("报价与下单时间", List.of(), List.of(new PdfExportUtils.PdfTable(
            "各订单阶段时间",
            List.of("订单号", "来源申请", "中标单位", "报价单号", "报价时间", "下单时间", "确认时间", "收货时间"),
            orderTimeRows(orders, quoteMap),
            new float[] {1.4f, 1.3f, 1.8f, 1.3f, 1.6f, 1.6f, 1.6f, 1.6f}
        ))));
        blocks.add(new PdfExportUtils.PdfBlock("物品详情", List.of(), List.of(new PdfExportUtils.PdfTable(
            "订单物品明细",
            List.of("序号", "订单号", "商品", "规格", "数量", "单位", "单价", "金额", "交期", "送达地址"),
            itemRows(orders),
            new float[] {0.5f, 1.4f, 2.0f, 1.6f, 0.8f, 0.6f, 0.9f, 1.0f, 0.8f, 2.2f}
        ))));
        blocks.add(new PdfExportUtils.PdfBlock("采购申请信息", List.of(), List.of(new PdfExportUtils.PdfTable(
            "关联申请单",
            List.of("申请单号", "标题", "类型", "部门", "申请提交", "审批通过", "报价开始", "报价截止"),
            requestRows(requests),
            new float[] {1.2f, 2.2f, 0.9f, 1.4f, 1.5f, 1.5f, 1.5f, 1.5f}
        ))));
        blocks.add(new PdfExportUtils.PdfBlock("完整审批流程", List.of(), List.of(new PdfExportUtils.PdfTable(
            "审批记录",
            List.of("申请单号", "轮次", "审批节点", "审批人", "动作", "流转状态", "审批意见", "审批时间"),
            approvalRows(approvalRecords, requestMap, userMap),
            new float[] {1.2f, 0.6f, 1.4f, 1.0f, 0.8f, 1.3f, 2.4f, 1.5f}
        ))));
        List<List<String>> afterSaleRows = afterSaleRows(orders);
        if (!afterSaleRows.isEmpty()) {
            blocks.add(new PdfExportUtils.PdfBlock("售后与收货", List.of(), List.of(new PdfExportUtils.PdfTable(
                "售后记录",
                List.of("订单号", "收货状态", "收货时间", "售后单号", "售后类型", "售后状态", "售后原因", "审核意见"),
                afterSaleRows,
                new float[] {1.2f, 1.0f, 1.5f, 1.4f, 1.1f, 1.0f, 2.0f, 2.0f}
            ))));
        }
        return new ExportFile(fileName, PdfExportUtils.PDF_CONTENT_TYPE, PdfExportUtils.toPdfBytes(title, blocks));
    }

    private List<PdfExportUtils.PdfMetric> decisionMetrics(List<ProcurementOrderVO> orders, QuoteComparison comparison) {
        String suppliers = comparison.awardedSupplierNames().isEmpty()
            ? joinUnique(orders.stream().map(ProcurementOrderVO::getSupplierName).toList())
            : String.join("、", comparison.awardedSupplierNames());
        String fastest = comparison.fastestAwardLeadTimeDays() == null ? "-" : comparison.fastestAwardLeadTimeDays() + " 天";
        return List.of(
            new PdfExportUtils.PdfMetric("中标单位", firstText(suppliers, "-"), "最终执行供应商"),
            new PdfExportUtils.PdfMetric("中标总额", moneyText(comparison.awardedTotal()), "按中标明细汇总"),
            new PdfExportUtils.PdfMetric("中标项数", comparison.awardedItemCount() + " / " + comparison.itemCount(), "已覆盖采购物品"),
            new PdfExportUtils.PdfMetric("最快交期", fastest, "中标明细最短交付")
        );
    }

    private List<List<String>> overviewRows(List<ProcurementOrderVO> orders, List<PurchaseRequest> requests) {
        return List.of(
            List.of("操作单号", displayGroupNo(orders)),
            List.of("订单类型", orderGroupType(orders)),
            List.of("订单状态", groupStatusSummary(orders)),
            List.of("中标单位", joinUnique(orders.stream().map(ProcurementOrderVO::getSupplierName).toList())),
            List.of("申请人", joinUnique(orders.stream().map(ProcurementOrderVO::getApplicantName).toList())),
            List.of("采购人", joinUnique(orders.stream().map(ProcurementOrderVO::getPurchaserName).toList())),
            List.of("部门", joinUnique(orders.stream().map(ProcurementOrderVO::getDeptName).toList())),
            List.of("物品项数", String.valueOf(orders.size())),
            List.of("合计金额", moneyText(orders.stream()
                .map(ProcurementOrderVO::getLineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add))),
            List.of("关联申请数", String.valueOf(requests.size())),
            List.of("最早下单时间", dateTimeText(orders.stream()
                .map(ProcurementOrderVO::getOrderTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null))),
            List.of("导出时间", dateTimeText(LocalDateTime.now()))
        );
    }

    private List<List<String>> orderTimeRows(List<ProcurementOrderVO> orders, Map<Long, SupplierQuote> quoteMap) {
        List<List<String>> rows = new ArrayList<>();
        for (ProcurementOrderVO order : orders) {
            SupplierQuote quote = quoteMap.get(order.getQuoteId());
            rows.add(List.of(
                safe(order.getOrderNo()),
                safe(firstText(order.getSourceReqNo(), order.getReqNo())),
                safe(order.getSupplierName()),
                quote == null ? "" : safe(quote.getQuoteNo()),
                quote == null ? "" : dateTimeText(quote.getSubmittedAt()),
                dateTimeText(order.getOrderTime()),
                dateTimeText(order.getSupplierConfirmedAt()),
                dateTimeText(order.getReceivedAt())
            ));
        }
        return rows;
    }

    private List<List<String>> itemRows(List<ProcurementOrderVO> orders) {
        List<List<String>> rows = new ArrayList<>();
        int index = 1;
        for (ProcurementOrderVO order : orders) {
            rows.add(List.of(
                String.valueOf(index++),
                safe(order.getOrderNo()),
                safe(order.getItemName()),
                safe(order.getSpecModel()),
                decimalText(order.getQty()),
                safe(order.getUnit()),
                moneyText(order.getUnitPrice()),
                moneyText(order.getLineAmount()),
                order.getLeadTimeDays() == null ? "" : order.getLeadTimeDays() + " 天",
                firstText(order.getDeliveryAddress(), order.getReceivingCity())
            ));
        }
        return rows;
    }

    private List<List<String>> comparisonOverviewRows(QuoteComparison comparison) {
        return List.of(
            List.of("比价商品数", String.valueOf(comparison.itemCount())),
            List.of("参与供应商数", String.valueOf(comparison.supplierCount())),
            List.of("报价明细数", String.valueOf(comparison.quoteItemCount())),
            List.of("中标项数", comparison.awardedItemCount() + " / " + comparison.itemCount()),
            List.of("中标合计", moneyText(comparison.awardedTotal())),
            List.of("中标价标识", comparison.highlightedCells().isEmpty() ? "暂无中标报价" : "浅绿色高亮为最终中标报价")
        );
    }

    private QuoteComparison quoteComparison(List<ProcurementOrderVO> orders) {
        List<SupplierQuoteItem> quoteItems = comparisonQuoteItems(orders);
        if (quoteItems.isEmpty()) {
            return QuoteComparison.empty();
        }
        Map<Long, SupplierQuote> quoteMap = relatedQuotesByQuoteItems(quoteItems);
        Map<Long, Supplier> supplierMap = relatedSuppliers(quoteMap.values());
        Map<Long, PurchaseRequestItem> requestItemMap = relatedRequestItems(orders, quoteItems);

        List<SupplierQuoteItem> visibleItems = quoteItems.stream()
            .filter(item -> {
                SupplierQuote quote = quoteMap.get(item.getQuoteId());
                return quote != null && "SUBMITTED".equals(quote.getStatus());
            })
            .sorted(Comparator
                .comparing(SupplierQuoteItem::getLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(this::effectiveUnitPrice, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(SupplierQuoteItem::getId, Comparator.nullsLast(Long::compareTo)))
            .toList();

        List<ComparisonSupplier> suppliers = comparisonSuppliers(visibleItems, quoteMap, supplierMap);
        List<ComparisonProduct> products = comparisonProducts(visibleItems, requestItemMap);
        Map<String, SupplierQuoteItem> itemByProductSupplier = comparisonItemMap(visibleItems, quoteMap);
        List<String> awardedSupplierNames = suppliers.stream()
            .filter(supplier -> supplier.awardedCount() > 0)
            .map(ComparisonSupplier::name)
            .filter(StringUtils::hasText)
            .toList();
        Integer fastestAwardLeadTime = visibleItems.stream()
            .filter(item -> Integer.valueOf(1).equals(item.getAwarded()))
            .map(SupplierQuoteItem::getLeadTimeDays)
            .filter(Objects::nonNull)
            .min(Integer::compareTo)
            .orElse(null);

        List<String> headers = new ArrayList<>();
        headers.add("物品");
        for (int i = 0; i < suppliers.size(); i++) {
            ComparisonSupplier supplier = suppliers.get(i);
            headers.add((i + 1) + ". " + supplier.name() + (supplier.awardedCount() > 0 ? "\n√ 中标 " + supplier.awardedCount() + " 项" : ""));
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> highlightedCells = new ArrayList<>();
        int awardedItemCount = 0;
        BigDecimal awardedTotal = BigDecimal.ZERO;
        for (ComparisonProduct product : products) {
            List<String> row = new ArrayList<>();
            row.add(product.description());
            int rowIndex = rows.size();
            for (int supplierIndex = 0; supplierIndex < suppliers.size(); supplierIndex++) {
                ComparisonSupplier supplier = suppliers.get(supplierIndex);
                SupplierQuoteItem item = itemByProductSupplier.get(product.key() + "|" + supplier.key());
                if (item == null) {
                    row.add("未报价");
                    continue;
                }
                boolean awarded = Integer.valueOf(1).equals(item.getAwarded());
                if (awarded) {
                    highlightedCells.add(PdfExportUtils.cellKey(rowIndex, supplierIndex + 1));
                    awardedItemCount++;
                    BigDecimal amount = effectiveLineAmount(item);
                    if (amount != null) {
                        awardedTotal = awardedTotal.add(amount);
                    }
                }
                row.add(comparisonMatrixCell(item, awarded, product.awardedUnitPrice()));
            }
            rows.add(row);
        }

        if (!products.isEmpty() && !suppliers.isEmpty()) {
            int subtotalRowIndex = rows.size();
            List<String> subtotal = new ArrayList<>();
            subtotal.add("小计");
            for (int supplierIndex = 0; supplierIndex < suppliers.size(); supplierIndex++) {
                ComparisonSupplier supplier = suppliers.get(supplierIndex);
                subtotal.add(comparisonSupplierSubtotal(supplier));
                if (supplier.awardedCount() > 0) {
                    highlightedCells.add(PdfExportUtils.cellKey(subtotalRowIndex, supplierIndex + 1));
                }
            }
            rows.add(subtotal);
        }

        if (rows.isEmpty()) {
            return QuoteComparison.empty();
        }
        return new QuoteComparison(
            headers,
            comparisonWidths(headers.size()),
            rows,
            highlightedCells,
            products.size(),
            suppliers.size(),
            visibleItems.size(),
            awardedItemCount,
            awardedTotal,
            awardedSupplierNames,
            fastestAwardLeadTime
        );
    }

    private List<ComparisonSupplier> comparisonSuppliers(List<SupplierQuoteItem> items,
                                                         Map<Long, SupplierQuote> quoteMap,
                                                         Map<Long, Supplier> supplierMap) {
        Map<String, SupplierQuote> firstQuoteBySupplier = new LinkedHashMap<>();
        Map<String, BigDecimal> totalBySupplier = new HashMap<>();
        Map<String, BigDecimal> awardedTotalBySupplier = new HashMap<>();
        Map<String, Integer> quotedCountBySupplier = new HashMap<>();
        Map<String, Integer> awardedCountBySupplier = new HashMap<>();
        Map<String, Integer> bestLeadTimeBySupplier = new HashMap<>();
        for (SupplierQuoteItem item : items) {
            SupplierQuote quote = quoteMap.get(item.getQuoteId());
            if (quote == null) {
                continue;
            }
            String key = comparisonSupplierKey(quote);
            firstQuoteBySupplier.putIfAbsent(key, quote);
            quotedCountBySupplier.merge(key, 1, Integer::sum);
            BigDecimal amount = effectiveLineAmount(item);
            if (amount != null) {
                totalBySupplier.merge(key, amount, BigDecimal::add);
            }
            if (item.getLeadTimeDays() != null) {
                bestLeadTimeBySupplier.merge(key, item.getLeadTimeDays(), Math::min);
            }
            if (Integer.valueOf(1).equals(item.getAwarded())) {
                awardedCountBySupplier.merge(key, 1, Integer::sum);
                if (amount != null) {
                    awardedTotalBySupplier.merge(key, amount, BigDecimal::add);
                }
            }
        }
        return firstQuoteBySupplier.entrySet().stream()
            .map(entry -> {
                SupplierQuote quote = entry.getValue();
                Supplier supplier = supplierMap.get(quote.getSupplierId());
                String name = supplierName(supplier, quote);
                return new ComparisonSupplier(
                    entry.getKey(),
                    name,
                    totalBySupplier.getOrDefault(entry.getKey(), BigDecimal.ZERO),
                    awardedTotalBySupplier.getOrDefault(entry.getKey(), BigDecimal.ZERO),
                    quotedCountBySupplier.getOrDefault(entry.getKey(), 0),
                    awardedCountBySupplier.getOrDefault(entry.getKey(), 0),
                    bestLeadTimeBySupplier.get(entry.getKey())
                );
            })
            .sorted(Comparator
                .comparing(ComparisonSupplier::awardedCount, Comparator.reverseOrder())
                .thenComparing(ComparisonSupplier::totalAmount)
                .thenComparing(ComparisonSupplier::name, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private List<ComparisonProduct> comparisonProducts(List<SupplierQuoteItem> items,
                                                       Map<Long, PurchaseRequestItem> requestItemMap) {
        Map<String, SupplierQuoteItem> firstItemByProduct = new LinkedHashMap<>();
        Map<String, BigDecimal> awardedPriceByProduct = new HashMap<>();
        for (SupplierQuoteItem item : items) {
            String key = comparisonProductKey(item);
            firstItemByProduct.putIfAbsent(key, item);
            if (Integer.valueOf(1).equals(item.getAwarded())) {
                awardedPriceByProduct.put(key, effectiveUnitPrice(item));
            }
        }
        List<ComparisonProduct> products = new ArrayList<>();
        for (Map.Entry<String, SupplierQuoteItem> entry : firstItemByProduct.entrySet()) {
            SupplierQuoteItem item = entry.getValue();
            products.add(new ComparisonProduct(
                entry.getKey(),
                comparisonProductText(requestItemMap.get(item.getPurchaseRequestItemId()), item),
                awardedPriceByProduct.get(entry.getKey())
            ));
        }
        return products;
    }

    private Map<String, SupplierQuoteItem> comparisonItemMap(List<SupplierQuoteItem> items,
                                                            Map<Long, SupplierQuote> quoteMap) {
        Map<String, SupplierQuoteItem> result = new HashMap<>();
        for (SupplierQuoteItem item : items) {
            SupplierQuote quote = quoteMap.get(item.getQuoteId());
            if (quote == null) {
                continue;
            }
            String key = comparisonProductKey(item) + "|" + comparisonSupplierKey(quote);
            SupplierQuoteItem existing = result.get(key);
            if (existing == null || isBetterComparisonItem(item, existing)) {
                result.put(key, item);
            }
        }
        return result;
    }

    private boolean isBetterComparisonItem(SupplierQuoteItem candidate, SupplierQuoteItem existing) {
        boolean candidateAwarded = Integer.valueOf(1).equals(candidate.getAwarded());
        boolean existingAwarded = Integer.valueOf(1).equals(existing.getAwarded());
        if (candidateAwarded != existingAwarded) {
            return candidateAwarded;
        }
        BigDecimal candidatePrice = effectiveUnitPrice(candidate);
        BigDecimal existingPrice = effectiveUnitPrice(existing);
        if (candidatePrice == null) {
            return false;
        }
        if (existingPrice == null) {
            return true;
        }
        return candidatePrice.compareTo(existingPrice) < 0;
    }

    private String comparisonMatrixCell(SupplierQuoteItem item,
                                        boolean awarded,
                                        BigDecimal awardedUnitPrice) {
        List<String> parts = new ArrayList<>();
        if (awarded) {
            parts.add("中标");
        }
        if (Integer.valueOf(1).equals(item.getReplacementFlag())) {
            parts.add("替换：" + firstText(item.getReplacementItemName(), item.getItemName()));
        }
        parts.add(moneyText(effectiveUnitPrice(item)));
        parts.add("金额 " + moneyText(effectiveLineAmount(item)));
        if (item.getLeadTimeDays() != null) {
            parts.add("交期 " + item.getLeadTimeDays() + " 天");
        }
        String diff = comparisonDiffText(effectiveUnitPrice(item), awardedUnitPrice);
        if (!awarded && StringUtils.hasText(diff)) {
            parts.add(diff);
        }
        return String.join("\n", parts);
    }

    private String comparisonSupplierSubtotal(ComparisonSupplier supplier) {
        List<String> parts = new ArrayList<>();
        parts.add(moneyText(supplier.totalAmount()));
        if (supplier.awardedCount() > 0) {
            parts.add("中标 " + supplier.awardedCount() + " / " + supplier.quotedCount() + " 项");
            parts.add("中标合计 " + moneyText(supplier.awardedAmount()));
        } else {
            parts.add("未中标");
        }
        if (supplier.bestLeadTimeDays() != null) {
            parts.add("最快 " + supplier.bestLeadTimeDays() + " 天");
        }
        return String.join("\n", parts);
    }

    private float[] comparisonWidths(int headerCount) {
        float[] widths = new float[Math.max(1, headerCount)];
        widths[0] = 2.4f;
        for (int i = 1; i < widths.length; i++) {
            widths[i] = 1.8f;
        }
        return widths;
    }

    private String comparisonProductKey(SupplierQuoteItem item) {
        if (item == null) {
            return "";
        }
        if (item.getPurchaseRequestItemId() != null) {
            return "item-" + item.getPurchaseRequestItemId();
        }
        return "line-" + safe(item.getLineNo() == null ? null : String.valueOf(item.getLineNo()));
    }

    private String comparisonSupplierKey(SupplierQuote quote) {
        if (quote == null) {
            return "";
        }
        if (quote.getSupplierId() != null) {
            return "supplier-" + quote.getSupplierId();
        }
        return "quote-" + quote.getId();
    }

    private List<SupplierQuoteItem> comparisonQuoteItems(List<ProcurementOrderVO> orders) {
        Set<Long> requestIds = new LinkedHashSet<>();
        Set<Long> itemIds = new LinkedHashSet<>();
        Set<Integer> lineNos = new LinkedHashSet<>();
        for (ProcurementOrderVO order : orders) {
            if (order.getPurchaseRequestId() != null) {
                requestIds.add(order.getPurchaseRequestId());
            }
            if (order.getPurchaseRequestItemId() != null) {
                itemIds.add(order.getPurchaseRequestItemId());
            }
            if (order.getLineNo() != null) {
                lineNos.add(order.getLineNo());
            }
        }
        if (requestIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<SupplierQuoteItem> wrapper = new LambdaQueryWrapper<SupplierQuoteItem>()
            .in(SupplierQuoteItem::getPurchaseRequestId, requestIds)
            .eq(SupplierQuoteItem::getStatus, "ACTIVE");
        if (!itemIds.isEmpty()) {
            wrapper.in(SupplierQuoteItem::getPurchaseRequestItemId, itemIds);
        } else if (!lineNos.isEmpty()) {
            wrapper.in(SupplierQuoteItem::getLineNo, lineNos);
        }
        return supplierQuoteItemMapper.selectList(wrapper
            .orderByAsc(SupplierQuoteItem::getLineNo)
            .orderByAsc(SupplierQuoteItem::getQuoteId)
            .orderByAsc(SupplierQuoteItem::getId));
    }

    private Map<Long, SupplierQuote> relatedQuotesByQuoteItems(List<SupplierQuoteItem> quoteItems) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SupplierQuoteItem item : quoteItems) {
            if (item.getQuoteId() != null) {
                ids.add(item.getQuoteId());
            }
        }
        return relatedQuotesByIds(ids);
    }

    private Map<Long, Supplier> relatedSuppliers(Collection<SupplierQuote> quotes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SupplierQuote quote : quotes == null ? List.<SupplierQuote>of() : quotes) {
            if (quote.getSupplierId() != null) {
                ids.add(quote.getSupplierId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Supplier> result = new HashMap<>();
        for (Supplier supplier : supplierMapper.selectBatchIds(ids)) {
            result.put(supplier.getId(), supplier);
        }
        return result;
    }

    private Map<Long, PurchaseRequestItem> relatedRequestItems(List<ProcurementOrderVO> orders,
                                                               List<SupplierQuoteItem> quoteItems) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ProcurementOrderVO order : orders == null ? List.<ProcurementOrderVO>of() : orders) {
            if (order.getPurchaseRequestItemId() != null) {
                ids.add(order.getPurchaseRequestItemId());
            }
        }
        for (SupplierQuoteItem item : quoteItems == null ? List.<SupplierQuoteItem>of() : quoteItems) {
            if (item.getPurchaseRequestItemId() != null) {
                ids.add(item.getPurchaseRequestItemId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PurchaseRequestItem> result = new HashMap<>();
        for (PurchaseRequestItem item : purchaseRequestItemMapper.selectBatchIds(ids)) {
            result.put(item.getId(), item);
        }
        return result;
    }

    private List<List<String>> requestRows(List<PurchaseRequest> requests) {
        List<List<String>> rows = new ArrayList<>();
        for (PurchaseRequest request : requests) {
            rows.add(List.of(
                safe(request.getReqNo()),
                safe(request.getReqTitle()),
                requestTypeLabel(request),
                safe(request.getDeptName()),
                dateTimeText(request.getSubmittedAt()),
                dateTimeText(request.getApprovedAt()),
                dateTimeText(request.getQuoteCountdownStartedAt()),
                dateTimeText(request.getQuoteCountdownDueAt())
            ));
        }
        return rows;
    }

    private List<List<String>> delegationRows(List<PurchaseRequest> requests, Map<Long, SysUser> userMap) {
        List<List<String>> rows = new ArrayList<>();
        for (PurchaseRequest request : requests) {
            if (request.getActingUserId() == null) {
                continue;
            }
            rows.add(List.of(
                safe(request.getReqNo()),
                displayUserName(userMap.get(request.getApplicantUserId())),
                firstText(request.getActingUserName(), displayUserName(userMap.get(request.getActingUserId()))),
                firstText(request.getDeptName(), request.getDeptCode()),
                safe(request.getActingReason())
            ));
        }
        return rows;
    }

    private List<List<String>> approvalRows(List<ApprovalRecord> records,
                                            Map<Long, PurchaseRequest> requestMap,
                                            Map<Long, SysUser> userMap) {
        if (records.isEmpty()) {
            return List.of(List.of("", "", "暂无审批记录", "", "", "", "", ""));
        }
        List<List<String>> rows = new ArrayList<>();
        for (ApprovalRecord record : records) {
            PurchaseRequest request = requestMap.get(record.getPurchaseRequestId());
            rows.add(List.of(
                request == null ? String.valueOf(record.getPurchaseRequestId()) : safe(request.getReqNo()),
                record.getSubmitRound() == null ? "" : String.valueOf(record.getSubmitRound()),
                safe(record.getNodeName()),
                record.getApproverUserId() == null ? "" : displayUserName(userMap.get(record.getApproverUserId())),
                approvalActionLabel(record.getAction()),
                approvalStatusLabel(record.getFromStatus()) + " -> " + approvalStatusLabel(record.getToStatus()),
                firstText(record.getApprovalComment(), record.getRemark()),
                dateTimeText(record.getOperatedAt())
            ));
        }
        return rows;
    }

    private List<List<String>> afterSaleRows(List<ProcurementOrderVO> orders) {
        List<List<String>> rows = new ArrayList<>();
        for (ProcurementOrderVO order : orders) {
            if (order.getLatestAfterSaleId() == null && order.getReceivedAt() == null) {
                continue;
            }
            rows.add(List.of(
                safe(order.getOrderNo()),
                receiptStatusLabel(order),
                dateTimeText(order.getReceivedAt()),
                safe(order.getLatestAfterSaleNo()),
                afterSaleTypeLabel(order.getLatestAfterSaleType()),
                afterSaleStatusLabel(order.getLatestAfterSaleStatus()),
                safe(order.getLatestAfterSaleReason()),
                safe(order.getLatestApprovalRemark())
            ));
        }
        return rows;
    }

    private List<PurchaseRequest> relatedRequests(List<ProcurementOrderVO> orders) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ProcurementOrderVO order : orders) {
            if (order.getPurchaseRequestId() != null) {
                ids.add(order.getPurchaseRequestId());
            }
            if (order.getSourceRequestId() != null) {
                ids.add(order.getSourceRequestId());
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return purchaseRequestMapper.selectBatchIds(ids).stream()
            .sorted(Comparator.comparing(PurchaseRequest::getReqNo, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private Map<Long, PurchaseRequest> mapRequests(List<PurchaseRequest> requests) {
        Map<Long, PurchaseRequest> result = new LinkedHashMap<>();
        for (PurchaseRequest request : requests) {
            if (request.getId() != null) {
                result.put(request.getId(), request);
            }
        }
        return result;
    }

    private Map<Long, SupplierQuote> relatedQuotes(List<ProcurementOrderVO> orders) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ProcurementOrderVO order : orders) {
            if (order.getQuoteId() != null) {
                ids.add(order.getQuoteId());
            }
        }
        return relatedQuotesByIds(ids);
    }

    private Map<Long, SupplierQuote> relatedQuotesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SupplierQuote> result = new HashMap<>();
        for (SupplierQuote quote : supplierQuoteMapper.selectBatchIds(ids)) {
            result.put(quote.getId(), quote);
        }
        return result;
    }

    private String comparisonProductText(PurchaseRequestItem requestItem, SupplierQuoteItem quoteItem) {
        String lineNo = quoteItem == null || quoteItem.getLineNo() == null ? "" : quoteItem.getLineNo() + ". ";
        String itemName = firstText(
            requestItem == null ? null : requestItem.getItemName(),
            quoteItem == null ? null : quoteItem.getItemName()
        );
        String spec = firstText(
            requestItem == null ? null : requestItem.getSpecModel(),
            quoteItem == null ? null : quoteItem.getSpecModel()
        );
        String code = firstText(
            requestItem == null ? null : requestItem.getItemCode(),
            quoteItem == null ? null : quoteItem.getItemCode()
        );
        List<String> parts = new ArrayList<>();
        parts.add(lineNo + itemName);
        if (StringUtils.hasText(spec)) {
            parts.add("规格：" + spec);
        }
        if (StringUtils.hasText(code)) {
            parts.add("编码：" + code);
        }
        String qty = decimalText(quoteItem == null ? null : quoteItem.getQty());
        String unit = safe(quoteItem == null ? null : quoteItem.getUnit());
        if (StringUtils.hasText(qty)) {
            parts.add("数量：" + qty + unit);
        }
        return String.join("\n", parts);
    }

    private String comparisonQuotedItemText(SupplierQuoteItem item) {
        if (item == null) {
            return "";
        }
        boolean replacement = Integer.valueOf(1).equals(item.getReplacementFlag())
            || StringUtils.hasText(item.getReplacementItemName())
            || item.getReplacementUnitPrice() != null;
        String itemName = replacement
            ? firstText(item.getReplacementItemName(), item.getItemName())
            : safe(item.getItemName());
        String spec = replacement
            ? firstText(item.getReplacementSpecModel(), item.getSpecModel())
            : safe(item.getSpecModel());
        List<String> parts = new ArrayList<>();
        parts.add((replacement ? "替换：" : "") + itemName);
        if (StringUtils.hasText(spec)) {
            parts.add("规格：" + spec);
        }
        if (replacement && StringUtils.hasText(item.getReplacementReason())) {
            parts.add("原因：" + item.getReplacementReason());
        }
        return String.join("\n", parts);
    }

    private String supplierName(Supplier supplier, SupplierQuote quote) {
        if (supplier != null) {
            return firstText(supplier.getSupplierName(), supplier.getSupplierShortName(), supplier.getSupplierCode());
        }
        if (quote != null && quote.getSupplierId() != null) {
            return "供应商ID " + quote.getSupplierId();
        }
        return "";
    }

    private BigDecimal effectiveUnitPrice(SupplierQuoteItem item) {
        if (item == null) {
            return null;
        }
        if (Integer.valueOf(1).equals(item.getReplacementFlag()) && item.getReplacementUnitPrice() != null) {
            return item.getReplacementUnitPrice();
        }
        return item.getUnitPrice();
    }

    private BigDecimal effectiveLineAmount(SupplierQuoteItem item) {
        if (item == null) {
            return null;
        }
        if (Integer.valueOf(1).equals(item.getReplacementFlag()) && item.getReplacementLineAmount() != null) {
            return item.getReplacementLineAmount();
        }
        return item.getLineAmount();
    }

    private String comparisonDiffText(BigDecimal price, BigDecimal awardedPrice) {
        if (price == null || awardedPrice == null || awardedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        BigDecimal diff = price.subtract(awardedPrice);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return "与中标价相同";
        }
        BigDecimal percent = diff
            .multiply(BigDecimal.valueOf(100))
            .divide(awardedPrice, 1, java.math.RoundingMode.HALF_UP);
        String prefix = diff.compareTo(BigDecimal.ZERO) > 0 ? "较中标 +" : "较中标 ";
        return prefix + moneyText(diff) + "（" + (percent.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + percent + "%）";
    }

    private List<ApprovalRecord> approvalRecords(Set<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return List.of();
        }
        return approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
            .in(ApprovalRecord::getPurchaseRequestId, requestIds)
            .eq(ApprovalRecord::getIsDeleted, 0)
            .orderByAsc(ApprovalRecord::getOperatedAt)
            .orderByAsc(ApprovalRecord::getId));
    }

    private Map<Long, SysUser> relatedUsers(List<ApprovalRecord> records, List<PurchaseRequest> requests) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ApprovalRecord record : records) {
            if (record.getApproverUserId() != null) {
                ids.add(record.getApproverUserId());
            }
            if (record.getTransferToUserId() != null) {
                ids.add(record.getTransferToUserId());
            }
        }
        for (PurchaseRequest request : requests) {
            if (request.getApplicantUserId() != null) {
                ids.add(request.getApplicantUserId());
            }
            if (request.getPurchaserUserId() != null) {
                ids.add(request.getPurchaserUserId());
            }
            if (request.getLeaderConfirmedBy() != null) {
                ids.add(request.getLeaderConfirmedBy());
            }
            if (request.getActingUserId() != null) {
                ids.add(request.getActingUserId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysUser> result = new HashMap<>();
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            result.put(user.getId(), user);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementOrderVO confirmReceipt(Long quoteItemId, OrderReceiptDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ProcurementOrderVO order = requireOrder(quoteItemId);
        ensureCanViewOrder(authUser, order);
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "供应商账号不能确认收货");
        }
        if (!"CONFIRMED".equals(order.getSupplierConfirmStatus())) {
            throw new BizException("ORDER_NOT_CONFIRMED", "供应商确认中标后才能确认收货");
        }
        if (RECEIPT_STATUS_RECEIVED.equals(order.getReceiptStatus())) {
            return order;
        }
        SupplierQuoteItem item = supplierQuoteItemMapper.selectById(quoteItemId);
        if (item == null) {
            throw new BizException("ORDER_NOT_FOUND", "订单不存在");
        }
        item.setReceiptStatus(RECEIPT_STATUS_RECEIVED);
        item.setReceivedBy(authUser.getUserId());
        item.setReceivedAt(LocalDateTime.now());
        item.setReceiptRemark(dto == null ? null : normalizeBlank(dto.getReceiptRemark()));
        item.setAutoReceived(0);
        supplierQuoteItemMapper.updateById(item);
        return orderDetail(quoteItemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO createAfterSale(Long quoteItemId, AfterSaleCreateDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "供应商账号不能发起售后申请");
        }
        ProcurementOrderVO order = requireOrder(quoteItemId);
        ensureCanViewOrder(authUser, order);
        if (!"CONFIRMED".equals(order.getSupplierConfirmStatus())) {
            throw new BizException("ORDER_NOT_CONFIRMED", "供应商确认中标后才能发起售后");
        }
        if (!RECEIPT_STATUS_RECEIVED.equals(order.getReceiptStatus())) {
            throw new BizException("ORDER_RECEIPT_REQUIRED", "确认收货后才能发起售后");
        }
        ProcurementAfterSale activeAfterSale = getOne(new LambdaQueryWrapper<ProcurementAfterSale>()
            .eq(ProcurementAfterSale::getQuoteItemId, quoteItemId)
            .in(ProcurementAfterSale::getStatus, List.of(STATUS_PENDING, STATUS_PROPOSAL_PENDING, STATUS_APPROVED))
            .orderByDesc(ProcurementAfterSale::getId)
            .last("LIMIT 1"));
        if (activeAfterSale != null) {
            if (STATUS_APPROVED.equals(activeAfterSale.getStatus())) {
                throw new BizException("AFTER_SALE_APPROVED_EXISTS", "该订单售后已通过，不能重复申请售后");
            }
            throw new BizException("AFTER_SALE_PENDING_EXISTS", "该订单已有处理中售后申请");
        }
        ensureAfterSaleWindow(order, dto.getAfterSaleType());
        if (dto.getRequestedQty().compareTo(order.getQty()) > 0) {
            throw new BizException("AFTER_SALE_QTY_INVALID", "售后数量不能超过订单数量");
        }
        String type = normalizeAfterSaleType(dto.getAfterSaleType());
        if (TYPE_REPLACE_ITEM.equals(type) && !StringUtils.hasText(dto.getReplacementItemName())) {
            throw new BizException("AFTER_SALE_REPLACEMENT_REQUIRED", "换货售后必须填写更换后的商品名称");
        }

        ProcurementAfterSale sale = new ProcurementAfterSale();
        sale.setAfterSaleNo(nextAfterSaleNo());
        sale.setPurchaseRequestId(order.getPurchaseRequestId());
        sale.setPurchaseRequestItemId(order.getPurchaseRequestItemId());
        sale.setQuoteId(order.getQuoteId());
        sale.setQuoteItemId(order.getQuoteItemId());
        sale.setSupplierId(order.getSupplierId());
        sale.setRequesterUserId(authUser.getUserId());
        sale.setAfterSaleType(type);
        sale.setReason(dto.getReason().trim());
        sale.setRequestedQty(dto.getRequestedQty());
        sale.setReplacementItemCode(normalizeBlank(dto.getReplacementItemCode()));
        sale.setReplacementItemName(normalizeBlank(dto.getReplacementItemName()));
        sale.setReplacementSpecModel(normalizeBlank(dto.getReplacementSpecModel()));
        sale.setReplacementUnit(normalizeBlank(dto.getReplacementUnit()));
        sale.setReplacementRemark(normalizeBlank(dto.getReplacementRemark()));
        sale.setStatus(STATUS_PENDING);
        save(sale);
        return toAfterSaleVO(sale, order);
    }

    @Override
    public PageResult<AfterSaleVO> pageAfterSales(ProcurementOrderPageQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        LambdaQueryWrapper<ProcurementAfterSale> wrapper = new LambdaQueryWrapper<ProcurementAfterSale>()
            .orderByDesc(ProcurementAfterSale::getCreatedAt)
            .orderByDesc(ProcurementAfterSale::getId);
        String status = normalizeBlank(queryDTO.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProcurementAfterSale::getStatus, normalizeStatus(status));
        }
        if (!SupplierSoldOrderAccess.canManageAll(authUser)) {
            if (DataScopeHelper.isSupplierUser(authUser)) {
                wrapper.eq(ProcurementAfterSale::getSupplierId, requireBoundSupplier(authUser));
            } else if (!DataScopeHelper.isSystemAdmin(authUser)) {
                wrapper.eq(ProcurementAfterSale::getRequesterUserId, authUser.getUserId());
            }
        }
        String keyword = normalizeBlank(queryDTO.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(keywordWrapper -> keywordWrapper
                .like(ProcurementAfterSale::getAfterSaleNo, keyword)
                .or()
                .exists(AFTER_SALE_KEYWORD_ORDER_EXISTS_SQL, keyword));
        }
        Page<ProcurementAfterSale> page = page(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<AfterSaleVO> records = page.getRecords().stream()
            .map(sale -> toAfterSaleVO(sale, orderQueryMapper.selectOrderRowByQuoteItemId(sale.getQuoteItemId())))
            .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO approveAfterSale(Long id, AfterSaleApprovalDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ProcurementAfterSale sale = getById(id);
        if (sale == null) {
            throw new BizException("AFTER_SALE_NOT_FOUND", "售后申请不存在");
        }
        ProcurementOrderVO order = requireOrder(sale.getQuoteItemId());
        String action = normalizeStatus(dto.getAction());
        String status = normalizeStatus(sale.getStatus());
        if (STATUS_PENDING.equals(status)) {
            handleSupplierAfterSaleProposal(authUser, sale, order, dto, action);
        } else if (STATUS_PROPOSAL_PENDING.equals(status)) {
            handleRequesterAfterSaleConfirmation(authUser, sale, order, dto, action);
        } else {
            throw new BizException("AFTER_SALE_ALREADY_REVIEWED", "售后申请已经处理");
        }
        return toAfterSaleVO(sale, order);
    }

    private void handleSupplierAfterSaleProposal(AuthUser authUser,
                                                 ProcurementAfterSale sale,
                                                 ProcurementOrderVO order,
                                                 AfterSaleApprovalDTO dto,
                                                 String action) {
        if (!canApproveAfterSale(authUser, order)) {
            throw new BizException("FORBIDDEN", "当前账号不能处理该售后申请");
        }
        if (isRejectAction(action)) {
            sale.setStatus(STATUS_REJECTED);
            sale.setApproverUserId(authUser.getUserId());
            sale.setApprovedAt(LocalDateTime.now());
            sale.setApprovalRemark(normalizeBlank(dto.getApprovalRemark()));
            updateById(sale);
            return;
        }
        if (!isApproveAction(action) && !"PROPOSE".equals(action)) {
            throw new BizException("AFTER_SALE_ACTION_INVALID", "售后处理动作不合法");
        }
        String proposalRemark = firstText(dto.getProposalRemark(), dto.getApprovalRemark());
        String replacementItemName = firstText(dto.getReplacementItemName(), sale.getReplacementItemName());
        String proposalType = normalizeAfterSaleType(firstText(dto.getProposalType(), sale.getAfterSaleType()));
        boolean replacementProposal = TYPE_REPLACE_ITEM.equals(proposalType) || StringUtils.hasText(replacementItemName);
        if (!StringUtils.hasText(proposalRemark) && !replacementProposal) {
            throw new BizException("AFTER_SALE_PROPOSAL_REQUIRED", "请填写供应商处理方案");
        }
        BigDecimal unitPrice = resolveProposalUnitPrice(dto, sale);
        BigDecimal lineAmount = resolveProposalLineAmount(dto, sale, unitPrice);
        if (replacementProposal) {
            if (!StringUtils.hasText(replacementItemName)) {
                throw new BizException("AFTER_SALE_REPLACEMENT_REQUIRED", "更换商品方案必须填写新商品名称");
            }
            if (unitPrice == null) {
                throw new BizException("AFTER_SALE_PRICE_REQUIRED", "更换商品方案必须填写方案单价");
            }
        }
        validateAfterSaleProposalPrice(order, unitPrice);

        LocalDateTime now = LocalDateTime.now();
        sale.setStatus(STATUS_PROPOSAL_PENDING);
        sale.setSupplierProposalType(proposalType);
        sale.setSupplierProposalRemark(normalizeBlank(proposalRemark));
        sale.setSupplierReplacementItemCode(firstTextOrNull(dto.getReplacementItemCode(), sale.getReplacementItemCode()));
        sale.setSupplierReplacementItemName(normalizeBlank(replacementItemName));
        sale.setSupplierReplacementSpecModel(firstTextOrNull(dto.getReplacementSpecModel(), sale.getReplacementSpecModel()));
        sale.setSupplierReplacementUnit(firstTextOrNull(dto.getReplacementUnit(), sale.getReplacementUnit(), order == null ? null : order.getUnit()));
        sale.setSupplierReplacementUnitPrice(unitPrice);
        sale.setSupplierReplacementLineAmount(lineAmount);
        sale.setSupplierProposedAt(now);
        sale.setApproverUserId(authUser.getUserId());
        sale.setApprovalRemark(normalizeBlank(proposalRemark));
        updateById(sale);
    }

    private void handleRequesterAfterSaleConfirmation(AuthUser authUser,
                                                       ProcurementAfterSale sale,
                                                       ProcurementOrderVO order,
                                                       AfterSaleApprovalDTO dto,
                                                       String action) {
        if (!canConfirmAfterSaleProposal(authUser, sale, order)) {
            throw new BizException("FORBIDDEN", "当前账号不能确认该售后方案");
        }
        if (isApproveAction(action) || "ACCEPT_PROPOSAL".equals(action)) {
            sale.setStatus(STATUS_APPROVED);
            sale.setRequesterConfirmUserId(authUser.getUserId());
            sale.setRequesterConfirmedAt(LocalDateTime.now());
            sale.setRequesterConfirmRemark(normalizeBlank(dto.getApprovalRemark()));
            sale.setApprovedAt(LocalDateTime.now());
            sale.setApprovalRemark(normalizeBlank(dto.getApprovalRemark()));
            updateById(sale);
            applyApprovedAfterSaleEffects(sale);
            return;
        }
        if (isRejectAction(action) || "REJECT_PROPOSAL".equals(action)) {
            sale.setStatus(STATUS_PENDING);
            sale.setRequesterConfirmUserId(authUser.getUserId());
            sale.setRequesterConfirmedAt(LocalDateTime.now());
            sale.setRequesterConfirmRemark(normalizeBlank(dto.getApprovalRemark()));
            sale.setApprovalRemark(normalizeBlank(dto.getApprovalRemark()));
            updateById(sale);
            return;
        }
        throw new BizException("AFTER_SALE_ACTION_INVALID", "售后方案确认动作不合法");
    }

    private boolean isApproveAction(String action) {
        return "APPROVE".equals(action) || STATUS_APPROVED.equals(action);
    }

    private boolean isRejectAction(String action) {
        return "REJECT".equals(action) || STATUS_REJECTED.equals(action);
    }

    private BigDecimal resolveProposalUnitPrice(AfterSaleApprovalDTO dto, ProcurementAfterSale sale) {
        BigDecimal unitPrice = dto.getReplacementUnitPrice();
        if (unitPrice == null && dto.getReplacementLineAmount() != null && positive(sale.getRequestedQty())) {
            unitPrice = dto.getReplacementLineAmount().divide(sale.getRequestedQty(), 4, java.math.RoundingMode.HALF_UP);
        }
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("AFTER_SALE_PRICE_INVALID", "方案单价必须大于0");
        }
        return unitPrice;
    }

    private BigDecimal resolveProposalLineAmount(AfterSaleApprovalDTO dto, ProcurementAfterSale sale, BigDecimal unitPrice) {
        BigDecimal lineAmount = dto.getReplacementLineAmount();
        if (lineAmount != null && lineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("AFTER_SALE_AMOUNT_INVALID", "方案金额必须大于0");
        }
        if (lineAmount == null && unitPrice != null && positive(sale.getRequestedQty())) {
            lineAmount = unitPrice.multiply(sale.getRequestedQty()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return lineAmount;
    }

    private void validateAfterSaleProposalPrice(ProcurementOrderVO order, BigDecimal proposalUnitPrice) {
        if (proposalUnitPrice == null || order == null || order.getUnitPrice() == null) {
            return;
        }
        BigDecimal maxAllowed = order.getUnitPrice().add(AFTER_SALE_UNIT_PRICE_INCREASE_LIMIT);
        if (proposalUnitPrice.compareTo(maxAllowed) > 0) {
            throw new BizException("AFTER_SALE_PRICE_LIMIT_EXCEEDED", "方案单价最多只能比现价高400元");
        }
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private void applyApprovedAfterSaleEffects(ProcurementAfterSale sale) {
        if (TYPE_REPLACE_ITEM.equals(sale.getSupplierProposalType())) {
            return;
        }
        if (!isReturnAfterSaleType(sale.getAfterSaleType())) {
            return;
        }
        PurchaseRequestItem item = sale.getPurchaseRequestItemId() == null
            ? null
            : purchaseRequestItemMapper.selectById(sale.getPurchaseRequestItemId());
        if (item == null) {
            recalculatePurchaseRequestTotal(sale.getPurchaseRequestId());
            return;
        }
        if (!ProcurementWorkflow.ITEM_STATUS_RETURNED.equals(item.getStatus())) {
            item.setStatus(ProcurementWorkflow.ITEM_STATUS_RETURNED);
            item.setRemark(appendRemark(item.getRemark(), afterSaleReturnRemark(sale)));
            purchaseRequestItemMapper.updateById(item);
        }
        recalculatePurchaseRequestTotal(item.getPurchaseRequestId());
    }

    private boolean isReturnAfterSaleType(String type) {
        return TYPE_NO_REASON_RETURN.equals(type) || TYPE_RETURN_EXCHANGE.equals(type);
    }

    private String afterSaleReturnRemark(ProcurementAfterSale sale) {
        String reason = normalizeBlank(sale.getReason());
        if (StringUtils.hasText(reason)) {
            return "售后退货通过：" + sale.getAfterSaleNo() + "，原因：" + reason;
        }
        return "售后退货通过：" + sale.getAfterSaleNo();
    }

    private String appendRemark(String oldRemark, String appendText) {
        if (!StringUtils.hasText(oldRemark)) {
            return appendText;
        }
        if (oldRemark.contains(appendText)) {
            return oldRemark;
        }
        return oldRemark + "；" + appendText;
    }

    private void recalculatePurchaseRequestTotal(Long requestId) {
        if (requestId == null) {
            return;
        }
        List<PurchaseRequestItem> items = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, requestId));
        BigDecimal total = items.stream()
            .filter(item -> !ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(item.getStatus()))
            .filter(item -> !ProcurementWorkflow.ITEM_STATUS_RETURNED.equals(item.getStatus()))
            .map(PurchaseRequestItem::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, java.math.RoundingMode.HALF_UP);
        PurchaseRequest request = purchaseRequestMapper.selectById(requestId);
        if (request == null) {
            return;
        }
        request.setTotalAmount(total);
        purchaseRequestMapper.updateById(request);
    }

    private ProcurementOrderVO requireOrder(Long quoteItemId) {
        ProcurementOrderVO order = orderQueryMapper.selectOrderRowByQuoteItemId(quoteItemId);
        if (order == null) {
            throw new BizException("ORDER_NOT_FOUND", "订单不存在或尚未确认中标");
        }
        decorateAfterSaleWindows(order);
        return order;
    }

    private void decorateAfterSaleWindows(ProcurementOrderVO order) {
        if (order == null || order.getReceivedAt() == null) {
            if (order != null) {
                order.setAfterSaleAllowed(false);
                order.setNoReasonReturnAvailable(false);
                order.setReturnExchangeAvailable(false);
            }
            return;
        }
        String latestAfterSaleStatus = order.getLatestAfterSaleStatus();
        if (STATUS_PENDING.equals(latestAfterSaleStatus)
            || STATUS_PROPOSAL_PENDING.equals(latestAfterSaleStatus)
            || STATUS_APPROVED.equals(latestAfterSaleStatus)) {
            order.setAfterSaleAllowed(false);
            order.setNoReasonReturnAvailable(false);
            order.setReturnExchangeAvailable(false);
            return;
        }
        int noReasonDays = intSetting("after_sale_no_reason_days", 7);
        int returnExchangeDays = intSetting("after_sale_return_exchange_days", 14);
        LocalDateTime noReasonDeadline = order.getReceivedAt().plusDays(Math.max(0, noReasonDays));
        LocalDateTime returnExchangeDeadline = order.getReceivedAt().plusDays(Math.max(0, returnExchangeDays));
        LocalDateTime now = LocalDateTime.now();
        boolean noReasonAvailable = booleanSetting("after_sale_no_reason_enabled", true) && !now.isAfter(noReasonDeadline);
        boolean returnExchangeAvailable = booleanSetting("after_sale_return_exchange_enabled", true) && !now.isAfter(returnExchangeDeadline);
        order.setNoReasonReturnDeadline(noReasonDeadline);
        order.setReturnExchangeDeadline(returnExchangeDeadline);
        order.setNoReasonReturnAvailable(noReasonAvailable);
        order.setReturnExchangeAvailable(returnExchangeAvailable);
        order.setAfterSaleAllowed(noReasonAvailable || returnExchangeAvailable);
    }

    private boolean decorateOrder(ProcurementOrderVO order, AuthUser authUser) {
        decorateAfterSaleWindows(order);
        return orderInvoiceService.decorate(order, authUser);
    }

    private boolean decorateOrder(ProcurementOrderVO order, AuthUser authUser, boolean reviewer) {
        decorateAfterSaleWindows(order);
        return orderInvoiceService.decorate(order, authUser, reviewer);
    }

    private InternalOrderSlice scanInternalOrders(String keyword,
                                                  String status,
                                                  String requestNo,
                                                  Long internalUserId,
                                                  AuthUser authUser,
                                                  long visibleOffset,
                                                  long visibleLimit) {
        List<ProcurementOrderVO> records = new ArrayList<>();
        Map<Long, Boolean> reviewerByRequestId = new HashMap<>();
        long candidateOffset = 0;
        long visibleTotal = 0;
        while (true) {
            List<ProcurementOrderVO> candidates = orderQueryMapper.selectOrderRows(
                keyword,
                status,
                requestNo,
                null,
                internalUserId,
                candidateOffset,
                INTERNAL_ORDER_SCAN_BATCH_SIZE
            );
            for (ProcurementOrderVO order : candidates) {
                boolean originalViewer = orderInvoiceService.canViewOriginalOrder(authUser, order);
                Boolean reviewer = null;
                if (!originalViewer) {
                    reviewer = resolveInvoiceReviewer(order, authUser, reviewerByRequestId);
                    if (!reviewer) {
                        continue;
                    }
                }
                if (visibleTotal >= visibleOffset && records.size() < visibleLimit) {
                    if (reviewer == null) {
                        reviewer = resolveInvoiceReviewer(order, authUser, reviewerByRequestId);
                    }
                    decorateOrder(order, authUser, reviewer);
                    records.add(order);
                }
                visibleTotal++;
            }
            candidateOffset += candidates.size();
            if (candidates.size() < INTERNAL_ORDER_SCAN_BATCH_SIZE) {
                break;
            }
        }
        return new InternalOrderSlice(visibleTotal, records);
    }

    private boolean resolveInvoiceReviewer(ProcurementOrderVO order,
                                           AuthUser authUser,
                                           Map<Long, Boolean> reviewerByRequestId) {
        Long requestId = order.getSourceRequestId() == null ? order.getPurchaseRequestId() : order.getSourceRequestId();
        if (requestId == null) {
            return orderInvoiceService.canReviewInvoice(authUser, order);
        }
        return reviewerByRequestId.computeIfAbsent(
            requestId,
            ignored -> orderInvoiceService.canReviewInvoice(authUser, order)
        );
    }

    private record InternalOrderSlice(long total, List<ProcurementOrderVO> records) {
    }

    private void ensureAfterSaleWindow(ProcurementOrderVO order, String requestedType) {
        decorateAfterSaleWindows(order);
        String type = normalizeAfterSaleType(requestedType);
        if (TYPE_NO_REASON_RETURN.equals(type)) {
            if (!Boolean.TRUE.equals(order.getNoReasonReturnAvailable())) {
                throw new BizException("AFTER_SALE_WINDOW_CLOSED", "7天无理由退货窗口已关闭");
            }
            return;
        }
        if (!Boolean.TRUE.equals(order.getReturnExchangeAvailable())) {
            throw new BizException("AFTER_SALE_WINDOW_CLOSED", "14天退换货窗口已关闭");
        }
    }

    private void ensureCanViewOrder(AuthUser authUser, ProcurementOrderVO order) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        if (SupplierSoldOrderAccess.canOperate(authUser, order)) {
            return;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "不能查看其他供应商订单");
        }
        if (Objects.equals(authUser.getUserId(), order.getApplicantUserId())
            || Objects.equals(authUser.getUserId(), order.getPurchaserUserId())
            || orderInvoiceService.canOperateAsSourceAssistant(authUser, order)) {
            return;
        }
        throw new BizException("FORBIDDEN", "不能查看他人的订单");
    }

    private boolean canApproveAfterSale(AuthUser authUser, ProcurementOrderVO order) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        return SupplierSoldOrderAccess.canOperate(authUser, order);
    }

    private boolean canConfirmAfterSaleProposal(AuthUser authUser, ProcurementAfterSale sale, ProcurementOrderVO order) {
        if (authUser == null || sale == null) {
            return false;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return false;
        }
        Long userId = authUser.getUserId();
        return Objects.equals(userId, sale.getRequesterUserId())
            || (order != null && (Objects.equals(userId, order.getApplicantUserId())
            || Objects.equals(userId, order.getPurchaserUserId())
            || orderInvoiceService.canOperateAsSourceAssistant(authUser, order)));
    }

    private ScopeFilter resolveOrderScope(AuthUser authUser) {
        if (SupplierSoldOrderAccess.canManageAll(authUser)) {
            return new ScopeFilter(null, null);
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            Long supplierId = requireBoundSupplier(authUser);
            return new ScopeFilter(supplierId, null);
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return new ScopeFilter(null, null);
        }
        return new ScopeFilter(null, authUser.getUserId());
    }

    private Long requireBoundSupplier(AuthUser authUser) {
        Long supplierId = authUser == null ? null : authUser.getSupplierId();
        if (supplierId == null) {
            throw new BizException("SUPPLIER_ACCOUNT_INVALID", "供应商账号未绑定供应商主档");
        }
        return supplierId;
    }

    private AfterSaleVO toAfterSaleVO(ProcurementAfterSale sale, ProcurementOrderVO order) {
        AfterSaleVO vo = new AfterSaleVO();
        vo.setId(sale.getId());
        vo.setAfterSaleNo(sale.getAfterSaleNo());
        vo.setPurchaseRequestId(sale.getPurchaseRequestId());
        vo.setPurchaseRequestItemId(sale.getPurchaseRequestItemId());
        vo.setQuoteId(sale.getQuoteId());
        vo.setQuoteItemId(sale.getQuoteItemId());
        vo.setSupplierId(sale.getSupplierId());
        vo.setRequesterUserId(sale.getRequesterUserId());
        vo.setAfterSaleType(sale.getAfterSaleType());
        vo.setReason(sale.getReason());
        vo.setRequestedQty(sale.getRequestedQty());
        vo.setReplacementItemCode(sale.getReplacementItemCode());
        vo.setReplacementItemName(sale.getReplacementItemName());
        vo.setReplacementSpecModel(sale.getReplacementSpecModel());
        vo.setReplacementUnit(sale.getReplacementUnit());
        vo.setReplacementRemark(sale.getReplacementRemark());
        vo.setSupplierProposalType(sale.getSupplierProposalType());
        vo.setSupplierProposalRemark(sale.getSupplierProposalRemark());
        vo.setSupplierReplacementItemCode(sale.getSupplierReplacementItemCode());
        vo.setSupplierReplacementItemName(sale.getSupplierReplacementItemName());
        vo.setSupplierReplacementSpecModel(sale.getSupplierReplacementSpecModel());
        vo.setSupplierReplacementUnit(sale.getSupplierReplacementUnit());
        vo.setSupplierReplacementUnitPrice(sale.getSupplierReplacementUnitPrice());
        vo.setSupplierReplacementLineAmount(sale.getSupplierReplacementLineAmount());
        vo.setSupplierProposedAt(sale.getSupplierProposedAt());
        vo.setRequesterConfirmUserId(sale.getRequesterConfirmUserId());
        vo.setRequesterConfirmedAt(sale.getRequesterConfirmedAt());
        vo.setRequesterConfirmRemark(sale.getRequesterConfirmRemark());
        vo.setStatus(sale.getStatus());
        vo.setApproverUserId(sale.getApproverUserId());
        vo.setApprovedAt(sale.getApprovedAt());
        vo.setApprovalRemark(sale.getApprovalRemark());
        vo.setCreatedAt(sale.getCreatedAt());
        vo.setUpdatedAt(sale.getUpdatedAt());
        if (order != null) {
            vo.setSupplierName(order.getSupplierName());
            vo.setOrderNo(order.getOrderNo());
            vo.setReqNo(order.getReqNo());
            vo.setReqTitle(order.getReqTitle());
            vo.setItemCode(order.getItemCode());
            vo.setItemName(order.getItemName());
            vo.setSpecModel(order.getSpecModel());
            vo.setUnit(order.getUnit());
            vo.setOrderQty(order.getQty());
            vo.setOrderAmount(order.getLineAmount());
            vo.setReceivingCity(order.getReceivingCity());
            vo.setDeliveryAddress(order.getDeliveryAddress());
        }
        SysUser requester = sale.getRequesterUserId() == null ? null : userMapper.selectById(sale.getRequesterUserId());
        vo.setRequesterName(displayUserName(requester));
        SysUser approver = sale.getApproverUserId() == null ? null : userMapper.selectById(sale.getApproverUserId());
        vo.setApproverName(displayUserName(approver));
        SysUser requesterConfirmUser = sale.getRequesterConfirmUserId() == null ? null : userMapper.selectById(sale.getRequesterConfirmUserId());
        vo.setRequesterConfirmName(displayUserName(requesterConfirmUser));
        return vo;
    }

    private String nextAfterSaleNo() {
        return "SH" + LocalDateTime.now().format(AFTER_SALE_NO_TIME) + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private String normalizeAfterSaleType(String type) {
        String normalized = normalizeBlank(type);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException("AFTER_SALE_TYPE_INVALID", "售后类型不能为空");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (TYPE_NO_REASON_RETURN.equals(upper)
            || TYPE_RETURN_EXCHANGE.equals(upper)
            || TYPE_REPLACE_ITEM.equals(upper)
            || "REPAIR".equals(upper)
            || "OTHER".equals(upper)) {
            return upper;
        }
        throw new BizException("AFTER_SALE_TYPE_INVALID", "售后类型不合法");
    }

    private int intSetting(String key, int defaultValue) {
        ProcurementSetting setting = procurementSettingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, key)
            .last("LIMIT 1"));
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(setting.getSettingValue().trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean booleanSetting(String key, boolean defaultValue) {
        ProcurementSetting setting = procurementSettingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, key)
            .last("LIMIT 1"));
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return defaultValue;
        }
        return Boolean.parseBoolean(setting.getSettingValue().trim());
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String displayUserName(SysUser user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        return safe(user.getUsername());
    }

    private String displayGroupNo(List<ProcurementOrderVO> orders) {
        if (orders == null || orders.isEmpty()) {
            return "";
        }
        ProcurementOrderVO first = orders.get(0);
        return firstText(first.getAggregateReqNo(), first.getReqNo(), first.getOrderNo());
    }

    private String orderGroupType(List<ProcurementOrderVO> orders) {
        String groupNo = displayGroupNo(orders);
        if (groupNo.startsWith("HZCG-")) {
            return "集中采购";
        }
        if (groupNo.startsWith("JJCG-") || orders.stream().anyMatch(this::isUrgentOrder)) {
            return "紧急采购";
        }
        return "普通采购";
    }

    private boolean isUrgentOrder(ProcurementOrderVO order) {
        if (order == null) {
            return false;
        }
        String text = String.join(" ",
            safe(order.getReqNo()),
            safe(order.getSourceReqNo()),
            safe(order.getReqTitle()),
            safe(order.getSourceReqTitle()),
            safe(order.getReqType()),
            safe(order.getSourceReqType()));
        return text.contains("JJCG-") || text.contains("紧急");
    }

    private String groupStatusSummary(List<ProcurementOrderVO> orders) {
        if (orders == null || orders.isEmpty()) {
            return "";
        }
        if (orders.stream().anyMatch(order -> "SUPPLIER_CONFIRM_PENDING".equals(order.getOrderStatus()))) {
            return "待供应商确认";
        }
        long waiting = orders.stream().filter(order -> "WAITING_RECEIPT".equals(order.getOrderStatus())).count();
        if (waiting > 0) {
            return "待收货 " + waiting + " 项";
        }
        long afterSale = orders.stream().filter(order -> order.getLatestAfterSaleId() != null).count();
        if (afterSale > 0) {
            return "售后记录 " + afterSale + " 单";
        }
        return "已完成";
    }

    private String requestTypeLabel(PurchaseRequest request) {
        if (request == null) {
            return "";
        }
        if (StringUtils.hasText(request.getReqType())) {
            return request.getReqType();
        }
        String reqNo = safe(request.getReqNo());
        if (reqNo.startsWith("HZCG-")) {
            return "集中采购";
        }
        if (reqNo.startsWith("JJCG-")) {
            return "紧急采购";
        }
        return "普通采购";
    }

    private String approvalActionLabel(String action) {
        return switch (safe(action)) {
            case ProcurementWorkflow.ACTION_SUBMIT -> "提交";
            case ProcurementWorkflow.ACTION_APPROVE -> "同意";
            case ProcurementWorkflow.ACTION_RETURN -> "退回";
            case ProcurementWorkflow.ACTION_REJECT -> "驳回";
            default -> safe(action);
        };
    }

    private String approvalStatusLabel(String status) {
        return switch (safe(status)) {
            case ProcurementWorkflow.APPROVAL_PENDING -> "审批中";
            case ProcurementWorkflow.APPROVAL_APPROVED -> "已通过";
            case ProcurementWorkflow.APPROVAL_RETURNED -> "已退回";
            case ProcurementWorkflow.APPROVAL_REJECTED -> "已驳回";
            default -> safe(status);
        };
    }

    private String joinUnique(List<String> values) {
        List<String> normalized = values == null ? List.of() : values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.size() <= 3) {
            return String.join("、", normalized);
        }
        return String.join("、", normalized.subList(0, 3)) + " 等 " + normalized.size() + " 项";
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

    private String firstTextOrNull(String... values) {
        String text = firstText(values);
        return StringUtils.hasText(text) ? text : null;
    }

    private String orderStatusLabel(String status) {
        return switch (safe(status)) {
            case "SUPPLIER_CONFIRM_PENDING" -> "待供应商确认";
            case "SUPPLIER_CONFIRM_WAIVED" -> "供应商已放弃";
            case "WAITING_RECEIPT" -> "待收货";
            case "AFTER_SALE_PENDING" -> "售后处理中";
            case "AFTER_SALE_PROPOSAL_PENDING" -> "售后方案待确认";
            case "AFTER_SALE_APPROVED" -> "售后已通过";
            case "AFTER_SALE_REJECTED" -> "售后已驳回";
            case "COMPLETED" -> "已完成";
            default -> "已完成";
        };
    }

    private String supplierConfirmStatusLabel(String status) {
        return switch (safe(status)) {
            case "PENDING" -> "待确认";
            case "CONFIRMED" -> "已确认";
            case "WAIVED" -> "已放弃";
            default -> "";
        };
    }

    private String receiptStatusLabel(ProcurementOrderVO order) {
        if (order == null) {
            return "";
        }
        if (RECEIPT_STATUS_RECEIVED.equals(order.getReceiptStatus())) {
            return Integer.valueOf(1).equals(order.getAutoReceived()) ? "已收货（系统自动）" : "已收货";
        }
        if ("CONFIRMED".equals(order.getSupplierConfirmStatus())) {
            return "待收货";
        }
        return "";
    }

    private String afterSaleTypeLabel(String type) {
        return switch (safe(type)) {
            case TYPE_NO_REASON_RETURN -> "7天无理由退货";
            case TYPE_RETURN_EXCHANGE -> "14天退换货";
            case TYPE_REPLACE_ITEM -> "更换商品";
            case "REPAIR" -> "维修/退换";
            case "OTHER" -> "其他售后";
            default -> "";
        };
    }

    private String afterSaleStatusLabel(String status) {
        return switch (safe(status)) {
            case STATUS_PENDING -> "待审核";
            case STATUS_PROPOSAL_PENDING -> "方案待确认";
            case STATUS_APPROVED -> "已通过";
            case STATUS_REJECTED -> "已驳回";
            default -> "";
        };
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String moneyText(BigDecimal value) {
        return value == null ? "" : "¥" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String dateTimeText(LocalDateTime value) {
        return value == null ? "" : value.format(EXPORT_TIME);
    }

    private String sanitizeFileName(String fileName) {
        return safe(fileName).replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record QuoteComparison(List<String> headers,
                                   float[] widths,
                                   List<List<String>> rows,
                                   List<String> highlightedCells,
                                   int itemCount,
                                   int supplierCount,
                                   int quoteItemCount,
                                   int awardedItemCount,
                                   BigDecimal awardedTotal,
                                   List<String> awardedSupplierNames,
                                   Integer fastestAwardLeadTimeDays) {

        private static QuoteComparison empty() {
            return new QuoteComparison(List.of(), new float[] {1.0f}, List.of(), List.of(), 0, 0, 0, 0, BigDecimal.ZERO, List.of(), null);
        }
    }

    private record ComparisonSupplier(String key,
                                      String name,
                                      BigDecimal totalAmount,
                                      BigDecimal awardedAmount,
                                      int quotedCount,
                                      int awardedCount,
                                      Integer bestLeadTimeDays) {
    }

    private record ComparisonProduct(String key,
                                     String description,
                                     BigDecimal awardedUnitPrice) {
    }

    private record ScopeFilter(Long supplierId, Long internalUserId) {
    }
}
