package com.agilerisk.dto.ai;

import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;
import com.agilerisk.dto.response.FeatureImpact;
import com.agilerisk.dto.response.RiskFinding;

import java.util.List;

public record ModelOutcome(
        ModelType modelType,
        Double riskScore,
        RiskLevel riskLevel,
        Double probability,
        String explanation,
        boolean degraded,
        Long aiResponseId,
        Integer latencyMs,
        List<RiskFinding> findings,
        List<FeatureImpact> featureImpacts,
        Double baselineRiskScore,
        // Communication & Collaboration specific fields (null/empty for other models)
        List<String> recommendations,
        String llmExplanation
) {
    public static ModelOutcome fallback(ModelType type, String reason) {
        return new ModelOutcome(
                type, 50.0, RiskLevel.UNKNOWN, null,
                "Fallback: " + reason, true, null, null,
                List.of(), List.of(), null,
                List.of(), null);
    }
}
