package com.agilerisk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UserStoryRequest(
        String externalKey,
        @NotBlank String title,
        String description,
        @PositiveOrZero Integer storyPoints,
        String priority,
        String status
) { }
