package com.agilerisk.dto.request;

import com.agilerisk.domain.enums.SprintStatus;

import java.time.LocalDate;

public record UpdateSprintRequest(
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
        Double sprintCapacityHours
) { }
