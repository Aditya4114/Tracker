package com.tracker.ingestion.models;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SyncRequest {
    private LocalDate startDate;
    private LocalDate endDate;
}
