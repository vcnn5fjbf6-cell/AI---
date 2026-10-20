package com.company.procurement.modules.procurement.settings.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.org.scope.mapper.OrgScopeMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.supplier.dto.SupplierAwardConfirmDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteSubmitDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierQuoteService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcurementFlowTestServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            PurchaseRequest.class
        );
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            PurchaseRequestItem.class
        );
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            Supplier.class
        );
    }

    @AfterEach
    void clearAuth() {
        AuthContextHolder.clear();
    }

    @Test
    void supplierConfirmUsesFormalSupplierServiceAndRestoresOperator() {
        WorkflowService workflowService = mock(WorkflowService.class);
        PurchaseRequestMapper requestMapper = mock(PurchaseRequestMapper.class);
        PurchaseRequestItemMapper requestItemMapper = mock(PurchaseRequestItemMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        SupplierQuoteMapper quoteMapper = mock(SupplierQuoteMapper.class);
        SupplierQuoteItemMapper quoteItemMapper = mock(SupplierQuoteItemMapper.class);
        SupplierQuoteService quoteService = mock(SupplierQuoteService.class);
        OrgScopeMapper scopeMapper = mock(OrgScopeMapper.class);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        ProcurementFlowTestServiceImpl service = new ProcurementFlowTestServiceImpl(
            workflowService,
            requestMapper,
            requestItemMapper,
            supplierMapper,
            quoteMapper,
            quoteItemMapper,
            quoteService,
            scopeMapper,
            environment
        );

        PurchaseRequest request = new PurchaseRequest();
        request.setId(1L);
        request.setReqTitle("[流程测试]普通采购");
        request.setStatus(ProcurementWorkflow.STATUS_APPROVED);
        request.setAwardStrategy(ProcurementWorkflow.AWARD_STRATEGY_TOTAL_LOWEST);
        request.setSelectedQuoteId(11L);
        SupplierQuote quote = new SupplierQuote();
        quote.setId(11L);
        quote.setSupplierId(22L);
        Supplier supplier = new Supplier();
        supplier.setId(22L);
        supplier.setSupplierCode("TEST-SUP-001");
        supplier.setSupplierName("流程测试供应商1");
        supplier.setRegionId(1L);

        when(requestMapper.selectOne(any())).thenReturn(request);
        when(requestMapper.selectById(1L)).thenReturn(request);
        when(quoteMapper.selectById(11L)).thenReturn(quote);
        when(supplierMapper.selectById(22L)).thenReturn(supplier);

        AuthUser original = AuthUser.builder().userId(99L).username("admin").build();
        AuthContextHolder.set(original);
        doAnswer(invocation -> {
            assertThat(AuthContextHolder.requireUser().getSupplierId()).isEqualTo(22L);
            assertThat(AuthContextHolder.requireUser().getRoleCodes()).containsExactly("SUPPLIER");
            return null;
        }).when(quoteService).confirmAward(eq(1L), any(SupplierAwardConfirmDTO.class));

        service.triggerFormalAction("SUPPLIER_CONFIRM");

        assertThat(AuthContextHolder.requireUser()).isSameAs(original);
        verify(quoteService).confirmAward(eq(1L), any(SupplierAwardConfirmDTO.class));
    }

    @Test
    void createQuotesPublishesDemandPoolAndUsesFormalSupplierQuoteService() {
        WorkflowService workflowService = mock(WorkflowService.class);
        PurchaseRequestMapper requestMapper = mock(PurchaseRequestMapper.class);
        PurchaseRequestItemMapper requestItemMapper = mock(PurchaseRequestItemMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        SupplierQuoteMapper quoteMapper = mock(SupplierQuoteMapper.class);
        SupplierQuoteItemMapper quoteItemMapper = mock(SupplierQuoteItemMapper.class);
        SupplierQuoteService quoteService = mock(SupplierQuoteService.class);
        OrgScopeMapper scopeMapper = mock(OrgScopeMapper.class);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        ProcurementFlowTestServiceImpl service = new ProcurementFlowTestServiceImpl(
            workflowService,
            requestMapper,
            requestItemMapper,
            supplierMapper,
            quoteMapper,
            quoteItemMapper,
            quoteService,
            scopeMapper,
            environment
        );

        PurchaseRequest source = new PurchaseRequest();
        source.setId(1L);
        source.setReqTitle("[流程测试]普通采购");
        source.setStatus(ProcurementWorkflow.STATUS_DEMAND_POOL);
        PurchaseRequest aggregate = new PurchaseRequest();
        aggregate.setId(2L);
        aggregate.setReqTitle("全国月度汇总单");
        aggregate.setStatus(ProcurementWorkflow.STATUS_QUOTING);
        aggregate.setRegionId(1L);
        PurchaseRequestItem requestItem = new PurchaseRequestItem();
        requestItem.setId(10L);
        requestItem.setLineNo(1);
        requestItem.setQty(new BigDecimal("2"));
        requestItem.setUnitPrice(new BigDecimal("100.00"));
        List<Supplier> suppliers = List.of(
            supplier(21L, "TEST-SUP-001"),
            supplier(22L, "TEST-SUP-002"),
            supplier(23L, "TEST-SUP-003")
        );

        when(requestMapper.selectOne(any())).thenReturn(source);
        when(workflowService.publishDemandPoolRequestForFlowTest(1L)).thenReturn(2L);
        when(requestMapper.selectById(2L)).thenReturn(aggregate);
        when(requestItemMapper.selectList(any())).thenReturn(List.of(requestItem));
        when(supplierMapper.selectOne(any())).thenReturn(suppliers.get(0), suppliers.get(1), suppliers.get(2));

        AuthUser original = AuthUser.builder().userId(99L).username("admin").build();
        AuthContextHolder.set(original);
        int[] submissionIndex = {0};
        doAnswer(invocation -> {
            SupplierQuoteSubmitDTO dto = invocation.getArgument(0);
            assertThat(dto.getPurchaseRequestId()).isEqualTo(2L);
            assertThat(dto.getItems()).hasSize(1);
            assertThat(dto.getItems().get(0).getPurchaseRequestItemId()).isEqualTo(10L);
            assertThat(AuthContextHolder.requireUser().getSupplierId())
                .isEqualTo(suppliers.get(submissionIndex[0]).getId());
            submissionIndex[0]++;
            return null;
        }).when(quoteService).submitQuote(any(SupplierQuoteSubmitDTO.class));

        service.triggerFormalAction("CREATE_QUOTES");

        assertThat(AuthContextHolder.requireUser()).isSameAs(original);
        assertThat(submissionIndex[0]).isEqualTo(3);
        verify(workflowService).publishDemandPoolRequestForFlowTest(1L);
        verify(quoteService, times(3)).submitQuote(any(SupplierQuoteSubmitDTO.class));
    }

    @Test
    void formalFlowTestIsRejectedOutsideTestProfile() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        ProcurementFlowTestServiceImpl service = new ProcurementFlowTestServiceImpl(
            mock(WorkflowService.class),
            mock(PurchaseRequestMapper.class),
            mock(PurchaseRequestItemMapper.class),
            mock(SupplierMapper.class),
            mock(SupplierQuoteMapper.class),
            mock(SupplierQuoteItemMapper.class),
            mock(SupplierQuoteService.class),
            mock(OrgScopeMapper.class),
            environment
        );

        assertThatThrownBy(() -> service.triggerFormalAction("CREATE_REQUEST"))
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("FLOW_TEST_TEST_ENV_ONLY"));
    }

    private static Supplier supplier(Long id, String code) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setSupplierCode(code);
        supplier.setSupplierName("流程测试供应商" + id);
        supplier.setRegionId(1L);
        return supplier;
    }
}
