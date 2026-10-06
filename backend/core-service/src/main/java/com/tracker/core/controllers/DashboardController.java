package com.tracker.core.controllers;

import com.tracker.core.models.JobApplication;
import com.tracker.core.repositories.JobApplicationRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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

    public DashboardController(JobApplicationRepository jobApplicationRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
    }

    @GetMapping
    public ResponseEntity<?> getDashboardData(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<JobApplication> allApplications = jobApplicationRepository.findByUserIdOrderByAppliedDateDesc(userId);

        List<JobApplication> filtered = allApplications.stream()
                .filter(app -> app.getAppliedDate() != null 
                        && !app.getAppliedDate().isBefore(startDateTime) 
                        && !app.getAppliedDate().isAfter(endDateTime))
                .collect(Collectors.toList());

        int totalApplications = filtered.size();
        long rejectedCount = filtered.stream()
                .filter(app -> "Rejected".equalsIgnoreCase(app.getCurrentStatus()))
                .count();

        long inProgressCount = filtered.stream()
                .filter(app -> "In Progress".equalsIgnoreCase(app.getCurrentStatus()) || "Interview".equalsIgnoreCase(app.getCurrentStatus()))
                .count();

        long respondedCount = filtered.stream()
                .filter(app -> !"Applied".equalsIgnoreCase(app.getCurrentStatus()))
                .count();

        int responseRate = totalApplications > 0 ? (int) Math.round((respondedCount * 100.0) / totalApplications) : 0;

        List<Map<String, Object>> manualReviewList = filtered.stream()
                .filter(app -> isMissing(app.getCompany()) || isMissing(app.getPosition()))
                .map(this::mapApplication)
                .collect(Collectors.toList());

        List<Map<String, Object>> applicationsList = filtered.stream()
                .map(this::mapApplication)
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

    private Map<String, Object> mapApplication(JobApplication app) {
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
    }

    private boolean isMissing(String value) {
        return value == null || value.trim().isEmpty() || "Not Available".equalsIgnoreCase(value.trim());
    }
}
