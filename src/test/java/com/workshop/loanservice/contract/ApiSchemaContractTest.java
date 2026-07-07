package com.workshop.loanservice.contract;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Schema contract tests: verify the shape and JSON types of the public API
 * responses. These pin the response contract so a data-source migration
 * (legacy → modern schema) cannot silently change field names or types.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSchemaContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loansListMatchesContract() throws Exception {
        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", Matchers.hasSize(5)))
                // string fields
                .andExpect(jsonPath("$[0].loanAccountNumber").isString())
                .andExpect(jsonPath("$[0].borrowerName").isString())
                .andExpect(jsonPath("$[0].productDescription").isString())
                .andExpect(jsonPath("$[0].status").isString())
                .andExpect(jsonPath("$[0].originationDate").isString())
                .andExpect(jsonPath("$[0].propertyAddress").isString())
                .andExpect(jsonPath("$[0].propertyType").isString())
                // numeric fields (typed in the DTO, parsed from legacy strings)
                .andExpect(jsonPath("$[0].originalAmount").isNumber())
                .andExpect(jsonPath("$[0].currentBalance").isNumber())
                .andExpect(jsonPath("$[0].interestRate").isNumber())
                .andExpect(jsonPath("$[0].monthlyPayment").isNumber());
    }

    @Test
    void singleLoanMatchesContractAndExpandsStatus() throws Exception {
        mockMvc.perform(get("/api/loans/{id}", "LN-2019-00142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$.borrowerName").value("James Mitchell"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andExpect(jsonPath("$.propertyType").value("Single Family Residence"))
                .andExpect(jsonPath("$.originalAmount").value(285000))
                .andExpect(jsonPath("$.interestRate").value(4.750));
    }

    @Test
    void borrowersListMatchesContract() throws Exception {
        mockMvc.perform(get("/api/borrowers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", Matchers.hasSize(5)))
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].fullName").isString())
                .andExpect(jsonPath("$[0].email").isString())
                .andExpect(jsonPath("$[0].phone").isString())
                .andExpect(jsonPath("$[0].city").isString())
                .andExpect(jsonPath("$[0].state").isString())
                .andExpect(jsonPath("$[0].employmentStatus").isString())
                .andExpect(jsonPath("$[0].creditScore").isNumber());
    }

    @Test
    void singleBorrowerIncludesTypedLoansArray() throws Exception {
        mockMvc.perform(get("/api/borrowers/{id}", "B-10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("B-10001"))
                .andExpect(jsonPath("$.fullName").value("James R. Mitchell"))
                .andExpect(jsonPath("$.creditScore").isNumber())
                .andExpect(jsonPath("$.loans").isArray())
                .andExpect(jsonPath("$.loans", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.loans[0].loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$.loans[0].currentBalance").isNumber());
    }

    @Test
    void dataQualityReportMatchesContract() throws Exception {
        mockMvc.perform(get("/api/data-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(25))
                .andExpect(jsonPath("$.overallScore").isNumber())
                .andExpect(jsonPath("$.errorCount").isNumber())
                .andExpect(jsonPath("$.tables").isArray())
                .andExpect(jsonPath("$.tables", Matchers.hasSize(4)));
    }
}
