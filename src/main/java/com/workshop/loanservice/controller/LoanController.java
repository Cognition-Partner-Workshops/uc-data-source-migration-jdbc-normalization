package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PagedResponse;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<LoanSummaryDto> getAllLoans() {
        return loanService.getAllLoans();
    }

    @GetMapping("/{id}")
    public LoanSummaryDto getLoan(@PathVariable String id) {
        return loanService.getLoanById(id);
    }

    /**
     * Paginated payment history for a loan, newest first.
     *
     * @param startDate inclusive, ISO-8601 (yyyy-MM-dd)
     * @param endDate   inclusive, ISO-8601 (yyyy-MM-dd)
     * @param type      comma-separated payment types: REGULAR/REG/Regular, EXTRA, PARTIAL, PREPAYMENT
     */
    @GetMapping("/{loanId}/payments")
    public PagedResponse<PaymentDto> getPayments(
            @PathVariable String loanId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + LoanService.DEFAULT_PAGE_SIZE) int size) {
        return loanService.getPaymentHistory(loanId, startDate, endDate, type, page, size);
    }
}
