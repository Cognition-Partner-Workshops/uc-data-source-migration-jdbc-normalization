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
@Table(name = "ods_collateral_records", schema = "ods")
public class OdsCollateralRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collateral_id", nullable = false)
    private String collateralId;

    @Column(name = "loan_number")
    private String loanNumber;

    @Column(name = "collateral_type")
    private String collateralType;

    @Column(name = "collateral_details_json", columnDefinition = "TEXT")
    private String collateralDetailsJson;

    @Column(name = "appraised_value")
    private BigDecimal appraisedValue;

    @Column(name = "appraisal_date")
    private LocalDate appraisalDate;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    @Column(name = "processed")
    private Boolean processed = false;

    public OdsCollateralRecord() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCollateralId() {
        return collateralId;
    }

    public void setCollateralId(String collateralId) {
        this.collateralId = collateralId;
    }

    public String getLoanNumber() {
        return loanNumber;
    }

    public void setLoanNumber(String loanNumber) {
        this.loanNumber = loanNumber;
    }

    public String getCollateralType() {
        return collateralType;
    }

    public void setCollateralType(String collateralType) {
        this.collateralType = collateralType;
    }

    public String getCollateralDetailsJson() {
        return collateralDetailsJson;
    }

    public void setCollateralDetailsJson(String collateralDetailsJson) {
        this.collateralDetailsJson = collateralDetailsJson;
    }

    public BigDecimal getAppraisedValue() {
        return appraisedValue;
    }

    public void setAppraisedValue(BigDecimal appraisedValue) {
        this.appraisedValue = appraisedValue;
    }

    public LocalDate getAppraisalDate() {
        return appraisalDate;
    }

    public void setAppraisalDate(LocalDate appraisalDate) {
        this.appraisalDate = appraisalDate;
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
