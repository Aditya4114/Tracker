package com.tracker.ingestion.repositories;

import com.tracker.ingestion.models.DailySyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailySyncLogRepository extends JpaRepository<DailySyncLog, Long> {
    List<DailySyncLog> findByUserIdAndSyncDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    boolean existsByUserIdAndSyncDate(Long userId, LocalDate syncDate);
}
