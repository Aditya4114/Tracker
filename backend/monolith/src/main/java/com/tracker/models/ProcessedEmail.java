package com.tracker.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_emails")
@Data
@NoArgsConstructor
public class ProcessedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String messageId;

    private LocalDateTime processedAt;

    public ProcessedEmail(User user, String messageId) {
        this.user = user;
        this.messageId = messageId;
        this.processedAt = LocalDateTime.now();
    }
}
