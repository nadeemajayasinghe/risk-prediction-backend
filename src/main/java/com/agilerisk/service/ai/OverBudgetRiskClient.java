package com.agilerisk.service.ai;

import com.agilerisk.config.AiProperties;
import com.agilerisk.domain.AiModelResponse;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.dto.ai.AiModelRawResponse;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.ai.OverBudgetModelRequest;
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
public class OverBudgetRiskClient implements AiModelClient<OverBudgetModelRequest> {

    private final WebClient webClient;
    private final AiProperties props;
    private final AiClientSupport support;

    public OverBudgetRiskClient(@Qualifier("overBudgetWebClient") WebClient webClient,
                                AiProperties props,
                                AiClientSupport support) {
        this.webClient = webClient;
        this.props = props;
        this.support = support;
    }

    @Override
    @Cacheable(value = "overBudgetPredictions", key = "#sprintId + ':' + #request.hashCode()")
    @Retry(name = "aiClient", fallbackMethod = "fallback")
    @CircuitBreaker(name = "aiClient", fallbackMethod = "fallback")
    public ModelOutcome predict(Long sprintId, OverBudgetModelRequest request) {
        long start = System.currentTimeMillis();
        try {
            AiModelRawResponse raw = webClient.post()
                    .uri(props.getOverBudget().getPath())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiModelRawResponse.class)
                    .block(Duration.ofMillis(props.getOverBudget().getTimeoutMs() + 1000));

            int latency = (int) (System.currentTimeMillis() - start);
            AiModelResponse audit = support.persistAudit(sprintId, ModelType.OVER_BUDGET,
                    request, raw, latency, 200, false, null);
            return support.toOutcome(ModelType.OVER_BUDGET, raw, audit);
        } catch (RuntimeException ex) {
            int latency = (int) (System.currentTimeMillis() - start);
            support.persistAudit(sprintId, ModelType.OVER_BUDGET, request, null, latency, null, true, ex.getMessage());
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    private ModelOutcome fallback(Long sprintId, OverBudgetModelRequest request, Throwable ex) {
        log.warn("Over-budget AI call failed (sprintId={}): {}", sprintId, ex.toString());
        return ModelOutcome.fallback(ModelType.OVER_BUDGET, ex.getMessage());
    }
}
