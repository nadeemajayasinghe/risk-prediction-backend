package com.agilerisk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SHAP-based contribution of a single input feature to a prediction.
 * <p>
 * {@code riskContribution} is signed and on the same 0-100 scale as the
 * aggregator's overall risk score. Positive values mean the feature pushed
 * the model toward higher risk; negative values mean it pushed toward lower risk.
 * <p>
 * {@code shapByClass} carries the raw per-class SHAP values for full transparency
 * (each class contribution sums with the model's bias to that class's predicted probability).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeatureImpact(
        String feature,
        Object value,
        Double riskContribution,
        ShapByClass shapByClass,
        String direction
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShapByClass(Double low, Double medium, Double high) { }
}
