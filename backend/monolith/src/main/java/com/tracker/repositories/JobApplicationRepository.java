package com.tracker.repositories;

import com.tracker.models.JobApplication;
import com.tracker.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByUser(User user);
    
    List<JobApplication> findByUserAndAppliedDateBetween(User user, LocalDateTime start, LocalDateTime end);

    Optional<JobApplication> findByIdAndUser(Long id, User user);
    
    // For deduplication: check if an application for this company exists on the same day for this user
    List<JobApplication> findByUserAndCompanyAndAppliedDateBetween(User user, String company, LocalDateTime start, LocalDateTime end);
}
