package com.glo.lending.loan.service.serviceImpl;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;


 // Core service for loan lifecycle management: creation, repayment, and queries.
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanServiceImpl.class);

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final LoanEventPublisher eventPublisher;
    private final TransactionalOperator transactionalOperator;


    private record CreateLoanResult(Loan loan, boolean created) {}

     // Creates and persists a new loan, then publishes a LOAN_CREATED event.
    @Override
    public Mono<LoanResponse> createLoan(final CreateLoanRequest request, final String idempotencyKeyHeader) {
        final String idempotencyKey = idempotencyKeyHeader.trim();
        log.info("Creating loan: customerId={}, idempotencyKey={}", request.getCustomerId(), idempotencyKey);

        return loanRepository.findByIdempotencyKey(idempotencyKey)
                .map(existingLoan -> new CreateLoanResult(existingLoan, false))
                .switchIfEmpty(Mono.defer(() -> createLoanAtomically(request, idempotencyKey)))
                .flatMap(result -> {
                    if (!result.created()) {
                        log.info("Idempotent replay detected. Returning existing loan for key={}", idempotencyKey);
                        return getLoanInstallments(result.loan());
                    }

                    // After transaction commits, publish events asynchronously
                    return getLoanInstallments(result.loan()).doOnNext(response -> publishEvent(
                                    result.loan().getCustomerId(), "LOAN_CREATED",
                                    Map.of(
                                            "eventType", "LOAN_CREATED",
                                            "loanId", result.loan().getId(),
                                            "customerId", result.loan().getCustomerId(),
                                            "loanAmount", result.loan().getPrincipalAmount(),
                                            "dueDate", result.loan().getDueDate().toString()
                                    )
                            ));
                });
    }

    /**
     * Atomically creates loan + installments within a single transaction.
     * Both succeed or both fail together.
     *  Ensure entire operation is atomic, the loan creation and installment creation are in the same transaction
     */

    private Mono<CreateLoanResult> createLoanAtomically( CreateLoanRequest request,  String idempotencyKey) {
        return createLoanWithIdempotencyProtection(request, idempotencyKey)
                .flatMap(result -> {
                    if (!result.created()) {
                        return Mono.just(result);
                    }

                    if (result.loan().getLoanType() == LoanType.INSTALLMENT) {
                        return createInstallments(result.loan())
                                .thenReturn(result);
                    }
                    return Mono.just(result);
                })
                .as(transactionalOperator::transactional);
    }



    private Mono<CreateLoanResult> createLoanWithIdempotencyProtection( CreateLoanRequest request, String idempotencyKey) {
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
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return loanRepository.save(loan)
                .map(savedLoan -> new CreateLoanResult(savedLoan, true))
                .onErrorResume(DataIntegrityViolationException.class, error ->
                        loanRepository.findByIdempotencyKey(idempotencyKey)
                                .map(existingLoan -> new CreateLoanResult(existingLoan, false))
                                .switchIfEmpty(Mono.error(error))
                );
    }


     //Retrieves a loan by ID with installments
    @Override
    public Mono<LoanResponse> getLoanById(UUID loanId) {
        return loanRepository.findById(loanId).switchIfEmpty(Mono.error(new LoanNotFoundException(loanId)))
                .flatMap(this::getLoanInstallments);
    }

    //Retrieves all loans for a customer with installments
    @Override
    public Flux<LoanResponse> getLoansByCustomerId(UUID customerId) {
        return loanRepository.findByCustomerId(customerId).flatMap(this::getLoanInstallments);
    }

    //Processes a repayment against a loan, updates loan balance and state, and publishes events accordingly
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

                     boolean isLoanClosed = loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0;
                    if (isLoanClosed) {
                        loan.setOutstandingBalance(BigDecimal.ZERO);
                        loan.setState(LoanState.CLOSED);
                    }

                    final String repaymentEventType = isLoanClosed ? "LOAN_CLOSED" : "REPAYMENT_RECEIVED";

                    // Atomically save repayment + update loan
                    return Mono.just(repayment)
                            .flatMap(repaymentRepository::save)
                            .flatMap(savedRepayment -> loanRepository.save(loan).thenReturn(savedRepayment))
                            .as(transactionalOperator::transactional)
                            .map(LoanMapper::toRepaymentResponse)
                            .doOnNext(r -> publishEvent(loan.getCustomerId(), repaymentEventType, Map.of(
                                    "eventType", repaymentEventType,
                                    "loanId", loan.getId(),
                                    "customerId", loan.getCustomerId(),
                                    "amount", request.getAmount()
                            )))
                            .doOnSuccess(r -> log.info("Repayment processed: id={}", r.id()));
                });
    }


    // Cancels an OPEN loan, updates its state, and publishes a LOAN_CANCELLED event. Only loans in OPEN state can be cancelled.
    @Override
    public Mono<LoanResponse> cancelLoan( UUID loanId) {
        log.info("Cancelling loan: {}", loanId);
        return loanRepository.findById(loanId)
                .switchIfEmpty(Mono.error(new LoanNotFoundException(loanId)))
                .flatMap(loan -> {
                    if (loan.getState() != LoanState.OPEN) {
                        return Mono.error(new IllegalStateException("Only OPEN loans can be cancelled"));
                    }
                    loan.setState(LoanState.CANCELLED);
                    loan.setUpdatedAt(LocalDateTime.now());
                    
                    return loanRepository.save(loan).as(transactionalOperator::transactional);
                })
                .flatMap(this::getLoanInstallments)
                .doOnNext(response -> publishEvent(response.customerId(), "LOAN_CANCELLED",
                        Map.of(
                                "eventType", "LOAN_CANCELLED",
                                "loanId", response.id(),
                                "customerId", response.customerId()
                        )
                ));
    }


    // Calculate and create loan installments with proper rounding for the last installment
    private Mono<Void> createInstallments( Loan loan) {
         int count = loan.getTenureValue();
         BigDecimal baseAmount = loan.getPrincipalAmount().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        return Flux.range(1, count).map(num -> {
                     LoanInstallment inst = new LoanInstallment();
                    inst.setLoanId(loan.getId());
                    inst.setInstallmentNumber(num);

                    // Last installment gets any remainder to ensure total equals principal
                    if (num == count) {
                         BigDecimal totalBefore = baseAmount.multiply(BigDecimal.valueOf(count - 1));
                         BigDecimal last = loan.getPrincipalAmount().subtract(totalBefore);
                        inst.setAmount(last);
                    } else {
                        inst.setAmount(baseAmount);
                    }

                    inst.setPaidAmount(BigDecimal.ZERO);
                    inst.setDueDate(loan.getOriginationDate().plusMonths(num - 1));
                    inst.setState(InstallmentState.PENDING);
                    return inst;
                })
                .flatMap(installmentRepository::save)
                .then();
    }


     private void publishEvent( UUID customerId,  String eventType,  Map<String, Object> payload) {
         eventPublisher.publishLoanEvent(customerId, payload)
                 .doOnSuccess(v -> log.debug("{} event published for customerId={}", eventType, customerId))
                 .doOnError(err -> log.error("Failed to publish {} event for customerId={}", eventType, customerId, err))
                 .subscribe();
     }

     private Mono<LoanResponse> getLoanInstallments( Loan loan) {
         return installmentRepository.findByLoanIdOrderByInstallmentNumber(loan.getId())
                 .collectList()
                 .map(installments -> LoanMapper.toResponse(loan, installments));
     }


    private LocalDate calculateDueDate( int tenureValue,  String tenureType) {
        return "MONTHS".equalsIgnoreCase(tenureType)
                ? LocalDate.now().plusMonths(tenureValue)
                : LocalDate.now().plusDays(tenureValue);
    }
}

