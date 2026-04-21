package com.glo.lending.loan.service;

import com.glo.lending.loan.model.enums.LoanState;
import com.glo.lending.loan.model.enums.SagaStatus;
import com.glo.lending.loan.repository.entities.Loan;
import com.glo.lending.loan.repository.repo.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class LoanCreationSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LoanCreationSagaOrchestrator.class);

    private final LoanRepository loanRepository;
    @Value("${app.service.customer-url}")
    private final LoanEventPublisher eventPublisher;
    @Value("${app.service.customer-url}")
    String customerServiceUrl;
    private final WebClient webClient = WebClient.builder().baseUrl(customerServiceUrl).build();


    public Mono<Loan> executeSaga(final Loan loan) {
        return checkIdempotency(loan.getIdempotencyKey())
                .switchIfEmpty(Mono.defer(() -> startSaga(loan)));
    }

    private Mono<Loan> checkIdempotency(final String idempotencyKey) {
        return loanRepository.findByIdempotencyKey(idempotencyKey)
                .flatMap(existingLoan -> {
                    final String status = existingLoan.getSagaStatus();
                    log.info("Idempotency check: loan exists with key={}, sagaStatus={}", idempotencyKey, status);

                    if (SagaStatus.COMPLETED.name().equals(status)) {
                        log.info("Returning existing completed loan for idempotency key: {}", idempotencyKey);
                        return Mono.just(existingLoan);
                    } else if (SagaStatus.FAILED.name().equals(status)) {
                        return Mono.error(new IllegalStateException(
                                "Previous loan creation with this idempotency key failed. Use a new key."));
                    } else {
                        // Resume saga from current state
                        log.warn("Resuming interrupted saga for idempotency key: {}, status: {}", idempotencyKey, status);
                        return resumeSaga(existingLoan);
                    }
                });
    }

    /**
     * Starts the saga from the beginning: save loan → reserve limit → finalize.
     */
    private Mono<Loan> startSaga(final Loan loan) {
        loan.setSagaStatus(SagaStatus.PENDING.name());
        loan.setState(LoanState.OPEN);

        return loanRepository.save(loan)
                .doOnNext(saved -> log.info("Saga STEP 1: Loan record created. id={}, saga=PENDING", saved.getId()))
                .flatMap(this::reserveCustomerLimit)
                .flatMap(this::finalizeSaga)
                .onErrorResume(error -> compensateSaga(loan, error));
    }

    /**
     * Resumes a saga that was interrupted (e.g., after service restart).
     */
    private Mono<Loan> resumeSaga(final Loan loan) {
        final String status = loan.getSagaStatus();

        if (SagaStatus.PENDING.name().equals(status)) {
            return reserveCustomerLimit(loan)
                    .flatMap(this::finalizeSaga)
                    .onErrorResume(error -> compensateSaga(loan, error));
        } else if (SagaStatus.LIMIT_RESERVED.name().equals(status)) {
            return finalizeSaga(loan)
                    .onErrorResume(error -> compensateSaga(loan, error));
        }

        return Mono.error(new IllegalStateException("Cannot resume saga in status: " + status));
    }

    /**
     * STEP 2: Reserve the customer's available loan limit.
     * Calls the Customer Service to atomically decrement the available amount.
     */
    private Mono<Loan> reserveCustomerLimit(final Loan loan) {
        log.info("Saga STEP 2: Reserving customer limit. loanId={}, customerId={}, amount={}",
                loan.getId(), loan.getCustomerId(), loan.getPrincipalAmount());

        return webClient.put()
                .uri("/api/v1/customers/{customerId}/loan-limits/reserve", loan.getCustomerId())
                .bodyValue(Map.of(
                        "amount", loan.getPrincipalAmount(),
                        "loanId", loan.getId(),
                        "idempotencyKey", loan.getIdempotencyKey()
                ))
                .retrieve()
                .bodyToMono(Void.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal -> log.warn("Retrying customer limit reservation, attempt: {}", signal.totalRetries() + 1)))
                .then(updateSagaStatus(loan, SagaStatus.LIMIT_RESERVED))
                .doOnNext(l -> log.info("Saga STEP 2 COMPLETE: Limit reserved. loanId={}", l.getId()));
    }

    /**
     * STEP 3: Finalize the saga — mark as completed and publish event.
     */
    private Mono<Loan> finalizeSaga(final Loan loan) {
        log.info("Saga STEP 3: Finalizing loan creation. loanId={}", loan.getId());

        return updateSagaStatus(loan, SagaStatus.COMPLETED)
                .flatMap(completedLoan ->
                        eventPublisher.publishLoanEvent(completedLoan.getCustomerId(), Map.of(
                                "eventType", "LOAN_CREATED",
                                "loanId", completedLoan.getId(),
                                "customerId", completedLoan.getCustomerId(),
                                "loanAmount", completedLoan.getPrincipalAmount(),
                                "dueDate", completedLoan.getDueDate().toString()
                        )).thenReturn(completedLoan)
                )
                .doOnNext(l -> log.info("Saga COMPLETED: Loan fully created. loanId={}", l.getId()));
    }

    /**
     * COMPENSATION: Reverses all completed saga steps on failure.
     * <ol>
     *   <li>If limit was reserved → release it via Customer Service</li>
     *   <li>Mark loan as CANCELLED with saga_status=FAILED</li>
     * </ol>
     */
    private Mono<Loan> compensateSaga(final Loan loan, final Throwable error) {
        log.error("Saga FAILED for loanId={}. Starting compensation. Error: {}", loan.getId(), error.getMessage());

        return updateSagaStatus(loan, SagaStatus.COMPENSATING)
                .flatMap(l -> {
                    if (SagaStatus.LIMIT_RESERVED.name().equals(l.getSagaStatus()) ||
                            SagaStatus.COMPENSATING.name().equals(l.getSagaStatus())) {
                        return releaseCustomerLimit(l);
                    }
                    return Mono.just(l);
                })
                .flatMap(l -> {
                    l.setState(LoanState.CANCELLED);
                    l.setSagaStatus(SagaStatus.FAILED.name());
                    return loanRepository.save(l);
                })
                .doOnNext(l -> log.info("Saga COMPENSATION COMPLETE: Loan cancelled. loanId={}", l.getId()))
                .flatMap(l -> Mono.error(new RuntimeException(
                        "Loan creation failed and was compensated. Reason: " + error.getMessage(), error)));
    }

    /**
     * Releases the previously reserved customer limit (compensation step).
     */
    private Mono<Loan> releaseCustomerLimit(final Loan loan) {
        log.info("Compensation: Releasing customer limit. loanId={}, customerId={}, amount={}",
                loan.getId(), loan.getCustomerId(), loan.getPrincipalAmount());

        return webClient.put()
                .uri("/api/v1/customers/{customerId}/loan-limits/release", loan.getCustomerId())
                .bodyValue(Map.of(
                        "amount", loan.getPrincipalAmount(),
                        "loanId", loan.getId(),
                        "idempotencyKey", loan.getIdempotencyKey()
                ))
                .retrieve()
                .bodyToMono(Void.class)
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .doBeforeRetry(signal -> log.warn("Retrying limit release, attempt: {}", signal.totalRetries() + 1)))
                .thenReturn(loan)
                .onErrorResume(releaseError -> {
                    log.error("CRITICAL: Failed to release customer limit during compensation. " +
                            "Manual intervention required. loanId={}, customerId={}, amount={}",
                            loan.getId(), loan.getCustomerId(), loan.getPrincipalAmount(), releaseError);
                    return Mono.just(loan);
                });
    }

    /**
     * Updates the saga status on the loan and persists it.
     */
    private Mono<Loan> updateSagaStatus(final Loan loan, final SagaStatus status) {
        loan.setSagaStatus(status.name());
        return loanRepository.save(loan);
    }

    /**
     * Determines if an error is retryable (network/5xx errors).
     */
    private boolean isRetryable(final Throwable throwable) {
        return !(throwable instanceof IllegalArgumentException ||
                 throwable instanceof IllegalStateException);
    }
}

