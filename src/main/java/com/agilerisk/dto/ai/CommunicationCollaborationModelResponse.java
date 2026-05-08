package com.agilerisk.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommunicationCollaborationModelResponse(
        @JsonProperty("risk_level")      String riskLevel,
        @JsonProperty("risk_score")      Integer riskScore,
        @JsonProperty("detected_causes") List<String> detectedCauses,
        @JsonProperty("recommendations") List<String> recommendations,
        @JsonProperty("llm_explanation") String llmExplanation
) { }
