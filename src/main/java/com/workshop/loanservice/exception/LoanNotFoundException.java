package com.workshop.loanservice.exception;

public class LoanNotFoundException extends RuntimeException {

    private final String loanId;

    public LoanNotFoundException(String loanId) {
        super("Loan not found: " + loanId);
        this.loanId = loanId;
    }

    public String getLoanId() {
        return loanId;
    }
}
