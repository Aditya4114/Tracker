package com.tracker.intelligence.repositories;

import com.tracker.intelligence.models.PendingReviewEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingReviewEmailRepository extends JpaRepository<PendingReviewEmail, Long> {
    List<PendingReviewEmail> findByUserIdOrderByReceivedDateDesc(Long userId);
    Optional<PendingReviewEmail> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndMessageId(Long userId, String messageId);
}
