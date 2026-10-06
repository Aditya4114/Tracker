package com.tracker.intelligence.models;

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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String messageId;

    private LocalDateTime processedAt;

    public ProcessedEmail(Long userId, String messageId) {
        this.userId = userId;
        this.messageId = messageId;
        this.processedAt = LocalDateTime.now();
    }
}
