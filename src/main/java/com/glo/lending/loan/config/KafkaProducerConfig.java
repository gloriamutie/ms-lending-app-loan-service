package com.glo.lending.loan.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
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
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.loan-events}")
    private String loanEventsTopic;

    @Value("${app.kafka.topic.partitions}")
    private int partitions;

    /**
     * KafkaAdmin is required for automatic topic creation from NewTopic beans.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        final Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Auto-creates the loan events topic on startup if it does not already exist.
     */
    @Bean
    public NewTopic loanEventsTopic() {
        log.info("Initializing Kafka topic={}, partitions={}", loanEventsTopic, partitions);
        return TopicBuilder.name(loanEventsTopic)
                .partitions(partitions)
                .replicas(1)
                .build();
    }



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

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {

        return new KafkaTemplate<>(producerFactory());
    }

}

