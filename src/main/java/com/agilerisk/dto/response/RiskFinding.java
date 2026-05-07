package com.agilerisk.dto.response;

/**
 * One human-readable issue found in the sprint plus a recommended action.
 * Returned alongside a risk prediction so a dashboard can render
 * "why" the risk is high and "what to do about it".
 */
public record RiskFinding(
        String code,        // stable identifier, e.g. EFFORT_OVERRUN
        String severity,    // INFO | WARNING | CRITICAL
        String reason,      // user-facing explanation, refers to actual values
        String suggestion   // actionable mitigation
) { }
