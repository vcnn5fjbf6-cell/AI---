package com.company.procurement.common.handler;

import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.errorlog.service.SystemErrorLogService;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GlobalExceptionHandlerTest {

    @Test
    void genericExceptionsDoNotReturnInternalMessagesToClients() {
        SystemErrorLogService errorLogService = mock(SystemErrorLogService.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(errorLogService);
        RuntimeException exception = new RuntimeException("jdbc:mysql://internal-db password=secret");

        Result<Void> result = handler.handleException(exception, new MockHttpServletRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("SYSTEM_ERROR");
        assertThat(result.getMessage()).doesNotContain("internal-db", "password=secret");
        verify(errorLogService).recordException(eq("SYSTEM_ERROR"), eq(exception.getMessage()), eq(exception), any());
    }

    @Test
    void malformedLocalDateRequestBodiesReturnValidationError() {
        SystemErrorLogService errorLogService = mock(SystemErrorLogService.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(errorLogService);
        InvalidFormatException cause = InvalidFormatException.from(null, "Cannot parse date", "7/10/26", LocalDate.class);
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON parse error", cause, null);

        Result<Void> result = handler.handleHttpMessageNotReadable(exception, new MockHttpServletRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(result.getMessage()).contains("yyyy-MM-dd");
        verifyNoInteractions(errorLogService);
    }

    @Test
    void missingRequiredRequestParametersReturnValidationError() {
        SystemErrorLogService errorLogService = mock(SystemErrorLogService.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(errorLogService);
        MissingServletRequestParameterException exception =
            new MissingServletRequestParameterException("status", "String");

        Result<Void> result = handler.handleMissingServletRequestParameter(
            exception,
            new MockHttpServletRequest()
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(result.getMessage()).contains("status");
        verifyNoInteractions(errorLogService);
    }
}
