package com.glo.lending.loan.repository.repo;

import com.glo.lending.loan.repository.entities.BillingCycle;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface BillingCycleRepository extends ReactiveCrudRepository<BillingCycle, UUID> {

    Mono<BillingCycle> findByCustomerId(UUID customerId);
}

