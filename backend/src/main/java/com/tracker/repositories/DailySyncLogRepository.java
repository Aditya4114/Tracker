package com.tracker.repositories;

import com.tracker.models.DailySyncLog;
import com.tracker.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailySyncLogRepository extends JpaRepository<DailySyncLog, Long> {
    List<DailySyncLog> findByUserAndSyncDateBetween(User user, LocalDate startDate, LocalDate endDate);
    boolean existsByUserAndSyncDate(User user, LocalDate syncDate);
}
