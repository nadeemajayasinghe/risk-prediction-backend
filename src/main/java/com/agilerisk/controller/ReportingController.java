package com.agilerisk.controller;

import com.agilerisk.dto.response.AggregatedRiskResponse;
import com.agilerisk.dto.response.RiskPredictionResponse;
import com.agilerisk.dto.response.RiskSummaryResponse;
import com.agilerisk.dto.response.RiskTrendPoint;
import com.agilerisk.service.ReportingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reporting")
public class ReportingController {

    private final ReportingService service;

    @GetMapping("/sprints/{sprintId}/risk-summary")
    public RiskSummaryResponse summary(@PathVariable Long sprintId) {
        return service.summary(sprintId);
    }

    @GetMapping("/sprints/{sprintId}/history")
    public List<RiskPredictionResponse> history(@PathVariable Long sprintId) {
        return service.history(sprintId);
    }

    @GetMapping("/sprints/{sprintId}/trend")
    public List<RiskTrendPoint> trend(@PathVariable Long sprintId) {
        return service.trend(sprintId);
    }

    @GetMapping("/sprints/compare")
    public List<AggregatedRiskResponse> compare(@RequestParam("ids") List<Long> ids) {
        return service.compare(ids);
    }
}
