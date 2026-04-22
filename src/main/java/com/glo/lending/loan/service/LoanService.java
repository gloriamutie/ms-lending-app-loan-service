package com.glo.lending.loan.service;

import com.glo.lending.loan.model.dto.CreateLoanRequest;
import com.glo.lending.loan.model.dto.LoanResponse;
import com.glo.lending.loan.model.dto.RepaymentRequest;
import com.glo.lending.loan.model.dto.RepaymentResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LoanService {
    Mono<LoanResponse> createLoan(final CreateLoanRequest request, final String idempotencyKey);
    Mono<LoanResponse> getLoanById(final UUID loanId);
    Flux<LoanResponse> getLoansByCustomerId(final UUID customerId);
    Mono<LoanResponse> cancelLoan(final UUID loanId);
    Mono<RepaymentResponse> makeRepayment(final RepaymentRequest request);
}
