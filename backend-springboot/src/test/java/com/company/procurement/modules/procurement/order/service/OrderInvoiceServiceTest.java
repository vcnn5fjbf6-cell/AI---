package com.company.procurement.modules.procurement.order.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.procurement.order.entity.OrderInvoiceAttachment;
import com.company.procurement.modules.procurement.order.entity.OrderInvoiceSubmission;
import com.company.procurement.modules.procurement.order.mapper.OrderInvoiceAttachmentMapper;
import com.company.procurement.modules.procurement.order.mapper.OrderInvoiceSubmissionMapper;
import com.company.procurement.modules.procurement.order.support.SupplierSoldOrderAccess;
import com.company.procurement.modules.procurement.order.vo.OrderInvoiceSubmissionVO;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderInvoiceServiceTest {

    @Mock
    private OrderInvoiceSubmissionMapper submissionMapper;

    @Mock
    private OrderInvoiceAttachmentMapper attachmentMapper;

    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;

    @Mock
    private ProcurementApprovalRouteResolver approvalRouteResolver;

    @Mock
    private FeishuApprovalCardService feishuApprovalCardService;

    @InjectMocks
    private OrderInvoiceService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "storageDir", tempDir.toString());
    }

    @Test
    void supplierReuploadSupersedesPendingVersionAndStoresRandomFile() throws Exception {
        ProcurementOrderVO order = order();
        AuthUser supplier = supplierUser();
        OrderInvoiceSubmission previous = submission(100L, 1, OrderInvoiceService.STATUS_PENDING);
        when(submissionMapper.selectOne(any())).thenReturn(previous);
        when(submissionMapper.updateById(previous)).thenReturn(1);
        when(submissionMapper.insert(any(OrderInvoiceSubmission.class))).thenAnswer(invocation -> {
            OrderInvoiceSubmission inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        });
        when(attachmentMapper.insert(any(OrderInvoiceAttachment.class))).thenAnswer(invocation -> {
            OrderInvoiceAttachment inserted = invocation.getArgument(0);
            inserted.setId(201L);
            return 1;
        });
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        MockMultipartFile pdf = new MockMultipartFile(
            "files",
            "invoice.pdf",
            "application/pdf",
            "%PDF-1.7 test invoice".getBytes()
        );

        OrderInvoiceSubmissionVO result = service.upload(order, supplier, List.of(pdf), null, null, null, null, null);

        assertThat(previous.getStatus()).isEqualTo(OrderInvoiceService.STATUS_SUPERSEDED);
        assertThat(result.getVersionNo()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(OrderInvoiceService.STATUS_PENDING);
        ArgumentCaptor<OrderInvoiceAttachment> attachmentCaptor = ArgumentCaptor.forClass(OrderInvoiceAttachment.class);
        verify(attachmentMapper).insert(attachmentCaptor.capture());
        Path stored = tempDir.resolve(attachmentCaptor.getValue().getStoragePath()).normalize();
        assertThat(stored).startsWith(tempDir);
        assertThat(Files.readString(stored)).startsWith("%PDF");
        assertThat(attachmentCaptor.getValue().getFileSha256()).hasSize(64);
    }

    @Test
    void transactionRollbackDeletesStoredInvoiceFiles() {
        when(submissionMapper.selectList(any())).thenReturn(List.of());
        when(submissionMapper.insert(any(OrderInvoiceSubmission.class))).thenAnswer(invocation -> {
            OrderInvoiceSubmission inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        });
        when(attachmentMapper.insert(any(OrderInvoiceAttachment.class))).thenAnswer(invocation -> {
            OrderInvoiceAttachment inserted = invocation.getArgument(0);
            inserted.setId(201L);
            return 1;
        });
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.upload(order(), supplierUser(), List.of(validPdf()), null, null, null, null, null);

            ArgumentCaptor<OrderInvoiceAttachment> attachmentCaptor = ArgumentCaptor.forClass(OrderInvoiceAttachment.class);
            verify(attachmentMapper).insert(attachmentCaptor.capture());
            Path stored = tempDir.resolve(attachmentCaptor.getValue().getStoragePath()).normalize();
            assertThat(stored).exists();
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            assertThat(stored).doesNotExist();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void acceptedInvoiceBlocksSupplierReupload() {
        ProcurementOrderVO order = order();
        OrderInvoiceSubmission accepted = submission(100L, 1, OrderInvoiceService.STATUS_ACCEPTED);
        when(submissionMapper.selectOne(any())).thenReturn(accepted);
        MockMultipartFile pdf = new MockMultipartFile(
            "files",
            "invoice.pdf",
            "application/pdf",
            "%PDF-1.7 test invoice".getBytes()
        );

        assertThatThrownBy(() -> service.upload(order, supplierUser(), List.of(pdf), null, null, null, null, null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_ACCEPTED");
    }

    @Test
    void returnedInvoiceCanBeResubmittedAsNewPendingVersion() {
        OrderInvoiceSubmission returned = submission(100L, 1, OrderInvoiceService.STATUS_RETURNED);
        when(submissionMapper.selectOne(any())).thenReturn(returned);
        when(submissionMapper.selectList(any())).thenReturn(List.of(returned));
        when(submissionMapper.insert(any(OrderInvoiceSubmission.class))).thenAnswer(invocation -> {
            OrderInvoiceSubmission inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        });
        when(attachmentMapper.insert(any(OrderInvoiceAttachment.class))).thenReturn(1);
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        OrderInvoiceSubmissionVO result = service.upload(
            order(),
            supplierUser(),
            List.of(validPdf()),
            null,
            null,
            null,
            null,
            null
        );

        assertThat(returned.getStatus()).isEqualTo(OrderInvoiceService.STATUS_RETURNED);
        assertThat(result.getVersionNo()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(OrderInvoiceService.STATUS_PENDING);
        ArgumentCaptor<OrderInvoiceSubmission> submissionCaptor = ArgumentCaptor.forClass(OrderInvoiceSubmission.class);
        verify(submissionMapper).insert(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getSupersedesSubmissionId()).isEqualTo(100L);
    }

    @Test
    void purchaserCanAcceptLatestPendingInvoice() {
        ProcurementOrderVO order = order();
        AuthUser purchaser = internalUser(22L);
        OrderInvoiceSubmission pending = submission(100L, 1, OrderInvoiceService.STATUS_PENDING);
        when(submissionMapper.selectById(100L)).thenReturn(pending);
        when(submissionMapper.selectOne(any())).thenReturn(pending);
        when(submissionMapper.update(isNull(), any())).thenReturn(1);
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        OrderInvoiceSubmissionVO result = service.accept(order, 100L, purchaser, "已核对");

        assertThat(result.getStatus()).isEqualTo(OrderInvoiceService.STATUS_ACCEPTED);
        assertThat(result.getReviewedByName()).isEqualTo("采购人");
        assertThat(result.getReviewRemark()).isEqualTo("已核对");
    }

    @Test
    void sourceAssistantCanListAndDownloadInvoiceAttachment() throws Exception {
        ProcurementOrderVO order = order();
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(91L);
        OrderInvoiceSubmission pending = submission(100L, 1, OrderInvoiceService.STATUS_PENDING);
        OrderInvoiceAttachment attachment = new OrderInvoiceAttachment();
        attachment.setId(201L);
        attachment.setSubmissionId(100L);
        attachment.setFileName("assistant-invoice.pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileSize(12L);
        attachment.setStoragePath("assistant-invoice.pdf");
        Files.writeString(tempDir.resolve("assistant-invoice.pdf"), "%PDF invoice");
        when(purchaseRequestMapper.selectById(91L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenReturn(List.of(33L));
        when(submissionMapper.selectList(any())).thenReturn(List.of(pending));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment));
        when(attachmentMapper.selectById(201L)).thenReturn(attachment);
        when(submissionMapper.selectById(100L)).thenReturn(pending);

        List<OrderInvoiceSubmissionVO> history = service.list(order, internalUser(33L));
        OrderInvoiceService.AttachmentResource resource = service.loadAttachment(order, 201L, internalUser(33L));

        assertThat(history).singleElement().satisfies(submission ->
            assertThat(submission.getAttachments()).singleElement().satisfies(file ->
                assertThat(file.getFileName()).isEqualTo("assistant-invoice.pdf")));
        assertThat(resource.fileName()).isEqualTo("assistant-invoice.pdf");
        assertThat(resource.contentLength()).isEqualTo(12L);
    }

    @Test
    void sourceAssistantGetsOrderOperationCapabilitiesWithinSourceScope() {
        ProcurementOrderVO order = order();
        order.setPurchaseRequestId(900L);
        order.setSourceRequestId(901L);
        order.setReceiptStatus("RECEIVED");
        order.setAfterSaleAllowed(true);
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(901L);
        when(submissionMapper.selectOne(any())).thenReturn(null);
        when(purchaseRequestMapper.selectById(901L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenReturn(List.of(33L));

        service.decorate(order, internalUser(33L));

        assertThat(order.getCanViewInvoice()).isTrue();
        assertThat(order.getCanReviewInvoice()).isTrue();
        assertThat(order.getCanUploadInvoice()).isFalse();
        assertThat(order.getCanUploadDeliveryNote()).isFalse();
        assertThat(order.getCanExportOrder()).isTrue();
        assertThat(order.getCanConfirmReceipt()).isFalse();
        assertThat(order.getCanApplyAfterSale()).isTrue();
        assertThat(order.getCanConfirmAfterSaleProposal()).isFalse();

        order.setReceiptStatus("PENDING");
        order.setAfterSaleAllowed(false);
        service.decorate(order, internalUser(33L));

        assertThat(order.getCanConfirmReceipt()).isTrue();
        assertThat(order.getCanApplyAfterSale()).isFalse();

        order.setReceiptStatus("RECEIVED");
        order.setLatestAfterSaleStatus("PROPOSAL_PENDING");
        service.decorate(order, internalUser(33L));

        assertThat(order.getCanConfirmAfterSaleProposal()).isTrue();
        verify(purchaseRequestMapper, times(3)).selectById(901L);
    }

    @Test
    void floorLeaderAndSupplierDoNotReceiveAssistantOrderOperations() {
        ProcurementOrderVO floorOrder = order();
        floorOrder.setLatestAfterSaleStatus("PROPOSAL_PENDING");
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(91L);
        when(purchaseRequestMapper.selectById(91L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenReturn(List.of());
        when(approvalRouteResolver.resolveAssignmentForNode(sourceRequest, ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW))
            .thenReturn(floorAssignment(44L));

        service.decorate(floorOrder, internalUser(44L));

        assertThat(floorOrder.getCanReviewInvoice()).isTrue();
        assertThat(floorOrder.getCanUploadInvoice()).isFalse();
        assertThat(floorOrder.getCanUploadDeliveryNote()).isFalse();
        assertThat(floorOrder.getCanExportOrder()).isFalse();
        assertThat(floorOrder.getCanConfirmReceipt()).isFalse();
        assertThat(floorOrder.getCanApplyAfterSale()).isFalse();
        assertThat(floorOrder.getCanConfirmAfterSaleProposal()).isFalse();

        ProcurementOrderVO supplierOrder = order();
        service.decorate(supplierOrder, supplierUser());

        assertThat(supplierOrder.getCanUploadInvoice()).isTrue();
        assertThat(supplierOrder.getCanUploadDeliveryNote()).isTrue();
        assertThat(supplierOrder.getCanExportOrder()).isTrue();
        assertThat(supplierOrder.getCanConfirmReceipt()).isFalse();
        assertThat(supplierOrder.getCanApplyAfterSale()).isFalse();
        assertThat(supplierOrder.getCanConfirmAfterSaleProposal()).isFalse();
    }

    @Test
    void currentFloorLeaderCanReviewButUnrelatedUserCannot() {
        ProcurementOrderVO order = order();
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(91L);
        when(purchaseRequestMapper.selectById(91L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenReturn(List.of());
        when(approvalRouteResolver.resolveAssignmentForNode(sourceRequest, ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW))
            .thenReturn(new ProcurementApprovalRouteResolver.ApprovalAssignment(
                44L,
                List.of(44L),
                "楼长",
                true,
                10L,
                20L,
                ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW,
                "V3",
                "楼长审核",
                ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
                true
            ));

        assertThat(service.canReviewInvoice(internalUser(44L), order)).isTrue();
        assertThat(service.canReviewInvoice(internalUser(45L), order)).isFalse();
        verify(purchaseRequestMapper, org.mockito.Mockito.times(2)).selectById(91L);
    }

    @Test
    void applicantKeepsOrderRowWithoutInvoiceReadAccess() {
        ProcurementOrderVO order = order();
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(91L);
        when(purchaseRequestMapper.selectById(91L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenReturn(List.of());
        when(approvalRouteResolver.resolveAssignmentForNode(sourceRequest, ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW))
            .thenReturn(new ProcurementApprovalRouteResolver.ApprovalAssignment(
                null,
                List.of(),
                "",
                true,
                null,
                null,
                ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW,
                "V3",
                "楼长审核",
                ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
                true
            ));

        boolean canAccessRow = service.decorate(order, internalUser(21L));

        assertThat(canAccessRow).isTrue();
        assertThat(order.getCanViewInvoice()).isFalse();
        assertThat(order.getLatestInvoice()).isNull();
        assertThat(order.getCanExportOrder()).isTrue();
        verifyNoInteractions(submissionMapper);
    }

    @Test
    void acceptedInvoiceCannotBeReturned() {
        ProcurementOrderVO order = order();
        OrderInvoiceSubmission accepted = submission(100L, 1, OrderInvoiceService.STATUS_ACCEPTED);
        when(submissionMapper.selectById(100L)).thenReturn(accepted);
        when(submissionMapper.selectOne(any())).thenReturn(accepted);

        assertThatThrownBy(() -> service.returnForResubmission(order, 100L, internalUser(22L), "重新开票"))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_STATUS_INVALID");
    }

    @Test
    void returningPendingInvoiceRequiresReason() {
        assertThatThrownBy(() -> service.returnForResubmission(order(), 100L, internalUser(22L), "  "))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_RETURN_REASON_REQUIRED");
        verifyNoInteractions(submissionMapper);
    }

    @Test
    void currentFloorLeaderCanReturnLatestPendingInvoiceForResubmission() {
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(91L);
        OrderInvoiceSubmission pending = submission(100L, 1, OrderInvoiceService.STATUS_PENDING);
        when(purchaseRequestMapper.selectById(91L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenReturn(List.of());
        when(approvalRouteResolver.resolveAssignmentForNode(sourceRequest, ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW))
            .thenReturn(floorAssignment(44L));
        when(submissionMapper.selectById(100L)).thenReturn(pending);
        when(submissionMapper.selectOne(any())).thenReturn(pending);
        when(submissionMapper.update(isNull(), any())).thenReturn(1);
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        OrderInvoiceSubmissionVO result = service.returnForResubmission(
            order(),
            100L,
            internalUser(44L),
            "抬头不一致"
        );

        assertThat(result.getStatus()).isEqualTo(OrderInvoiceService.STATUS_RETURNED);
        assertThat(result.getReviewRemark()).isEqualTo("抬头不一致");
    }

    @Test
    void unrelatedInternalUserCannotReadInvoiceHistory() {
        assertThatThrownBy(() -> service.list(order(), internalUser(45L)))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FORBIDDEN");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void otherSupplierCannotUploadInvoice() {
        assertThatThrownBy(() -> service.upload(
                order(),
                supplierUser(56L),
                List.of(validPdf()),
                null,
                null,
                null,
                null,
                null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FORBIDDEN");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void supplierSupervisorCanManageOtherSupplierDocumentsWithoutInternalActions() {
        ProcurementOrderVO order = order();
        AuthUser supervisor = supplierSupervisor();
        when(submissionMapper.selectOne(any())).thenReturn(null);

        boolean canAccessRow = service.decorate(order, supervisor);

        assertThat(canAccessRow).isTrue();
        assertThat(order.getCanViewInvoice()).isTrue();
        assertThat(order.getCanViewOrderDocuments()).isTrue();
        assertThat(order.getCanUploadInvoice()).isTrue();
        assertThat(order.getCanUploadDeliveryNote()).isTrue();
        assertThat(order.getCanExportOrder()).isTrue();
        assertThat(order.getCanReviewInvoice()).isFalse();
        assertThat(order.getCanConfirmReceipt()).isFalse();
        assertThat(order.getCanApplyAfterSale()).isFalse();
        assertThat(order.getCanConfirmAfterSaleProposal()).isFalse();
    }

    @Test
    void supplierSupervisorCanUploadForOtherSupplier() {
        stubSuccessfulUpload();

        service.upload(
            order(),
            supplierSupervisor(),
            List.of(validPdf()),
            null,
            null,
            null,
            null,
            null
        );

        ArgumentCaptor<OrderInvoiceSubmission> captor = ArgumentCaptor.forClass(OrderInvoiceSubmission.class);
        verify(submissionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSubmittedByRole()).isEqualTo(OrderInvoiceService.SUBMITTED_ROLE_SUPPLIER);
        assertThat(captor.getValue().getSupplierId()).isEqualTo(55L);
    }

    @Test
    void uploadStopsAtOrderVersionLimit() {
        List<OrderInvoiceSubmission> versions = java.util.stream.IntStream.range(0, 50)
            .mapToObj(index -> submission(100L + index, index + 1, OrderInvoiceService.STATUS_RETURNED))
            .toList();
        when(submissionMapper.selectList(any())).thenReturn(versions);

        assertThatThrownBy(() -> service.upload(
                order(),
                supplierUser(),
                List.of(validPdf()),
                null,
                null,
                null,
                null,
                null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_VERSION_LIMIT");
        verifyNoInteractions(attachmentMapper);
    }

    @Test
    void attachmentFromAnotherOrderCannotBeDownloaded() {
        OrderInvoiceAttachment attachment = new OrderInvoiceAttachment();
        attachment.setId(201L);
        attachment.setSubmissionId(101L);
        OrderInvoiceSubmission otherOrderSubmission = submission(101L, 1, OrderInvoiceService.STATUS_PENDING);
        otherOrderSubmission.setQuoteItemId(12L);
        when(attachmentMapper.selectById(201L)).thenReturn(attachment);
        when(submissionMapper.selectById(101L)).thenReturn(otherOrderSubmission);

        assertThatThrownBy(() -> service.loadAttachment(order(), 201L, supplierUser()))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_ATTACHMENT_NOT_FOUND");
    }

    @Test
    void corruptedStoredAttachmentIsRejectedBeforeDownload() throws Exception {
        OrderInvoiceAttachment attachment = new OrderInvoiceAttachment();
        attachment.setId(201L);
        attachment.setSubmissionId(101L);
        attachment.setFileName("invoice.pdf");
        attachment.setStoragePath("invoice.pdf");
        attachment.setFileSize(999L);
        OrderInvoiceSubmission submission = submission(101L, 1, OrderInvoiceService.STATUS_PENDING);
        Files.writeString(tempDir.resolve("invoice.pdf"), "%PDF invoice");
        when(attachmentMapper.selectById(201L)).thenReturn(attachment);
        when(submissionMapper.selectById(101L)).thenReturn(submission);

        assertThatThrownBy(() -> service.loadAttachment(order(), 201L, supplierUser()))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_ATTACHMENT_INTEGRITY_FAILED");
    }

    @Test
    void invalidExtensionIsRejectedBeforeDatabaseWrite() {
        MockMultipartFile executable = new MockMultipartFile(
            "files",
            "invoice.exe",
            "application/octet-stream",
            new byte[] {1, 2, 3}
        );

        assertThatThrownBy(() -> service.upload(order(), supplierUser(), List.of(executable), null, null, null, null, null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_FILE_TYPE_INVALID");
    }

    @Test
    void fileLargerThanTwentyMegabytesIsRejectedBeforeRead() {
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(20L * 1024L * 1024L + 1L);

        assertThatThrownBy(() -> service.upload(order(), supplierUser(), List.of(oversized), null, null, null, null, null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_FILE_TOO_LARGE");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void mismatchedFileSignatureIsRejected() {
        MockMultipartFile invalidPdf = new MockMultipartFile(
            "files",
            "invoice.pdf",
            "application/pdf",
            "not a pdf".getBytes()
        );

        assertThatThrownBy(() -> service.upload(order(), supplierUser(), List.of(invalidPdf), null, null, null, null, null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_FILE_CONTENT_INVALID");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void emptyFileIsNotSilentlyIgnored() {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(
                order(),
                supplierUser(),
                List.of(validPdf(), empty),
                null,
                null,
                null,
                null,
                null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_FILE_EMPTY");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void invoiceMetadataBeyondDatabaseLimitsIsRejected() {
        assertThatThrownBy(() -> service.upload(
                order(),
                supplierUser(),
                List.of(validPdf()),
                "N".repeat(129),
                null,
                null,
                null,
                null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_NO_TOO_LONG");

        assertThatThrownBy(() -> service.upload(
                order(),
                supplierUser(),
                List.of(validPdf()),
                null,
                null,
                new BigDecimal("1.001"),
                null,
                null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_AMOUNT_INVALID");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void invoiceTypeLongerThanDatabaseColumnIsRejected() {
        assertThatThrownBy(() -> service.upload(
                order(),
                supplierUser(),
                List.of(validPdf()),
                null,
                null,
                null,
                "A".repeat(33),
                null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ORDER_INVOICE_TYPE_TOO_LONG");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void sourceAssistantCannotUploadOrderDocument() {
        assertThatThrownBy(() -> service.upload(
                order(),
                internalUser(88L),
                OrderInvoiceService.DOCUMENT_TYPE_DELIVERY_NOTE,
                List.of(validPdf()),
                "DN-001",
                null,
                null,
                null,
                null))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FORBIDDEN");
        verifyNoInteractions(submissionMapper, attachmentMapper);
    }

    @Test
    void superAdminCanUploadOrderDocument() {
        stubSuccessfulUpload();
        ProcurementOrderVO order = order();

        service.decorate(order, superAdmin());

        assertThat(order.getCanUploadInvoice()).isTrue();
        assertThat(order.getCanUploadDeliveryNote()).isTrue();

        service.upload(
            order,
            superAdmin(),
            OrderInvoiceService.DOCUMENT_TYPE_INVOICE,
            List.of(validPdf()),
            null,
            null,
            null,
            null,
            null
        );

        ArgumentCaptor<OrderInvoiceSubmission> captor = ArgumentCaptor.forClass(OrderInvoiceSubmission.class);
        verify(submissionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSubmittedByRole()).isEqualTo(OrderInvoiceService.SUBMITTED_ROLE_ADMIN);
    }

    @Test
    void supplierUploadNotifiesSourceAssistant() {
        ProcurementOrderVO order = order();
        order.setOrderNo("ORDER-001");
        order.setReqNo("REQ-001");
        order.setSupplierName("测试供应商");
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(91L);
        when(purchaseRequestMapper.selectById(91L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenReturn(List.of(88L));
        stubSuccessfulUpload();

        service.upload(
            order,
            supplierUser(),
            OrderInvoiceService.DOCUMENT_TYPE_INVOICE,
            List.of(validPdf()),
            null,
            null,
            null,
            null,
            null
        );

        verify(feishuApprovalCardService).sendBusinessNotification(
            eq(88L),
            eq("订单发票已上传"),
            contains("ORDER-001")
        );
    }

    @Test
    void notificationRouteFailureDoesNotRollbackSupplierUpload() {
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(91L);
        when(purchaseRequestMapper.selectById(91L)).thenReturn(sourceRequest);
        when(approvalRouteResolver.routeCandidateUserIds(sourceRequest, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT))
            .thenThrow(new IllegalStateException("route unavailable"));
        stubSuccessfulUpload();

        OrderInvoiceSubmissionVO result = service.upload(
            order(),
            supplierUser(),
            OrderInvoiceService.DOCUMENT_TYPE_DELIVERY_NOTE,
            List.of(validPdf()),
            null,
            null,
            null,
            null,
            null
        );

        assertThat(result.getStatus()).isEqualTo(OrderInvoiceService.STATUS_PENDING);
        assertThat(result.getDocumentType()).isEqualTo(OrderInvoiceService.DOCUMENT_TYPE_DELIVERY_NOTE);
        verifyNoInteractions(feishuApprovalCardService);
    }

    private ProcurementOrderVO order() {
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setQuoteItemId(11L);
        order.setPurchaseRequestId(90L);
        order.setSourceRequestId(91L);
        order.setSupplierId(55L);
        order.setApplicantUserId(21L);
        order.setPurchaserUserId(22L);
        order.setSupplierConfirmStatus("CONFIRMED");
        order.setReceiptStatus("PENDING");
        return order;
    }

    private OrderInvoiceSubmission submission(Long id, int versionNo, String status) {
        OrderInvoiceSubmission submission = new OrderInvoiceSubmission();
        submission.setId(id);
        submission.setQuoteItemId(11L);
        submission.setSupplierId(55L);
        submission.setVersionNo(versionNo);
        submission.setVersion(0);
        submission.setStatus(status);
        submission.setAttachmentCount(1);
        return submission;
    }

    private AuthUser supplierUser() {
        return supplierUser(55L);
    }

    private AuthUser supplierUser(Long supplierId) {
        return AuthUser.builder()
            .userId(77L)
            .username("supplier")
            .realName("供应商")
            .userType("SUPPLIER")
            .supplierId(supplierId)
            .roleCodes(List.of("SUPPLIER"))
            .build();
    }

    private AuthUser supplierSupervisor() {
        return AuthUser.builder()
            .userId(255L)
            .username("supplier-supervisor")
            .realName("供应商监督员")
            .userType("SUPPLIER_QUOTE_VIEWER")
            .roleCodes(List.of("SUPPLIER_QUOTE_VIEWER"))
            .permissionCodes(List.of(SupplierSoldOrderAccess.MANAGE_ALL_PERMISSION))
            .build();
    }

    private MockMultipartFile validPdf() {
        return new MockMultipartFile(
            "files",
            "invoice.pdf",
            "application/pdf",
            "%PDF-1.7 test invoice".getBytes()
        );
    }

    private void stubSuccessfulUpload() {
        when(submissionMapper.selectList(any())).thenReturn(List.of());
        when(submissionMapper.insert(any(OrderInvoiceSubmission.class))).thenAnswer(invocation -> {
            OrderInvoiceSubmission inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        });
        when(attachmentMapper.insert(any(OrderInvoiceAttachment.class))).thenAnswer(invocation -> {
            OrderInvoiceAttachment inserted = invocation.getArgument(0);
            inserted.setId(201L);
            return 1;
        });
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
    }

    private AuthUser internalUser(Long userId) {
        return AuthUser.builder()
            .userId(userId)
            .username("purchaser")
            .realName("采购人")
            .userType("EMPLOYEE")
            .roleCodes(List.of("PURCHASER"))
            .build();
    }

    private AuthUser superAdmin() {
        return AuthUser.builder()
            .userId(1L)
            .username("admin")
            .realName("管理员")
            .userType("SUPER_ADMIN")
            .roleCodes(List.of("SUPER_ADMIN"))
            .build();
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment floorAssignment(Long userId) {
        return new ProcurementApprovalRouteResolver.ApprovalAssignment(
            userId,
            List.of(userId),
            "楼长",
            true,
            10L,
            20L,
            ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW,
            "V3",
            "楼长审核",
            ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
            true
        );
    }
}
