package com.tracker.ingestion.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.tracker.events.RawEmailEvent;
import com.tracker.ingestion.config.KafkaTopicConfig;
import com.tracker.ingestion.models.DailySyncLog;
import com.tracker.ingestion.repositories.DailySyncLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class IngestionGmailService {

    private static final String APPLICATION_NAME = "Job Tracker Ingestion";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${intelligence.service.url:http://localhost:8083}")
    private String intelligenceServiceUrl;

    @Value("${internal.secret:super-secret-internal-key}")
    private String internalSecret;

    private final DailySyncLogRepository dailySyncLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final java.util.concurrent.atomic.AtomicBoolean kafkaAvailable = new java.util.concurrent.atomic.AtomicBoolean(true);
    private long lastKafkaCheckTime = 0;

    public IngestionGmailService(DailySyncLogRepository dailySyncLogRepository,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 RestTemplate restTemplate) {
        this.dailySyncLogRepository = dailySyncLogRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
    }

    private Gmail getGmailService(Long userId) throws Exception {
        String url = authServiceUrl + "/api/auth/internal/google-token/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to acquire short-lived Google access token: " + response.getStatusCode());
        }

        String accessToken = response.getBody();
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);

        return new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    @Async
    public CompletableFuture<String> syncEmailsAsync(Long userId, String userEmail, LocalDate startDate, LocalDate endDate) {
        try {
            List<DailySyncLog> existingLogs = dailySyncLogRepository.findByUserIdAndSyncDateBetween(userId, startDate, endDate);
            Set<LocalDate> syncedDates = existingLogs.stream()
                    .map(DailySyncLog::getSyncDate)
                    .collect(Collectors.toSet());

            // Today should never be considered permanently synced, because new emails can arrive anytime today.
            syncedDates.remove(LocalDate.now());

            List<LocalDate> missingDates = new ArrayList<>();
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                if (!syncedDates.contains(d)) {
                    missingDates.add(d);
                }
            }

            // If all past dates were already synced, always check the last 2 days for any new emails
            if (missingDates.isEmpty()) {
                LocalDate recentPast = endDate.isAfter(startDate) ? endDate.minusDays(1) : endDate;
                if (!missingDates.contains(recentPast)) missingDates.add(recentPast);
                if (!missingDates.contains(endDate)) missingDates.add(endDate);
            }

            List<LocalDate[]> ranges = groupIntoContiguousRanges(missingDates);
            Gmail gmail = getGmailService(userId);

            int dispatchedCount = 0;
            for (LocalDate[] range : ranges) {
                LocalDate rangeStart = range[0];
                LocalDate rangeEnd = range[1];

                String query = "((subject:(\"application\" OR \"applied\" OR \"interview\" OR \"offer\" OR \"status update\" OR \"application update\" OR \"thank you for applying\" OR \"application received\" OR \"thank you for your interest\") " +
                        "OR from:(greenhouse.io OR lever.co OR workday.com OR linkedin.com OR indeed.com OR ashbyhq.com OR smartrecruiters.com OR icims.com OR myworkdayjobs.com)))";
                query += " after:" + rangeStart.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                query += " before:" + rangeEnd.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

                System.out.println("Executing Gmail query for range [" + rangeStart + " to " + rangeEnd + "]: " + query);
                ListMessagesResponse response = gmail.users().messages().list("me").setQ(query).execute();
                List<Message> messages = response.getMessages();

                if (messages != null && !messages.isEmpty()) {
                    System.out.println("Found " + messages.size() + " messages for range [" + rangeStart + " to " + rangeEnd + "]");
                    for (Message msgRef : messages) {
                        dispatchEmail(gmail, userId, userEmail, msgRef.getId());
                        dispatchedCount++;
                    }
                } else {
                    System.out.println("No messages found for range [" + rangeStart + " to " + rangeEnd + "]");
                }
            }

            for (LocalDate d : missingDates) {
                // Only save past dates as permanently synced
                if (d.isBefore(LocalDate.now())) {
                    try {
                        dailySyncLogRepository.save(new DailySyncLog(userId, d));
                    } catch (Exception ignored) {}
                }
            }

            return CompletableFuture.completedFuture("Sync completed successfully. Dispatched " + dispatchedCount + " emails.");
        } catch (Exception e) {
            System.err.println("Sync failed: " + e.getMessage());
            return CompletableFuture.completedFuture("Sync failed: " + e.getMessage());
        }
    }

    private void dispatchEmail(Gmail gmail, Long userId, String userEmail, String messageId) {
        try {
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
            LocalDateTime emailDate = LocalDateTime.now();
            if (message.getInternalDate() != null) {
                emailDate = Instant.ofEpochMilli(message.getInternalDate())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            String fullBody = extractBody(message);

            RawEmailEvent event = RawEmailEvent.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .messageId(messageId)
                    .subject(subject)
                    .sender(sender)
                    .snippet(snippet)
                    .fullBody(fullBody)
                    .emailDate(emailDate)
                    .build();

            // Try Kafka with circuit breaker
            boolean shouldTryKafka = kafkaAvailable.get() || (System.currentTimeMillis() - lastKafkaCheckTime > 30000);
            boolean publishedToKafka = false;
            if (shouldTryKafka) {
                lastKafkaCheckTime = System.currentTimeMillis();
                try {
                    kafkaTemplate.send(KafkaTopicConfig.RAW_EMAILS_TOPIC, String.valueOf(userId), event)
                            .get(5, TimeUnit.SECONDS);
                    publishedToKafka = true;
                    kafkaAvailable.set(true);
                } catch (Exception kafkaEx) {
                    kafkaAvailable.set(false);
                    System.out.println("Kafka offline (" + kafkaEx.getMessage() + "), routing to intelligence-service directly via HTTP...");
                }
            }

            // Fallback to direct HTTP if Kafka is not running
            if (!publishedToKafka) {
                try {
                    restTemplate.postForEntity(intelligenceServiceUrl + "/api/classification/internal/process-email", event, Void.class);
                } catch (Exception httpEx) {
                    System.err.println("Failed direct dispatch to intelligence-service: " + httpEx.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch/dispatch message " + messageId + ": " + e.getMessage());
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

        if ("text/plain".equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(part.getBody().getData());
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }

        if (part.getParts() != null && !part.getParts().isEmpty()) {
            for (MessagePart subPart : part.getParts()) {
                if ("text/plain".equalsIgnoreCase(subPart.getMimeType())) {
                    String text = extractBodyFromPart(subPart);
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
            for (MessagePart subPart : part.getParts()) {
                String text = extractBodyFromPart(subPart);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }

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
