package com.tracker.repositories;

import com.tracker.models.PendingReviewEmail;
import com.tracker.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingReviewEmailRepository extends JpaRepository<PendingReviewEmail, Long> {
    List<PendingReviewEmail> findByUserOrderByReceivedDateDesc(User user);
    Optional<PendingReviewEmail> findByIdAndUser(Long id, User user);
    boolean existsByUserAndMessageId(User user, String messageId);
    void deleteByUserAndMessageId(User user, String messageId);
}
