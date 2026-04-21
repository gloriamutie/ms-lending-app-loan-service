package com.glo.lending.loan.components;

import com.glo.lending.loan.components.LoanCreationSagaOrchestrator;
import com.glo.lending.loan.model.enums.SagaStatus;
import com.glo.lending.loan.repository.repo.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that detects and recovers stale saga transactions.
 * <p>
 * If a loan creation saga is interrupted (e.g., service crash, network partition),
 * the loan record will remain in PENDING or LIMIT_RESERVED status. This job
 * periodically scans for such stale sagas and triggers compensation to release
 * any reserved customer limits and cancel the loan.
 * </p>
 * <p>
 * Runs every 5 minutes. Stale threshold: sagas older than 10 minutes in
 * PENDING or LIMIT_RESERVED status are considered abandoned.
 * </p>
 */
@Component
public class SagaRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryJob.class);

    private final LoanRepository loanRepository;
    private final LoanCreationSagaOrchestrator sagaOrchestrator;

    public SagaRecoveryJob(final LoanRepository loanRepository,
                           final LoanCreationSagaOrchestrator sagaOrchestrator) {
        this.loanRepository = loanRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    /**
     * Scans for stale sagas in PENDING or LIMIT_RESERVED state and attempts recovery.
     * For PENDING sagas: retries the saga from step 2.
     * For LIMIT_RESERVED sagas: retries finalization or compensates.
     */
    @Scheduled(fixedDelay = 300_000) // Every 5 minutes
    public void recoverStaleSagas() {
        log.info("Saga recovery job started — scanning for stale sagas");

        loanRepository.findBySagaStatus(SagaStatus.PENDING.name())
                .mergeWith(loanRepository.findBySagaStatus(SagaStatus.LIMIT_RESERVED.name()))
                .filter(loan -> loan.getCreatedAt() != null &&
                        loan.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(10)))
                .flatMap(staleLoan -> {
                    log.warn("Recovering stale saga: loanId={}, sagaStatus={}, createdAt={}",
                            staleLoan.getId(), staleLoan.getSagaStatus(), staleLoan.getCreatedAt());
                    return sagaOrchestrator.executeSaga(staleLoan)
                            .onErrorResume(error -> {
                                log.error("Saga recovery failed for loanId={}: {}",
                                        staleLoan.getId(), error.getMessage());
                                return reactor.core.publisher.Mono.empty();
                            });
                })
                .subscribe(
                        recovered -> log.info("Saga recovered successfully for loanId={}", recovered.getId()),
                        error -> log.error("Saga recovery job encountered an error", error),
                        () -> log.info("Saga recovery job completed")
                );
    }

    /**
     * Scans for sagas stuck in COMPENSATING state (compensation itself failed).
     * These require manual intervention — logged as CRITICAL.
     */
    @Scheduled(fixedDelay = 600_000) // Every 10 minutes
    public void detectStuckCompensations() {
        loanRepository.findBySagaStatus(SagaStatus.COMPENSATING.name())
                .subscribe(loan -> log.error(
                        "CRITICAL: Saga stuck in COMPENSATING state. Manual intervention required. " +
                        "loanId={}, customerId={}, amount={}, createdAt={}",
                        loan.getId(), loan.getCustomerId(), loan.getPrincipalAmount(), loan.getCreatedAt()));
    }
}

