package com.agilerisk.repository;

import com.agilerisk.domain.RiskPrediction;
import com.agilerisk.domain.enums.ModelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskPredictionRepository extends JpaRepository<RiskPrediction, Long> {

    List<RiskPrediction> findBySprintIdOrderByCreatedAtDesc(Long sprintId);

    Optional<RiskPrediction> findFirstBySprintIdAndModelTypeOrderByCreatedAtDesc(Long sprintId, ModelType modelType);
}
