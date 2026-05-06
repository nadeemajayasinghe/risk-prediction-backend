package com.agilerisk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aggregation")
public class AggregationProperties {
    private Weights weights = new Weights();
    private Thresholds thresholds = new Thresholds();

    @Getter
    @Setter
    public static class Weights {
        private double overBudget = 0.55;
        private double requirementChange = 0.45;
    }

    @Getter
    @Setter
    public static class Thresholds {
        private double medium = 35.0;
        private double high = 70.0;
    }
}
