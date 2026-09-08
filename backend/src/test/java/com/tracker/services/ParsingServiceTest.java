package com.tracker.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParsingServiceTest {

    private ParsingService parsingService;

    @BeforeEach
    void setUp() {
        parsingService = new ParsingService();
    }

    @Test
    void testLinkedInEasyApplyParsing() {
        String subject = "Your application for Senior Software Engineer at Stripe was sent to Stripe";
        String body = "Hi Alex, your application for Senior Software Engineer at Stripe was submitted.";
        String sender = "LinkedIn <jobs-listings@linkedin.com>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Stripe", result.get("company"));
        assertEquals("Senior Software Engineer", result.get("position"));
        assertEquals("Applied", result.get("status"));
    }

    @Test
    void testGreenhouseApplicationParsing() {
        String subject = "Thank you for applying to Figma!";
        String body = "Hi Alex, thank you for applying to the Frontend Engineer role at Figma. We have received your application. Req ID: REQ-89123";
        String sender = "Figma Careers <no-reply@greenhouse.io>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Figma", result.get("company"));
        assertEquals("Frontend Engineer", result.get("position"));
        assertEquals("REQ-89123", result.get("jobId"));
        assertEquals("Applied", result.get("status"));
    }

    @Test
    void testLeverRejectionParsing() {
        String subject = "Thank you for your interest in Datadog";
        String body = "Thank you for applying to Datadog. Unfortunately, after careful consideration, we have decided not to move forward with your application for the Backend Developer position at this time.";
        String sender = "Datadog Team <jobs@lever.co>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Datadog", result.get("company"));
        assertEquals("Rejected", result.get("status"));
    }

    @Test
    void testWorkdayInterviewInvitation() {
        String subject = "Application Submitted: Workday - Interview Invitation";
        String body = "Thank you for applying to Workday. We would like to invite you to interview for the Systems Architect position. Please schedule a time.";
        String sender = "Workday Recruitment <noreply@myworkday.com>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Workday", result.get("company"));
        assertEquals("Interview", result.get("status"));
    }

    @Test
    void testIndeedApplicationParsing() {
        String subject = "Indeed Application: Full Stack Developer - Airbnb";
        String body = "You applied to Airbnb for Full Stack Developer.";
        String sender = "Indeed Applications <indeedapply@indeed.com>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Airbnb", result.get("company"));
        assertEquals("Full Stack Developer", result.get("position"));
        assertEquals("Applied", result.get("status"));
    }

    @Test
    void testDirectCompanyDomainFallback() {
        String subject = "Update regarding your application";
        String body = "Thank you for taking the time to speak with us. We are pleased to offer you the position!";
        String sender = "Uber Recruiting <careers@uber.com>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Uber", result.get("company"));
        assertEquals("Offer", result.get("status"));
    }

    @Test
    void testGeneralSubjectHeuristicWithJobId() {
        String subject = "Application to Snowflake for Cloud Engineer (Job ID: SNOW-4401)";
        String body = "We have received your application. We are currently reviewing your application.";
        String sender = "Snowflake <no-reply@snowflake.com>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Snowflake", result.get("company"));
        assertEquals("Cloud Engineer", result.get("position"));
        assertEquals("SNOW-4401", result.get("jobId"));
        assertEquals("In Progress", result.get("status"));
    }

    @Test
    void testAshbyApplicationParsing() {
        String subject = "Application to Notion";
        String body = "Thanks for applying to the Product Designer role at Notion! We are excited to review your background.";
        String sender = "Notion <jobs@ashbyhq.com>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Notion", result.get("company"));
        assertEquals("Product Designer", result.get("position"));
        assertEquals("Applied", result.get("status"));
    }

    @Test
    void testSmartRecruitersAssessmentParsing() {
        String subject = "Your application to Spotify";
        String body = "Hi Alex, thank you for your application. As next step, please complete this coding assessment on HackerRank within 5 days.";
        String sender = "Spotify Recruiting <no-reply@smartrecruiters.com>";

        Map<String, String> result = parsingService.extractUsingRegex(subject, body, sender);

        assertEquals("Spotify", result.get("company"));
        assertEquals("Interview", result.get("status"));
    }

    @Test
    void testParseEmailBypassesGeminiWhenConfident() {
        // Since Gemini API key / URL might not be real in test mode,
        // parseEmail should return immediately without throwing an exception or calling Gemini.
        String subject = "Thank you for applying to Canva!";
        String body = "We have received your application for Software Engineer.";
        String sender = "Canva Careers <careers@canva.com>";

        Map<String, String> result = parsingService.parseEmail(subject, body, sender);

        assertEquals("Canva", result.get("company"));
        assertEquals("Applied", result.get("status"));
    }
}
