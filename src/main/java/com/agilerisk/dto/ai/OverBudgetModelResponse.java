package com.agilerisk.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OverBudgetModelResponse(
        String riskLabel,
        Double confidence,
        Probabilities probabilities
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Probabilities(Double low, Double medium, Double high) { }
}
