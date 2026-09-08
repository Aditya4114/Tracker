package com.tracker.repositories;

import com.tracker.models.ProcessedEmail;
import com.tracker.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, Long> {
    Optional<ProcessedEmail> findByMessageId(String messageId);
    boolean existsByMessageId(String messageId);
}
