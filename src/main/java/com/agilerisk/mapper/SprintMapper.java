package com.agilerisk.mapper;

import com.agilerisk.domain.AggregatedRiskResult;
import com.agilerisk.domain.RiskPrediction;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.response.AggregatedRiskResponse;
import com.agilerisk.dto.response.FeatureImpact;
import com.agilerisk.dto.response.RiskFinding;
import com.agilerisk.dto.response.RiskPredictionResponse;
import com.agilerisk.dto.response.SprintResponse;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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

    /** Used by reporting endpoints — no live outcomes available, so derived fields are empty. */
    public AggregatedRiskResponse toResponse(AggregatedRiskResult a) {
        return toResponse(a, List.of());
    }

    /** Used right after an evaluation — merges findings + per-model feature impacts from outcomes. */
    public AggregatedRiskResponse toResponse(AggregatedRiskResult a, Collection<ModelOutcome> outcomes) {
        List<RiskFinding> findings = outcomes.stream()
                .filter(Objects::nonNull)
                .flatMap(o -> o.findings() == null ? java.util.stream.Stream.<RiskFinding>empty() : o.findings().stream())
                .toList();

        ModelOutcome ob = pick(outcomes, ModelType.OVER_BUDGET);
        ModelOutcome rc = pick(outcomes, ModelType.REQUIREMENT_CHANGE);

        List<FeatureImpact> obImpacts = (ob != null && ob.featureImpacts() != null) ? ob.featureImpacts() : List.of();
        List<FeatureImpact> rcImpacts = (rc != null && rc.featureImpacts() != null) ? rc.featureImpacts() : List.of();
        Double obBaseline = ob != null ? ob.baselineRiskScore() : null;
        Double rcBaseline = rc != null ? rc.baselineRiskScore() : null;

        return new AggregatedRiskResponse(
                a.getId(), a.getSprint().getId(), a.getEvaluationId(),
                a.getOverallScore(), a.getOverallLevel(),
                a.getOverBudgetScore(), a.getRequirementChangeScore(),
                a.getCombinedExplanation(), a.isDegraded(),
                findings,
                obImpacts, obBaseline,
                rcImpacts, rcBaseline,
                a.getCreatedAt());
    }

    private ModelOutcome pick(Collection<ModelOutcome> outcomes, ModelType type) {
        return outcomes.stream()
                .filter(Objects::nonNull)
                .filter(o -> o.modelType() == type)
                .findFirst()
                .orElse(null);
    }
}
