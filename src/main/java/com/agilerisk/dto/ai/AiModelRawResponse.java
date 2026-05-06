package com.agilerisk.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModelRawResponse(
        Double riskScore,
        String riskLevel,
        Double probability,
        String explanation
) { }
