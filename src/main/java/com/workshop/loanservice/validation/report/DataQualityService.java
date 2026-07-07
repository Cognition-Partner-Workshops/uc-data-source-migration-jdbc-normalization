package com.workshop.loanservice.validation.report;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.validation.BorrowerValidator;
import com.workshop.loanservice.validation.LoanAccountValidator;
import com.workshop.loanservice.validation.LoanProductValidator;
import com.workshop.loanservice.validation.PaymentValidator;
import com.workshop.loanservice.validation.RecordValidator;
import com.workshop.loanservice.validation.ReferenceData;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the table validators across all legacy records and produces a scored
 * {@link DataQualityReport}. This is the entry point used both by the
 * {@code DataQualityController} and by reconciliation/reporting tooling.
 */
@Service
public class DataQualityService {

    private final LegacyBorrowerRepository borrowerRepository;
    private final LegacyLoanAccountRepository loanAccountRepository;
    private final LegacyLoanProductRepository loanProductRepository;
    private final LegacyPaymentRepository paymentRepository;

    private final BorrowerValidator borrowerValidator;
    private final LoanProductValidator loanProductValidator;
    private final LoanAccountValidator loanAccountValidator;
    private final PaymentValidator paymentValidator;

    public DataQualityService(LegacyBorrowerRepository borrowerRepository,
                              LegacyLoanAccountRepository loanAccountRepository,
                              LegacyLoanProductRepository loanProductRepository,
                              LegacyPaymentRepository paymentRepository,
                              BorrowerValidator borrowerValidator,
                              LoanProductValidator loanProductValidator,
                              LoanAccountValidator loanAccountValidator,
                              PaymentValidator paymentValidator) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentRepository = paymentRepository;
        this.borrowerValidator = borrowerValidator;
        this.loanProductValidator = loanProductValidator;
        this.loanAccountValidator = loanAccountValidator;
        this.paymentValidator = paymentValidator;
    }

    /** Validate every legacy record and return the aggregated report. */
    public DataQualityReport generateReport() {
        List<LegacyBorrower> borrowers = borrowerRepository.findAll();
        List<LegacyLoanProduct> products = loanProductRepository.findAll();
        List<LegacyLoanAccount> accounts = loanAccountRepository.findAll();
        List<LegacyPayment> payments = paymentRepository.findAll();

        ReferenceData refs = new ReferenceData(
                keySet(borrowers, LegacyBorrower::getBorrowerId),
                keySet(products, LegacyLoanProduct::getProductCode),
                keySet(accounts, LegacyLoanAccount::getLoanAccountNumber));

        List<TableQualityReport> tables = List.of(
                scoreTable(borrowerValidator, borrowers, refs),
                scoreTable(loanProductValidator, products, refs),
                scoreTable(loanAccountValidator, accounts, refs),
                scoreTable(paymentValidator, payments, refs));

        return DataQualityReport.of(tables);
    }

    private <T> TableQualityReport scoreTable(RecordValidator<T> validator, List<T> records, ReferenceData refs) {
        List<RecordQualityScore> scores = records.stream()
                .map(record -> RecordQualityScore.of(
                        validator.tableName(),
                        validator.recordId(record),
                        validator.validate(record, refs)))
                .collect(Collectors.toList());
        return TableQualityReport.of(validator.tableName(), scores);
    }

    private <T> Set<String> keySet(List<T> records, java.util.function.Function<T, String> key) {
        return records.stream()
                .map(key)
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
