package com.tracker.intelligence.repositories;

import com.tracker.intelligence.models.ProcessedEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, Long> {
    boolean existsByMessageId(String messageId);
    Optional<ProcessedEmail> findByMessageId(String messageId);
}
