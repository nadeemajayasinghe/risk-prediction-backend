package com.agilerisk.repository;

import com.agilerisk.domain.SprintMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SprintMetricRepository extends JpaRepository<SprintMetric, Long> {
    List<SprintMetric> findBySprintIdOrderByRecordedAtDesc(Long sprintId);
}
