package com.modernbank.datasource.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernbank.datasource.model.ods.OdsCollateralRecord;
import com.modernbank.datasource.model.ods.OdsCustomerRecord;
import com.modernbank.datasource.model.ods.OdsLoanRecord;
import com.modernbank.datasource.model.staging.StagingCollateral;
import com.modernbank.datasource.model.staging.StagingCustomer;
import com.modernbank.datasource.model.staging.StagingLoan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Extracts and transforms data from ODS records (JSON blob + normal columns)
 * into staging table entities with proper types.
 */
@Service
public class OdsDataExtractorService {

    private static final Logger log = LoggerFactory.getLogger(OdsDataExtractorService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;

    public OdsDataExtractorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StagingLoan transformLoan(OdsLoanRecord odsRecord, Long batchId) {
        StagingLoan staging = new StagingLoan();
        staging.setLoanNumber(odsRecord.getLoanNumber());
        staging.setLoanStatus(odsRecord.getLoanStatus());
        staging.setOriginationDate(odsRecord.getOriginationDate());
        staging.setMaturityDate(odsRecord.getMaturityDate());
        staging.setOutstandingBalance(odsRecord.getOutstandingBalance());
        staging.setBatchId(batchId);
        staging.setBatchTimestamp(LocalDateTime.now());
        staging.setProcessingStatus("STAGED");

        if (odsRecord.getLoanDetailsJson() != null && !odsRecord.getLoanDetailsJson().isBlank()) {
            try {
                JsonNode json = objectMapper.readTree(odsRecord.getLoanDetailsJson());
                staging.setLoanType(getTextValue(json, "loan_type"));
                staging.setLoanAmount(getDecimalValue(json, "loan_amount"));
                staging.setInterestRate(getDecimalValue(json, "interest_rate"));
                staging.setTermMonths(getIntValue(json, "term_months"));
                staging.setPaymentFrequency(getTextValue(json, "payment_frequency"));
                staging.setMonthlyPayment(getDecimalValue(json, "monthly_payment"));
                staging.setCustomerId(getTextValue(json, "customer_id"));
                staging.setRiskRating(getTextValue(json, "risk_rating"));
                staging.setBranchCode(getTextValue(json, "branch_code"));
                staging.setOfficerId(getTextValue(json, "officer_id"));
                staging.setDelinquencyDays(getIntValue(json, "delinquency_days"));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse loan JSON for loan_number={}: {}",
                        odsRecord.getLoanNumber(), e.getMessage());
            }
        }

        return staging;
    }

    public StagingCustomer transformCustomer(OdsCustomerRecord odsRecord, Long batchId) {
        StagingCustomer staging = new StagingCustomer();
        staging.setCustomerId(odsRecord.getCustomerId());
        staging.setCustomerName(odsRecord.getCustomerName());
        staging.setCreditScore(odsRecord.getCreditScore());
        staging.setAnnualIncome(odsRecord.getAnnualIncome());
        staging.setBatchId(batchId);
        staging.setBatchTimestamp(LocalDateTime.now());
        staging.setProcessingStatus("STAGED");

        if (odsRecord.getCustomerDetailsJson() != null
                && !odsRecord.getCustomerDetailsJson().isBlank()) {
            try {
                JsonNode json = objectMapper.readTree(odsRecord.getCustomerDetailsJson());
                staging.setDateOfBirth(getDateValue(json, "date_of_birth"));
                staging.setEmail(getTextValue(json, "email"));
                staging.setPhone(getTextValue(json, "phone"));
                staging.setAddressLine(getTextValue(json, "address_line"));
                staging.setCity(getTextValue(json, "city"));
                staging.setState(getTextValue(json, "state"));
                staging.setZipCode(getTextValue(json, "zip_code"));
                staging.setEmployer(getTextValue(json, "employer"));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse customer JSON for customer_id={}: {}",
                        odsRecord.getCustomerId(), e.getMessage());
            }
        }

        return staging;
    }

    public StagingCollateral transformCollateral(OdsCollateralRecord odsRecord, Long batchId) {
        StagingCollateral staging = new StagingCollateral();
        staging.setCollateralId(odsRecord.getCollateralId());
        staging.setLoanNumber(odsRecord.getLoanNumber());
        staging.setCollateralType(odsRecord.getCollateralType());
        staging.setAppraisedValue(odsRecord.getAppraisedValue());
        staging.setAppraisalDate(odsRecord.getAppraisalDate());
        staging.setBatchId(batchId);
        staging.setBatchTimestamp(LocalDateTime.now());
        staging.setProcessingStatus("STAGED");

        if (odsRecord.getCollateralDetailsJson() != null
                && !odsRecord.getCollateralDetailsJson().isBlank()) {
            try {
                JsonNode json = objectMapper.readTree(odsRecord.getCollateralDetailsJson());
                staging.setDescription(getTextValue(json, "description"));
                staging.setAddress(getTextValue(json, "address"));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse collateral JSON for collateral_id={}: {}",
                        odsRecord.getCollateralId(), e.getMessage());
            }
        }

        return staging;
    }

    private String getTextValue(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    private BigDecimal getDecimalValue(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node != null && !node.isNull()) {
            try {
                return new BigDecimal(node.asText());
            } catch (NumberFormatException e) {
                log.warn("Invalid decimal for field {}: {}", field, node.asText());
            }
        }
        return null;
    }

    private Integer getIntValue(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node != null && !node.isNull()) {
            try {
                return Integer.parseInt(node.asText());
            } catch (NumberFormatException e) {
                log.warn("Invalid integer for field {}: {}", field, node.asText());
            }
        }
        return null;
    }

    private LocalDate getDateValue(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node != null && !node.isNull()) {
            try {
                return LocalDate.parse(node.asText(), DATE_FORMAT);
            } catch (DateTimeParseException e) {
                log.warn("Invalid date for field {}: {}", field, node.asText());
            }
        }
        return null;
    }
}
