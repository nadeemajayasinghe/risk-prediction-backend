package com.agilerisk.dto.response;

import com.agilerisk.domain.enums.RiskLevel;

import java.time.Instant;

public record RiskTrendPoint(
        Instant timestamp,
        Double overallScore,
        RiskLevel overallLevel,
        Double overBudgetScore,
        Double requirementChangeScore
) { }
