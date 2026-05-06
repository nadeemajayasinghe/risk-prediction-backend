package com.agilerisk.mapper;

import com.agilerisk.domain.AggregatedRiskResult;
import com.agilerisk.domain.RiskPrediction;
import com.agilerisk.domain.Sprint;
import com.agilerisk.dto.response.AggregatedRiskResponse;
import com.agilerisk.dto.response.RiskPredictionResponse;
import com.agilerisk.dto.response.SprintResponse;
import org.springframework.stereotype.Component;

@Component
public class SprintMapper {

    public SprintResponse toResponse(Sprint s) {
        return new SprintResponse(
                s.getId(), s.getName(), s.getGoal(),
                s.getStartDate(), s.getEndDate(), s.getStatus(),
                s.getTeamId(), s.getCapacityPoints(),
                s.getTeamType(), s.getTeamSize(), s.getComplexity(), s.getBaseVelocity(),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    public RiskPredictionResponse toResponse(RiskPrediction p) {
        return new RiskPredictionResponse(
                p.getId(), p.getSprint().getId(), p.getModelType(),
                p.getRiskScore(), p.getRiskLevel(), p.getProbability(),
                p.getExplanation(), p.getCreatedAt());
    }

    public AggregatedRiskResponse toResponse(AggregatedRiskResult a) {
        return new AggregatedRiskResponse(
                a.getId(), a.getSprint().getId(), a.getEvaluationId(),
                a.getOverallScore(), a.getOverallLevel(),
                a.getOverBudgetScore(), a.getRequirementChangeScore(),
                a.getCombinedExplanation(), a.isDegraded(), a.getCreatedAt());
    }
}
