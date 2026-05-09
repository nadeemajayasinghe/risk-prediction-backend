package com.agilerisk.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OverBudgetModelRequest(
        @JsonProperty("team_type")              String teamType,
        @JsonProperty("team_size")              int teamSize,
        @JsonProperty("base_velocity")          double baseVelocity,
        @JsonProperty("complexity")             double complexity,
        @JsonProperty("hours_per_point")        double hoursPerPoint,
        @JsonProperty("sprint_capacity_hours")  double sprintCapacityHours,
        @JsonProperty("planned_hours")          double plannedHours,
        @JsonProperty("committed_story_points") double committedStoryPoints,
        @JsonProperty("velocity_rolling_prev")  double velocityRollingPrev,
        @JsonProperty("velocity_trend")         double velocityTrend,
        @JsonProperty("prev_carry_over_points") double prevCarryOverPoints,
        @JsonProperty("prev_carry_over_rate")   double prevCarryOverRate,
        @JsonProperty("prev_rework_score")      double prevReworkScore,
        @JsonProperty("prev_blocked_tasks")     double prevBlockedTasks,
        @JsonProperty("prev_reopened_tasks")    double prevReopenedTasks,
        @JsonProperty("fatigue")                double fatigue,
        @JsonProperty("scope_added")            double scopeAdded
) { }
