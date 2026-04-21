package com.glo.lending.loan.controller;

import com.glo.lending.loan.model.dto.*;
import com.glo.lending.loan.service.LoanService;
import com.glo.lending.loan.service.serviceImpl.LoanServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for loan lifecycle management.
 */
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private static final Logger log = LoggerFactory.getLogger(LoanController.class);
    private final LoanService loanService;

    @PostMapping
    public Mono<ResponseEntity<LoanResponse>> createLoan(@Valid @RequestBody final CreateLoanRequest request) {
        log.info("POST /api/v1/loans — idempotencyKey={}", request.getIdempotencyKey());
        return loanService.createLoan(request).map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r));
    }

    @GetMapping("/{loanId}")
    public Mono<ResponseEntity<LoanResponse>> getLoan(@PathVariable final UUID loanId) {
        log.info("GET /api/v1/loans/{}", loanId);
        return loanService.getLoanById(loanId).map(r -> ResponseEntity.ok().body(r));
    }

    @GetMapping("/customer/{customerId}")
    public Mono<ResponseEntity<Flux<LoanResponse>>> getLoansByCustomer(@PathVariable final UUID customerId) {
        log.info("GET /api/v1/loans/customer/{}", customerId);
        return Mono.just(ResponseEntity.ok(loanService.getLoansByCustomerId(customerId)));
    }

    @PostMapping("/repayments")
    public Mono<ResponseEntity<RepaymentResponse>> makeRepayment(@Valid @RequestBody final RepaymentRequest request) {
        log.info("POST /api/v1/loans/repayments — loanId={}", request.getLoanId());
        return loanService.makeRepayment(request).map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r));
    }

    @PutMapping("/{loanId}/cancel")
    public Mono<ResponseEntity<LoanResponse>> cancelLoan(@PathVariable final UUID loanId) {
        log.info("PUT /api/v1/loans/{}/cancel", loanId);
        return loanService.cancelLoan(loanId).map(r -> ResponseEntity.ok().body(r));
    }
}

