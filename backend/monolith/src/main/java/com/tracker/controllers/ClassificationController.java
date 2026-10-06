package com.tracker.controllers;

import com.tracker.models.PendingReviewEmail;
import com.tracker.models.User;
import com.tracker.repositories.PendingReviewEmailRepository;
import com.tracker.repositories.UserRepository;
import com.tracker.services.GmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/emails")
public class ClassificationController {

    private final PendingReviewEmailRepository pendingReviewEmailRepository;
    private final UserRepository userRepository;
    private final GmailService gmailService;

    public ClassificationController(PendingReviewEmailRepository pendingReviewEmailRepository,
                                    UserRepository userRepository,
                                    GmailService gmailService) {
        this.pendingReviewEmailRepository = pendingReviewEmailRepository;
        this.userRepository = userRepository;
        this.gmailService = gmailService;
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingEmails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        List<PendingReviewEmail> pending = pendingReviewEmailRepository.findByUserOrderByReceivedDateDesc(userOpt.get());
        List<Map<String, Object>> response = pending.stream().map(email -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", email.getId());
            map.put("messageId", email.getMessageId());
            map.put("sender", email.getSender());
            map.put("subject", email.getSubject());
            map.put("snippet", email.getSnippet());
            map.put("receivedDate", email.getReceivedDate());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/pending/{id}/feedback")
    public ResponseEntity<?> submitFeedback(@PathVariable Long id, @RequestParam(name = "isJob") boolean isJob) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();

        boolean result;
        if (isJob) {
            result = gmailService.approvePendingEmail(user, id);
        } else {
            result = gmailService.rejectPendingEmail(user, id);
        }

        if (!result) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process feedback for email ID: " + id));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", isJob ? "Email confirmed as job application and processed." : "Email marked as not a job and dismissed."
        ));
    }
}
