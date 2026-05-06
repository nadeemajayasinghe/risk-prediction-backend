package com.agilerisk.dto.request;

import com.agilerisk.domain.enums.ChangeType;
import jakarta.validation.constraints.NotNull;

public record RequirementChangeRequest(
        Long storyId,
        @NotNull ChangeType changeType,
        String description,
        String requestedBy
) { }
