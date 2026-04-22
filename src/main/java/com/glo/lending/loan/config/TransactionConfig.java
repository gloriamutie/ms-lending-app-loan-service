package com.glo.lending.loan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Configuration for reactive transaction management in R2DBC.
 */
@Configuration
public class TransactionConfig {

    /**
     * Provides a TransactionalOperator bean for reactive transaction handling.
     * Used with {@code .as(transactionalOperator::transactional)} to wrap Mono/Flux streams
     * in database transactions.
     */
    @Bean
    public TransactionalOperator transactionalOperator( ReactiveTransactionManager txManager) {
        return TransactionalOperator.create(txManager);
    }
}

