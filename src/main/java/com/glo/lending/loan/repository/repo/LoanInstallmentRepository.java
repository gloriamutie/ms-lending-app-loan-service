package com.glo.lending.loan.repository.repo;

import com.glo.lending.loan.repository.entities.LoanInstallment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import com.glo.lending.loan.model.enums.InstallmentState;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface LoanInstallmentRepository extends ReactiveCrudRepository<LoanInstallment, UUID> {

    Flux<LoanInstallment> findByLoanIdOrderByInstallmentNumber(UUID loanId);

    Flux<LoanInstallment> findByStateAndDueDateLessThanEqual(InstallmentState state, LocalDate date);
}

