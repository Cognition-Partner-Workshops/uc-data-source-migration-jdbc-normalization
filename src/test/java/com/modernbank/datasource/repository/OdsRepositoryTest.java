package com.modernbank.datasource.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modernbank.datasource.model.ods.OdsCustomerRecord;
import com.modernbank.datasource.model.ods.OdsLoanRecord;
import com.modernbank.datasource.repository.ods.OdsCustomerRepository;
import com.modernbank.datasource.repository.ods.OdsLoanRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OdsRepositoryTest {

    @Autowired
    private OdsLoanRepository loanRepository;

    @Autowired
    private OdsCustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void findByProcessedFalse_returnsUnprocessedRecords() {
        OdsLoanRecord processed = new OdsLoanRecord();
        processed.setLoanNumber("PROC-001");
        processed.setProcessed(true);
        loanRepository.save(processed);

        OdsLoanRecord unprocessed = new OdsLoanRecord();
        unprocessed.setLoanNumber("UNPROC-001");
        unprocessed.setProcessed(false);
        loanRepository.save(unprocessed);

        List<OdsLoanRecord> results = loanRepository.findByProcessedFalse();

        assertEquals(1, results.size());
        assertEquals("UNPROC-001", results.get(0).getLoanNumber());
    }

    @Test
    void findByProcessedFalse_withPagination_returnsPagedResults() {
        for (int i = 0; i < 15; i++) {
            OdsLoanRecord record = new OdsLoanRecord();
            record.setLoanNumber("PAGE-" + i);
            record.setProcessed(false);
            loanRepository.save(record);
        }

        Page<OdsLoanRecord> page = loanRepository.findByProcessedFalse(PageRequest.of(0, 10));

        assertEquals(10, page.getContent().size());
        assertEquals(15, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void countByProcessedFalse_returnsCorrectCount() {
        for (int i = 0; i < 3; i++) {
            OdsLoanRecord record = new OdsLoanRecord();
            record.setLoanNumber("COUNT-" + i);
            record.setProcessed(false);
            loanRepository.save(record);
        }
        OdsLoanRecord processed = new OdsLoanRecord();
        processed.setLoanNumber("COUNT-PROC");
        processed.setProcessed(true);
        loanRepository.save(processed);

        long count = loanRepository.countByProcessedFalse();
        assertEquals(3, count);
    }

    @Test
    void markAsProcessed_updatesProcessedFlag() {
        OdsLoanRecord record = new OdsLoanRecord();
        record.setLoanNumber("MARK-001");
        record.setProcessed(false);
        OdsLoanRecord saved = loanRepository.save(record);

        int updated = loanRepository.markAsProcessed(List.of(saved.getId()));

        assertEquals(1, updated);
    }

    @Test
    void customerRepository_savesAndRetrievesWithJson() {
        OdsCustomerRecord customer = new OdsCustomerRecord();
        customer.setCustomerId("REPO-CUST-001");
        customer.setCustomerName("Repo Test");
        customer.setCreditScore(750);
        customer.setAnnualIncome(new BigDecimal("100000.00"));
        customer.setCustomerDetailsJson("{\"email\":\"test@repo.com\"}");
        customer.setProcessed(false);

        OdsCustomerRecord saved = customerRepository.save(customer);

        assertNotNull(saved.getId());
        OdsCustomerRecord found = customerRepository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("REPO-CUST-001", found.getCustomerId());
        assertEquals("{\"email\":\"test@repo.com\"}", found.getCustomerDetailsJson());
        assertFalse(found.getProcessed());
    }
}
