-- =============================================================================
-- STORED PROCEDURE: sp_GetLoanById
-- =============================================================================
-- Replaces: LoanService.getLoanById() Java method
-- Replaces: Access form/query that looked up a single loan by account number
-- =============================================================================

USE [LoanServiceDB];
GO

IF OBJECT_ID('dbo.sp_GetLoanById', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_GetLoanById;
GO

CREATE PROCEDURE dbo.sp_GetLoanById
    @AccountNumber NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.loan_accounts WHERE account_number = @AccountNumber)
    BEGIN
        RAISERROR('Loan not found: %s', 16, 1, @AccountNumber);
        RETURN;
    END;

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
    WHERE la.account_number = @AccountNumber;
END;
GO

PRINT 'Created stored procedure: sp_GetLoanById';
GO
