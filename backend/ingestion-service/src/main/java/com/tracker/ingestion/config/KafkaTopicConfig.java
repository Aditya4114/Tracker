package com.tracker.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String RAW_EMAILS_TOPIC = "jobtracker.raw-emails";

    @Bean
    public NewTopic rawEmailsTopic() {
        return TopicBuilder.name(RAW_EMAILS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
