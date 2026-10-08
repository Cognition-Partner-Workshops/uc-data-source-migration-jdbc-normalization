package com.workshop.loanservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:paymenthist;DB_CLOSE_DELAY=-1")
class PaymentHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSeededPaymentsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].paymentDate").value("12/15/2025"))
                .andExpect(jsonPath("$.content[1].paymentDate").value("11/15/2025"));
    }

    @Test
    void filtersSeededPaymentsByDateAndType() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("startDate", "2025-12-01")
                        .param("endDate", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].paymentDate").value("12/15/2025"));

        mockMvc.perform(get("/api/loans/LN-2019-00142/payments").param("type", "EXT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void paginatesSeededPayments() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void returns404ForUnknownLoanAnd400ForInvalidDate() throws Exception {
        mockMvc.perform(get("/api/loans/NOPE/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/loans/LN-2019-00142/payments")
                        .param("startDate", "12-01-2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("startDate")));
    }
}
