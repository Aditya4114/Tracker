package com.tracker.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.tracker.models.DailySyncLog;
import com.tracker.models.JobApplication;
import com.tracker.models.PendingReviewEmail;
import com.tracker.models.ProcessedEmail;
import com.tracker.models.User;
import com.tracker.repositories.DailySyncLogRepository;
import com.tracker.repositories.JobApplicationRepository;
import com.tracker.repositories.PendingReviewEmailRepository;
import com.tracker.repositories.ProcessedEmailRepository;
import com.tracker.security.EncryptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class GmailService {

    private static final String APPLICATION_NAME = "Job Tracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final EncryptionUtils encryptionUtils;
    private final ProcessedEmailRepository processedEmailRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final DailySyncLogRepository dailySyncLogRepository;
    private final PendingReviewEmailRepository pendingReviewEmailRepository;
    private final BayesianClassifierService bayesianClassifierService;
    private final ParsingService parsingService;

    public GmailService(EncryptionUtils encryptionUtils,
                        ProcessedEmailRepository processedEmailRepository,
                        JobApplicationRepository jobApplicationRepository,
                        DailySyncLogRepository dailySyncLogRepository,
                        PendingReviewEmailRepository pendingReviewEmailRepository,
                        BayesianClassifierService bayesianClassifierService,
                        ParsingService parsingService) {
        this.encryptionUtils = encryptionUtils;
        this.processedEmailRepository = processedEmailRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.dailySyncLogRepository = dailySyncLogRepository;
        this.pendingReviewEmailRepository = pendingReviewEmailRepository;
        this.bayesianClassifierService = bayesianClassifierService;
        this.parsingService = parsingService;
    }

    private Gmail getGmailService(User user) throws Exception {
        String decryptedRefreshToken = encryptionUtils.decrypt(user.getGoogleRefreshToken());
        if (decryptedRefreshToken == null) {
            throw new RuntimeException("No valid Google Refresh Token found for user.");
        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setRefreshToken(decryptedRefreshToken);

        return new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    @Async
    public CompletableFuture<String> syncEmailsAsync(User user, LocalDate startDate, LocalDate endDate) {
        try {
            // Find all dates already synced for this user in this range
            List<DailySyncLog> existingLogs = dailySyncLogRepository.findByUserAndSyncDateBetween(user, startDate, endDate);
            Set<LocalDate> syncedDates = existingLogs.stream()
                    .map(DailySyncLog::getSyncDate)
                    .collect(Collectors.toSet());

            List<LocalDate> missingDates = new ArrayList<>();
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                if (!syncedDates.contains(d)) {
                    missingDates.add(d);
                }
            }

            if (missingDates.isEmpty()) {
                System.out.println("All requested dates from " + startDate + " to " + endDate + " are already synced. Skipping Gmail API.");
                return CompletableFuture.completedFuture("All requested dates already synced.");
            }

            System.out.println("Syncing missing dates: " + missingDates);
            List<LocalDate[]> ranges = groupIntoContiguousRanges(missingDates);
            Gmail gmail = getGmailService(user);

            for (LocalDate[] range : ranges) {
                LocalDate rangeStart = range[0];
                LocalDate rangeEnd = range[1];

                String query = "((subject:(\"application\" OR \"applied\" OR \"interview\" OR \"offer\" OR \"status update\" OR \"application update\" OR \"thank you for applying\" OR \"application received\" OR \"thank you for your interest\") " +
                        "OR from:(greenhouse.io OR lever.co OR workday.com OR linkedin.com OR indeed.com OR ashbyhq.com OR smartrecruiters.com OR icims.com OR myworkdayjobs.com)))";
                query += " after:" + rangeStart.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                query += " before:" + rangeEnd.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

                System.out.println("Executing Gmail Query: " + query);
                ListMessagesResponse response = gmail.users().messages().list("me").setQ(query).execute();
                List<Message> messages = response.getMessages();

                if (messages != null && !messages.isEmpty()) {
                    for (Message msgRef : messages) {
                        processMessage(gmail, user, msgRef.getId());
                    }
                }
            }

            // Record missing dates as synced
            for (LocalDate d : missingDates) {
                try {
                    dailySyncLogRepository.save(new DailySyncLog(user, d));
                } catch (Exception ignored) {}
            }

            return CompletableFuture.completedFuture("Sync completed successfully.");
        } catch (Exception e) {
            System.err.println("Failed to sync emails: " + e.getMessage());
            return CompletableFuture.completedFuture("Sync failed: " + e.getMessage());
        }
    }

    private List<LocalDate[]> groupIntoContiguousRanges(List<LocalDate> dates) {
        List<LocalDate[]> ranges = new ArrayList<>();
        if (dates.isEmpty()) return ranges;

        LocalDate start = dates.get(0);
        LocalDate end = start;

        for (int i = 1; i < dates.size(); i++) {
            LocalDate current = dates.get(i);
            if (current.equals(end.plusDays(1))) {
                end = current;
            } else {
                ranges.add(new LocalDate[]{start, end});
                start = current;
                end = current;
            }
        }
        ranges.add(new LocalDate[]{start, end});
        return ranges;
    }

    private void processMessage(Gmail gmail, User user, String messageId) {
        try {
            // Deduplication check
            if (processedEmailRepository.existsByMessageId(messageId)) {
                return;
            }

            Message message = gmail.users().messages().get("me", messageId).setFormat("full").execute();
            
            String subject = "";
            String sender = "";
            if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
                for (MessagePartHeader header : message.getPayload().getHeaders()) {
                    if (header.getName().equalsIgnoreCase("Subject")) {
                        subject = header.getValue();
                    } else if (header.getName().equalsIgnoreCase("From")) {
                        sender = header.getValue();
                    }
                }
            }

            String snippet = message.getSnippet() != null ? message.getSnippet() : "";

            // Extract email date from internalDate
            LocalDateTime emailDate = LocalDateTime.now();
            if (message.getInternalDate() != null) {
                emailDate = Instant.ofEpochMilli(message.getInternalDate())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            // Step 1: Run Bayesian Classification
            BayesianClassifierService.ClassificationResult classification =
                    bayesianClassifierService.classify(subject, snippet, sender);

            // Step 2: Route by Classification
            if (classification == BayesianClassifierService.ClassificationResult.SPAM) {
                processedEmailRepository.save(new ProcessedEmail(user, messageId));
                return;
            }

            if (classification == BayesianClassifierService.ClassificationResult.UNSURE) {
                if (!pendingReviewEmailRepository.existsByUserAndMessageId(user, messageId)) {
                    pendingReviewEmailRepository.save(new PendingReviewEmail(
                            user, messageId, sender, subject,
                            snippet.length() > 500 ? snippet.substring(0, 500) : snippet,
                            emailDate
                    ));
                }
                return;
            }

            // Step 3: Guaranteed JOB -> Extract and save
            String fullBody = extractBody(message);
            Map<String, String> extractedData = parsingService.parseEmail(subject, fullBody, sender);

            String company = extractedData.get("company");

            // Check for same company, same day duplicate
            LocalDateTime dayStart = emailDate.toLocalDate().atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            List<JobApplication> existingToday = jobApplicationRepository.findByUserAndCompanyAndAppliedDateBetween(user, company, dayStart, dayEnd);
            
            if (!existingToday.isEmpty() && !"Not Available".equals(company)) {
                // Skip same day same company
                processedEmailRepository.save(new ProcessedEmail(user, messageId));
                return;
            }

            JobApplication application = new JobApplication();
            application.setUser(user);
            application.setCompany(company);
            application.setPosition(extractedData.get("position"));
            application.setJobId(extractedData.get("jobId"));
            application.setCurrentStatus(extractedData.get("status"));
            application.setAppliedDate(emailDate);
            application.setSourceEmailId(messageId);
            application.setEmailSubject(subject);
            application.setEmailSnippet(snippet.length() > 500 ? snippet.substring(0, 500) : snippet);
            
            jobApplicationRepository.save(application);
            processedEmailRepository.save(new ProcessedEmail(user, messageId));
            
        } catch (Exception e) {
            System.err.println("Error processing message " + messageId + ": " + e.getMessage());
        }
    }

    public boolean approvePendingEmail(User user, Long pendingId) {
        Optional<PendingReviewEmail> pendingOpt = pendingReviewEmailRepository.findByIdAndUser(pendingId, user);
        if (pendingOpt.isEmpty()) return false;

        PendingReviewEmail pending = pendingOpt.get();
        String messageId = pending.getMessageId();

        try {
            // 1. Train classifier positively
            bayesianClassifierService.train(pending.getSubject(), pending.getSnippet(), pending.getSender(), true);

            // 2. Fetch full body from Gmail
            Gmail gmail = getGmailService(user);
            Message message = gmail.users().messages().get("me", messageId).setFormat("full").execute();
            String fullBody = extractBody(message);

            // 3. Extract and save application
            Map<String, String> extractedData = parsingService.parseEmail(pending.getSubject(), fullBody, pending.getSender());
            String company = extractedData.get("company");
            LocalDateTime emailDate = pending.getReceivedDate() != null ? pending.getReceivedDate() : LocalDateTime.now();

            JobApplication application = new JobApplication();
            application.setUser(user);
            application.setCompany(company);
            application.setPosition(extractedData.get("position"));
            application.setJobId(extractedData.get("jobId"));
            application.setCurrentStatus(extractedData.get("status"));
            application.setAppliedDate(emailDate);
            application.setSourceEmailId(messageId);
            application.setEmailSubject(pending.getSubject());
            application.setEmailSnippet(pending.getSnippet());

            jobApplicationRepository.save(application);
            processedEmailRepository.save(new ProcessedEmail(user, messageId));
            pendingReviewEmailRepository.delete(pending);
            return true;
        } catch (Exception e) {
            System.err.println("Error approving pending email " + messageId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean rejectPendingEmail(User user, Long pendingId) {
        Optional<PendingReviewEmail> pendingOpt = pendingReviewEmailRepository.findByIdAndUser(pendingId, user);
        if (pendingOpt.isEmpty()) return false;

        PendingReviewEmail pending = pendingOpt.get();
        String messageId = pending.getMessageId();

        try {
            // 1. Train classifier negatively (as spam)
            bayesianClassifierService.train(pending.getSubject(), pending.getSnippet(), pending.getSender(), false);

            // 2. Mark processed and delete pending
            processedEmailRepository.save(new ProcessedEmail(user, messageId));
            pendingReviewEmailRepository.delete(pending);
            return true;
        } catch (Exception e) {
            System.err.println("Error rejecting pending email " + messageId + ": " + e.getMessage());
            return false;
        }
    }

    private String extractBody(Message message) {
        if (message == null || message.getPayload() == null) {
            return message != null && message.getSnippet() != null ? message.getSnippet() : "";
        }
        String body = extractBodyFromPart(message.getPayload());
        if (body == null || body.trim().isEmpty()) {
            return message.getSnippet() != null ? message.getSnippet() : "";
        }
        return body.trim();
    }

    private String extractBodyFromPart(MessagePart part) {
        if (part == null) return "";

        // If it's plain text, decode it
        if ("text/plain".equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(part.getBody().getData());
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }

        // If multipart, prioritize text/plain parts
        if (part.getParts() != null && !part.getParts().isEmpty()) {
            for (MessagePart subPart : part.getParts()) {
                if ("text/plain".equalsIgnoreCase(subPart.getMimeType())) {
                    String text = extractBodyFromPart(subPart);
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
            // Fallback to searching all parts
            for (MessagePart subPart : part.getParts()) {
                String text = extractBodyFromPart(subPart);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }

        // If text/html only and has data, strip HTML tags
        if ("text/html".equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(part.getBody().getData());
                String html = new String(decoded, StandardCharsets.UTF_8);
                return stripHtml(html);
            } catch (Exception e) {
                return "";
            }
        }

        return "";
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                   .replaceAll("(?i)</p>", "\n")
                   .replaceAll("<[^>]*>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("\\s+", " ");
    }
}
