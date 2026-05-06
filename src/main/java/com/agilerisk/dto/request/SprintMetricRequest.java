package com.agilerisk.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record SprintMetricRequest(
        @PositiveOrZero Integer plannedPoints,
        @PositiveOrZero Integer completedPoints,
        Double effortDeviation,
        @PositiveOrZero Integer bugsCount,
        @PositiveOrZero Integer scopeChangesCount,
        Double velocity
) { }
