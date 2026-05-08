package com.agilerisk.dto.ai;

import com.agilerisk.dto.response.FeatureImpact;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RequirementChangeModelResponse(
        String riskLabel,
        Double confidence,           // 0..1 per the contract
        Probabilities probabilities,
        List<FeatureImpact> featureImpacts,
        Double baselineRiskScore
) {
    /** Inner keys are PascalCase per the contract: Low / Medium / High. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Probabilities(
            @JsonProperty("Low")    Double low,
            @JsonProperty("Medium") Double medium,
            @JsonProperty("High")   Double high
    ) { }
}
