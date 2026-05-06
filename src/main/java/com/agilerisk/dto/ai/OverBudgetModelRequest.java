package com.agilerisk.dto.ai;

import java.util.List;

public record OverBudgetModelRequest(
        Long sprintId,
        Integer plannedPoints,
        Integer completedPoints,
        Integer capacityPoints,
        Double effortDeviation,
        Integer bugsCount,
        Integer scopeChangesCount,
        Double velocity,
        List<TaskFeature> tasks
) {
    public record TaskFeature(
            Long storyId,
            Integer storyPoints,
            Double estimatedHours,
            Double actualHours,
            String status
    ) { }
}
