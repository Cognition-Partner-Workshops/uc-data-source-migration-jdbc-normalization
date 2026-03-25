-- =============================================================================
-- TEST SEED DATA — deterministic dataset for Playwright E2E tests
-- =============================================================================
-- This script first clears all tables, then inserts a small, predictable
-- dataset that test assertions can rely on.

-- Clear existing data (order matters due to implicit references)
DELETE FROM CDW_PMT_HIST;
DELETE FROM CDW_LN_ACCT;
DELETE FROM CDW_LN_PROD;
DELETE FROM CDW_BORR_MSTR;

-- Test Borrowers (2)
INSERT INTO CDW_BORR_MSTR VALUES ('T-B001', 'Alice', 'Test', NULL, 'ENC_T_001', '06/15/1985', '100 Main St', 'Apt 4A', 'Springfield', 'IL', '60001', '217-555-0001', 'alice.test@email.com', '750', 'EMPLOYED', '95,000', '01/01/2020', '01/01/2025', 'ACT', 'PRI');
INSERT INTO CDW_BORR_MSTR VALUES ('T-B002', 'Bob', 'Sample', 'J', 'ENC_T_002', '03/22/1978', '200 Oak Ave', NULL, 'Austin', 'TX', '73301', '512-555-0002', 'bob.sample@email.com', '680', 'SELF-EMP', '72,000', '06/15/2019', '06/15/2025', 'ACT', 'PRI');

-- Test Loan Products (2)
INSERT INTO CDW_LN_PROD VALUES ('TFXD30', 'Test 30-Year Fixed', 'FXD', '360', 'FIXED', '50,000', '1,000,000', 'ACT', '01/01/2020', '12/31/2099');
INSERT INTO CDW_LN_PROD VALUES ('TARM51', 'Test 5/1 ARM', 'ARM', '360', 'VARIABLE', '50,000', '800,000', 'ACT', '01/01/2020', '12/31/2099');

-- Test Loan Accounts (2)
INSERT INTO CDW_LN_ACCT VALUES ('TLN-001', 'T-B001', 'Alice', 'Test', '0001', 'TFXD30', '250,000', '230,000.00', '4.500', '360', '1,302.50', '03/01/2021', '03/01/2051', '04/01/2021', '01/01/2026', 'ACT', '0', '2,800.00', '78.0', '100 Main St', 'Springfield', 'IL', '60001', 'SFR', '320,000', '03/01/2021', '12/01/2025');
INSERT INTO CDW_LN_ACCT VALUES ('TLN-002', 'T-B002', 'Bob', 'Sample', '0002', 'TARM51', '180,000', '170,500.00', '5.750', '360', '1,050.25', '06/01/2020', '06/01/2050', '07/01/2020', '01/01/2026', 'ACT', '0', '1,500.00', '72.0', '200 Oak Ave', 'Austin', 'TX', '73301', 'CND', '250,000', '06/01/2020', '12/01/2025');

-- Test Payment History (3 — 2 for TLN-001, 1 for TLN-002)
INSERT INTO CDW_PMT_HIST VALUES ('TPMT-001', 'TLN-001', '12/01/2025', '1,302.50', '427.30', '862.50', '12.70', '0.00', 'REG', 'PST', '11/30/2025', '12/01/2025', '12/01/2025', '12/01/2025');
INSERT INTO CDW_PMT_HIST VALUES ('TPMT-002', 'TLN-001', '11/01/2025', '1,302.50', '425.10', '864.70', '12.70', '0.00', 'REG', 'PST', '10/31/2025', '11/01/2025', '11/01/2025', '11/01/2025');
INSERT INTO CDW_PMT_HIST VALUES ('TPMT-003', 'TLN-002', '12/01/2025', '1,050.25', '287.45', '762.80', '0.00', '0.00', 'REG', 'PST', '11/30/2025', '12/01/2025', '12/01/2025', '12/01/2025');
