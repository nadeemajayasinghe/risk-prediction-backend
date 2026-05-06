package com.agilerisk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private Endpoint overBudget = new Endpoint();
    private Endpoint requirementChange = new Endpoint();

    @Getter
    @Setter
    public static class Endpoint {
        private String baseUrl;
        private String path;
        private String apiKey;
        private long timeoutMs = 5000;
    }
}
