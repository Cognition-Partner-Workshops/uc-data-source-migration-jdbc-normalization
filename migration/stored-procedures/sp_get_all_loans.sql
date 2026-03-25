-- =============================================================================
-- STORED PROCEDURE: sp_GetAllLoans
-- =============================================================================
-- Replaces: LoanService.getAllLoans() Java method
-- Replaces: Access query "qryAllLoans" (if existed)
-- Returns all loan summaries with borrower name and product description.
-- =============================================================================

USE [LoanServiceDB];
GO

IF OBJECT_ID('dbo.sp_GetAllLoans', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_GetAllLoans;
GO

CREATE PROCEDURE dbo.sp_GetAllLoans
AS
BEGIN
    SET NOCOUNT ON;

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
    ORDER BY la.account_number;
END;
GO

PRINT 'Created stored procedure: sp_GetAllLoans';
GO
