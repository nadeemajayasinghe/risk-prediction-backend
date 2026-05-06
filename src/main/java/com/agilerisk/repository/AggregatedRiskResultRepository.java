package com.agilerisk.repository;

import com.agilerisk.domain.AggregatedRiskResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AggregatedRiskResultRepository extends JpaRepository<AggregatedRiskResult, Long> {

    Optional<AggregatedRiskResult> findFirstBySprintIdOrderByCreatedAtDesc(Long sprintId);

    List<AggregatedRiskResult> findBySprintIdOrderByCreatedAtDesc(Long sprintId);
}
