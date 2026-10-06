package com.tracker.intelligence.services;

import com.tracker.events.ParsedJobEvent;
import com.tracker.events.RawEmailEvent;
import com.tracker.intelligence.models.PendingReviewEmail;
import com.tracker.intelligence.models.ProcessedEmail;
import com.tracker.intelligence.repositories.PendingReviewEmailRepository;
import com.tracker.intelligence.repositories.ProcessedEmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class EmailConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(EmailConsumerService.class);

    public static final String PARSED_JOBS_TOPIC = "jobtracker.parsed-jobs";
    public static final String RAW_EMAILS_DLQ_TOPIC = "jobtracker.raw-emails.dlq";

    @Value("${core.service.url:http://localhost:8084}")
    private String coreServiceUrl;

    private final ProcessedEmailRepository processedEmailRepository;
    private final PendingReviewEmailRepository pendingReviewEmailRepository;
    private final BayesianClassifierService bayesianClassifierService;
    private final ParsingService parsingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final java.util.concurrent.atomic.AtomicBoolean kafkaAvailable = new java.util.concurrent.atomic.AtomicBoolean(true);
    private long lastKafkaCheckTime = 0;

    public EmailConsumerService(ProcessedEmailRepository processedEmailRepository,
                                PendingReviewEmailRepository pendingReviewEmailRepository,
                                BayesianClassifierService bayesianClassifierService,
                                ParsingService parsingService,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                RestTemplate restTemplate) {
        this.processedEmailRepository = processedEmailRepository;
        this.pendingReviewEmailRepository = pendingReviewEmailRepository;
        this.bayesianClassifierService = bayesianClassifierService;
        this.parsingService = parsingService;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
    }

    @KafkaListener(topics = "jobtracker.raw-emails", groupId = "intelligence-group")
    public void consumeRawEmail(RawEmailEvent event) {
        logger.info("Received RawEmailEvent for messageId: {}, user: {}", event.getMessageId(), event.getUserId());

        try {
            // Deduplication check
            if (processedEmailRepository.existsByMessageId(event.getMessageId())) {
                logger.info("Email {} already processed. Skipping.", event.getMessageId());
                return;
            }

            // Step 1: Run Bayesian Classification
            BayesianClassifierService.ClassificationResult classification =
                    bayesianClassifierService.classify(event.getSubject(), event.getSnippet(), event.getSender());

            // Step 2: Route by Classification
            if (classification == BayesianClassifierService.ClassificationResult.SPAM) {
                logger.info("Classified email {} as SPAM. Recording as processed.", event.getMessageId());
                processedEmailRepository.save(new ProcessedEmail(event.getUserId(), event.getMessageId()));
                return;
            }

            if (classification == BayesianClassifierService.ClassificationResult.UNSURE) {
                logger.info("Classified email {} as UNSURE. Queuing for human review.", event.getMessageId());
                if (!pendingReviewEmailRepository.existsByUserIdAndMessageId(event.getUserId(), event.getMessageId())) {
                    pendingReviewEmailRepository.save(new PendingReviewEmail(
                            event.getUserId(),
                            event.getMessageId(),
                            event.getSender(),
                            event.getSubject(),
                            event.getSnippet(),
                            event.getFullBody(),
                            event.getEmailDate()
                    ));
                }
                return;
            }

            // Step 3: Guaranteed JOB -> Extract details via Regex & Gemini Fallback
            processAndPublishJob(event.getUserId(), event.getMessageId(), event.getSubject(), 
                    event.getFullBody(), event.getSender(), event.getSnippet(), event.getEmailDate());

        } catch (Exception e) {
            logger.error("Error processing email {}: {}. Routing to DLQ.", event.getMessageId(), e.getMessage(), e);
            try {
                kafkaTemplate.send(RAW_EMAILS_DLQ_TOPIC, String.valueOf(event.getUserId()), event);
            } catch (Exception ignored) {}
        }
    }

    public void processAndPublishJob(Long userId, String messageId, String subject, String body, 
                                     String sender, String snippet, LocalDateTime emailDate) {
        Map<String, String> extractedData = parsingService.parseEmail(subject, body, sender);

        String company = extractedData.get("company");
        String position = extractedData.get("position");
        String jobId = extractedData.get("jobId");
        String status = extractedData.get("status");

        ParsedJobEvent parsedJobEvent = ParsedJobEvent.builder()
                .userId(userId)
                .company(company)
                .position(position)
                .jobId(jobId)
                .status(status)
                .appliedDate(emailDate != null ? emailDate : LocalDateTime.now())
                .sourceEmailId(messageId)
                .emailSubject(subject)
                .emailSnippet(snippet != null && snippet.length() > 500 ? snippet.substring(0, 500) : snippet)
                .build();

        // Try publishing to Kafka topic for core service with circuit breaker
        boolean shouldTryKafka = kafkaAvailable.get() || (System.currentTimeMillis() - lastKafkaCheckTime > 30000);
        boolean publishedToKafka = false;
        if (shouldTryKafka) {
            lastKafkaCheckTime = System.currentTimeMillis();
            try {
                kafkaTemplate.send(PARSED_JOBS_TOPIC, String.valueOf(userId), parsedJobEvent)
                        .get(5, TimeUnit.SECONDS);
                publishedToKafka = true;
                kafkaAvailable.set(true);
                logger.info("Successfully published ParsedJobEvent for messageId: {} to Kafka topic: {}", messageId, PARSED_JOBS_TOPIC);
            } catch (Exception kafkaEx) {
                kafkaAvailable.set(false);
                logger.warn("Kafka unavailable ({}), falling back to direct REST dispatch to core-service...", kafkaEx.getMessage());
            }
        }

        if (!publishedToKafka) {
            try {
                restTemplate.postForEntity(coreServiceUrl + "/api/applications/internal/save-job", parsedJobEvent, Void.class);
                logger.info("Successfully dispatched ParsedJobEvent for messageId: {} to core-service via direct HTTP", messageId);
            } catch (Exception httpEx) {
                logger.error("Direct HTTP dispatch to core-service failed: {}", httpEx.getMessage());
            }
        }

        // Mark as processed
        processedEmailRepository.save(new ProcessedEmail(userId, messageId));
    }
}
