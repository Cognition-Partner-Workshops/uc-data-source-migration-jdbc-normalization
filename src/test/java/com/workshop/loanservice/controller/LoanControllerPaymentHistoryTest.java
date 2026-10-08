package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.PagedResponse;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.exception.InvalidRequestException;
import com.workshop.loanservice.exception.ResourceNotFoundException;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
class LoanControllerPaymentHistoryTest {

    private static final String URL = "/api/loans/{id}/payments";

    @Autowired private MockMvc mockMvc;

    @MockBean private LoanService loanService;

    @Test
    void usesDefaultPaginationWhenNoParamsGiven() throws Exception {
        when(loanService.getPaymentHistory("LN-1", null, null, null, 0, LoanService.DEFAULT_PAGE_SIZE))
                .thenReturn(new PagedResponse<>(List.of(payment("P-1")), 0, LoanService.DEFAULT_PAGE_SIZE, 1));

        mockMvc.perform(get(URL, "LN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentId").value("P-1"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(1487.02))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(LoanService.DEFAULT_PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void bindsAllQueryParameters() throws Exception {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 6, 30);
        when(loanService.getPaymentHistory("LN-1", start, end, "REG,EXT", 2, 5))
                .thenReturn(new PagedResponse<>(List.of(), 2, 5, 0));

        mockMvc.perform(get(URL, "LN-1")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-06-30")
                        .param("type", "REG,EXT")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(loanService).getPaymentHistory("LN-1", start, end, "REG,EXT", 2, 5);
    }

    @Test
    void returns404WhenLoanNotFound() throws Exception {
        when(loanService.getPaymentHistory(eq("LN-X"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Loan not found: LN-X"));

        mockMvc.perform(get(URL, "LN-X"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Loan not found: LN-X"))
                .andExpect(jsonPath("$.path").value("/api/loans/LN-X/payments"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returns400WhenServiceRejectsParameters() throws Exception {
        when(loanService.getPaymentHistory(anyString(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new InvalidRequestException("size must be between 1 and 100"));

        mockMvc.perform(get(URL, "LN-1").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }

    @Test
    void returns400ForMalformedDate() throws Exception {
        mockMvc.perform(get(URL, "LN-1").param("startDate", "12/01/2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("startDate")));

        verify(loanService, never()).getPaymentHistory(anyString(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void returns400ForNonNumericPage() throws Exception {
        mockMvc.perform(get(URL, "LN-1").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    void preservesStatusOfFrameworkExceptions() throws Exception {
        mockMvc.perform(post(URL, "LN-1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void returns500WithGenericMessageForUnexpectedErrors() throws Exception {
        when(loanService.getPaymentHistory(anyString(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("db exploded: secret details"));

        mockMvc.perform(get(URL, "LN-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    private static PaymentDto payment(String id) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(id);
        dto.setLoanAccountNumber("LN-1");
        dto.setPaymentDate("12/15/2025");
        dto.setTotalAmount(new BigDecimal("1487.02"));
        dto.setType("Regular");
        dto.setStatus("Posted");
        return dto;
    }
}
