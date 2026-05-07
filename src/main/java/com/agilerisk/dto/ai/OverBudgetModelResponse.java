package com.agilerisk.dto.ai;

import com.agilerisk.dto.response.FeatureImpact;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OverBudgetModelResponse(
        String riskLabel,
        Double confidence,
        Probabilities probabilities,
        List<FeatureImpact> featureImpacts,
        Double baselineRiskScore
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Probabilities(Double low, Double medium, Double high) { }
}
