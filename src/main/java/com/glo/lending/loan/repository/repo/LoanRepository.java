package com.glo.lending.loan.repository.repo;

import com.glo.lending.loan.repository.entities.Loan;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.glo.lending.loan.model.enums.LoanState;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reactive repository for {@link Loan} entities.
 */
@Repository
public interface LoanRepository extends ReactiveCrudRepository<Loan, UUID> {

    Flux<Loan> findByCustomerId(UUID customerId);

    Flux<Loan> findByState(LoanState state);

    Flux<Loan> findByStateAndDueDateBefore(LoanState state, LocalDate date);

    Flux<Loan> findByBillingCycleId(UUID billingCycleId);

    /**
     * Finds an existing loan by its client-supplied idempotency key.
     * Used to detect and return already-created loans on retry.
     *
     * @param idempotencyKey the unique idempotency key
     * @return a {@link Mono} emitting the existing loan if found
     */
    Mono<Loan> findByIdempotencyKey(String idempotencyKey);
}

