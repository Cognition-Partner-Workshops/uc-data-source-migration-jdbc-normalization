package com.modernbank.datasource.model.staging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stg_loan", schema = "staging")
public class StagingLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_number", nullable = false)
    private String loanNumber;

    @Column(name = "loan_type")
    private String loanType;

    @Column(name = "loan_status")
    private String loanStatus;

    @Column(name = "loan_amount")
    private BigDecimal loanAmount;

    @Column(name = "interest_rate")
    private BigDecimal interestRate;

    @Column(name = "term_months")
    private Integer termMonths;

    @Column(name = "origination_date")
    private LocalDate originationDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "payment_frequency")
    private String paymentFrequency;

    @Column(name = "monthly_payment")
    private BigDecimal monthlyPayment;

    @Column(name = "outstanding_balance")
    private BigDecimal outstandingBalance;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "risk_rating")
    private String riskRating;

    @Column(name = "branch_code")
    private String branchCode;

    @Column(name = "officer_id")
    private String officerId;

    @Column(name = "delinquency_days")
    private Integer delinquencyDays = 0;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "batch_timestamp")
    private LocalDateTime batchTimestamp;

    @Column(name = "processing_status")
    private String processingStatus = "STAGED";

    public StagingLoan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLoanNumber() { return loanNumber; }
    public void setLoanNumber(String loanNumber) { this.loanNumber = loanNumber; }
    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }
    public String getLoanStatus() { return loanStatus; }
    public void setLoanStatus(String loanStatus) { this.loanStatus = loanStatus; }
    public BigDecimal getLoanAmount() { return loanAmount; }
    public void setLoanAmount(BigDecimal loanAmount) { this.loanAmount = loanAmount; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }
    public LocalDate getOriginationDate() { return originationDate; }
    public void setOriginationDate(LocalDate originationDate) { this.originationDate = originationDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public String getPaymentFrequency() { return paymentFrequency; }
    public void setPaymentFrequency(String paymentFrequency) { this.paymentFrequency = paymentFrequency; }
    public BigDecimal getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(BigDecimal monthlyPayment) { this.monthlyPayment = monthlyPayment; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public void setOutstandingBalance(BigDecimal outstandingBalance) { this.outstandingBalance = outstandingBalance; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getRiskRating() { return riskRating; }
    public void setRiskRating(String riskRating) { this.riskRating = riskRating; }
    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
    public String getOfficerId() { return officerId; }
    public void setOfficerId(String officerId) { this.officerId = officerId; }
    public Integer getDelinquencyDays() { return delinquencyDays; }
    public void setDelinquencyDays(Integer delinquencyDays) { this.delinquencyDays = delinquencyDays; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDateTime getBatchTimestamp() { return batchTimestamp; }
    public void setBatchTimestamp(LocalDateTime batchTimestamp) { this.batchTimestamp = batchTimestamp; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
}
