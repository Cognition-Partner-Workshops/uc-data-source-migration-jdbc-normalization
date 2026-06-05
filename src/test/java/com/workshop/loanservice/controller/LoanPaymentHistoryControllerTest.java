package com.workshop.loanservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoanPaymentHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPaymentHistory_returnsPagedResults() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void getPaymentHistory_defaultPagination() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getPaymentHistory_secondPageEmpty() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getPaymentHistory_filterByDateRange() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("startDate", "2025-12-01")
                        .param("endDate", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].paymentDate").value("12/15/2025"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getPaymentHistory_filterByStartDateOnly() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("startDate", "2025-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].paymentDate").value("12/15/2025"));
    }

    @Test
    void getPaymentHistory_filterByEndDateOnly() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("endDate", "2025-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].paymentDate").value("11/15/2025"));
    }

    @Test
    void getPaymentHistory_filterByPaymentType() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("paymentType", "REG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(2)))
                .andExpect(jsonPath("$.payments[0].type").value("Regular"));
    }

    @Test
    void getPaymentHistory_filterByPaymentTypeCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("paymentType", "reg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(2)));
    }

    @Test
    void getPaymentHistory_noMatchingPaymentType() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("paymentType", "PRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getPaymentHistory_invalidLoanId_returns404() throws Exception {
        mockMvc.perform(get("/api/loans/INVALID-LOAN-ID/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Loan not found: INVALID-LOAN-ID"))
                .andExpect(jsonPath("$.loanId").value("INVALID-LOAN-ID"));
    }

    @Test
    void getPaymentHistory_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("startDate", "12/01/2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("Invalid date format")));
    }

    @Test
    void getPaymentHistory_combinedFilters() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("startDate", "2025-11-01")
                        .param("endDate", "2025-11-30")
                        .param("paymentType", "REG")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].paymentDate").value("11/15/2025"))
                .andExpect(jsonPath("$.payments[0].type").value("Regular"));
    }

    @Test
    void getPaymentHistory_paymentDtoFieldsPresent() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].paymentId").exists())
                .andExpect(jsonPath("$.payments[0].loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$.payments[0].totalAmount").isNumber())
                .andExpect(jsonPath("$.payments[0].principalAmount").isNumber())
                .andExpect(jsonPath("$.payments[0].interestAmount").isNumber())
                .andExpect(jsonPath("$.payments[0].type").exists())
                .andExpect(jsonPath("$.payments[0].status").value("Posted"));
    }

    @Test
    void getPaymentHistory_resultsOrderedByDateDesc() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].paymentDate").value("12/15/2025"))
                .andExpect(jsonPath("$.payments[1].paymentDate").value("11/15/2025"));
    }

    @Test
    void getLoan_invalidId_returns404() throws Exception {
        mockMvc.perform(get("/api/loans/NONEXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.loanId").value("NONEXISTENT"));
    }
}
