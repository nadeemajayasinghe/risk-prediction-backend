package com.agilerisk.service.ai;

import com.agilerisk.config.AiProperties;
import com.agilerisk.domain.AiModelResponse;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;
import com.agilerisk.dto.ai.CommunicationCollaborationModelRequest;
import com.agilerisk.dto.ai.CommunicationCollaborationModelResponse;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.response.RiskFinding;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CommunicationCollaborationRiskClient implements AiModelClient<CommunicationCollaborationModelRequest> {

    private final WebClient webClient;
    private final AiProperties props;
    private final AiClientSupport support;

    public CommunicationCollaborationRiskClient(@Qualifier("communicationCollaborationWebClient") WebClient webClient,
                                                AiProperties props,
                                                AiClientSupport support) {
        this.webClient = webClient;
        this.props = props;
        this.support = support;
    }

    @Override
    @Cacheable(value = "communicationCollaborationPredictions", key = "#sprintId + ':' + #request.hashCode()")
    @Retry(name = "aiClient", fallbackMethod = "fallback")
    @CircuitBreaker(name = "aiClient", fallbackMethod = "fallback")
    public ModelOutcome predict(Long sprintId, CommunicationCollaborationModelRequest request) {
        long start = System.currentTimeMillis();
        try {
            CommunicationCollaborationModelResponse raw = webClient.post()
                    .uri(props.getCommunicationCollaboration().getPath())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CommunicationCollaborationModelResponse.class)
                    .block(Duration.ofMillis(props.getCommunicationCollaboration().getTimeoutMs() + 1000));

            int latency = (int) (System.currentTimeMillis() - start);
            AiModelResponse audit = support.persistAudit(sprintId, ModelType.COMMUNICATION_COLLABORATION,
                    request, raw, latency, 200, false, null);
            return toOutcome(raw, audit);
        } catch (RuntimeException ex) {
            int latency = (int) (System.currentTimeMillis() - start);
            support.persistAudit(sprintId, ModelType.COMMUNICATION_COLLABORATION,
                    request, null, latency, null, true, ex.getMessage());
            throw ex;
        }
    }

    private ModelOutcome toOutcome(CommunicationCollaborationModelResponse raw, AiModelResponse audit) {
        if (raw == null) {
            return ModelOutcome.fallback(ModelType.COMMUNICATION_COLLABORATION, "Empty response");
        }
        RiskLevel level = parseLevel(raw.riskLevel());
        double riskScore = raw.riskScore() == null ? 0.0 : raw.riskScore().doubleValue();
        List<RiskFinding> findings = causesAsFindings(raw);
        String explanation = buildExplanation(raw);
        List<String> recommendations = raw.recommendations() == null ? List.of() : raw.recommendations();
        return new ModelOutcome(
                ModelType.COMMUNICATION_COLLABORATION,
                riskScore,
                level,
                null,
                explanation,
                false,
                audit != null ? audit.getId() : null,
                audit != null ? audit.getLatencyMs() : null,
                findings,
                List.of(),     // no SHAP — rule-based scorer
                null,          // no statistical baseline
                recommendations,
                raw.llmExplanation()
        );
    }

    private List<RiskFinding> causesAsFindings(CommunicationCollaborationModelResponse raw) {
        List<RiskFinding> findings = new ArrayList<>();
        List<String> causes = raw.detectedCauses() == null ? List.of() : raw.detectedCauses();
        String severity = inferSeverity(raw.riskLevel());
        for (int i = 0; i < causes.size(); i++) {
            findings.add(new RiskFinding(
                    "COMM_COLLAB_" + (i + 1),
                    severity,
                    causes.get(i),
                    "See AI-generated recommendations for full mitigation guidance."));
        }
        return findings;
    }

    private String inferSeverity(String riskLevel) {
        if ("HIGH".equalsIgnoreCase(riskLevel)) return "CRITICAL";
        if ("MEDIUM".equalsIgnoreCase(riskLevel)) return "WARNING";
        return "INFO";
    }

    private RiskLevel parseLevel(String label) {
        if (label == null) return RiskLevel.UNKNOWN;
        try {
            return RiskLevel.valueOf(label.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown risk label from CC service: {}", label);
            return RiskLevel.UNKNOWN;
        }
    }

    private String buildExplanation(CommunicationCollaborationModelResponse raw) {
        return String.format("Predicted %s (rule-score %d/100). %d causes flagged.",
                raw.riskLevel(),
                raw.riskScore() == null ? 0 : raw.riskScore(),
                raw.detectedCauses() == null ? 0 : raw.detectedCauses().size());
    }

    @SuppressWarnings("unused")
    private ModelOutcome fallback(Long sprintId, CommunicationCollaborationModelRequest request, Throwable ex) {
        log.warn("CommunicationCollaboration AI call failed (sprintId={}): {}", sprintId, ex.toString());
        return ModelOutcome.fallback(ModelType.COMMUNICATION_COLLABORATION, ex.getMessage());
    }
}
