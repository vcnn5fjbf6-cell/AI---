package com.company.procurement.modules.platform.errorlog.service.impl;

import com.company.procurement.modules.platform.errorlog.entity.SystemErrorLog;
import com.company.procurement.modules.platform.errorlog.mapper.SystemErrorLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemErrorLogServiceImplTest {

    @Test
    void procurementOrderErrorsUseProcurementModule() {
        SystemErrorLogMapper mapper = mock(SystemErrorLogMapper.class);
        SystemErrorLogServiceImpl service = new SystemErrorLogServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/api/procurement/orders/1/after-sales"
        );

        service.recordException("TEST_ERROR", "test", new RuntimeException("test"), request);

        verify(mapper).insert(argThat((SystemErrorLog log) -> "PROCUREMENT".equals(log.getModuleCode())));
    }
}
