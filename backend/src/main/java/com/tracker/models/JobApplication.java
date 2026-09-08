package com.tracker.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "job_applications")
@Data
@NoArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String company;
    
    private String position;
    
    private String jobId;

    // e.g. "Applied", "Rejected", "In Progress"
    private String currentStatus;
    
    // e.g. URL to job posting or company page
    private String jobLink;
    
    private LocalDateTime appliedDate;

    private String sourceEmailId;

    private String emailSubject;

    @Column(columnDefinition = "TEXT")
    private String emailSnippet;

    // If a field couldn't be parsed, it might contain "Not Available"
    
    @OneToMany(mappedBy = "jobApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationEvent> events;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
