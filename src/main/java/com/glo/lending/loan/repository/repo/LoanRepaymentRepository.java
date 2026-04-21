package com.glo.lending.loan.repository.repo;

import com.glo.lending.loan.repository.entities.LoanRepayment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface LoanRepaymentRepository extends ReactiveCrudRepository<LoanRepayment, UUID> {

    Flux<LoanRepayment> findByLoanId(UUID loanId);
}

