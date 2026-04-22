package com.glo.lending.loan.components;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LoanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);
    private static final String LOAN_EVENTS_TOPIC = "lendingLoanEvents";

    private final KafkaTemplate<String, Object> kafkaTemplate;

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

