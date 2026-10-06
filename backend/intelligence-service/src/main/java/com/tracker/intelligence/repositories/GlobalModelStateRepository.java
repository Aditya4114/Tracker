package com.tracker.intelligence.repositories;

import com.tracker.intelligence.models.GlobalModelState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalModelStateRepository extends JpaRepository<GlobalModelState, Long> {
    Optional<GlobalModelState> findTopByOrderByIdDesc();
}
