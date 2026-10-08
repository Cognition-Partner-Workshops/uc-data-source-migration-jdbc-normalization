package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.PagedResponse;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.exception.InvalidRequestException;
import com.workshop.loanservice.exception.LoanNotFoundException;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServicePaymentHistoryTest {

    private static final String LOAN_ID = "LN-TEST";

    @Mock
    private LegacyBorrowerRepository borrowerRepository;

    @Mock
    private LegacyLoanAccountRepository loanAccountRepository;

    @Mock
    private LegacyLoanProductRepository loanProductRepository;

    @Mock
    private LegacyPaymentRepository paymentRepository;

    @InjectMocks
    private LoanService loanService;

    private final List<LegacyPayment> payments = List.of(
            payment("10", "12/15/2024", "REG"),
            payment("11", "01/10/2025", "EXT"),
            payment("13", "01/10/2025", "PRT"),
            payment("12", "02/28/2025", "REG"),
            payment("14", "03/01/2025", "EXT"),
            payment("15", "not-a-date", "REG"));

    @Test
    void sortsByParsedDateAcrossYearsThenBySequenceAndPlacesMalformedDatesLast() {
        stubHistory();

        PagedResponse<PaymentDto> result = getHistory(null, null, null, 0, 20);

        assertPaymentIds(result, "14", "12", "13", "11", "10", "15");
    }

    @Test
    void appliesInclusiveDateBoundsAndEachSingleBound() {
        stubHistory();

        assertPaymentIds(getHistory("2025-01-10", "2025-02-28", null, 0, 20),
                "12", "13", "11");
        assertPaymentIds(getHistory("2025-02-28", null, null, 0, 20), "14", "12");
        assertPaymentIds(getHistory(null, "2024-12-15", null, 0, 20), "10");
    }

    @Test
    void resolvesPaymentTypeByCodeNameLabelAndMultipleValuesCaseInsensitively() {
        stubHistory();

        assertPaymentIds(getHistory(null, null, "REG", 0, 20), "12", "10", "15");
        assertPaymentIds(getHistory(null, null, "EXTRA", 0, 20), "14", "11");
        assertPaymentIds(getHistory(null, null, "pArTiAl", 0, 20), "13");
        assertPaymentIds(getHistory(null, null, "rEgUlAr", 0, 20), "12", "10", "15");
        assertPaymentIds(getHistory(null, null, "REG, pRt", 0, 20), "12", "13", "10", "15");
    }

    @Test
    void combinesDateAndTypeFiltersAndExcludesMalformedDatesWhenFilteringDates() {
        stubHistory();

        PagedResponse<PaymentDto> combined = getHistory(
                "2025-01-10", "2025-02-28", "Regular", 0, 20);
        assertPaymentIds(combined, "12");

        PagedResponse<PaymentDto> dateFiltered = getHistory(
                "2024-01-01", null, null, 0, 20);
        assertThat(dateFiltered.getTotalElements()).isEqualTo(5);
        assertThat(dateFiltered.getContent()).extracting(PaymentDto::getPaymentId)
                .doesNotContain("15");
    }

    @Test
    void paginatesWithCountsAndReturnsEmptyPageBeyondTheEnd() {
        stubHistory();

        PagedResponse<PaymentDto> first = getHistory(null, null, null, 0, 4);
        assertPaymentIds(first, "14", "12", "13", "11");
        assertThat(first.getTotalElements()).isEqualTo(6);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.isFirst()).isTrue();
        assertThat(first.isLast()).isFalse();

        PagedResponse<PaymentDto> last = getHistory(null, null, null, 1, 4);
        assertPaymentIds(last, "10", "15");
        assertThat(last.isFirst()).isFalse();
        assertThat(last.isLast()).isTrue();

        PagedResponse<PaymentDto> beyondEnd = getHistory(null, null, null, 2, 4);
        assertThat(beyondEnd.getContent()).isEmpty();
        assertThat(beyondEnd.getTotalElements()).isEqualTo(6);
        assertThat(beyondEnd.getTotalPages()).isEqualTo(2);
        assertThat(beyondEnd.isLast()).isTrue();
    }

    @Test
    void emptyResultHasZeroPagesAndIsBothFirstAndLast() {
        stubHistory();

        PagedResponse<PaymentDto> result = getHistory(null, null, "PRE", 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void rejectsInvalidQueriesBeforeCallingRepositories(
            int page, int size, String startDate, String endDate, String type, String messagePart) {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> getHistory(startDate, endDate, type, page, size));

        assertThat(exception.getMessage()).contains(messagePart);
        verifyNoInteractions(
                borrowerRepository, loanAccountRepository, loanProductRepository, paymentRepository);
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of(-1, 20, null, null, null, "page"),
                Arguments.of(0, 0, null, null, null, "size"),
                Arguments.of(0, 101, null, null, null, "size"),
                Arguments.of(0, 20, "12-01-2025", null, null, "startDate"),
                Arguments.of(0, 20, "2025-02-01", "2025-01-01", null, "startDate"),
                Arguments.of(0, 20, null, null, "HOLIDAY", "REGULAR"),
                Arguments.of(0, 20, null, null, ",", "type"),
                Arguments.of(0, 20, null, null, "", "type"),
                Arguments.of(0, 20, null, null, "REG,", "type"));
    }

    @Test
    void throwsLoanNotFoundWhenLoanDoesNotExist() {
        when(loanAccountRepository.existsById(LOAN_ID)).thenReturn(false);

        assertThrows(LoanNotFoundException.class,
                () -> getHistory(null, null, null, 0, 20));

        verify(loanAccountRepository).existsById(LOAN_ID);
        verifyNoInteractions(borrowerRepository, loanProductRepository, paymentRepository);
    }

    private void stubHistory() {
        when(loanAccountRepository.existsById(LOAN_ID)).thenReturn(true);
        when(paymentRepository.findByLoanAccountNumber(LOAN_ID)).thenReturn(payments);
    }

    private PagedResponse<PaymentDto> getHistory(
            String startDate, String endDate, String type, int page, int size) {
        return loanService.getPaymentHistory(LOAN_ID, startDate, endDate, type, page, size);
    }

    private static void assertPaymentIds(PagedResponse<PaymentDto> result, String... ids) {
        assertThat(result.getContent()).extracting(PaymentDto::getPaymentId).containsExactly(ids);
    }

    private static LegacyPayment payment(String sequence, String date, String type) {
        LegacyPayment payment = new LegacyPayment();
        payment.setPaymentSequenceNumber(sequence);
        payment.setLoanAccountNumber(LOAN_ID);
        payment.setPaymentDate(date);
        payment.setTypeCode(type);
        payment.setTotalAmount("100.00");
        return payment;
    }
}
