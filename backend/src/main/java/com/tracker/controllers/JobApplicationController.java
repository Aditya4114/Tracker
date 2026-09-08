package com.tracker.controllers;

import com.tracker.models.JobApplication;
import com.tracker.models.User;
import com.tracker.repositories.JobApplicationRepository;
import com.tracker.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;
    private final com.tracker.repositories.ProcessedEmailRepository processedEmailRepository;
    private final com.tracker.services.BayesianClassifierService bayesianClassifierService;

    public JobApplicationController(JobApplicationRepository jobApplicationRepository,
                                  UserRepository userRepository,
                                  com.tracker.repositories.ProcessedEmailRepository processedEmailRepository,
                                  com.tracker.services.BayesianClassifierService bayesianClassifierService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userRepository = userRepository;
        this.processedEmailRepository = processedEmailRepository;
        this.bayesianClassifierService = bayesianClassifierService;
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateApplication(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();

        Optional<JobApplication> appOpt = jobApplicationRepository.findByIdAndUser(id, user);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JobApplication application = appOpt.get();

        String company = payload.get("company");
        String position = payload.get("position");
        String status = payload.get("status");

        if (company != null && !company.trim().isEmpty()) {
            application.setCompany(company.trim());
        }
        if (position != null && !position.trim().isEmpty()) {
            application.setPosition(position.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            application.setCurrentStatus(status.trim());
        }

        jobApplicationRepository.save(application);

        return ResponseEntity.ok(Map.of("message", "Application updated successfully"));
    }

    @PostMapping("/{id}/mark-as-spam")
    public ResponseEntity<?> markAsSpam(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();

        Optional<JobApplication> appOpt = jobApplicationRepository.findByIdAndUser(id, user);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JobApplication application = appOpt.get();

        // 1. Train the Bayesian model with negative feedback
        String subject = application.getEmailSubject() != null ? application.getEmailSubject() : "";
        String snippet = application.getEmailSnippet() != null ? application.getEmailSnippet() : "";
        bayesianClassifierService.train(subject, snippet, null, false);

        // 2. Ensure sourceEmailId is marked as processed so it won't be re-imported on future syncs
        if (application.getSourceEmailId() != null && !application.getSourceEmailId().isBlank()) {
            if (!processedEmailRepository.existsByMessageId(application.getSourceEmailId())) {
                processedEmailRepository.save(new com.tracker.models.ProcessedEmail(user, application.getSourceEmailId()));
            }
        }

        // 3. Delete the JobApplication
        jobApplicationRepository.delete(application);

        return ResponseEntity.ok(Map.of("message", "Application marked as spam, model trained, and record removed."));
    }
}
