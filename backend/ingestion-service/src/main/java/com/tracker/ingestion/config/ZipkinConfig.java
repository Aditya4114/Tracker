package com.tracker.ingestion.config;

import brave.Tracing;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.Sender;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

@Configuration
public class ZipkinConfig {

    @Value("${spring.application.name:ingestion-service}")
    private String serviceName;

    @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}")
    private String endpoint;

    @Bean
    public Sender zipkinSender() {
        return URLConnectionSender.create(endpoint);
    }

    @Bean
    public SpanHandler zipkinSpanHandler(Sender sender) {
        return AsyncZipkinSpanHandler.create(sender);
    }

    @Bean
    public Tracing braveTracing(SpanHandler spanHandler) {
        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .addSpanHandler(spanHandler)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .build();
    }

    @Bean
    public brave.Tracer braveTracer(Tracing tracing) {
        return tracing.tracer();
    }

    @Bean
    public io.micrometer.tracing.Tracer micrometerTracer(brave.Tracer tracer, Tracing tracing) {
        return new BraveTracer(tracer, new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());
    }

    @Bean
    public ObservationRegistry observationRegistry(io.micrometer.tracing.Tracer tracer) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
        return registry;
    }
}
