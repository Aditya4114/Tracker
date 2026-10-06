package com.tracker.intelligence.controllers;

import com.tracker.intelligence.models.PendingReviewEmail;
import com.tracker.intelligence.models.ProcessedEmail;
import com.tracker.intelligence.repositories.PendingReviewEmailRepository;
import com.tracker.intelligence.repositories.ProcessedEmailRepository;
import com.tracker.intelligence.services.BayesianClassifierService;
import com.tracker.intelligence.services.EmailConsumerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping({"/api/classification", "/api/emails"})
@CrossOrigin(origins = "*")
public class ClassificationController {

    private final PendingReviewEmailRepository pendingReviewEmailRepository;
    private final ProcessedEmailRepository processedEmailRepository;
    private final BayesianClassifierService bayesianClassifierService;
    private final EmailConsumerService emailConsumerService;

    public ClassificationController(PendingReviewEmailRepository pendingReviewEmailRepository,
                                    ProcessedEmailRepository processedEmailRepository,
                                    BayesianClassifierService bayesianClassifierService,
                                    EmailConsumerService emailConsumerService) {
        this.pendingReviewEmailRepository = pendingReviewEmailRepository;
        this.processedEmailRepository = processedEmailRepository;
        this.bayesianClassifierService = bayesianClassifierService;
        this.emailConsumerService = emailConsumerService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PendingReviewEmail>> getPendingReviews(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId) {
        return ResponseEntity.ok(pendingReviewEmailRepository.findByUserIdOrderByReceivedDateDesc(userId));
    }

    @PostMapping("/pending/{id}/feedback")
    public ResponseEntity<?> submitFeedback(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id,
            @RequestParam(name = "isJob") boolean isJob) {
        if (isJob) {
            return approvePendingEmail(userId, id);
        } else {
            return rejectPendingEmail(userId, id);
        }
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approvePendingEmail(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id) {
        Optional<PendingReviewEmail> pendingOpt = pendingReviewEmailRepository.findByIdAndUserId(id, userId);
        if (pendingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PendingReviewEmail pending = pendingOpt.get();

        // 1. Train classifier positively
        bayesianClassifierService.train(pending.getSubject(), pending.getSnippet(), pending.getSender(), true);

        // 2. Parse and publish to Kafka
        emailConsumerService.processAndPublishJob(
                userId,
                pending.getMessageId(),
                pending.getSubject(),
                pending.getFullBody() != null ? pending.getFullBody() : pending.getSnippet(),
                pending.getSender(),
                pending.getSnippet(),
                pending.getReceivedDate()
        );

        // 3. Remove from pending review
        pendingReviewEmailRepository.delete(pending);

        return ResponseEntity.ok().body(java.util.Map.of(
                "success", true,
                "message", "Email confirmed as job application and processed."
        ));
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> rejectPendingEmail(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id) {
        Optional<PendingReviewEmail> pendingOpt = pendingReviewEmailRepository.findByIdAndUserId(id, userId);
        if (pendingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PendingReviewEmail pending = pendingOpt.get();

        // 1. Train classifier negatively (as spam)
        bayesianClassifierService.train(pending.getSubject(), pending.getSnippet(), pending.getSender(), false);

        // 2. Mark as processed
        processedEmailRepository.save(new ProcessedEmail(userId, pending.getMessageId()));
        pendingReviewEmailRepository.delete(pending);

        return ResponseEntity.ok().body(java.util.Map.of(
                "success", true,
                "message", "Email marked as not a job and dismissed."
        ));
    }

    @PostMapping("/internal/process-email")
    public ResponseEntity<?> processEmailInternal(@RequestBody com.tracker.events.RawEmailEvent event) {
        emailConsumerService.consumeRawEmail(event);
        return ResponseEntity.ok(java.util.Map.of("status", "PROCESSED"));
    }

    @PostMapping("/internal/train-spam")
    public ResponseEntity<?> trainSpamInternal(@RequestBody java.util.Map<String, Object> payload) {
        Long userId = payload.get("userId") != null ? Long.valueOf(payload.get("userId").toString()) : 1L;
        String subject = (String) payload.get("subject");
        String snippet = (String) payload.get("snippet");
        String messageId = (String) payload.get("messageId");

        // 1. Train classifier negatively
        bayesianClassifierService.train(subject != null ? subject : "", snippet != null ? snippet : "", null, false);

        // 2. Mark processed so it won't be re-imported
        if (messageId != null && !messageId.isBlank()) {
            if (!processedEmailRepository.existsByMessageId(messageId)) {
                processedEmailRepository.save(new ProcessedEmail(userId, messageId));
            }
        }

        return ResponseEntity.ok(java.util.Map.of("status", "TRAINED"));
    }
}
