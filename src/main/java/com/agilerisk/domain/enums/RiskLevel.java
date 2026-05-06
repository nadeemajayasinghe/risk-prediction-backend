package com.agilerisk.domain.enums;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, UNKNOWN;

    public RiskLevel bumpUp() {
        return switch (this) {
            case LOW -> MEDIUM;
            case MEDIUM, UNKNOWN -> HIGH;
            case HIGH -> HIGH;
        };
    }
}
