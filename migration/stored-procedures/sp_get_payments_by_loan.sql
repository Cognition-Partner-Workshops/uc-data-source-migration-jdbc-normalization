-- =============================================================================
-- STORED PROCEDURE: sp_GetPaymentsByLoan
-- =============================================================================
-- Replaces: LoanService.getPaymentsByLoan() Java method
-- Replaces: Access query for payment history lookup
-- Returns payment history for a given loan account, ordered by date descending.
-- =============================================================================

USE [LoanServiceDB];
GO

IF OBJECT_ID('dbo.sp_GetPaymentsByLoan', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_GetPaymentsByLoan;
GO

CREATE PROCEDURE dbo.sp_GetPaymentsByLoan
    @LoanAccountNumber NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @LoanAccountId BIGINT;

    SELECT @LoanAccountId = id
    FROM dbo.loan_accounts
    WHERE account_number = @LoanAccountNumber;

    IF @LoanAccountId IS NULL
    BEGIN
        RAISERROR('Loan account not found: %s', 16, 1, @LoanAccountNumber);
        RETURN;
    END;

    SELECT
        p.id                    AS PaymentId,
        la.account_number       AS LoanAccountNumber,
        p.payment_date          AS PaymentDate,
        p.total_amount          AS TotalAmount,
        p.principal_amount      AS PrincipalAmount,
        p.interest_amount       AS InterestAmount,
        p.escrow_amount         AS EscrowAmount,
        p.late_fee              AS LateFee,
        p.[type]                AS [Type],
        p.[status]              AS [Status]
    FROM dbo.payments p
    INNER JOIN dbo.loan_accounts la ON la.id = p.loan_account_id
    WHERE la.account_number = @LoanAccountNumber
    ORDER BY p.payment_date DESC;
END;
GO

PRINT 'Created stored procedure: sp_GetPaymentsByLoan';
GO
