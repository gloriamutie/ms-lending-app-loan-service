package com.glo.lending.loan.exception;

import java.util.UUID;

public class LoanNotFoundException extends RuntimeException {
    public LoanNotFoundException(final UUID loanId) {
        super("Loan not found with id: " + loanId);
    }
}

