package com.tracker.core.repositories;

import com.tracker.core.models.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByUserIdOrderByAppliedDateDesc(Long userId);
    List<JobApplication> findByUserIdAndCompanyAndAppliedDateBetween(Long userId, String company, LocalDateTime start, LocalDateTime end);
    List<JobApplication> findByUserIdAndCurrentStatus(Long userId, String status);
    long countByUserId(Long userId);
    long countByUserIdAndCurrentStatus(Long userId, String status);
    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);
}
