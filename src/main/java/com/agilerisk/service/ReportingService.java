package com.agilerisk.service;

import com.agilerisk.domain.AggregatedRiskResult;
import com.agilerisk.domain.RiskPrediction;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.enums.ModelType;
import com.agilerisk.dto.response.*;
import com.agilerisk.exception.ResourceNotFoundException;
import com.agilerisk.mapper.SprintMapper;
import com.agilerisk.repository.AggregatedRiskResultRepository;
import com.agilerisk.repository.RiskPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final SprintService sprintService;
    private final RiskPredictionRepository predictionRepo;
    private final AggregatedRiskResultRepository aggregatedRepo;
    private final SprintMapper mapper;

    @Transactional(readOnly = true)
    public RiskSummaryResponse summary(Long sprintId) {
        Sprint sprint = sprintService.load(sprintId);
        AggregatedRiskResult latest = aggregatedRepo.findFirstBySprintIdOrderByCreatedAtDesc(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("No risk evaluation yet for sprint " + sprintId));

        List<RiskPredictionResponse> latestPerModel = new ArrayList<>();
        for (ModelType type : ModelType.values()) {
            predictionRepo.findFirstBySprintIdAndModelTypeOrderByCreatedAtDesc(sprintId, type)
                    .map(mapper::toResponse)
                    .ifPresent(latestPerModel::add);
        }

        return new RiskSummaryResponse(
                mapper.toResponse(sprint),
                mapper.toResponse(latest),
                latestPerModel
        );
    }

    @Transactional(readOnly = true)
    public List<RiskPredictionResponse> history(Long sprintId) {
        sprintService.load(sprintId);
        return predictionRepo.findBySprintIdOrderByCreatedAtDesc(sprintId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RiskTrendPoint> trend(Long sprintId) {
        sprintService.load(sprintId);
        List<AggregatedRiskResult> all = aggregatedRepo.findBySprintIdOrderByCreatedAtDesc(sprintId);
        return all.stream()
                .map(r -> new RiskTrendPoint(
                        r.getCreatedAt(),
                        r.getOverallScore(),
                        r.getOverallLevel(),
                        r.getOverBudgetScore(),
                        r.getRequirementChangeScore()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AggregatedRiskResponse> compare(List<Long> sprintIds) {
        List<AggregatedRiskResponse> out = new ArrayList<>();
        for (Long id : sprintIds) {
            aggregatedRepo.findFirstBySprintIdOrderByCreatedAtDesc(id)
                    .map(mapper::toResponse)
                    .ifPresent(out::add);
        }
        return out;
    }
}
