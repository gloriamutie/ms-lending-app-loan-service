package com.glo.lending.loan.repository.repo;

import com.glo.lending.loan.repository.entities.LoanFee;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface LoanFeeRepository extends ReactiveCrudRepository<LoanFee, UUID> {

    Flux<LoanFee> findByLoanId(UUID loanId);

    Flux<LoanFee> findByLoanIdAndIsPaid(UUID loanId, Boolean isPaid);
}

