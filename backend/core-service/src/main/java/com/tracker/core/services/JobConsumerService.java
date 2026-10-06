package com.tracker.core.services;

import com.tracker.core.models.ApplicationEvent;
import com.tracker.core.models.JobApplication;
import com.tracker.core.repositories.ApplicationEventRepository;
import com.tracker.core.repositories.JobApplicationRepository;
import com.tracker.events.ParsedJobEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(JobConsumerService.class);

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationEventRepository applicationEventRepository;

    public JobConsumerService(JobApplicationRepository jobApplicationRepository,
                              ApplicationEventRepository applicationEventRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.applicationEventRepository = applicationEventRepository;
    }

    @KafkaListener(topics = "jobtracker.parsed-jobs", groupId = "core-group")
    @Transactional
    public void consumeParsedJob(ParsedJobEvent event) {
        logger.info("Received ParsedJobEvent for user: {}, company: {}, position: {}", 
                event.getUserId(), event.getCompany(), event.getPosition());

        try {
            LocalDateTime appliedDate = event.getAppliedDate() != null ? event.getAppliedDate() : LocalDateTime.now();

            // Check for duplicate on the same day for same company and user
            if (event.getCompany() != null && !"Not Available".equalsIgnoreCase(event.getCompany())) {
                LocalDateTime dayStart = appliedDate.toLocalDate().atStartOfDay();
                LocalDateTime dayEnd = dayStart.plusDays(1);
                List<JobApplication> existing = jobApplicationRepository.findByUserIdAndCompanyAndAppliedDateBetween(
                        event.getUserId(), event.getCompany(), dayStart, dayEnd);
                if (!existing.isEmpty()) {
                    logger.info("Application already exists for user {} at company {} on {}. Skipping duplicate.",
                            event.getUserId(), event.getCompany(), appliedDate.toLocalDate());
                    return;
                }
            }

            JobApplication application = new JobApplication();
            application.setUserId(event.getUserId());
            application.setCompany(event.getCompany());
            application.setPosition(event.getPosition());
            application.setJobId(event.getJobId());
            application.setCurrentStatus(event.getStatus() != null ? event.getStatus() : "Applied");
            application.setAppliedDate(appliedDate);
            application.setSourceEmailId(event.getSourceEmailId());
            application.setEmailSubject(event.getEmailSubject());
            application.setEmailSnippet(event.getEmailSnippet());

            JobApplication saved = jobApplicationRepository.save(application);

            // Record initial application event
            ApplicationEvent appEvent = new ApplicationEvent();
            appEvent.setJobApplication(saved);
            appEvent.setEventType(saved.getCurrentStatus());
            appEvent.setDescription("Application imported via email ingestion: " + (event.getEmailSubject() != null ? event.getEmailSubject() : ""));
            appEvent.setEventDate(appliedDate);
            applicationEventRepository.save(appEvent);

            logger.info("Successfully persisted JobApplication id: {} for user: {}", saved.getId(), event.getUserId());

        } catch (Exception e) {
            logger.error("Failed to process ParsedJobEvent: {}", e.getMessage(), e);
            throw e; // Kafka will retry based on consumer container retry policy
        }
    }
}
