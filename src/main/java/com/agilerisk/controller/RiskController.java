package com.agilerisk.controller;

import com.agilerisk.dto.response.AggregatedRiskResponse;
import com.agilerisk.service.RiskOrchestrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sprints/{sprintId}")
@RequiredArgsConstructor
@Tag(name = "Risk evaluation")
public class RiskController {

    private final RiskOrchestrationService orchestrator;

    @PostMapping("/evaluate-risk")
    public AggregatedRiskResponse evaluate(@PathVariable Long sprintId) {
        return orchestrator.evaluate(sprintId);
    }
}
