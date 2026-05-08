package com.agilerisk.service.ai;

import com.agilerisk.config.AiProperties;
import com.agilerisk.domain.AiModelResponse;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import com.agilerisk.dto.ai.RequirementChangeModelResponse;
import com.agilerisk.dto.response.FeatureImpact;
import com.agilerisk.dto.response.RiskFinding;
import com.agilerisk.service.explain.RequirementChangeExplainer;
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
public class RequirementChangeRiskClient implements AiModelClient<RequirementChangeModelRequest> {

    private final WebClient webClient;
    private final AiProperties props;
    private final AiClientSupport support;
    private final RequirementChangeExplainer explainer;

    public RequirementChangeRiskClient(@Qualifier("requirementChangeWebClient") WebClient webClient,
                                       AiProperties props,
                                       AiClientSupport support,
                                       RequirementChangeExplainer explainer) {
        this.webClient = webClient;
        this.props = props;
        this.support = support;
        this.explainer = explainer;
    }

    @Override
    @Cacheable(value = "requirementChangePredictions", key = "#sprintId + ':' + #request.hashCode()")
    @Retry(name = "aiClient", fallbackMethod = "fallback")
    @CircuitBreaker(name = "aiClient", fallbackMethod = "fallback")
    public ModelOutcome predict(Long sprintId, RequirementChangeModelRequest request) {
        long start = System.currentTimeMillis();
        try {
            RequirementChangeModelResponse raw = webClient.post()
                    .uri(props.getRequirementChange().getPath())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RequirementChangeModelResponse.class)
                    .block(Duration.ofMillis(props.getRequirementChange().getTimeoutMs() + 1000));

            int latency = (int) (System.currentTimeMillis() - start);
            AiModelResponse audit = support.persistAudit(sprintId, ModelType.REQUIREMENT_CHANGE,
                    request, raw, latency, 200, false, null);
            return toOutcome(request, raw, audit);
        } catch (RuntimeException ex) {
            int latency = (int) (System.currentTimeMillis() - start);
            support.persistAudit(sprintId, ModelType.REQUIREMENT_CHANGE, request, null, latency, null, true, ex.getMessage());
            throw ex;
        }
    }

    private ModelOutcome toOutcome(RequirementChangeModelRequest request, RequirementChangeModelResponse raw, AiModelResponse audit) {
        if (raw == null) {
            return ModelOutcome.fallback(ModelType.REQUIREMENT_CHANGE, "Empty response");
        }
        RiskLevel level = parseLevel(raw.riskLabel());
        double riskScore = computeRiskScore(raw);
        Double topProb = topProbability(raw);
        String explanation = buildExplanation(raw);
        List<RiskFinding> findings = explainer.explain(request);
        List<FeatureImpact> impacts = raw.featureImpacts() != null ? raw.featureImpacts() : List.of();
        return new ModelOutcome(
                ModelType.REQUIREMENT_CHANGE,
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

    /** Same risk-score formula as over-budget: 50*P(Medium) + 100*P(High). */
    private double computeRiskScore(RequirementChangeModelResponse raw) {
        RequirementChangeModelResponse.Probabilities p = raw.probabilities();
        if (p == null) {
            // confidence is 0..1 per contract; convert to 0..100 if used as fallback
            return raw.confidence() != null ? raw.confidence() * 100.0 : 50.0;
        }
        double low = nullSafe(p.low());
        double med = nullSafe(p.medium());
        double high = nullSafe(p.high());
        double total = low + med + high;
        if (total <= 0) {
            return raw.confidence() != null ? raw.confidence() * 100.0 : 50.0;
        }
        double weighted = (med * 50.0 + high * 100.0) / total;
        return Math.round(weighted * 100.0) / 100.0;
    }

    private Double topProbability(RequirementChangeModelResponse raw) {
        RequirementChangeModelResponse.Probabilities p = raw.probabilities();
        if (p == null) return raw.confidence();
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
            log.debug("Unknown risk label from requirement-change model: {}", label);
            return RiskLevel.UNKNOWN;
        }
    }

    private String buildExplanation(RequirementChangeModelResponse raw) {
        RequirementChangeModelResponse.Probabilities p = raw.probabilities();
        double confPct = raw.confidence() != null ? raw.confidence() * 100.0 : 0.0;
        if (p == null) {
            return String.format("Predicted %s (confidence %.2f%%)", raw.riskLabel(), confPct);
        }
        return String.format("Predicted %s (confidence %.2f%%). P(Low)=%.3f, P(Medium)=%.3f, P(High)=%.3f",
                raw.riskLabel(), confPct,
                nullSafe(p.low()), nullSafe(p.medium()), nullSafe(p.high()));
    }

    private double nullSafe(Double d) {
        return d == null ? 0.0 : d;
    }

    @SuppressWarnings("unused")
    private ModelOutcome fallback(Long sprintId, RequirementChangeModelRequest request, Throwable ex) {
        log.warn("Requirement-change AI call failed (sprintId={}): {}", sprintId, ex.toString());
        return ModelOutcome.fallback(ModelType.REQUIREMENT_CHANGE, ex.getMessage());
    }
}
