package com.agilerisk.dto.response;

import java.util.List;

public record RiskSummaryResponse(
        SprintResponse sprint,
        AggregatedRiskResponse latestAggregated,
        List<RiskPredictionResponse> latestPerModel
) { }
