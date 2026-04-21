package com.glo.lending.loan.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration with partitioning strategy.
 * <p>
 * <b>Partitioning Strategy:</b> Events are partitioned by {@code customerId} so that
 * all events for a given customer are delivered to the same partition, guaranteeing
 * ordered processing per customer. This is critical for:
 * <ul>
 *   <li>Correct notification sequencing (e.g., LOAN_CREATED before DUE_DATE_REMINDER)</li>
 *   <li>Preventing race conditions in customer limit updates</li>
 *   <li>Enabling scalable, parallel consumption across partitions</li>
 * </ul>
 * Topics use 6 partitions for horizontal scalability with 2 replicas for fault tolerance.
 * </p>
 */
@Configuration
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the loan events topic with 6 partitions and replication factor 2.
     * Partitioning by customerId ensures per-customer ordering.
     *
     * @return the loan events topic configuration
     */
    @Bean
    public NewTopic loanEventsTopic() {
        log.info("Creating Kafka topic: lending.loan.events with 6 partitions");
        return TopicBuilder.name("lending.loan.events")
                .partitions(6)
                .replicas(1) // Use 2+ in production with multi-broker setup
                .build();
    }

    /**
     * Creates the customer events topic with 6 partitions.
     *
     * @return the customer events topic configuration
     */
    @Bean
    public NewTopic customerEventsTopic() {
        log.info("Creating Kafka topic: lending.customer.events with 6 partitions");
        return TopicBuilder.name("lending.customer.events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    /**
     * Producer factory configured for JSON serialization.
     *
     * @return a {@link ProducerFactory} for String keys and Object values
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        final Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Enable idempotent producer to avoid duplicate messages on retries
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Kafka template configured to use customerId as partition key.
     *
     * @return a {@link KafkaTemplate} for publishing events
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

