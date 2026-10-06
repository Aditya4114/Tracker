package com.tracker.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_sync_logs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "sync_date"})
})
@Data
@NoArgsConstructor
public class DailySyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sync_date", nullable = false)
    private LocalDate syncDate;

    private LocalDateTime createdAt;

    public DailySyncLog(User user, LocalDate syncDate) {
        this.user = user;
        this.syncDate = syncDate;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
