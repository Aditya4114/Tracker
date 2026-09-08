package com.tracker.repositories;

import com.tracker.models.ApplicationEvent;
import com.tracker.models.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {
    List<ApplicationEvent> findByJobApplicationOrderByEventDateDesc(JobApplication jobApplication);
}
