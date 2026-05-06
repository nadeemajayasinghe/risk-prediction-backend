package com.agilerisk.dto.ai;

import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;

public record ModelOutcome(
        ModelType modelType,
        Double riskScore,
        RiskLevel riskLevel,
        Double probability,
        String explanation,
        boolean degraded,
        Long aiResponseId,
        Integer latencyMs
) {
    public static ModelOutcome fallback(ModelType type, String reason) {
        return new ModelOutcome(type, 50.0, RiskLevel.UNKNOWN, null,
                "Fallback: " + reason, true, null, null);
    }
}
