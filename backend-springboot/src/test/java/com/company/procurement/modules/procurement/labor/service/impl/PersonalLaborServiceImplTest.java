package com.company.procurement.modules.procurement.labor.service.impl;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.labor.entity.PersonalLaborItemRecord;
import com.company.procurement.modules.procurement.labor.mapper.PersonalLaborItemRecordMapper;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalLaborServiceImplTest {

    @Mock
    private ProcurementSettingMapper settingMapper;
    @Mock
    private PersonalLaborItemRecordMapper recordMapper;
    @Mock
    private WorkflowService workflowService;

    private PersonalLaborServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonalLaborServiceImpl(settingMapper, recordMapper, workflowService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void onlyApplicantCanConfirmReceivedEvenWhenOperatorIsAdmin() {
        PersonalLaborItemRecord record = requestedRecord();
        when(recordMapper.selectById(1L)).thenReturn(record);
        AuthContextHolder.set(AuthUser.builder()
            .userId(99L)
            .username("admin")
            .roleCodes(List.of("ADMIN"))
            .build());

        assertThatThrownBy(() -> service.confirmReceived(1L))
            .isInstanceOf(BizException.class)
            .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo("FORBIDDEN"));
        verify(recordMapper, never()).updateById(any(PersonalLaborItemRecord.class));
    }

    @Test
    void procurementMustBeApprovedBeforeApplicantConfirmsReceived() {
        PersonalLaborItemRecord record = requestedRecord();
        when(recordMapper.selectById(1L)).thenReturn(record);
        setApplicant();

        assertThatThrownBy(() -> service.confirmReceived(1L))
            .isInstanceOf(BizException.class)
            .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo("LABOR_PROCUREMENT_NOT_APPROVED"));
        verify(recordMapper, never()).updateById(any(PersonalLaborItemRecord.class));
    }

    @Test
    void repeatedConfirmationIsIdempotent() {
        PersonalLaborItemRecord record = requestedRecord();
        record.setStatus("RECEIVED");
        record.setLastReceivedDate(LocalDate.of(2026, 7, 1));
        record.setNextDueDate(LocalDate.of(2027, 7, 1));
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(recordMapper.hasApprovedProcurement(1L)).thenReturn(true);
        when(recordMapper.hasCompletedDelivery(1L)).thenReturn(true);
        setApplicant();

        service.confirmReceived(1L);

        assertThat(record.getLastReceivedDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(record.getNextDueDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        verify(recordMapper, never()).updateById(any(PersonalLaborItemRecord.class));
    }

    @Test
    void completedApprovalWithoutDeliveryCannotBeConfirmed() {
        PersonalLaborItemRecord record = requestedRecord();
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(recordMapper.hasApprovedProcurement(1L)).thenReturn(true);
        when(recordMapper.hasCompletedDelivery(1L)).thenReturn(false);
        setApplicant();

        assertThatThrownBy(() -> service.confirmReceived(1L))
            .isInstanceOf(BizException.class)
            .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo("LABOR_DELIVERY_NOT_COMPLETED"));
        verify(recordMapper, never()).updateById(any(PersonalLaborItemRecord.class));
    }

    @Test
    void applicantCanConfirmAfterApprovalAndCompletedDelivery() {
        PersonalLaborItemRecord record = requestedRecord();
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(recordMapper.hasApprovedProcurement(1L)).thenReturn(true);
        when(recordMapper.hasCompletedDelivery(1L)).thenReturn(true);
        setApplicant();

        var result = service.confirmReceived(1L);

        assertThat(record.getStatus()).isEqualTo("RECEIVED");
        assertThat(record.getLastReceivedDate()).isEqualTo(LocalDate.now());
        assertThat(result.getCanConfirmReceived()).isFalse();
        verify(recordMapper).updateById(record);
    }

    private PersonalLaborItemRecord requestedRecord() {
        PersonalLaborItemRecord record = new PersonalLaborItemRecord();
        record.setId(1L);
        record.setUserId(7L);
        record.setItemCode("TEST_PEN");
        record.setStatus("REQUESTED");
        record.setPurchaseRequestId(10L);
        return record;
    }

    private void setApplicant() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(7L)
            .username("applicant")
            .build());
    }
}
