package com.tracker.auth.controllers;

import com.tracker.auth.dtos.AuthRequest;
import com.tracker.auth.dtos.AuthResponse;
import com.tracker.auth.dtos.RegisterRequest;
import com.tracker.auth.models.User;
import com.tracker.auth.repositories.UserRepository;
import com.tracker.auth.security.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final com.tracker.auth.security.GoogleTokenService googleTokenService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
                          PasswordEncoder encoder, JwtUtils jwtUtils, 
                          com.tracker.auth.security.GoogleTokenService googleTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.googleTokenService = googleTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody AuthRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        
        User user = userRepository.findByUsername(loginRequest.getUsername()).orElseThrow();

        return ResponseEntity.ok(new AuthResponse(jwt, user.getUsername(), user.isGoogleConnected()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        if (userRepository.findByUsername(signUpRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        if (userRepository.findByEmail(signUpRequest.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(encoder.encode(signUpRequest.getPassword()));
        user.setGoogleConnected(false);

        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    // INTERNAL ENDPOINT - In production, this should be secured via mTLS or an internal API key
    // For now, we will require an internal secret header
    @GetMapping("/internal/google-token/{userId}")
    public ResponseEntity<?> getGoogleAccessToken(
            @PathVariable Long userId,
            @RequestHeader("X-Internal-Secret") String internalSecret) {
        
        // In a real app, inject this secret from application.yml
        if (!"super-secret-internal-key".equals(internalSecret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        try {
            // Instantiate googleTokenService (we need to inject it)
            String accessToken = googleTokenService.getShortLivedAccessToken(userId);
            return ResponseEntity.ok(accessToken);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to get token: " + e.getMessage());
        }
    }

    @GetMapping("/internal/user/{username}")
    public ResponseEntity<?> getUserByUsername(
            @PathVariable String username,
            @RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret) {
        if (!"super-secret-internal-key".equals(internalSecret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        return userRepository.findByUsername(username)
                .map(u -> ResponseEntity.ok(java.util.Map.of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "email", u.getEmail(),
                        "isGoogleConnected", u.isGoogleConnected()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}

