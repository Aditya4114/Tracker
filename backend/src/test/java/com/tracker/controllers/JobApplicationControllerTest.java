package com.tracker.controllers;

import com.tracker.models.JobApplication;
import com.tracker.models.ProcessedEmail;
import com.tracker.models.User;
import com.tracker.repositories.JobApplicationRepository;
import com.tracker.repositories.ProcessedEmailRepository;
import com.tracker.repositories.UserRepository;
import com.tracker.services.BayesianClassifierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationControllerTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProcessedEmailRepository processedEmailRepository;

    @Mock
    private BayesianClassifierService bayesianClassifierService;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private JobApplicationController jobApplicationController;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testMarkAsSpam_Success() {
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        JobApplication app = new JobApplication();
        app.setId(100L);
        app.setUser(user);
        app.setEmailSubject("Spam Offer");
        app.setEmailSnippet("Make millions from home");
        app.setSourceEmailId("msg-12345");

        when(jobApplicationRepository.findByIdAndUser(100L, user)).thenReturn(Optional.of(app));
        when(processedEmailRepository.existsByMessageId("msg-12345")).thenReturn(false);

        ResponseEntity<?> response = jobApplicationController.markAsSpam(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bayesianClassifierService, times(1)).train(eq("Spam Offer"), eq("Make millions from home"), isNull(), eq(false));
        verify(processedEmailRepository, times(1)).save(any(ProcessedEmail.class));
        verify(jobApplicationRepository, times(1)).delete(app);
    }

    @Test
    void testMarkAsSpam_NotFound() {
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jobApplicationRepository.findByIdAndUser(999L, user)).thenReturn(Optional.empty());

        ResponseEntity<?> response = jobApplicationController.markAsSpam(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(bayesianClassifierService, never()).train(any(), any(), any(), anyBoolean());
        verify(jobApplicationRepository, never()).delete(any());
    }
}
