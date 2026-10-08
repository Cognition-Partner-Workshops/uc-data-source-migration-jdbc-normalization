package com.workshop.loanservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against the seeded legacy H2 schema (data-legacy.sql).
 * Uses its own in-memory database so the schema script does not collide with
 * other Spring contexts that share the default DB_CLOSE_DELAY=-1 database.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-history-it;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PaymentHistoryIntegrationTest {

    private static final String URL = "/api/loans/{id}/payments";
    private static final String LOAN_ID = "LN-2019-00142";

    @Autowired private MockMvc mockMvc;

    @Test
    void returnsSeededPaymentsNewestFirst() throws Exception {
        mockMvc.perform(get(URL, LOAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].paymentId").value("PMT-2025120001"))
                .andExpect(jsonPath("$.content[0].paymentDate").value("12/15/2025"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(1487.02))
                .andExpect(jsonPath("$.content[0].type").value("Regular"))
                .andExpect(jsonPath("$.content[0].status").value("Posted"))
                .andExpect(jsonPath("$.content[1].paymentId").value("PMT-2025110001"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void paginatesSeededPayments() throws Exception {
        mockMvc.perform(get(URL, LOAN_ID).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].paymentId").value("PMT-2025110001"))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void filtersSeededPaymentsByDateRange() throws Exception {
        mockMvc.perform(get(URL, LOAN_ID).param("startDate", "2025-11-01").param("endDate", "2025-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].paymentId").value("PMT-2025110001"));
    }

    @Test
    void filtersSeededPaymentsByType() throws Exception {
        mockMvc.perform(get(URL, LOAN_ID).param("type", "REGULAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get(URL, LOAN_ID).param("type", "EXTRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void returns404ForUnknownLoan() throws Exception {
        mockMvc.perform(get(URL, "LN-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Loan not found: LN-DOES-NOT-EXIST"));
    }

    @Test
    void returns400ForInvertedDateRange() throws Exception {
        mockMvc.perform(get(URL, LOAN_ID).param("startDate", "2025-12-31").param("endDate", "2025-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForUnknownPaymentType() throws Exception {
        mockMvc.perform(get(URL, LOAN_ID).param("type", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForEmptyPaymentTypeList() throws Exception {
        mockMvc.perform(get(URL, LOAN_ID).param("type", ","))
                .andExpect(status().isBadRequest());
    }

    @Test
    void existingLoanLookupNowReturns404InsteadOf500() throws Exception {
        mockMvc.perform(get("/api/loans/{id}", "LN-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }
}
