package com.agilerisk.service.ai;

import com.agilerisk.config.AiProperties;
import com.agilerisk.domain.AiModelResponse;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.ai.OverBudgetModelResponse;
import com.agilerisk.dto.response.RiskFinding;
import com.agilerisk.service.explain.OverBudgetExplainer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class OverBudgetRiskClient implements AiModelClient<OverBudgetModelRequest> {

    private final WebClient webClient;
    private final AiProperties props;
    private final AiClientSupport support;
    private final OverBudgetExplainer explainer;

    public OverBudgetRiskClient(@Qualifier("overBudgetWebClient") WebClient webClient,
                                AiProperties props,
                                AiClientSupport support,
                                OverBudgetExplainer explainer) {
        this.webClient = webClient;
        this.props = props;
        this.support = support;
        this.explainer = explainer;
    }

    @Override
    @Cacheable(value = "overBudgetPredictions", key = "#sprintId + ':' + #request.hashCode()")
    @Retry(name = "aiClient", fallbackMethod = "fallback")
    @CircuitBreaker(name = "aiClient", fallbackMethod = "fallback")
    public ModelOutcome predict(Long sprintId, OverBudgetModelRequest request) {
        long start = System.currentTimeMillis();
        try {
            OverBudgetModelResponse raw = webClient.post()
                    .uri(props.getOverBudget().getPath())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OverBudgetModelResponse.class)
                    .block(Duration.ofMillis(props.getOverBudget().getTimeoutMs() + 1000));

            int latency = (int) (System.currentTimeMillis() - start);
            AiModelResponse audit = support.persistAudit(sprintId, ModelType.OVER_BUDGET,
                    request, raw, latency, 200, false, null);
            return toOutcome(request, raw, audit);
        } catch (RuntimeException ex) {
            int latency = (int) (System.currentTimeMillis() - start);
            support.persistAudit(sprintId, ModelType.OVER_BUDGET, request, null, latency, null, true, ex.getMessage());
            throw ex;
        }
    }

    private ModelOutcome toOutcome(OverBudgetModelRequest request, OverBudgetModelResponse raw, AiModelResponse audit) {
        if (raw == null) {
            return ModelOutcome.fallback(ModelType.OVER_BUDGET, "Empty response");
        }
        RiskLevel level = parseLevel(raw.riskLabel());
        double riskScore = computeRiskScore(raw);
        Double topProb = topProbability(raw);
        String explanation = buildExplanation(raw);
        List<RiskFinding> findings = explainer.explain(request);
        List<com.agilerisk.dto.response.FeatureImpact> impacts =
                raw.featureImpacts() != null ? raw.featureImpacts() : List.of();
        return new ModelOutcome(
                ModelType.OVER_BUDGET,
                riskScore,
                level,
                topProb,
                explanation,
                false,
                audit != null ? audit.getId() : null,
                audit != null ? audit.getLatencyMs() : null,
                findings,
                impacts,
                raw.baselineRiskScore(),
                List.of(),
                null
        );
    }

    /**
     * Single 0–100 score derived from the class probabilities.
     * Weighted average over class severities (Low=0, Medium=50, High=100), so it stays meaningful
     * even when the predicted class confidence is borderline.
     */
    private double computeRiskScore(OverBudgetModelResponse raw) {
        OverBudgetModelResponse.Probabilities p = raw.probabilities();
        if (p == null) {
            return raw.confidence() != null ? raw.confidence() : 50.0;
        }
        double low = nullSafe(p.low());
        double med = nullSafe(p.medium());
        double high = nullSafe(p.high());
        double total = low + med + high;
        if (total <= 0) {
            return raw.confidence() != null ? raw.confidence() : 50.0;
        }
        double weighted = (med * 50.0 + high * 100.0) / total;
        return Math.round(weighted * 100.0) / 100.0;
    }

    private Double topProbability(OverBudgetModelResponse raw) {
        OverBudgetModelResponse.Probabilities p = raw.probabilities();
        if (p == null) return null;
        double low = nullSafe(p.low());
        double med = nullSafe(p.medium());
        double high = nullSafe(p.high());
        return Math.max(low, Math.max(med, high));
    }

    private RiskLevel parseLevel(String label) {
        if (label == null) return RiskLevel.UNKNOWN;
        try {
            return RiskLevel.valueOf(label.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown risk label from over-budget model: {}", label);
            return RiskLevel.UNKNOWN;
        }
    }

    private String buildExplanation(OverBudgetModelResponse raw) {
        OverBudgetModelResponse.Probabilities p = raw.probabilities();
        if (p == null) {
            return String.format("Predicted %s (confidence %.2f%%)", raw.riskLabel(), nullSafe(raw.confidence()));
        }
        return String.format("Predicted %s (confidence %.2f%%). P(Low)=%.3f, P(Medium)=%.3f, P(High)=%.3f",
                raw.riskLabel(), nullSafe(raw.confidence()),
                nullSafe(p.low()), nullSafe(p.medium()), nullSafe(p.high()));
    }

    private double nullSafe(Double d) {
        return d == null ? 0.0 : d;
    }

    @SuppressWarnings("unused")
    private ModelOutcome fallback(Long sprintId, OverBudgetModelRequest request, Throwable ex) {
        log.warn("Over-budget AI call failed (sprintId={}): {}", sprintId, ex.toString());
        return ModelOutcome.fallback(ModelType.OVER_BUDGET, ex.getMessage());
    }
}
