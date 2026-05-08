package com.agilerisk.service;

import com.agilerisk.config.AggregationProperties;
import com.agilerisk.domain.AggregatedRiskResult;
import com.agilerisk.domain.RiskPrediction;
import com.agilerisk.domain.Sprint;
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
    public AggregatedRiskResult aggregate(Sprint sprint,
                                          ModelOutcome overBudget,
                                          ModelOutcome requirementChange,
                                          ModelOutcome communicationCollaboration) {
        UUID evaluationId = UUID.randomUUID();

        persistPrediction(sprint, overBudget);
        persistPrediction(sprint, requirementChange);
        persistPrediction(sprint, communicationCollaboration);

        double wOb = props.getWeights().getOverBudget();
        double wRc = props.getWeights().getRequirementChange();
        double wCc = props.getWeights().getCommunicationCollaboration();
        double normW = wOb + wRc + wCc;
        if (normW == 0) { wOb = wRc = wCc = 1.0/3.0; normW = 1.0; }

        double overall = (wOb * safeScore(overBudget)
                         + wRc * safeScore(requirementChange)
                         + wCc * safeScore(communicationCollaboration)) / normW;

        boolean degraded = overBudget.degraded() || requirementChange.degraded() || communicationCollaboration.degraded();
        RiskLevel level = classify(overall);
        if (degraded) level = level.bumpUp();

        String explanation = buildExplanation(overBudget, requirementChange, communicationCollaboration,
                wOb, wRc, wCc, overall, degraded);

        AggregatedRiskResult result = AggregatedRiskResult.builder()
                .sprint(sprint)
                .evaluationId(evaluationId)
                .overallScore(round(overall))
                .overallLevel(level)
                .overBudgetScore(overBudget.riskScore())
                .requirementChangeScore(requirementChange.riskScore())
                .communicationCollaborationScore(communicationCollaboration.riskScore())
                .combinedExplanation(explanation)
                .degraded(degraded)
                .build();
        return aggregatedRepo.save(result);
    }

    private void persistPrediction(Sprint sprint, ModelOutcome outcome) {
        var aiResp = outcome.aiResponseId() == null ? null
                : responseRepo.findById(outcome.aiResponseId()).orElse(null);
        RiskPrediction p = RiskPrediction.builder()
                .sprint(sprint)
                .modelType(outcome.modelType())
                .riskScore(outcome.riskScore())
                .riskLevel(outcome.riskLevel())
                .probability(outcome.probability())
                .explanation(outcome.explanation())
                .aiResponse(aiResp)
                .build();
        predictionRepo.save(p);
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

    private String buildExplanation(ModelOutcome ob, ModelOutcome rc, ModelOutcome cc,
                                    double wOb, double wRc, double wCc,
                                    double overall, boolean degraded) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Overall risk %.2f (weights ob=%.2f rc=%.2f cc=%.2f). ",
                overall, wOb, wRc, wCc));
        appendModel(sb, "Over-budget", ob);
        appendModel(sb, "Requirement-change", rc);
        appendModel(sb, "Communication-collab", cc);
        if (degraded) sb.append("[DEGRADED: at least one model fell back to a default.]");
        return sb.toString().trim();
    }

    private void appendModel(StringBuilder sb, String label, ModelOutcome o) {
        sb.append(label).append(": score=").append(fmt(o.riskScore()))
          .append(", level=").append(o.riskLevel());
        if (o.explanation() != null) sb.append(" - ").append(o.explanation());
        sb.append(". ");
    }

    private String fmt(Double d) {
        return d == null ? "n/a" : String.format("%.2f", d);
    }
}
