-- =============================================================================
-- STORED PROCEDURE: sp_GetAllBorrowers
-- =============================================================================
-- Replaces: LoanService.getAllBorrowers() Java method
-- Replaces: Access query/form for borrower listing
-- =============================================================================

USE [LoanServiceDB];
GO

IF OBJECT_ID('dbo.sp_GetAllBorrowers', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_GetAllBorrowers;
GO

CREATE PROCEDURE dbo.sp_GetAllBorrowers
AS
BEGIN
    SET NOCOUNT ON;

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
    ORDER BY b.last_name, b.first_name;
END;
GO

PRINT 'Created stored procedure: sp_GetAllBorrowers';
GO
