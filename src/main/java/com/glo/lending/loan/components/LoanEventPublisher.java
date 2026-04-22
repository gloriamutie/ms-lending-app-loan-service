package com.glo.lending.loan.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class LoanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String loanEventsTopic;

    public LoanEventPublisher(final KafkaTemplate<String, Object> kafkaTemplate,
                              @Value("${app.kafka.topic.loan-events}") final String loanEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.loanEventsTopic = loanEventsTopic;
    }



    public Mono<Void> publishLoanEvent(final UUID customerId, final Object event) {
        final String partitionKey = customerId.toString();
        log.info("Publishing loan event to topic={}, partitionKey={}, eventType={}",
                loanEventsTopic, partitionKey, event.getClass().getSimpleName());

        return Mono.fromFuture(
                kafkaTemplate.send(loanEventsTopic, partitionKey, event)
                        .toCompletableFuture()
        ).doOnSuccess(result -> log.debug("Loan event published successfully for customer: {}", customerId))
         .doOnError(error -> log.error("Failed to publish loan event for customer: {}", customerId, error))
         .then();
    }
}

