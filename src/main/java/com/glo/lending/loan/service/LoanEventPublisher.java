package com.glo.lending.loan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Publishes loan lifecycle events to Kafka.
 * <p>
 * <b>Partitioning Strategy:</b> All events use the {@code customerId} as the
 * Kafka message key. Since Kafka hashes the key to determine the partition,
 * this guarantees that all events for the same customer land on the same
 * partition, preserving per-customer ordering for downstream consumers
 * (e.g., Notification Service).
 * </p>
 */
@Service
public class LoanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);
    private static final String LOAN_EVENTS_TOPIC = "lending.loan.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LoanEventPublisher(final KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes an event to the loan events topic, partitioned by customerId.
     *
     * @param customerId the customer ID used as the partition key
     * @param event      the event payload
     * @return a {@link Mono} that completes when the event is acknowledged by Kafka
     */
    public Mono<Void> publishLoanEvent(final UUID customerId, final Object event) {
        final String partitionKey = customerId.toString();
        log.info("Publishing loan event to topic={}, partitionKey={}, eventType={}",
                LOAN_EVENTS_TOPIC, partitionKey, event.getClass().getSimpleName());

        return Mono.fromFuture(
                kafkaTemplate.send(LOAN_EVENTS_TOPIC, partitionKey, event)
                        .toCompletableFuture()
        ).doOnSuccess(result -> log.debug("Loan event published successfully for customer: {}", customerId))
         .doOnError(error -> log.error("Failed to publish loan event for customer: {}", customerId, error))
         .then();
    }
}

