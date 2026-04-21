package com.glo.lending.loan.repository.repo;

import com.glo.lending.loan.repository.entities.Loan;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reactive repository for {@link Loan} entities.
 */
@Repository
public interface LoanRepository extends ReactiveCrudRepository<Loan, UUID> {

    Flux<Loan> findByCustomerId(UUID customerId);

    Flux<Loan> findByState(String state);

    Flux<Loan> findByStateAndDueDateBefore(String state, LocalDate date);

    Flux<Loan> findByBillingCycleId(UUID billingCycleId);

    /**
     * Finds an existing loan by its client-supplied idempotency key.
     * Used to detect and return already-created loans on retry.
     *
     * @param idempotencyKey the unique idempotency key
     * @return a {@link Mono} emitting the existing loan if found
     */
    Mono<Loan> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds loans with a pending saga that need compensation or completion.
     *
     * @param sagaStatus the saga status to filter by
     * @return a {@link Flux} of loans in the given saga state
     */
    Flux<Loan> findBySagaStatus(String sagaStatus);
}

