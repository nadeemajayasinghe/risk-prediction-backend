package com.agilerisk.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OverBudgetModelRequest(
        @JsonProperty("team_type")              String teamType,
        @JsonProperty("base_velocity")          double baseVelocity,
        @JsonProperty("team_size")              int teamSize,
        @JsonProperty("complexity")             double complexity,
        @JsonProperty("remaining_work")         double remainingWork,
        @JsonProperty("velocity")               double velocity,
        @JsonProperty("completed_story_points") double completedStoryPoints,
        @JsonProperty("effort_deviation")       double effortDeviation,
        @JsonProperty("rework_score")           double reworkScore,
        @JsonProperty("blocked_tasks")          int blockedTasks,
        @JsonProperty("reopened_tasks")         int reopenedTasks,
        @JsonProperty("scope_added")            double scopeAdded,
        @JsonProperty("fatigue")                double fatigue
) { }
