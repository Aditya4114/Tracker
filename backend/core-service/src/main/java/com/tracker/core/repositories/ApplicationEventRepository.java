package com.tracker.core.repositories;

import com.tracker.core.models.ApplicationEvent;
import com.tracker.core.models.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {
    List<ApplicationEvent> findByJobApplicationOrderByEventDateAsc(JobApplication application);
}
