package com.api.backend.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic reservationCreatedTopic() {
        return TopicBuilder.name("ReservationCreated").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name("PaymentCompleted").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reservationExpiredTopic() {
        return TopicBuilder.name("ReservationExpired").partitions(3).replicas(1).build();
    }
}
