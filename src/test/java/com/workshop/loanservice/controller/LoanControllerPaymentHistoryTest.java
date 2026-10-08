package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.PagedResponse;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.exception.InvalidRequestException;
import com.workshop.loanservice.exception.LoanNotFoundException;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
class LoanControllerPaymentHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanService loanService;

    @Test
    void defaultsPageAndSizeAndSerializesThePagedResponseEnvelope() throws Exception {
        when(loanService.getPaymentHistory("LN-1", null, null, null, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(payment()), 0, 20, 1));

        mockMvc.perform(get("/api/loans/LN-1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentId").value("PMT-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        verify(loanService).getPaymentHistory("LN-1", null, null, null, 0, 20);
    }

    @Test
    void bindsAllOptionalQueryParameters() throws Exception {
        when(loanService.getPaymentHistory(
                "LN-1", "2025-01-01", "2025-03-01", "REG,EXT", 2, 3))
                .thenReturn(new PagedResponse<>(List.of(), 2, 3, 0));

        mockMvc.perform(get("/api/loans/LN-1/payments")
                        .param("page", "2")
                        .param("size", "3")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-03-01")
                        .param("type", "REG,EXT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(3));

        verify(loanService).getPaymentHistory(
                "LN-1", "2025-01-01", "2025-03-01", "REG,EXT", 2, 3);
    }

    @Test
    void returnsApiErrorForMissingLoan() throws Exception {
        when(loanService.getPaymentHistory("NOPE", null, null, null, 0, 20))
                .thenThrow(new LoanNotFoundException("NOPE"));

        mockMvc.perform(get("/api/loans/NOPE/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Loan not found: NOPE"))
                .andExpect(jsonPath("$.path").value("/api/loans/NOPE/payments"));
    }

    @Test
    void returnsBadRequestForInvalidRequestAndMismatchedParameterType() throws Exception {
        when(loanService.getPaymentHistory("LN-1", "bad-date", null, null, 0, 20))
                .thenThrow(new InvalidRequestException("startDate must be a valid ISO date"));

        mockMvc.perform(get("/api/loans/LN-1/payments").param("startDate", "bad-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("startDate must be a valid ISO date"));

        mockMvc.perform(get("/api/loans/LN-1/payments").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'page'"));
    }

    @Test
    void hidesUnexpectedExceptionMessage() throws Exception {
        when(loanService.getPaymentHistory("LN-1", null, null, null, 0, 20))
                .thenThrow(new RuntimeException("internal failure detail"));

        mockMvc.perform(get("/api/loans/LN-1/payments"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                                .doesNotContain("internal failure detail"));
    }

    @Test
    void mapsLoanDetailsNotFoundTo404() throws Exception {
        when(loanService.getLoanById("NOPE")).thenThrow(new LoanNotFoundException("NOPE"));

        mockMvc.perform(get("/api/loans/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/loans/NOPE"));
    }

    @Test
    void preservesErrorResponseStatusesForUnknownRoutesAndMethods() throws Exception {
        mockMvc.perform(get("/api/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));

        mockMvc.perform(post("/api/loans/LN-1/payments"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    private static PaymentDto payment() {
        PaymentDto payment = new PaymentDto();
        payment.setPaymentId("PMT-1");
        payment.setLoanAccountNumber("LN-1");
        payment.setPaymentDate("03/01/2025");
        payment.setType("Regular");
        payment.setStatus("Posted");
        return payment;
    }
}
