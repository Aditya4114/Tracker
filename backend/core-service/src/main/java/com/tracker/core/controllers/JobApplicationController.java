package com.tracker.core.controllers;

import com.tracker.core.models.JobApplication;
import com.tracker.core.repositories.JobApplicationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    @Value("${intelligence.service.url:http://localhost:8083}")
    private String intelligenceServiceUrl;

    private final JobApplicationRepository jobApplicationRepository;
    private final com.tracker.core.services.JobConsumerService jobConsumerService;
    private final RestTemplate restTemplate;

    public JobApplicationController(JobApplicationRepository jobApplicationRepository,
                                    com.tracker.core.services.JobConsumerService jobConsumerService,
                                    RestTemplate restTemplate) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobConsumerService = jobConsumerService;
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<List<JobApplication>> getApplications(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId) {
        return ResponseEntity.ok(jobApplicationRepository.findByUserIdOrderByAppliedDateDesc(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getApplication(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id) {
        Optional<JobApplication> appOpt = jobApplicationRepository.findByIdAndUserId(id, userId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(appOpt.get());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateApplication(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        Optional<JobApplication> appOpt = jobApplicationRepository.findByIdAndUserId(id, userId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JobApplication application = appOpt.get();

        String company = payload.get("company");
        String position = payload.get("position");
        String status = payload.get("status");
        String jobLink = payload.get("jobLink");

        if (company != null && !company.trim().isEmpty()) {
            application.setCompany(company.trim());
        }
        if (position != null && !position.trim().isEmpty()) {
            application.setPosition(position.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            application.setCurrentStatus(status.trim());
        }
        if (jobLink != null) {
            application.setJobLink(jobLink.trim());
        }

        jobApplicationRepository.save(application);

        return ResponseEntity.ok(Map.of("message", "Application updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteApplication(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id) {

        Optional<JobApplication> appOpt = jobApplicationRepository.findByIdAndUserId(id, userId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        jobApplicationRepository.delete(appOpt.get());
        return ResponseEntity.ok(Map.of("message", "Application removed successfully"));
    }

    @PostMapping("/{id}/mark-as-spam")
    public ResponseEntity<?> markAsSpam(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id) {

        Optional<JobApplication> appOpt = jobApplicationRepository.findByIdAndUserId(id, userId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JobApplication application = appOpt.get();

        // 1. Train Bayesian model negatively and mark email as processed in intelligence service
        try {
            Map<String, Object> payload = Map.of(
                    "userId", userId,
                    "subject", application.getEmailSubject() != null ? application.getEmailSubject() : "",
                    "snippet", application.getEmailSnippet() != null ? application.getEmailSnippet() : "",
                    "messageId", application.getSourceEmailId() != null ? application.getSourceEmailId() : ""
            );
            restTemplate.postForEntity(intelligenceServiceUrl + "/api/classification/internal/train-spam", payload, Void.class);
        } catch (Exception e) {
            System.err.println("Could not train intelligence service on spam: " + e.getMessage());
        }

        // 2. Delete the JobApplication
        jobApplicationRepository.delete(application);

        return ResponseEntity.ok(Map.of("message", "Application marked as spam, model trained, and record removed."));
    }

    @PostMapping("/internal/save-job")
    public ResponseEntity<?> saveJobInternal(@RequestBody com.tracker.events.ParsedJobEvent event) {
        jobConsumerService.consumeParsedJob(event);
        return ResponseEntity.ok(Map.of("status", "SAVED"));
    }
}
