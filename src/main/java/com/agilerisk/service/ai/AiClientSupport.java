package com.agilerisk.service.ai;

import com.agilerisk.domain.AiModelResponse;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.domain.enums.RiskLevel;
import com.agilerisk.dto.ai.AiModelRawResponse;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.exception.ResourceNotFoundException;
import com.agilerisk.repository.AiModelResponseRepository;
import com.agilerisk.repository.SprintRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClientSupport {

    private final AiModelResponseRepository responseRepo;
    private final SprintRepository sprintRepo;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiModelResponse persistAudit(Long sprintId, ModelType type, Object request, Object response,
                                        Integer latencyMs, Integer httpStatus, boolean degraded, String error) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));

        AiModelResponse audit = AiModelResponse.builder()
                .sprint(sprint)
                .modelType(type)
                .requestPayload(asMap(request))
                .responsePayload(asMap(response))
                .latencyMs(latencyMs)
                .httpStatus(httpStatus)
                .degraded(degraded)
                .errorMessage(error)
                .build();
        return responseRepo.save(audit);
    }

    public ModelOutcome toOutcome(ModelType type, AiModelRawResponse raw, AiModelResponse audit) {
        RiskLevel level = parseLevel(raw.riskLevel(), raw.riskScore());
        return new ModelOutcome(
                type,
                raw.riskScore() != null ? raw.riskScore() : 0.0,
                level,
                raw.probability(),
                raw.explanation(),
                false,
                audit != null ? audit.getId() : null,
                audit != null ? audit.getLatencyMs() : null,
                java.util.List.of(),
                java.util.List.of(),
                null
        );
    }

    public RiskLevel parseLevel(String raw, Double score) {
        if (raw != null) {
            try {
                return RiskLevel.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                log.debug("Unknown risk level from AI: {}", raw);
            }
        }
        if (score == null) return RiskLevel.UNKNOWN;
        if (score >= 70) return RiskLevel.HIGH;
        if (score >= 35) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.convertValue(value, Map.class);
        } catch (IllegalArgumentException ex) {
            log.warn("Could not serialise audit payload", ex);
            return null;
        }
    }
}
