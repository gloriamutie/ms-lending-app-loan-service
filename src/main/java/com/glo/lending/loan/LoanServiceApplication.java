package com.glo.lending.loan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Loan Service microservice.
 * Manages loan lifecycle, disbursement, repayments, billing cycles, and sweep jobs.
 */
@SpringBootApplication
@EnableScheduling
public class LoanServiceApplication {

    public static void main(final String[] args) {
        SpringApplication.run(LoanServiceApplication.class, args);
    }
}

