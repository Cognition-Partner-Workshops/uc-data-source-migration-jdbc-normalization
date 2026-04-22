package com.modernbank.datasource.model.ods;

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
@Table(name = "ods_loan_records", schema = "ods")
public class OdsLoanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_number", nullable = false)
    private String loanNumber;

    @Column(name = "loan_status")
    private String loanStatus;

    @Column(name = "loan_details_json", columnDefinition = "TEXT")
    private String loanDetailsJson;

    @Column(name = "origination_date")
    private LocalDate originationDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "outstanding_balance")
    private BigDecimal outstandingBalance;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    @Column(name = "processed")
    private Boolean processed = false;

    public OdsLoanRecord() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLoanNumber() {
        return loanNumber;
    }

    public void setLoanNumber(String loanNumber) {
        this.loanNumber = loanNumber;
    }

    public String getLoanStatus() {
        return loanStatus;
    }

    public void setLoanStatus(String loanStatus) {
        this.loanStatus = loanStatus;
    }

    public String getLoanDetailsJson() {
        return loanDetailsJson;
    }

    public void setLoanDetailsJson(String loanDetailsJson) {
        this.loanDetailsJson = loanDetailsJson;
    }

    public LocalDate getOriginationDate() {
        return originationDate;
    }

    public void setOriginationDate(LocalDate originationDate) {
        this.originationDate = originationDate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public void setMaturityDate(LocalDate maturityDate) {
        this.maturityDate = maturityDate;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(BigDecimal outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public Boolean getProcessed() {
        return processed;
    }

    public void setProcessed(Boolean processed) {
        this.processed = processed;
    }
}
