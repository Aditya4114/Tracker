package com.tracker.ingestion.controllers;

import com.tracker.ingestion.config.TokenExtractor;
import com.tracker.ingestion.models.SyncRequest;
import com.tracker.ingestion.services.IngestionGmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/sync")
@CrossOrigin(origins = "*")
public class SyncController {

    private final IngestionGmailService ingestionGmailService;
    private final TokenExtractor tokenExtractor;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${internal.secret:super-secret-internal-key}")
    private String internalSecret;

    public SyncController(IngestionGmailService ingestionGmailService,
                          TokenExtractor tokenExtractor,
                          RestTemplate restTemplate) {
        this.ingestionGmailService = ingestionGmailService;
        this.tokenExtractor = tokenExtractor;
        this.restTemplate = restTemplate;
    }

    @PostMapping({"", "/start"})
    public ResponseEntity<?> syncEmails(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) SyncRequest request) {

        String username = tokenExtractor.extractUsername(authHeader);
        String userEmail = username;

        // Resolve userId & userEmail from auth-service if userId is not supplied in header
        if (userId == null && username != null) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internalSecret);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> res = restTemplate.exchange(
                        authServiceUrl + "/api/auth/internal/user/" + username,
                        HttpMethod.GET, entity, Map.class);
                if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                    Object idObj = res.getBody().get("id");
                    if (idObj instanceof Number) {
                        userId = ((Number) idObj).longValue();
                    }
                    Object emailObj = res.getBody().get("email");
                    if (emailObj != null) {
                        userEmail = emailObj.toString();
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not resolve user from auth-service: " + e.getMessage());
            }
        }

        // Fallback default
        if (userId == null) {
            userId = 1L;
        }
        if (userEmail == null) {
            userEmail = "aditya04112002@gmail.com";
        }

        LocalDate startDate = (request != null && request.getStartDate() != null) 
                ? request.getStartDate() 
                : LocalDate.now().minusDays(7);

        LocalDate endDate = (request != null && request.getEndDate() != null) 
                ? request.getEndDate() 
                : LocalDate.now();

        if (startDate.isBefore(endDate.minusMonths(1))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Sync range cannot exceed 1 month."));
        }

        try {
            CompletableFuture<String> future = ingestionGmailService.syncEmailsAsync(userId, userEmail, startDate, endDate);
            String result = future.get();
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", result
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Sync failed: " + e.getMessage()));
        }
    }
}
