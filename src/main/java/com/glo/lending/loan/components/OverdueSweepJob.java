package com.glo.lending.loan.components;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the overdue sweep process.
 */
@Component
@RequiredArgsConstructor
public class OverdueSweepJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueSweepJob.class);

    private final OverdueSweepService sweepService;


     //Runs on the configured cron schedule, 8 am
    @Scheduled(cron = "${app.sweep.cron}")
    public void sweepOverdueLoans() {
        log.info("Overdue sweep job started");

        sweepService.processOverdueLoans()
                .doOnSuccess(v -> log.info("Overdue sweep job completed"))
                .doOnError(err -> log.error("Overdue sweep job failed", err))
                .subscribe();
    }
}
