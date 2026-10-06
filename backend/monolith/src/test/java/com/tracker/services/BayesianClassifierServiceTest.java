package com.tracker.services;

import com.tracker.models.GlobalModelState;
import com.tracker.repositories.GlobalModelStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BayesianClassifierServiceTest {

    private BayesianClassifierService classifierService;
    private GlobalModelStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(GlobalModelStateRepository.class);
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any(GlobalModelState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        classifierService = new BayesianClassifierService(repository);
        classifierService.init(); // Seeds baseline
    }

    @Test
    void testDedicatedAtsFastPassReturnsJob() {
        String subject = "Update regarding your application";
        String snippet = "Thank you for applying";
        String sender = "no-reply@greenhouse.io";

        BayesianClassifierService.ClassificationResult result =
                classifierService.classify(subject, snippet, sender);

        assertEquals(BayesianClassifierService.ClassificationResult.JOB, result);
    }

    @Test
    void testStandardJobApplicationReturnsJob() {
        String subject = "Thank you for applying to Canva!";
        String snippet = "We have received your application for Software Engineer.";
        String sender = "careers@canva.com";

        BayesianClassifierService.ClassificationResult result =
                classifierService.classify(subject, snippet, sender);

        assertEquals(BayesianClassifierService.ClassificationResult.JOB, result);
    }

    @Test
    void testSafetyNetPolicy_FirstTimePromotionalEmailIsNotSilentlyDropped() {
        // A promotional email with low job score from an unknown domain
        String subject = "Aditya, apply now to new open roles!";
        String snippet = "Explore recommendations and apply today on TalentNet.";
        String sender = "alerts@newtalentportal.com";

        BayesianClassifierService.ClassificationResult result =
                classifierService.classify(subject, snippet, sender);

        // Under the Safety Net First Policy, since this domain has NOT been confirmed spam >= 2 times,
        // it must be routed to UNSURE (PendingReview) so the user can review it rather than silently deleting it!
        assertEquals(BayesianClassifierService.ClassificationResult.UNSURE, result);
    }

    @Test
    void testRepeatedSpamFeedbackEnablesSilentSpamFilter() {
        String subject = "Special job alert for you";
        String snippet = "10 people viewed your profile. Apply now!";
        String sender = "marketing@spamdomain.com";

        // First time -> UNSURE
        BayesianClassifierService.ClassificationResult firstRun =
                classifierService.classify(subject, snippet, sender);
        assertEquals(BayesianClassifierService.ClassificationResult.UNSURE, firstRun);

        // Train as spam twice (user clicks "Not a Job" in UI)
        classifierService.train(subject, snippet, sender, false);
        classifierService.train(subject, snippet, sender, false);

        // Now with >= 2 explicit spam confirmations and low score, it is recognized as SPAM
        BayesianClassifierService.ClassificationResult secondRun =
                classifierService.classify(subject, snippet, sender);
        assertEquals(BayesianClassifierService.ClassificationResult.SPAM, secondRun);
    }

    @Test
    void testPiiSanitizationStripsEmailsAndNumbers() {
        String raw = "Hi Aditya Jha, send your resume to candidate123@gmail.com with phone 9876543210 or req ID 1092834.";
        List<String> tokens = classifierService.sanitizeAndTokenize(raw);

        // None of the raw tokens should contain candidate email or numbers
        for (String token : tokens) {
            assertFalse(token.contains("candidate123@gmail.com"));
            assertFalse(token.contains("9876543210"));
            assertFalse(token.contains("1092834"));
        }
        assertTrue(tokens.contains("resume"));
    }
}
