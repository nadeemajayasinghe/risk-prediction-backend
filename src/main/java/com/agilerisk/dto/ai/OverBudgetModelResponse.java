package com.agilerisk.dto.ai;

import com.agilerisk.dto.response.FeatureImpact;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OverBudgetModelResponse(
        @JsonProperty("risk_level")             String riskLevel,
        @JsonProperty("probability_overbudget") Double probabilityOverbudget,
        @JsonProperty("class_probabilities")    ClassProbabilities classProbabilities,
        // featureImpacts comes back camelCase from FastAPI (alias-fallback to snake_case)
        @JsonAlias({"feature_impacts", "featureImpacts"})
        List<FeatureImpact> featureImpacts,
        @JsonAlias({"baseline_risk_score", "baselineRiskScore"})
        Double baselineRiskScore
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClassProbabilities(
            @JsonProperty("High")   Double high,
            @JsonProperty("Low")    Double low,
            @JsonProperty("Medium") Double medium
    ) { }
}
