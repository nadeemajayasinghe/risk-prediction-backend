package com.agilerisk.dto.response;

import com.agilerisk.domain.enums.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AggregatedRiskResponse(
        Long id,
        Long sprintId,
        UUID evaluationId,
        Double overallScore,
        RiskLevel overallLevel,
        Double overBudgetScore,
        Double requirementChangeScore,
        String combinedExplanation,
        boolean degraded,
        List<RiskFinding> findings,
        List<FeatureImpact> overBudgetFeatureImpacts,
        Double overBudgetBaselineRiskScore,
        List<FeatureImpact> requirementChangeFeatureImpacts,
        Double requirementChangeBaselineRiskScore,
        Instant createdAt
) { }
