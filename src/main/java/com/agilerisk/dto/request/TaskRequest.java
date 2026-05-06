package com.agilerisk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record TaskRequest(
        @NotBlank String title,
        String description,
        @PositiveOrZero Double estimatedHours,
        @PositiveOrZero Double actualHours,
        String status,
        String assignee
) { }
