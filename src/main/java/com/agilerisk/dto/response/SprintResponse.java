package com.agilerisk.dto.response;

import com.agilerisk.domain.enums.SprintStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SprintResponse(
        Long id,
        String name,
        String goal,
        LocalDate startDate,
        LocalDate endDate,
        SprintStatus status,
        String teamId,
        Integer capacityPoints,
        String teamType,
        Integer teamSize,
        Double complexity,
        Double baseVelocity,
        Instant createdAt,
        Instant updatedAt
) { }
