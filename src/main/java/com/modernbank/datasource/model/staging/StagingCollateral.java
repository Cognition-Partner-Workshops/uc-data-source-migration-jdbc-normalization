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
@Table(name = "stg_collateral", schema = "staging")
public class StagingCollateral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collateral_id", nullable = false)
    private String collateralId;

    @Column(name = "loan_number")
    private String loanNumber;

    @Column(name = "collateral_type")
    private String collateralType;

    @Column(name = "description")
    private String description;

    @Column(name = "appraised_value")
    private BigDecimal appraisedValue;

    @Column(name = "address")
    private String address;

    @Column(name = "appraisal_date")
    private LocalDate appraisalDate;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "batch_timestamp")
    private LocalDateTime batchTimestamp;

    @Column(name = "processing_status")
    private String processingStatus = "STAGED";

    public StagingCollateral() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCollateralId() { return collateralId; }
    public void setCollateralId(String collateralId) { this.collateralId = collateralId; }
    public String getLoanNumber() { return loanNumber; }
    public void setLoanNumber(String loanNumber) { this.loanNumber = loanNumber; }
    public String getCollateralType() { return collateralType; }
    public void setCollateralType(String collateralType) { this.collateralType = collateralType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAppraisedValue() { return appraisedValue; }
    public void setAppraisedValue(BigDecimal appraisedValue) { this.appraisedValue = appraisedValue; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDate getAppraisalDate() { return appraisalDate; }
    public void setAppraisalDate(LocalDate appraisalDate) { this.appraisalDate = appraisalDate; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDateTime getBatchTimestamp() { return batchTimestamp; }
    public void setBatchTimestamp(LocalDateTime batchTimestamp) { this.batchTimestamp = batchTimestamp; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
}
