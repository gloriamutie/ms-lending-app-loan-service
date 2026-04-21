package com.glo.lending.loan.service.serviceImpl;

import com.glo.lending.loan.components.LoanCreationSagaOrchestrator;
import com.glo.lending.loan.components.LoanEventPublisher;
import com.glo.lending.loan.exception.LoanNotFoundException;
import com.glo.lending.loan.model.dto.*;
import com.glo.lending.loan.model.enums.InstallmentState;
import com.glo.lending.loan.model.enums.LoanState;
import com.glo.lending.loan.model.enums.LoanType;
import com.glo.lending.loan.repository.entities.*;
import com.glo.lending.loan.repository.repo.*;
import com.glo.lending.loan.service.LoanService;
import com.glo.lending.loan.utils.LoanMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for loan lifecycle management: creation (via saga), repayment, and queries.
 */
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanServiceImpl.class);

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final LoanCreationSagaOrchestrator sagaOrchestrator;
    private final LoanEventPublisher eventPublisher;

    /**
     * Creates a loan via the distributed saga (idempotent).
     *
     * @param request the loan creation request with idempotency key
     * @return a {@link Mono} emitting the created loan response
     */
    @Override
    public Mono<LoanResponse> createLoan( CreateLoanRequest request) {
        log.info("Creating loan: customerId={}, idempotencyKey={}", request.getCustomerId(), request.getIdempotencyKey());
        Loan loan = Loan.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .principalAmount(request.getPrincipalAmount())
                .outstandingBalance(request.getPrincipalAmount())
                .totalFees(BigDecimal.ZERO)
                .loanType(request.getLoanType())
                .state(LoanState.OPEN)
                .originationDate(LocalDate.now())
                .dueDate(calculateDueDate(request.getTenureValue(), request.getTenureType()))
                .billingCycleId(request.getBillingCycleId())
                .tenureValue(request.getTenureValue())
                .tenureType(request.getTenureType())
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();


        return sagaOrchestrator.executeSaga(loan).flatMap(savedLoan -> {
                    if (request.getLoanType() == LoanType.INSTALLMENT) {
                        return createInstallments(savedLoan).thenReturn(savedLoan);
                    }
                    return Mono.just(savedLoan);
                }).flatMap(this::enrichWithInstallments);
    }

    /**
     * Retrieves a loan by ID with installments.
     */
    @Override
    public Mono<LoanResponse> getLoanById(final UUID loanId) {
        return loanRepository.findById(loanId)
                .switchIfEmpty(Mono.error(new LoanNotFoundException(loanId)))
                .flatMap(this::enrichWithInstallments);
    }

    /**
     * Retrieves all loans for a customer.
     */
    @Override
    public Flux<LoanResponse> getLoansByCustomerId(final UUID customerId) {
        return loanRepository.findByCustomerId(customerId)
                .flatMap(this::enrichWithInstallments);
    }

    /**
     * Processes a repayment against a loan.
     */
    @Override
    public Mono<RepaymentResponse> makeRepayment(final RepaymentRequest request) {
        log.info("Processing repayment: loanId={}, amount={}", request.getLoanId(), request.getAmount());

        return loanRepository.findById(request.getLoanId())
                .switchIfEmpty(Mono.error(new LoanNotFoundException(request.getLoanId())))
                .flatMap(loan -> {
                    if (loan.getState() == LoanState.CLOSED || loan.getState() == LoanState.CANCELLED) {
                        return Mono.error(new IllegalStateException("Cannot repay a " + loan.getState() + " loan"));
                    }

                    LoanRepayment repayment = new LoanRepayment();
                    repayment.setLoanId(loan.getId());
                    repayment.setInstallmentId(request.getInstallmentId());
                    repayment.setAmount(request.getAmount());
                    repayment.setPaymentDate(LocalDateTime.now());
                    repayment.setPaymentReference(request.getPaymentReference());

                    loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(request.getAmount()));
                    loan.setUpdatedAt(LocalDateTime.now());

                    if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
                        loan.setOutstandingBalance(BigDecimal.ZERO);
                        loan.setState(LoanState.CLOSED);
                    }

                    return repaymentRepository.save(repayment)
                            .flatMap(saved -> loanRepository.save(loan).thenReturn(saved))
                            .flatMap(saved -> eventPublisher.publishLoanEvent(loan.getCustomerId(), Map.of(
                                    "eventType", loan.getState() == LoanState.CLOSED ? "LOAN_CLOSED" : "REPAYMENT_RECEIVED",
                                    "loanId", loan.getId(), "customerId", loan.getCustomerId(),
                                    "amount", request.getAmount()
                            )).thenReturn(saved));
                })
                .map(LoanMapper::toRepaymentResponse)
                .doOnSuccess(r -> log.info("Repayment processed: id={}", r.id()));
    }

    /**
     * Cancels a loan (only OPEN loans can be cancelled).
     */
    @Override
    public Mono<LoanResponse> cancelLoan(final UUID loanId) {
        log.info("Cancelling loan: {}", loanId);
        return loanRepository.findById(loanId).switchIfEmpty(Mono.error(new LoanNotFoundException(loanId)))
                .flatMap(loan -> {
                    if (loan.getState() != LoanState.OPEN) {
                        return Mono.error(new IllegalStateException("Only OPEN loans can be cancelled"));
                    }
                    loan.setState(LoanState.CANCELLED);
                    loan.setUpdatedAt(LocalDateTime.now());
                    return loanRepository.save(loan);
                }).flatMap(loan -> eventPublisher.publishLoanEvent(loan.getCustomerId(), Map.of(
                        "eventType", "LOAN_CANCELLED", "loanId", loan.getId(), "customerId", loan.getCustomerId()
                )).thenReturn(loan)).flatMap(this::enrichWithInstallments);
    }

    private Mono<LoanResponse> enrichWithInstallments(final Loan loan) {
        return installmentRepository.findByLoanIdOrderByInstallmentNumber(loan.getId())
                .collectList()
                .map(installments -> LoanMapper.toResponse(loan, installments));
    }

    private Mono<Void> createInstallments(final Loan loan) {
        final int count = loan.getTenureValue();
        final BigDecimal installmentAmount = loan.getPrincipalAmount()
                .divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);

        return Flux.range(1, count)
                .map(num -> {
                    final LoanInstallment inst = new LoanInstallment();
                    inst.setLoanId(loan.getId());
                    inst.setInstallmentNumber(num);
                    inst.setAmount(installmentAmount);
                    inst.setPaidAmount(BigDecimal.ZERO);
                    inst.setDueDate(loan.getOriginationDate().plusMonths(num));
                    inst.setState(InstallmentState.PENDING);
                    return inst;
                })
                .flatMap(installmentRepository::save)
                .then();
    }

    private LocalDate calculateDueDate(final int tenureValue, final String tenureType) {
        return "MONTHS".equalsIgnoreCase(tenureType)
                ? LocalDate.now().plusMonths(tenureValue)
                : LocalDate.now().plusDays(tenureValue);
    }
}

