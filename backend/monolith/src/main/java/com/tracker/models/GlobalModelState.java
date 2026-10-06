package com.tracker.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "global_model_state")
@Data
@NoArgsConstructor
public class GlobalModelState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String modelData;

    private LocalDateTime lastUpdated;

    public GlobalModelState(String modelData) {
        this.modelData = modelData;
        this.lastUpdated = LocalDateTime.now();
    }
}
