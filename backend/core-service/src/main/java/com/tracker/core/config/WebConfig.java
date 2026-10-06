package com.tracker.core.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebConfig {

    @Bean
    public RestTemplate restTemplate(ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        RestTemplate restTemplate = new RestTemplate();
        ObservationRegistry registry = observationRegistryProvider.getIfAvailable();
        if (registry != null) {
            restTemplate.setObservationRegistry(registry);
        }
        return restTemplate;
    }
}
