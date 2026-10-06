package com.tracker.intelligence.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_review_emails", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "messageId"})
})
@Data
@NoArgsConstructor
public class PendingReviewEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String messageId;

    private String sender;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Column(columnDefinition = "TEXT")
    private String fullBody;

    private LocalDateTime receivedDate;

    private LocalDateTime createdAt;

    public PendingReviewEmail(Long userId, String messageId, String sender, String subject, String snippet, String fullBody, LocalDateTime receivedDate) {
        this.userId = userId;
        this.messageId = messageId;
        this.sender = sender;
        this.subject = subject;
        this.snippet = snippet;
        this.fullBody = fullBody;
        this.receivedDate = receivedDate != null ? receivedDate : LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
}
