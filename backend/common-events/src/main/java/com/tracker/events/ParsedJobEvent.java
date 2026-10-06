package com.tracker.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedJobEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String company;
    private String position;
    private String jobId;
    private String status;
    private LocalDateTime appliedDate;
    private String sourceEmailId;
    private String emailSubject;
    private String emailSnippet;
}
