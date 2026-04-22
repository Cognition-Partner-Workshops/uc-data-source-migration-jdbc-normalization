# Testing Report — Data Source Migration (Legacy to Modern)

## Overview

This report documents all tests added for the ODS-to-Staging-to-MV data pipeline. The application uses Spring Boot 3.2.5 with Spring Batch to extract operational data (JSON blobs + typed columns) from ODS tables, transform and stage it, then load it into Materialized View (MV) tables across separate PostgreSQL schemas.

**Total Tests: 27** | **Test Framework: JUnit 5 + Spring Boot Test + Spring Batch Test**

Tests run against an H2 in-memory database in PostgreSQL compatibility mode. Schemas (`ods`, `staging`, `mv_loan`, `mv_customer`, `mv_collateral`, `legacy_cdw`) are created via JDBC INIT parameters, JPA entity tables via Hibernate `ddl-auto=create`, and non-JPA MV tables via a `TestSchemaConfig` configuration bean.

---

## 1. Unit Tests — `OdsDataExtractorServiceTest` (9 tests)

Tests the core JSON extraction and transformation logic without Spring context.

| # | Test Name | What It Validates |
|---|-----------|-------------------|
| 1 | `transformLoan_withJsonBlob_extractsAllFields` | Extracts all 11 fields from a loan JSON blob (loan_type, loan_amount, interest_rate, term_months, payment_frequency, monthly_payment, customer_id, risk_rating, branch_code, officer_id, delinquency_days) and merges with normal ODS columns (loan_number, loan_status, origination_date, maturity_date, outstanding_balance). Verifies batch_id and processing_status set correctly. |
| 2 | `transformLoan_withNullJson_usesNormalColumnsOnly` | When `loan_details_json` is null, transformation still succeeds using only the typed ODS columns. JSON-derived fields remain null. |
| 3 | `transformLoan_withInvalidJson_handlesGracefully` | Malformed JSON (`"not valid json {{{"`) does not throw an exception. The service logs a warning and populates only the non-JSON fields. |
| 4 | `transformLoan_withPartialJson_extractsAvailableFields` | JSON blob containing only `loan_type` and `customer_id` correctly populates those fields while leaving others (loan_amount, interest_rate, term_months) null. |
| 5 | `transformCustomer_withJsonBlob_extractsAllFields` | Extracts all 8 customer detail fields from JSON (date_of_birth, email, phone, address_line, city, state, zip_code, employer) and merges with normal columns (customer_id, customer_name, credit_score, annual_income). |
| 6 | `transformCustomer_withEmptyJson_usesNormalColumnsOnly` | Empty string JSON (`""`) is handled gracefully, using only typed columns. |
| 7 | `transformCustomer_withInvalidDateInJson_handlesGracefully` | Invalid date format (`"not-a-date"`) in `date_of_birth` field does not crash; date is set to null while other valid fields (email) are still extracted. |
| 8 | `transformCollateral_withJsonBlob_extractsAllFields` | Extracts description and address from JSON blob, merges with typed columns (collateral_id, loan_number, collateral_type, appraised_value, appraisal_date). |
| 9 | `transformCollateral_withNullJson_usesNormalColumnsOnly` | Null JSON blob handled gracefully; only typed ODS columns populate the staging entity. |

---

## 2. Integration Tests — `StagingToMvServiceTest` (5 tests)

Tests the staging-to-MV SQL load operations using `@Sql` annotations to seed test data.

| # | Test Name | What It Validates |
|---|-----------|-------------------|
| 1 | `loadLoanSummary_insertsIntoMvLoan` | Seeds one `STAGED` loan in `staging.stg_loan`, calls `loadLoanSummary()`, verifies exactly 1 row inserted into `mv_loan.loan_summary`. |
| 2 | `loadCustomerProfile_insertsIntoMvCustomer` | Seeds one `STAGED` customer, loads MV, verifies row in `mv_customer.customer_profile` and staging `processing_status` updated to `MV_LOADED`. |
| 3 | `loadCollateralRegistry_insertsIntoMvCollateral` | Seeds one `STAGED` collateral, loads MV, verifies row in `mv_collateral.collateral_registry` and staging status updated to `MV_LOADED`. |
| 4 | `loadAllMaterializedViews_populatesAllMvTables` | Seeds loan + customer + collateral with same batch_id, calls `loadAllMaterializedViews()`, verifies all 4 MV tables populated (`loan_summary`, `loan_performance`, `customer_profile`, `collateral_registry`). |
| 5 | `loadLoanSummary_withNoStagedData_doesNothing` | Calls `loadLoanSummary()` with no staged data present. Verifies zero rows in MV table (no errors thrown). |

---

## 3. Batch Job Tests — `OdsStagingBatchConfigTest` (5 tests)

Tests the Spring Batch job that reads from ODS and writes to staging tables.

| # | Test Name | What It Validates |
|---|-----------|-------------------|
| 1 | `odsStagingJob_processesLoanRecords` | Saves one unprocessed `OdsLoanRecord` with JSON blob, launches batch job, verifies: (a) job completes with `COMPLETED` status, (b) one row in `staging.stg_loan`, (c) `loan_type` extracted from JSON. |
| 2 | `odsStagingJob_processesCustomerRecords` | Saves one unprocessed `OdsCustomerRecord` with JSON, launches job, verifies staging row created in `staging.stg_customer`. |
| 3 | `odsStagingJob_processesCollateralRecords` | Saves one unprocessed `OdsCollateralRecord` with JSON, launches job, verifies staging row in `staging.stg_collateral`. |
| 4 | `odsStagingJob_withNoUnprocessedRecords_completesSuccessfully` | Launches job with empty ODS tables. Verifies job completes without error (no NPE, no empty batch failures). |
| 5 | `odsStagingJob_processesMultipleRecordsInBatch` | Saves 5 unprocessed loan records, launches job, verifies all 5 appear in staging. Validates batch chunking works correctly. |

---

## 4. Repository Tests — `OdsRepositoryTest` (5 tests)

Tests the JPA repository custom queries for ODS entities.

| # | Test Name | What It Validates |
|---|-----------|-------------------|
| 1 | `findByProcessedFalse_returnsUnprocessedRecords` | With one processed and one unprocessed record, `findByProcessedFalse()` returns only the unprocessed one. |
| 2 | `findByProcessedFalse_withPagination_returnsPagedResults` | With 15 unprocessed records and page size 10, verifies page contains 10 items, total elements is 15, total pages is 2. |
| 3 | `countByProcessedFalse_returnsCorrectCount` | With 3 unprocessed and 1 processed record, `countByProcessedFalse()` returns 3. |
| 4 | `markAsProcessed_updatesProcessedFlag` | Saves an unprocessed record, calls `markAsProcessed()` with its ID, verifies 1 row updated. |
| 5 | `customerRepository_savesAndRetrievesWithJson` | Saves an `OdsCustomerRecord` with a JSON blob, retrieves by ID, verifies all fields including JSON string preserved exactly. |

---

## 5. End-to-End Integration Tests — `FullPipelineIntegrationTest` (2 tests)

Tests the complete ODS -> Staging -> MV pipeline.

| # | Test Name | What It Validates |
|---|-----------|-------------------|
| 1 | `fullPipeline_odsToStagingToMv_worksEndToEnd` | Seeds ODS with loan (JSON: loan_type=MORTGAGE, customer_id, interest_rate, etc.), customer (JSON: email, phone, address, etc.), and collateral (JSON: description, address). Runs batch job, verifies: (a) 3 staging rows created, (b) JSON extracted correctly (loan_type=MORTGAGE, email=e2e@test.com), (c) all 4 MV tables populated, (d) MV data matches source (loan_type=MORTGAGE, customer_name preserved), (e) staging status updated to MV_LOADED. |
| 2 | `fullPipeline_withEmptyOds_completesWithoutErrors` | Runs complete pipeline with empty ODS. Verifies batch job completes and MV load succeeds with zero rows (no NPE or SQL errors). |

---

## 6. Application Context Test — `DataSourceMigrationApplicationTest` (1 test)

| # | Test Name | What It Validates |
|---|-----------|-------------------|
| 1 | `contextLoads` | Verifies the Spring Boot application context loads successfully with all beans wired (DataSource, JPA, Spring Batch, services, repositories). |

---

## Test Infrastructure

| Component | Technology |
|-----------|-----------|
| Test DB | H2 in-memory (PostgreSQL mode) |
| Schema creation | JDBC INIT parameter + Hibernate ddl-auto + TestSchemaConfig |
| Test profile | `@ActiveProfiles("test")` → `application-test.properties` |
| Batch testing | `@SpringBatchTest` + `JobLauncherTestUtils` |
| Data seeding | `@Sql` annotations (MV tests), programmatic via repositories (batch/pipeline tests) |
| Assertions | JUnit 5 `assertEquals`, `assertNotNull`, `assertNull`, `assertFalse`, `assertTrue` |

## Test Coverage Summary

| Layer | Tests | Coverage Focus |
|-------|-------|---------------|
| JSON extraction / transformation | 9 | Valid JSON, null JSON, invalid JSON, partial JSON, invalid dates |
| Staging-to-MV SQL operations | 5 | Insert into MV, status update, empty data handling |
| Spring Batch job execution | 5 | Single record, multiple records, empty input, JSON extraction in batch |
| JPA repository queries | 5 | Custom finder methods, pagination, bulk update, JSON persistence |
| End-to-end pipeline | 2 | Full flow (ODS->Staging->MV), empty flow |
| Application bootstrap | 1 | Context loads with all beans |
| **Total** | **27** | |
