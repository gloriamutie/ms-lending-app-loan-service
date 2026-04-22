package com.glo.lending.loan.utils;

import com.glo.lending.loan.components.LoanEventPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class Utilities {
    private static final Logger log = LoggerFactory.getLogger(Utilities.class);
    private final LoanEventPublisher eventPublisher;

    /**
     * Publishes an event fire-and-forget style (subscribes immediately).
     */
    public void publishEvent(final UUID customerId, final String eventType, final Map<String, Object> payload) {
        publishEventReactive(customerId, eventType, payload).subscribe();
    }

    /**
     * Publishes an event reactively, allowing callers to chain or handle errors.
     *
     * @param customerId partition key for Kafka
     * @param eventType  descriptive event type for logging
     * @param payload    event payload
     * @return Mono completing when the event is sent
     */
    public Mono<Void> publishEventReactive(final UUID customerId, final String eventType, final Map<String, Object> payload) {
        return eventPublisher.publishLoanEvent(customerId, payload)
                .doOnSuccess(v -> log.debug("{} event published for customerId={}", eventType, customerId))
                .doOnError(err -> log.error("Failed to publish {} event for customerId={}", eventType, customerId, err));
    }
}
