package com.agilerisk.service.ai;

import com.agilerisk.config.AiProperties;
import com.agilerisk.domain.AiModelResponse;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.dto.ai.AiModelRawResponse;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Service
public class RequirementChangeRiskClient implements AiModelClient<RequirementChangeModelRequest> {

    private final WebClient webClient;
    private final AiProperties props;
    private final AiClientSupport support;

    public RequirementChangeRiskClient(@Qualifier("requirementChangeWebClient") WebClient webClient,
                                       AiProperties props,
                                       AiClientSupport support) {
        this.webClient = webClient;
        this.props = props;
        this.support = support;
    }

    @Override
    @Cacheable(value = "requirementChangePredictions", key = "#sprintId + ':' + #request.hashCode()")
    @Retry(name = "aiClient", fallbackMethod = "fallback")
    @CircuitBreaker(name = "aiClient", fallbackMethod = "fallback")
    public ModelOutcome predict(Long sprintId, RequirementChangeModelRequest request) {
        long start = System.currentTimeMillis();
        try {
            AiModelRawResponse raw = webClient.post()
                    .uri(props.getRequirementChange().getPath())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiModelRawResponse.class)
                    .block(Duration.ofMillis(props.getRequirementChange().getTimeoutMs() + 1000));

            int latency = (int) (System.currentTimeMillis() - start);
            AiModelResponse audit = support.persistAudit(sprintId, ModelType.REQUIREMENT_CHANGE,
                    request, raw, latency, 200, false, null);
            return support.toOutcome(ModelType.REQUIREMENT_CHANGE, raw, audit);
        } catch (RuntimeException ex) {
            int latency = (int) (System.currentTimeMillis() - start);
            support.persistAudit(sprintId, ModelType.REQUIREMENT_CHANGE, request, null, latency, null, true, ex.getMessage());
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    private ModelOutcome fallback(Long sprintId, RequirementChangeModelRequest request, Throwable ex) {
        log.warn("Requirement-change AI call failed (sprintId={}): {}", sprintId, ex.toString());
        return ModelOutcome.fallback(ModelType.REQUIREMENT_CHANGE, ex.getMessage());
    }
}
