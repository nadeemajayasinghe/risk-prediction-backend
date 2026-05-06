package com.agilerisk.dto.request;

import com.agilerisk.domain.enums.SprintStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record CreateSprintRequest(
        @NotBlank String name,
        String goal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull SprintStatus status,
        String teamId,
        @PositiveOrZero Integer capacityPoints,
        String teamType,
        @PositiveOrZero Integer teamSize,
        @PositiveOrZero Double complexity,
        @PositiveOrZero Double baseVelocity
) { }
