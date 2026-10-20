package com.company.procurement.modules.approval.record.service.impl;

import com.company.procurement.modules.approval.record.dto.ApprovalRecordCreateDTO;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRecordServiceImplTest {

    @Test
    void continuingApprovalExplicitlyClearsEndedAt() {
        ApprovalRecordMapper recordMapper = mock(ApprovalRecordMapper.class);
        ApprovalInstanceMapper instanceMapper = mock(ApprovalInstanceMapper.class);
        PurchaseRequestMapper requestMapper = mock(PurchaseRequestMapper.class);
        ApprovalRecordServiceImpl service = new ApprovalRecordServiceImpl(instanceMapper, requestMapper);
        ReflectionTestUtils.setField(service, "baseMapper", recordMapper);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(8L);
        instance.setCurrentNodeNo(2);
        instance.setEndedAt(LocalDateTime.now().minusDays(1));
        when(instanceMapper.selectById(8L)).thenReturn(instance);

        ApprovalRecordCreateDTO dto = new ApprovalRecordCreateDTO();
        dto.setInstanceId(8L);
        dto.setNodeNo(3);
        dto.setAction("APPROVE");
        dto.setIsFinalNode(0);
        dto.setTransferToUserId(9L);
        dto.setOperatedAt(LocalDateTime.now());

        service.create(dto);

        verify(instanceMapper).updateById(argThat((ApprovalInstance updated) -> {
            assertThat(updated.getStatus()).isEqualTo("APPROVING");
            return updated.getEndedAt() == null;
        }));
        verify(instanceMapper).clearEndedAt(8L);
    }
}
