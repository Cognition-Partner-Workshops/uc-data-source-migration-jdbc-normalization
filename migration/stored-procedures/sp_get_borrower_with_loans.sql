-- =============================================================================
-- STORED PROCEDURE: sp_GetBorrowerWithLoans
-- =============================================================================
-- Replaces: LoanService.getBorrowerById() Java method
-- Returns borrower details plus all associated loans.
-- Uses two result sets: borrower info + loan list.
-- =============================================================================

USE [LoanServiceDB];
GO

IF OBJECT_ID('dbo.sp_GetBorrowerWithLoans', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_GetBorrowerWithLoans;
GO

CREATE PROCEDURE dbo.sp_GetBorrowerWithLoans
    @BorrowerId NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.borrowers WHERE external_id = @BorrowerId)
    BEGIN
        RAISERROR('Borrower not found: %s', 16, 1, @BorrowerId);
        RETURN;
    END;

    -- Result Set 1: Borrower details
    SELECT
        b.external_id           AS Id,
        b.first_name
            + CASE
                WHEN b.middle_initial IS NOT NULL
                THEN ' ' + b.middle_initial + '.'
                ELSE ''
              END
            + ' ' + b.last_name AS FullName,
        b.email                 AS Email,
        b.phone                 AS Phone,
        b.city                  AS City,
        b.[state]               AS [State],
        b.credit_score          AS CreditScore,
        b.employment_status     AS EmploymentStatus
    FROM dbo.borrowers b
    WHERE b.external_id = @BorrowerId;

    -- Result Set 2: Loans for this borrower
    SELECT
        la.account_number       AS LoanAccountNumber,
        b.first_name + ' ' + b.last_name
                                AS BorrowerName,
        lp.[name]               AS ProductDescription,
        la.original_amount      AS OriginalAmount,
        la.current_balance      AS CurrentBalance,
        la.interest_rate        AS InterestRate,
        la.monthly_payment      AS MonthlyPayment,
        la.[status]             AS [Status],
        la.origination_date     AS OriginationDate,
        la.property_address + ', ' + la.property_city
            + ', ' + la.property_state + ' ' + la.property_zip
                                AS PropertyAddress,
        la.property_type        AS PropertyType
    FROM dbo.loan_accounts la
    INNER JOIN dbo.borrowers b ON b.id = la.borrower_id
    INNER JOIN dbo.loan_products lp ON lp.id = la.product_id
    WHERE b.external_id = @BorrowerId
    ORDER BY la.account_number;
END;
GO

PRINT 'Created stored procedure: sp_GetBorrowerWithLoans';
GO
