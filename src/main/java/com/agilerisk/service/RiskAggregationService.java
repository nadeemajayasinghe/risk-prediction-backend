package com.agilerisk.service;

import com.agilerisk.config.AggregationProperties;
import com.agilerisk.domain.AggregatedRiskResult;
import com.agilerisk.domain.RiskPrediction;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.repository.AggregatedRiskResultRepository;
import com.agilerisk.repository.AiModelResponseRepository;
import com.agilerisk.repository.RiskPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskAggregationService {

    private final AggregationProperties props;
    private final RiskPredictionRepository predictionRepo;
    private final AggregatedRiskResultRepository aggregatedRepo;
    private final AiModelResponseRepository responseRepo;

    @Transactional
    public AggregatedRiskResult aggregate(Sprint sprint, ModelOutcome overBudget, ModelOutcome requirementChange) {
        UUID evaluationId = UUID.randomUUID();

        RiskPrediction overBudgetPred = persistPrediction(sprint, overBudget);
        RiskPrediction requirementPred = persistPrediction(sprint, requirementChange);

        double w1 = props.getWeights().getOverBudget();
        double w2 = props.getWeights().getRequirementChange();
        double normW = w1 + w2;
        if (normW == 0) { w1 = 0.5; w2 = 0.5; normW = 1.0; }

        double overall = (w1 * safeScore(overBudget) + w2 * safeScore(requirementChange)) / normW;
        boolean degraded = overBudget.degraded() || requirementChange.degraded();

        RiskLevel level = classify(overall);
        if (degraded) level = level.bumpUp();

        String explanation = buildExplanation(overBudget, requirementChange, w1, w2, overall, degraded);

        AggregatedRiskResult result = AggregatedRiskResult.builder()
                .sprint(sprint)
                .evaluationId(evaluationId)
                .overallScore(round(overall))
                .overallLevel(level)
                .overBudgetScore(overBudget.riskScore())
                .requirementChangeScore(requirementChange.riskScore())
                .combinedExplanation(explanation)
                .degraded(degraded)
                .build();
        return aggregatedRepo.save(result);
    }

    private RiskPrediction persistPrediction(Sprint sprint, ModelOutcome outcome) {
        var aiResp = outcome.aiResponseId() == null ? null : responseRepo.findById(outcome.aiResponseId()).orElse(null);
        RiskPrediction p = RiskPrediction.builder()
                .sprint(sprint)
                .modelType(outcome.modelType())
                .riskScore(outcome.riskScore())
                .riskLevel(outcome.riskLevel())
                .probability(outcome.probability())
                .explanation(outcome.explanation())
                .aiResponse(aiResp)
                .build();
        return predictionRepo.save(p);
    }

    private double safeScore(ModelOutcome outcome) {
        return outcome.riskScore() == null ? 50.0 : outcome.riskScore();
    }

    private RiskLevel classify(double score) {
        if (score >= props.getThresholds().getHigh()) return RiskLevel.HIGH;
        if (score >= props.getThresholds().getMedium()) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String buildExplanation(ModelOutcome ob, ModelOutcome rc,
                                    double w1, double w2, double overall, boolean degraded) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Overall risk %.2f (weights ob=%.2f, rc=%.2f). ", overall, w1, w2));
        sb.append("Over-budget: score=").append(fmt(ob.riskScore()))
                .append(", level=").append(ob.riskLevel());
        if (ob.explanation() != null) sb.append(" — ").append(ob.explanation());
        sb.append(". Requirement-change: score=").append(fmt(rc.riskScore()))
                .append(", level=").append(rc.riskLevel());
        if (rc.explanation() != null) sb.append(" — ").append(rc.explanation());
        if (degraded) sb.append(". [DEGRADED: at least one model fell back to a default.]");
        return sb.toString();
    }

    private String fmt(Double d) {
        return d == null ? "n/a" : String.format("%.2f", d);
    }
}
