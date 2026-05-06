package com.agilerisk.repository;

import com.agilerisk.domain.AiModelResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiModelResponseRepository extends JpaRepository<AiModelResponse, Long> {
    List<AiModelResponse> findBySprintIdOrderByCreatedAtDesc(Long sprintId);
}
