package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.PagedResponse;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.exception.InvalidRequestException;
import com.workshop.loanservice.exception.ResourceNotFoundException;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServicePaymentHistoryTest {

    private static final String LOAN_ID = "LN-TEST-001";

    @Mock private LegacyBorrowerRepository borrowerRepository;
    @Mock private LegacyLoanAccountRepository loanAccountRepository;
    @Mock private LegacyLoanProductRepository loanProductRepository;
    @Mock private LegacyPaymentRepository paymentRepository;

    @InjectMocks private LoanService loanService;

    @BeforeEach
    void setUp() {
        lenient().when(loanAccountRepository.existsById(LOAN_ID)).thenReturn(true);
        lenient().when(paymentRepository.findByLoanAccountNumber(LOAN_ID)).thenReturn(List.of(
                payment("P-01", "01/15/2025", "REG", "1,000.00"),
                payment("P-02", "02/15/2025", "EXT", "500.00"),
                payment("P-03", "03/15/2025", "REG", "1,000.00"),
                payment("P-04", "12/15/2024", "PRT", "250.00"),
                payment("P-05", "04/15/2025", "PRE", "10,000.00"),
                payment("P-06", "not-a-date", "REG", "1,000.00")
        ));
    }

    @Test
    void returnsAllPaymentsNewestFirstWithMalformedDatesLast() {
        PagedResponse<PaymentDto> result = history(null, null, null, 0, 20);

        assertThat(ids(result)).containsExactly("P-05", "P-03", "P-02", "P-01", "P-04", "P-06");
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void sortsAcrossYearBoundaryByActualDateNotString() {
        // "12/15/2024" > "04/15/2025" lexically; must still sort as the oldest dated payment.
        PagedResponse<PaymentDto> result = history(null, null, null, 0, 20);
        assertThat(ids(result).indexOf("P-04")).isGreaterThan(ids(result).indexOf("P-01"));
    }

    @Test
    void paginatesResults() {
        PagedResponse<PaymentDto> page0 = history(null, null, null, 0, 2);
        PagedResponse<PaymentDto> page1 = history(null, null, null, 1, 2);
        PagedResponse<PaymentDto> page2 = history(null, null, null, 2, 2);

        assertThat(ids(page0)).containsExactly("P-05", "P-03");
        assertThat(ids(page1)).containsExactly("P-02", "P-01");
        assertThat(ids(page2)).containsExactly("P-04", "P-06");
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.isFirst()).isTrue();
        assertThat(page0.isLast()).isFalse();
        assertThat(page2.isLast()).isTrue();
    }

    @Test
    void pageBeyondLastReturnsEmptyContent() {
        PagedResponse<PaymentDto> result = history(null, null, null, 10, 2);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void filtersByInclusiveDateRangeAndExcludesUnparseableDates() {
        PagedResponse<PaymentDto> result = history(
                LocalDate.of(2025, 1, 15), LocalDate.of(2025, 3, 15), null, 0, 20);

        assertThat(ids(result)).containsExactly("P-03", "P-02", "P-01");
    }

    @Test
    void filtersByStartDateOnly() {
        PagedResponse<PaymentDto> result = history(LocalDate.of(2025, 3, 1), null, null, 0, 20);
        assertThat(ids(result)).containsExactly("P-05", "P-03");
    }

    @Test
    void filtersByEndDateOnly() {
        PagedResponse<PaymentDto> result = history(null, LocalDate.of(2024, 12, 31), null, 0, 20);
        assertThat(ids(result)).containsExactly("P-04");
    }

    @Test
    void filtersBySinglePaymentTypeUsingAnySupportedSpelling() {
        assertThat(ids(history(null, null, "REG", 0, 20))).containsExactly("P-03", "P-01", "P-06");
        assertThat(ids(history(null, null, "regular", 0, 20))).containsExactly("P-03", "P-01", "P-06");
        assertThat(ids(history(null, null, "REGULAR", 0, 20))).containsExactly("P-03", "P-01", "P-06");
    }

    @Test
    void filtersByMultiplePaymentTypes() {
        PagedResponse<PaymentDto> result = history(null, null, "EXTRA, Prepayment", 0, 20);
        assertThat(ids(result)).containsExactly("P-05", "P-02");
    }

    @Test
    void combinesDateRangeTypeFilterAndPagination() {
        PagedResponse<PaymentDto> result = history(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "REG,PRE", 1, 2);

        assertThat(ids(result)).containsExactly("P-01");
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void mapsLegacyFieldsToDto() {
        PaymentDto dto = history(null, null, "PRE", 0, 20).getContent().get(0);

        assertThat(dto.getPaymentId()).isEqualTo("P-05");
        assertThat(dto.getLoanAccountNumber()).isEqualTo(LOAN_ID);
        assertThat(dto.getPaymentDate()).isEqualTo("04/15/2025");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(dto.getType()).isEqualTo("Prepayment");
        assertThat(dto.getStatus()).isEqualTo("Posted");
    }

    @Test
    void returnsEmptyPageWhenLoanHasNoPayments() {
        when(loanAccountRepository.existsById("LN-EMPTY")).thenReturn(true);
        when(paymentRepository.findByLoanAccountNumber("LN-EMPTY")).thenReturn(List.of());

        PagedResponse<PaymentDto> result = loanService.getPaymentHistory("LN-EMPTY", null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void throwsNotFoundForUnknownLoan() {
        when(loanAccountRepository.existsById("LN-MISSING")).thenReturn(false);

        assertThatThrownBy(() -> loanService.getPaymentHistory("LN-MISSING", null, null, null, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("LN-MISSING");
        verify(paymentRepository, never()).findByLoanAccountNumber(anyString());
    }

    @Test
    void rejectsStartDateAfterEndDate() {
        assertThatThrownBy(() -> history(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 1, 1), null, 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("startDate");
    }

    @Test
    void rejectsUnknownPaymentType() {
        assertThatThrownBy(() -> history(null, null, "REG,BOGUS", 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> history(null, null, null, -1, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("page");
    }

    @Test
    void rejectsOutOfRangePageSize() {
        assertThatThrownBy(() -> history(null, null, null, 0, 0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> history(null, null, null, 0, LoanService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void validatesParametersBeforeQueryingRepositories() {
        assertThatThrownBy(() -> loanService.getPaymentHistory("LN-ANY", null, null, "BOGUS", 0, 20))
                .isInstanceOf(InvalidRequestException.class);
        verify(loanAccountRepository, never()).existsById(anyString());
    }

    private PagedResponse<PaymentDto> history(LocalDate start, LocalDate end, String type, int page, int size) {
        return loanService.getPaymentHistory(LOAN_ID, start, end, type, page, size);
    }

    private static List<String> ids(PagedResponse<PaymentDto> response) {
        return response.getContent().stream().map(PaymentDto::getPaymentId).toList();
    }

    private static LegacyPayment payment(String id, String date, String typeCode, String amount) {
        LegacyPayment p = new LegacyPayment();
        p.setPaymentSequenceNumber(id);
        p.setLoanAccountNumber(LOAN_ID);
        p.setPaymentDate(date);
        p.setTotalAmount(amount);
        p.setPrincipalAmount("0.00");
        p.setInterestAmount("0.00");
        p.setEscrowAmount("0.00");
        p.setLateFee("0.00");
        p.setTypeCode(typeCode);
        p.setStatusCode("PST");
        return p;
    }
}
