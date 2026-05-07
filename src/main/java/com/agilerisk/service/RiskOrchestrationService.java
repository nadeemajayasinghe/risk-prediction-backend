package com.agilerisk.service;

import com.agilerisk.domain.AggregatedRiskResult;
import com.agilerisk.domain.Sprint;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import com.agilerisk.dto.response.AggregatedRiskResponse;
import com.agilerisk.mapper.SprintMapper;
import com.agilerisk.service.ai.OverBudgetRiskClient;
import com.agilerisk.service.ai.RequirementChangeRiskClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskOrchestrationService {

    private final SprintService sprintService;
    private final PayloadBuilder payloadBuilder;
    private final OverBudgetRiskClient overBudgetClient;
    private final RequirementChangeRiskClient requirementChangeClient;
    private final RiskAggregationService aggregationService;
    private final SprintMapper mapper;

    @Transactional
    public AggregatedRiskResponse evaluate(Long sprintId) {
        Sprint sprint = sprintService.load(sprintId);

        OverBudgetModelRequest obReq = payloadBuilder.buildOverBudget(sprint);
        RequirementChangeModelRequest rcReq = payloadBuilder.buildRequirementChange(sprint);

        CompletableFuture<ModelOutcome> obFuture = CompletableFuture.supplyAsync(
                () -> overBudgetClient.predict(sprintId, obReq));
        CompletableFuture<ModelOutcome> rcFuture = CompletableFuture.supplyAsync(
                () -> requirementChangeClient.predict(sprintId, rcReq));

        try {
            CompletableFuture.allOf(obFuture, rcFuture).join();
            ModelOutcome ob = obFuture.get();
            ModelOutcome rc = rcFuture.get();
            log.info("Sprint {} evaluation: ob={} rc={} (degraded ob={} rc={})",
                    sprintId, ob.riskScore(), rc.riskScore(), ob.degraded(), rc.degraded());
            AggregatedRiskResult result = aggregationService.aggregate(sprint, ob, rc);
            return mapper.toResponse(result, List.of(ob, rc));
        } catch (InterruptedException | ExecutionException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to evaluate sprint risk", ex);
        }
    }
}
