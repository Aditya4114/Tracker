package com.tracker.ingestion.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_sync_logs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "syncDate"})
})
@Data
@NoArgsConstructor
public class DailySyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate syncDate;

    private LocalDateTime createdAt;

    public DailySyncLog(Long userId, LocalDate syncDate) {
        this.userId = userId;
        this.syncDate = syncDate;
        this.createdAt = LocalDateTime.now();
    }
}
