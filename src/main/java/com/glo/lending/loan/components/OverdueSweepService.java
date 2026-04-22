package com.glo.lending.loan.components;

import com.glo.lending.loan.model.enums.InstallmentState;
import com.glo.lending.loan.model.enums.LoanState;
import com.glo.lending.loan.repository.entities.Loan;
import com.glo.lending.loan.repository.entities.LoanInstallment;
import com.glo.lending.loan.repository.repo.LoanInstallmentRepository;
import com.glo.lending.loan.repository.repo.LoanRepository;
import com.glo.lending.loan.utils.Utilities;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Processes overdue loans and installments in batches.
 * Designed to handle millions of records efficiently using buffered reactive streams.
 */
@Service
@RequiredArgsConstructor
public class OverdueSweepService {

    private static final Logger log = LoggerFactory.getLogger(OverdueSweepService.class);
    private static final int BATCH_SIZE = 100;

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final TransactionalOperator tx;
    private final Utilities utilities;


      //marks overdue loans and then overdue installments
      //return Mono completing when all batches are processed
    public Mono<Void> processOverdueLoans() {
        final LocalDate today = LocalDate.now();
        return processLoanBatches(today).then(processInstallmentBatches(today));
    }

    private Mono<Void> processLoanBatches( LocalDate today) {
        return loanRepository.findByStateAndDueDateBefore(LoanState.OPEN, today)
                .buffer(BATCH_SIZE)
                .concatMap(this::processEachLoanBatch)
                .then();
    }

    /**
     *  updates a batch of loans to OVERDUE, then publishes events after commit.
     */
    private Mono<Void> processEachLoanBatch(final List<Loan> batch) {
        return updateLoansToOverdue(batch)
                .flatMap(this::publishOverdueEvents);
    }

    private Mono<List<Loan>> updateLoansToOverdue(final List<Loan> batch) {
        return Flux.fromIterable(batch).flatMap(loan -> {
                    loan.setState(LoanState.OVERDUE);
                    loan.setUpdatedAt(LocalDateTime.now());
                    return loanRepository.save(loan);
                })
                .collectList()
                .as(tx::transactional);
    }

    private Mono<Void> publishOverdueEvents(final List<Loan> savedLoans) {
        return Flux.fromIterable(savedLoans)
                .flatMap(loan -> utilities.publishEventReactive(
                                loan.getCustomerId(),
                                "OVERDUE_NOTICE",
                                Map.of(
                                        "eventType", "OVERDUE_NOTICE",
                                        "loanId", loan.getId(),
                                        "customerId", loan.getCustomerId(),
                                        "outstandingBalance", loan.getOutstandingBalance(),
                                        "dueDate", loan.getDueDate().toString()
                                ))
                        .doOnError(err -> log.error("Event publish failed for loanId={}", loan.getId(), err))
                        .onErrorResume(e -> Mono.empty())
                )
                .then();
    }

    // collects all overdue installments in batches and updates them to OVERDUE
    private Mono<Void> processInstallmentBatches(final LocalDate today) {
        return installmentRepository.findByStateAndDueDateBefore(InstallmentState.PENDING, today)
                .buffer(BATCH_SIZE)
                .concatMap(this::processInstallmentBatch)
                .then();
    }


    // updates each batch of installments to OVERDUE
    private Mono<Void> processInstallmentBatch(final List<LoanInstallment> batch) {
        return Flux.fromIterable(batch).flatMap(inst -> {
                    inst.setState(InstallmentState.OVERDUE);
                    return installmentRepository.save(inst);
                })
                .as(tx::transactional)
                .then();
    }
}