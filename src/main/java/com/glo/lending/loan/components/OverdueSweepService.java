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

    /**
     * Marks overdue loans and then overdue installments.
     *
     * @return Mono completing when all batches are processed
     */
    public Mono<Void> processOverdueLoans() {
        final LocalDate today = LocalDate.now();
        return processLoanBatches(today).then(processInstallmentBatches(today));
    }

    private Mono<Void> processLoanBatches(final LocalDate today) {
        return loanRepository.findByStateAndDueDateLessThanEqual(LoanState.OPEN, today)
                .switchIfEmpty(Flux.defer(() -> {
                    log.info("No OPEN loans found with dueDate on or before {}", today);
                    return Flux.empty();
                }))
                .buffer(BATCH_SIZE)
                .doOnNext(batch -> log.info("Processing overdue loan batch, size={}", batch.size()))
                .concatMap(this::processEachLoanBatch)
                .then();
    }

    /**
     * Updates a batch of loans to OVERDUE, then publishes events after commit.
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
                .as(tx::transactional)
                .doOnSuccess(saved -> log.info("Batch of {} loans updated to OVERDUE", saved.size()));
    }

    private Mono<Void> publishOverdueEvents(final List<Loan> savedLoans) {
        log.info("Publishing OVERDUE_NOTICE events for {} loans", savedLoans.size());
        return Flux.fromIterable(savedLoans)
                .flatMap(loan -> {
                    log.info("Publishing OVERDUE_NOTICE for loanId={}, customerId={}", loan.getId(), loan.getCustomerId());
                    return utilities.publishEventReactive(
                                    loan.getCustomerId(),
                                    "OVERDUE_NOTICE",
                                    Map.of(
                                            "eventType", "OVERDUE_NOTICE",
                                            "loanId", loan.getId(),
                                            "customerId", loan.getCustomerId(),
                                            "outstandingBalance", loan.getOutstandingBalance(),
                                            "dueDate", loan.getDueDate().toString()
                                    ))
                            .doOnSuccess(v -> log.info("OVERDUE_NOTICE published for loanId={}", loan.getId()))
                            .doOnError(err -> log.error("OVERDUE_NOTICE publish FAILED for loanId={}", loan.getId(), err));
                })
                .then();
    }

    private Mono<Void> processInstallmentBatches(final LocalDate today) {
        return installmentRepository.findByStateAndDueDateLessThanEqual(InstallmentState.PENDING, today)
                .buffer(BATCH_SIZE)
                .concatMap(this::processInstallmentBatch)
                .then();
    }

    private Mono<Void> processInstallmentBatch(final List<LoanInstallment> batch) {
        return Flux.fromIterable(batch).flatMap(inst -> {
                    inst.setState(InstallmentState.OVERDUE);
                    return installmentRepository.save(inst);
                })
                .as(tx::transactional)
                .then();
    }
}