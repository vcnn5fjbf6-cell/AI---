package com.company.procurement.modules.procurement.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.procurement.order.dto.AfterSaleApprovalDTO;
import com.company.procurement.modules.procurement.order.dto.AfterSaleCreateDTO;
import com.company.procurement.modules.procurement.order.dto.OrderReceiptDTO;
import com.company.procurement.modules.procurement.order.dto.ProcurementOrderPageQueryDTO;
import com.company.procurement.modules.procurement.order.entity.ProcurementAfterSale;
import com.company.procurement.modules.procurement.order.mapper.ProcurementAfterSaleMapper;
import com.company.procurement.modules.procurement.order.mapper.ProcurementOrderQueryMapper;
import com.company.procurement.modules.procurement.order.service.OrderInvoiceService;
import com.company.procurement.modules.procurement.order.vo.AfterSaleVO;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcurementOrderServiceImplRegressionTest {

    @Mock
    private ProcurementAfterSaleMapper afterSaleMapper;
    @Mock
    private ProcurementOrderQueryMapper orderQueryMapper;
    @Mock
    private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private SupplierQuoteMapper supplierQuoteMapper;
    @Mock
    private ProcurementSettingMapper procurementSettingMapper;
    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;
    @Mock
    private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock
    private ApprovalRecordMapper approvalRecordMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrderInvoiceService orderInvoiceService;

    private ProcurementOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProcurementOrderServiceImpl(
            orderQueryMapper,
            supplierQuoteItemMapper,
            supplierMapper,
            supplierQuoteMapper,
            procurementSettingMapper,
            purchaseRequestMapper,
            purchaseRequestItemMapper,
            approvalRecordMapper,
            userMapper,
            orderInvoiceService
        );
        ReflectionTestUtils.setField(service, "baseMapper", afterSaleMapper);
        AuthContextHolder.set(AuthUser.builder()
            .userId(1L)
            .username("admin")
            .roleCodes(List.of("ADMIN"))
            .build());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void approvalRowsAllowSystemRecordWithoutApprover() {
        ApprovalRecord record = new ApprovalRecord();
        record.setPurchaseRequestId(51L);
        record.setSubmitRound(1);
        record.setNodeName("需求池汇总直达报价");
        record.setAction("SUBMIT");
        record.setFromStatus("APPROVED");
        record.setToStatus("APPROVED");
        record.setOperatedAt(LocalDateTime.of(2026, 8, 21, 0, 5));

        PurchaseRequest request = new PurchaseRequest();
        request.setId(51L);
        request.setReqNo("HZCG-000001");

        List<List<String>> rows = ReflectionTestUtils.invokeMethod(
            service,
            "approvalRows",
            List.of(record),
            Map.of(51L, request),
            Map.of()
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).hasSize(8);
            assertThat(row.get(3)).isEmpty();
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void afterSaleKeywordIsAppliedBeforeDatabasePagination() {
        ProcurementAfterSale unrelated = new ProcurementAfterSale();
        unrelated.setId(10L);
        unrelated.setQuoteItemId(100L);
        unrelated.setAfterSaleNo("SH-UNRELATED");

        when(afterSaleMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<ProcurementAfterSale> page = invocation.getArgument(0);
            LambdaQueryWrapper<ProcurementAfterSale> wrapper = invocation.getArgument(1);
            boolean databaseFilterPresent = !wrapper.getExpression().getNormal().isEmpty();
            page.setTotal(databaseFilterPresent ? 0 : 1);
            page.setRecords(databaseFilterPresent ? List.of() : List.of(unrelated));
            return page;
        });
        ProcurementOrderPageQueryDTO query = new ProcurementOrderPageQueryDTO();
        query.setKeyword("needle");
        PageResult<AfterSaleVO> result = service.pageAfterSales(query);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void activeAfterSaleIsReportedBeforeExpiredWindow() {
        ProcurementOrderVO order = receivedOrderOutsideWindow();
        when(orderQueryMapper.selectOrderRowByQuoteItemId(88L)).thenReturn(order);
        ProcurementAfterSale active = new ProcurementAfterSale();
        active.setId(9L);
        active.setQuoteItemId(88L);
        active.setStatus("PENDING");
        when(afterSaleMapper.selectOne(any(Wrapper.class), eq(true))).thenReturn(active);

        AfterSaleCreateDTO dto = new AfterSaleCreateDTO();
        dto.setAfterSaleType("NO_REASON_RETURN");
        dto.setReason("重复申请测试");
        dto.setRequestedQty(BigDecimal.ONE);

        assertThatThrownBy(() -> service.createAfterSale(88L, dto))
            .isInstanceOf(BizException.class)
            .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo("AFTER_SALE_PENDING_EXISTS"));
    }

    @Test
    void approvedAfterSaleIsReportedBeforeExpiredWindow() {
        ProcurementOrderVO order = receivedOrderOutsideWindow();
        when(orderQueryMapper.selectOrderRowByQuoteItemId(88L)).thenReturn(order);
        ProcurementAfterSale active = new ProcurementAfterSale();
        active.setId(9L);
        active.setQuoteItemId(88L);
        active.setStatus("APPROVED");
        when(afterSaleMapper.selectOne(any(Wrapper.class), eq(true))).thenReturn(active);

        AfterSaleCreateDTO dto = new AfterSaleCreateDTO();
        dto.setAfterSaleType("RETURN_EXCHANGE");
        dto.setReason("重复申请测试");
        dto.setRequestedQty(BigDecimal.ONE);

        assertThatThrownBy(() -> service.createAfterSale(88L, dto))
            .isInstanceOf(BizException.class)
            .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo("AFTER_SALE_APPROVED_EXISTS"));
    }

    @Test
    void applicantCanStillConfirmReceiptManually() {
        ProcurementOrderVO pending = new ProcurementOrderVO();
        pending.setQuoteItemId(88L);
        pending.setApplicantUserId(1L);
        pending.setSupplierConfirmStatus("CONFIRMED");
        pending.setReceiptStatus("PENDING");
        ProcurementOrderVO received = new ProcurementOrderVO();
        received.setQuoteItemId(88L);
        received.setApplicantUserId(1L);
        received.setSupplierConfirmStatus("CONFIRMED");
        received.setReceiptStatus("RECEIVED");
        SupplierQuoteItem item = new SupplierQuoteItem();
        item.setId(88L);
        when(orderQueryMapper.selectOrderRowByQuoteItemId(88L)).thenReturn(pending, received);
        when(supplierQuoteItemMapper.selectById(88L)).thenReturn(item);
        OrderReceiptDTO dto = new OrderReceiptDTO();
        dto.setReceiptRemark("现场已核对到货");

        ProcurementOrderVO result = service.confirmReceipt(88L, dto);

        assertThat(result.getReceiptStatus()).isEqualTo("RECEIVED");
        assertThat(item.getReceiptStatus()).isEqualTo("RECEIVED");
        assertThat(item.getReceivedBy()).isEqualTo(1L);
        assertThat(item.getReceivedAt()).isNotNull();
        assertThat(item.getReceiptRemark()).isEqualTo("现场已核对到货");
        assertThat(item.getAutoReceived()).isZero();
        verify(supplierQuoteItemMapper).updateById(item);
    }

    @Test
    void sourceAssistantCanConfirmReceiptForAuthorizedOrder() {
        AuthUser assistant = internalUser(33L);
        AuthContextHolder.set(assistant);
        ProcurementOrderVO pending = pendingOrder();
        ProcurementOrderVO received = pendingOrder();
        received.setReceiptStatus("RECEIVED");
        SupplierQuoteItem item = new SupplierQuoteItem();
        item.setId(88L);
        when(orderQueryMapper.selectOrderRowByQuoteItemId(88L)).thenReturn(pending, received);
        when(orderInvoiceService.canOperateAsSourceAssistant(assistant, pending)).thenReturn(true);
        when(orderInvoiceService.canOperateAsSourceAssistant(assistant, received)).thenReturn(true);
        when(supplierQuoteItemMapper.selectById(88L)).thenReturn(item);

        ProcurementOrderVO result = service.confirmReceipt(88L, new OrderReceiptDTO());

        assertThat(result.getReceiptStatus()).isEqualTo("RECEIVED");
        assertThat(item.getReceivedBy()).isEqualTo(33L);
        verify(supplierQuoteItemMapper).updateById(item);
    }

    @Test
    void sourceAssistantCanCreateAfterSaleAndConfirmSupplierProposal() {
        AuthUser assistant = internalUser(33L);
        AuthContextHolder.set(assistant);
        ProcurementOrderVO order = receivedOrderWithinWindow();
        when(orderQueryMapper.selectOrderRowByQuoteItemId(88L)).thenReturn(order);
        when(orderInvoiceService.canOperateAsSourceAssistant(assistant, order)).thenReturn(true);
        when(afterSaleMapper.selectOne(any(Wrapper.class), eq(true))).thenReturn(null);

        AfterSaleCreateDTO create = new AfterSaleCreateDTO();
        create.setAfterSaleType("OTHER");
        create.setReason("现场验收发现异常");
        create.setRequestedQty(BigDecimal.ONE);
        AfterSaleVO created = service.createAfterSale(88L, create);

        assertThat(created.getRequesterUserId()).isEqualTo(33L);
        assertThat(created.getStatus()).isEqualTo("PENDING");

        ProcurementAfterSale proposal = new ProcurementAfterSale();
        proposal.setId(9L);
        proposal.setQuoteItemId(88L);
        proposal.setRequesterUserId(1L);
        proposal.setAfterSaleType("OTHER");
        proposal.setStatus("PROPOSAL_PENDING");
        when(afterSaleMapper.selectById(9L)).thenReturn(proposal);
        AfterSaleApprovalDTO approval = new AfterSaleApprovalDTO();
        approval.setAction("APPROVE");
        approval.setApprovalRemark("同意供应商方案");

        AfterSaleVO approved = service.approveAfterSale(9L, approval);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(proposal.getRequesterConfirmUserId()).isEqualTo(33L);
    }

    @Test
    void unrelatedInternalUserCannotOperateDepartmentOrder() {
        AuthUser unrelated = internalUser(99L);
        AuthContextHolder.set(unrelated);
        ProcurementOrderVO pending = pendingOrder();
        when(orderQueryMapper.selectOrderRowByQuoteItemId(88L)).thenReturn(pending);
        when(orderInvoiceService.canOperateAsSourceAssistant(unrelated, pending)).thenReturn(false);

        assertThatThrownBy(() -> service.confirmReceipt(88L, new OrderReceiptDTO()))
            .isInstanceOf(BizException.class)
            .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo("FORBIDDEN"));
    }

    private ProcurementOrderVO receivedOrderOutsideWindow() {
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setQuoteItemId(88L);
        order.setApplicantUserId(1L);
        order.setQty(BigDecimal.ONE);
        order.setSupplierConfirmStatus("CONFIRMED");
        order.setReceiptStatus("RECEIVED");
        order.setReceivedAt(LocalDateTime.now().minusDays(30));
        return order;
    }

    private ProcurementOrderVO pendingOrder() {
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setQuoteItemId(88L);
        order.setApplicantUserId(1L);
        order.setSupplierConfirmStatus("CONFIRMED");
        order.setReceiptStatus("PENDING");
        return order;
    }

    private ProcurementOrderVO receivedOrderWithinWindow() {
        ProcurementOrderVO order = pendingOrder();
        order.setQty(new BigDecimal("2"));
        order.setReceiptStatus("RECEIVED");
        order.setReceivedAt(LocalDateTime.now().minusDays(1));
        order.setNoReasonReturnAvailable(true);
        order.setReturnExchangeAvailable(true);
        return order;
    }

    private AuthUser internalUser(Long userId) {
        return AuthUser.builder()
            .userId(userId)
            .username("user-" + userId)
            .userType("EMPLOYEE")
            .roleCodes(List.of("EMPLOYEE"))
            .build();
    }
}
