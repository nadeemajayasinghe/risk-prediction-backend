package com.agilerisk.repository;

import com.agilerisk.domain.RequirementChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequirementChangeRepository extends JpaRepository<RequirementChange, Long> {
    List<RequirementChange> findBySprintIdOrderByChangedAtDesc(Long sprintId);
}
