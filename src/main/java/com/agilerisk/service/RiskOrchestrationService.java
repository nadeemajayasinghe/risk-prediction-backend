package com.agilerisk.service;

import com.agilerisk.domain.AggregatedRiskResult;
import com.agilerisk.domain.Sprint;
import com.agilerisk.dto.ai.CommunicationCollaborationModelRequest;
import com.agilerisk.dto.ai.ModelOutcome;
import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import com.agilerisk.dto.response.AggregatedRiskResponse;
import com.agilerisk.mapper.SprintMapper;
import com.agilerisk.service.ai.CommunicationCollaborationRiskClient;
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
    private final CommunicationCollaborationRiskClient communicationCollaborationClient;
    private final RiskAggregationService aggregationService;
    private final SprintMapper mapper;

    @Transactional
    public AggregatedRiskResponse evaluate(Long sprintId) {
        Sprint sprint = sprintService.load(sprintId);

        OverBudgetModelRequest obReq = payloadBuilder.buildOverBudget(sprint);
        RequirementChangeModelRequest rcReq = payloadBuilder.buildRequirementChange(sprint);
        CommunicationCollaborationModelRequest ccReq = payloadBuilder.buildCommunicationCollaboration(sprint);

        CompletableFuture<ModelOutcome> obFuture = CompletableFuture.supplyAsync(
                () -> overBudgetClient.predict(sprintId, obReq));
        CompletableFuture<ModelOutcome> rcFuture = CompletableFuture.supplyAsync(
                () -> requirementChangeClient.predict(sprintId, rcReq));
        CompletableFuture<ModelOutcome> ccFuture = CompletableFuture.supplyAsync(
                () -> communicationCollaborationClient.predict(sprintId, ccReq));

        try {
            CompletableFuture.allOf(obFuture, rcFuture, ccFuture).join();
            ModelOutcome ob = obFuture.get();
            ModelOutcome rc = rcFuture.get();
            ModelOutcome cc = ccFuture.get();
            log.info("Sprint {} evaluation: ob={} rc={} cc={} (degraded ob={} rc={} cc={})",
                    sprintId, ob.riskScore(), rc.riskScore(), cc.riskScore(),
                    ob.degraded(), rc.degraded(), cc.degraded());
            AggregatedRiskResult result = aggregationService.aggregate(sprint, ob, rc, cc);
            return mapper.toResponse(result, List.of(ob, rc, cc));
        } catch (InterruptedException | ExecutionException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to evaluate sprint risk", ex);
        }
    }
}
