package com.agilerisk.dto.response;

import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;

import java.time.Instant;

public record RiskPredictionResponse(
        Long id,
        Long sprintId,
        ModelType modelType,
        Double riskScore,
        RiskLevel riskLevel,
        Double probability,
        String explanation,
        Instant createdAt
) { }
