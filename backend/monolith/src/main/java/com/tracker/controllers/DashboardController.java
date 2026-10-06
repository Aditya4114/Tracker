package com.tracker.controllers;

import com.tracker.models.JobApplication;
import com.tracker.models.User;
import com.tracker.repositories.JobApplicationRepository;
import com.tracker.repositories.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;

    public DashboardController(JobApplicationRepository jobApplicationRepository, UserRepository userRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getDashboardData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        // Limit range to max 1 month
        if (startDate.isBefore(endDate.minusMonths(1))) {
            startDate = endDate.minusMonths(1);
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<JobApplication> applications = jobApplicationRepository.findByUserAndAppliedDateBetween(user, startDateTime, endDateTime);

        int totalApplications = applications.size();
        long rejectedCount = applications.stream()
                .filter(app -> "Rejected".equalsIgnoreCase(app.getCurrentStatus()))
                .count();

        long inProgressCount = applications.stream()
                .filter(app -> "In Progress".equalsIgnoreCase(app.getCurrentStatus()) || "Interview".equalsIgnoreCase(app.getCurrentStatus()))
                .count();

        long respondedCount = applications.stream()
                .filter(app -> !"Applied".equalsIgnoreCase(app.getCurrentStatus()))
                .count();

        int responseRate = totalApplications > 0 ? (int) Math.round((respondedCount * 100.0) / totalApplications) : 0;

        List<Map<String, Object>> manualReviewList = applications.stream()
                .filter(app -> isMissing(app.getCompany()) || isMissing(app.getPosition()))
                .map(app -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", app.getId());
                    map.put("company", app.getCompany());
                    map.put("position", app.getPosition());
                    map.put("currentStatus", app.getCurrentStatus());
                    map.put("appliedDate", app.getAppliedDate());
                    map.put("sourceEmailId", app.getSourceEmailId());
                    map.put("emailSubject", app.getEmailSubject());
                    map.put("emailSnippet", app.getEmailSnippet());
                    map.put("gmailLink", app.getSourceEmailId() != null 
                            ? "https://mail.google.com/mail/u/0/#all/" + app.getSourceEmailId() 
                            : null);
                    return map;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> applicationsList = applications.stream()
                .map(app -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", app.getId());
                    map.put("company", app.getCompany());
                    map.put("position", app.getPosition());
                    map.put("currentStatus", app.getCurrentStatus());
                    map.put("appliedDate", app.getAppliedDate());
                    map.put("sourceEmailId", app.getSourceEmailId());
                    map.put("emailSubject", app.getEmailSubject());
                    map.put("emailSnippet", app.getEmailSnippet());
                    map.put("gmailLink", app.getSourceEmailId() != null 
                            ? "https://mail.google.com/mail/u/0/#all/" + app.getSourceEmailId() 
                            : null);
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("totalApplications", totalApplications);
        response.put("inProgress", inProgressCount);
        response.put("rejected", rejectedCount);
        response.put("responseRate", responseRate);
        response.put("manualReviewList", manualReviewList);
        response.put("applications", applicationsList);

        return ResponseEntity.ok(response);
    }

    private boolean isMissing(String value) {
        return value == null || value.trim().isEmpty() || "Not Available".equalsIgnoreCase(value.trim());
    }
}
