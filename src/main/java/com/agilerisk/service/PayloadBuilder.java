package com.agilerisk.service;

import com.agilerisk.domain.RequirementChange;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.SprintMetric;
import com.agilerisk.domain.UserStory;
import com.agilerisk.domain.enums.ChangeType;
import com.agilerisk.dto.ai.CommunicationCollaborationModelRequest;
import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import com.agilerisk.repository.CommentRepository;
import com.agilerisk.repository.RequirementChangeRepository;
import com.agilerisk.repository.SprintMetricRepository;
import com.agilerisk.repository.SprintRepository;
import com.agilerisk.repository.TaskRepository;
import com.agilerisk.repository.UserStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PayloadBuilder {

    private final SprintRepository sprintRepo;
    private final SprintMetricRepository metricRepo;
    private final UserStoryRepository storyRepo;
    private final CommentRepository commentRepo;
    private final RequirementChangeRepository changeRepo;
    private final TaskRepository taskRepo;

    /**
     * Builds the 17-feature payload for the over-budget v2 model.
     *
     * <p>Features come from three places:</p>
     * <ul>
     *   <li><b>Current sprint plan</b>: team_type, team_size, base_velocity, complexity,
     *       sprint_capacity_hours (Sprint entity); committed_story_points + planned_hours
     *       (computed from current stories + tasks)</li>
     *   <li><b>Current sprint observations</b>: fatigue (latest SprintMetric); scope_added
     *       (count of SCOPE_ADDED RequirementChange rows)</li>
     *   <li><b>Previous sprint history</b> (same team_id, prior start_date):
     *       prev_carry_over_*, prev_rework_score, prev_blocked_tasks, prev_reopened_tasks,
     *       velocity_rolling_prev (rolling avg over up to 3 prior sprints), velocity_trend
     *       (slope between last two sprints).</li>
     * </ul>
     *
     * <p>If the team has no prior sprint, all <code>prev_*</code> features default to 0
     * and <code>velocity_rolling_prev</code> falls back to <code>base_velocity</code>.</p>
     */
    @Transactional(readOnly = true)
    public OverBudgetModelRequest buildOverBudget(Sprint sprint) {
        // ----- Current-sprint plan inputs -----
        List<UserStory> stories = storyRepo.findBySprintId(sprint.getId());
        double committedPoints = stories.stream()
                .mapToInt(s -> nz(s.getStoryPoints()))
                .sum();
        double plannedHours = stories.stream()
                .flatMap(s -> taskRepo.findByStoryId(s.getId()).stream())
                .mapToDouble(t -> nz(t.getEstimatedHours()))
                .sum();
        double sprintCapacityHours = nz(sprint.getSprintCapacityHours());
        double hoursPerPoint = committedPoints > 0
                ? plannedHours / committedPoints
                : 0.0;

        // ----- Current observations -----
        SprintMetric latest = metricRepo.findBySprintIdOrderByRecordedAtDesc(sprint.getId())
                .stream().findFirst().orElse(null);
        double fatigue = latest != null ? nz(latest.getFatigue()) : 0.0;

        long scopeAddedCount = changeRepo.findBySprintIdOrderByChangedAtDesc(sprint.getId()).stream()
                .filter(c -> c.getChangeType() == ChangeType.SCOPE_ADDED)
                .count();

        // ----- Previous-sprint history -----
        PrevStats prev = computePreviousStats(sprint);

        return new OverBudgetModelRequest(
                emptyIfNull(sprint.getTeamType()),
                nz(sprint.getTeamSize()),
                nz(sprint.getBaseVelocity()),
                nz(sprint.getComplexity()),
                round4(hoursPerPoint),
                round4(sprintCapacityHours),
                round4(plannedHours),
                round4(committedPoints),
                round4(prev.velocityRollingPrev),
                round4(prev.velocityTrend),
                round4(prev.carryOverPoints),
                round4(prev.carryOverRate),
                round4(prev.reworkScore),
                round4(prev.blockedTasks),
                round4(prev.reopenedTasks),
                round4(fatigue),
                scopeAddedCount
        );
    }

    /** Encapsulated previous-sprint history derived from up to 3 prior sprints. */
    private record PrevStats(
            double velocityRollingPrev,
            double velocityTrend,
            double carryOverPoints,
            double carryOverRate,
            double reworkScore,
            double blockedTasks,
            double reopenedTasks
    ) { }

    private PrevStats computePreviousStats(Sprint current) {
        if (current.getTeamId() == null || current.getStartDate() == null) {
            return new PrevStats(nz(current.getBaseVelocity()), 0, 0, 0, 0, 0, 0);
        }
        List<Sprint> prior = sprintRepo.findPreviousSprints(current.getTeamId(), current.getStartDate());
        if (prior.isEmpty()) {
            return new PrevStats(nz(current.getBaseVelocity()), 0, 0, 0, 0, 0, 0);
        }
        Sprint prev = prior.get(0);
        SprintMetric prevMetric = metricRepo.findBySprintIdOrderByRecordedAtDesc(prev.getId())
                .stream().findFirst().orElse(null);

        double prevVelocity = prevMetric != null ? nz(prevMetric.getVelocity()) : nz(prev.getBaseVelocity());
        double prevPlanned = prevMetric != null ? nz(prevMetric.getPlannedPoints()) : 0.0;
        double prevCompleted = prevMetric != null ? nz(prevMetric.getCompletedPoints()) : 0.0;
        double carryOverPoints = Math.max(0.0, prevPlanned - prevCompleted);
        double carryOverRate = prevPlanned > 0 ? carryOverPoints / prevPlanned : 0.0;
        double reworkScore = prevMetric != null ? nz(prevMetric.getReworkScore()) : 0.0;
        double blockedTasks = prevMetric != null ? nz(prevMetric.getBlockedTasks()) : 0.0;
        double reopenedTasks = prevMetric != null ? nz(prevMetric.getReopenedTasks()) : 0.0;

        // Rolling average over up to the 3 most recent prior sprints
        List<Double> recentVelocities = prior.stream()
                .limit(3)
                .map(s -> metricRepo.findBySprintIdOrderByRecordedAtDesc(s.getId()).stream()
                        .findFirst()
                        .map(m -> nz(m.getVelocity()))
                        .orElse(nz(s.getBaseVelocity())))
                .toList();
        double rolling = recentVelocities.isEmpty()
                ? prevVelocity
                : recentVelocities.stream().mapToDouble(Double::doubleValue).average().orElse(prevVelocity);

        // Trend = slope between the two most recent sprints (positive = improving)
        double trend = 0.0;
        if (recentVelocities.size() >= 2) {
            trend = recentVelocities.get(0) - recentVelocities.get(1);
        }

        return new PrevStats(
                rolling,
                trend,
                carryOverPoints,
                Math.min(1.0, carryOverRate),
                reworkScore,
                blockedTasks,
                reopenedTasks
        );
    }

    // ---- Requirement-Change side (unchanged) -----------------------------------

    @Transactional(readOnly = true)
    public RequirementChangeModelRequest buildRequirementChange(Sprint sprint) {
        List<UserStory> stories = storyRepo.findBySprintId(sprint.getId());
        List<RequirementChange> changes = changeRepo.findBySprintIdOrderByChangedAtDesc(sprint.getId());

        Instant sprintStart = sprint.getStartDate() == null
                ? Instant.now()
                : sprint.getStartDate().atStartOfDay().toInstant(ZoneOffset.UTC);

        int baselineCount = (int) stories.stream()
                .filter(s -> s.getCreatedAt() != null && !s.getCreatedAt().isAfter(sprintStart))
                .count();
        if (baselineCount == 0 && !stories.isEmpty()) {
            baselineCount = stories.size();
        }

        int updatedCount = (int) stories.stream()
                .filter(s -> s.getCreatedAt() != null && s.getUpdatedAt() != null)
                .filter(s -> Duration.between(s.getCreatedAt(), s.getUpdatedAt()).toSeconds() > 60
                          || s.getUpdatedAt().isAfter(sprintStart))
                .count();

        double changeRatio = baselineCount > 0 ? (double) updatedCount / baselineCount : 0.0;
        if (changeRatio > 1.0) changeRatio = 1.0;

        int acChanges = (int) changes.stream()
                .filter(c -> c.getChangeType() == ChangeType.ACCEPTANCE_CRITERIA_CHANGED)
                .count();
        int crCount = changes.size();

        int totalComments = stories.stream()
                .mapToInt(s -> commentRepo.findByStoryId(s.getId()).size())
                .sum();

        double volatility = baselineCount > 0 ? (double) crCount / baselineCount : 0.0;
        if (volatility > 1.0) volatility = 1.0;

        return new RequirementChangeModelRequest(
                baselineCount,
                updatedCount,
                round4(changeRatio),
                acChanges,
                crCount,
                totalComments,
                round4(volatility)
        );
    }

    // ---- Communication & Collaboration side (unchanged) ------------------------

    @Transactional(readOnly = true)
    public CommunicationCollaborationModelRequest buildCommunicationCollaboration(Sprint sprint) {
        SprintMetric latest = metricRepo.findBySprintIdOrderByRecordedAtDesc(sprint.getId())
                .stream().findFirst().orElse(null);

        List<UserStory> stories = storyRepo.findBySprintId(sprint.getId());
        int totalTasks = stories.stream()
                .mapToInt(s -> taskRepo.findByStoryId(s.getId()).size())
                .sum();
        int totalComments = stories.stream()
                .mapToInt(s -> commentRepo.findByStoryId(s.getId()).size())
                .sum();
        int commentsPerTask = totalTasks > 0 ? totalComments / totalTasks : 0;

        return new CommunicationCollaborationModelRequest(
                latest != null ? nz(latest.getAvgResponseTimeHours()) : 0,
                commentsPerTask,
                latest != null ? nz(latest.getInactiveDays()) : 0,
                latest != null ? nz(latest.getBlockedTasks()) : 0,
                latest != null ? nz(latest.getReopenedTasks()) : 0
        );
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static double nz(Double v) { return v == null ? 0.0 : v; }
    private static String emptyIfNull(String v) { return v == null ? "" : v; }
    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }

    // Reserved for potential date-utility refactors.
    @SuppressWarnings("unused")
    private static LocalDate atStart(LocalDate d) { return d; }
    @SuppressWarnings("unused")
    private static Comparator<Object> reserved() { return (a, b) -> 0; }
}
