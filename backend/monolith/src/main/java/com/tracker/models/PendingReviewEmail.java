package com.tracker.models;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String messageId;

    private String sender;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    private LocalDateTime receivedDate;

    private LocalDateTime createdAt;

    public PendingReviewEmail(User user, String messageId, String sender, String subject, String snippet, LocalDateTime receivedDate) {
        this.user = user;
        this.messageId = messageId;
        this.sender = sender;
        this.subject = subject;
        this.snippet = snippet;
        this.receivedDate = receivedDate != null ? receivedDate : LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
}
