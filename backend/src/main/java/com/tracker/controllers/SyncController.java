package com.tracker.controllers;

import com.tracker.dtos.SyncRequest;
import com.tracker.models.User;
import com.tracker.repositories.UserRepository;
import com.tracker.services.GmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final GmailService gmailService;
    private final UserRepository userRepository;

    public SyncController(GmailService gmailService, UserRepository userRepository) {
        this.gmailService = gmailService;
        this.userRepository = userRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startSync(@RequestBody(required = false) SyncRequest syncRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            System.out.println("Returning 400 User not found");
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();

        if (!user.isGoogleConnected() || user.getGoogleRefreshToken() == null) {
            System.out.println("Returning 403 Gmail is not connected.");
            return ResponseEntity.status(403).body("Gmail is not connected.");
        }

        LocalDate startDate = (syncRequest != null && syncRequest.getStartDate() != null) 
                ? syncRequest.getStartDate() 
                : LocalDate.now().minusDays(7);
                
        LocalDate endDate = (syncRequest != null && syncRequest.getEndDate() != null) 
                ? syncRequest.getEndDate() 
                : LocalDate.now();

        // Check if range exceeds 1 month
        if (startDate.isBefore(endDate.minusMonths(1))) {
            System.out.println("Returning 400 Sync range cannot exceed 1 month.");
            return ResponseEntity.badRequest().body("Sync range cannot exceed 1 month.");
        }

        try {
            String result = gmailService.syncEmailsAsync(user, startDate, endDate).get();
            System.out.println("Sync finished with result: " + result);
            return ResponseEntity.ok(java.util.Map.of("message", result));
        } catch (Exception e) {
            System.err.println("Sync failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(java.util.Map.of("message", "Sync failed: " + e.getMessage()));
        }
    }
}
