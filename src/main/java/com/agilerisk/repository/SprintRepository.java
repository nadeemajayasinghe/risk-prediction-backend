package com.agilerisk.repository;

import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.enums.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    List<Sprint> findByStatus(SprintStatus status);

    List<Sprint> findByTeamId(String teamId);

    @Query("""
            select s from Sprint s
            left join fetch s.metrics
            left join fetch s.requirementChanges
            where s.id = :id
            """)
    Optional<Sprint> findWithDetailsById(Long id);
}
