package com.agilerisk.service;

import com.agilerisk.domain.RequirementChange;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.SprintMetric;
import com.agilerisk.domain.UserStory;
import com.agilerisk.domain.enums.ChangeType;
import com.agilerisk.dto.ai.CommunicationCollaborationModelRequest;
import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import com.agilerisk.repository.TaskRepository;
import com.agilerisk.repository.CommentRepository;
import com.agilerisk.repository.RequirementChangeRepository;
import com.agilerisk.repository.SprintMetricRepository;
import com.agilerisk.repository.UserStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PayloadBuilder {

    private final SprintMetricRepository metricRepo;
    private final UserStoryRepository storyRepo;
    private final CommentRepository commentRepo;
    private final RequirementChangeRepository changeRepo;
    private final TaskRepository taskRepo;

    @Transactional(readOnly = true)
    public OverBudgetModelRequest buildOverBudget(Sprint sprint) {
        SprintMetric latest = metricRepo.findBySprintIdOrderByRecordedAtDesc(sprint.getId())
                .stream().findFirst().orElse(null);

        long scopeAdded = changeRepo.findBySprintIdOrderByChangedAtDesc(sprint.getId()).stream()
                .filter(c -> c.getChangeType() == ChangeType.SCOPE_ADDED)
                .count();

        int planned   = nz(latest == null ? null : latest.getPlannedPoints());
        int completed = nz(latest == null ? null : latest.getCompletedPoints());

        return new OverBudgetModelRequest(
                emptyIfNull(sprint.getTeamType()),
                nz(sprint.getBaseVelocity()),
                nz(sprint.getTeamSize()),
                nz(sprint.getComplexity()),
                Math.max(0, planned - completed),
                latest != null ? nz(latest.getVelocity()) : 0.0,
                completed,
                latest != null ? nz(latest.getEffortDeviation()) : 0.0,
                latest != null ? nz(latest.getReworkScore()) : 0.0,
                latest != null ? nz(latest.getBlockedTasks()) : 0,
                latest != null ? nz(latest.getReopenedTasks()) : 0,
                scopeAdded,
                latest != null ? nz(latest.getFatigue()) : 0.0
        );
    }

    /**
     * Builds the 7-feature payload required by the requirement-change model.
     * Features are derived from existing sprint data:
     *
     * <ul>
     *   <li><b>baseline_story_count</b>: stories created before/at sprint start</li>
     *   <li><b>updated_story_count</b>: stories whose latest update happened materially after creation
     *       (proxy: updated_at &gt; created_at + 60s) OR after the sprint started</li>
     *   <li><b>story_change_ratio</b>: updated / max(baseline, 1)</li>
     *   <li><b>acceptance_criteria_changes</b>: requirement_changes rows of type ACCEPTANCE_CRITERIA_CHANGED</li>
     *   <li><b>change_requests_count</b>: total requirement_changes rows for this sprint</li>
     *   <li><b>comments_on_stories</b>: total comments across all stories in the sprint</li>
     *   <li><b>requirement_volatility_score</b>: change_requests / baseline_story_count, capped at 1.0</li>
     * </ul>
     */
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
            // Fall back to total stories — handles the common case where stories were created
            // close to or just after sprint start during ingestion testing.
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

    /**
     * Builds the 5-feature payload required by the Communication & Collaboration scorer.
     *
     * <ul>
     *   <li><b>avg_response_time</b>: latest sprint_metrics.avg_response_time_hours (or 0)</li>
     *   <li><b>comments_per_task</b>: total comments / total tasks for this sprint (rounded down)</li>
     *   <li><b>inactive_days</b>: latest sprint_metrics.inactive_days (or 0)</li>
     *   <li><b>blockers</b>: latest sprint_metrics.blocked_tasks (or 0)</li>
     *   <li><b>reopened_tasks</b>: latest sprint_metrics.reopened_tasks (or 0)</li>
     * </ul>
     */
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

    @SuppressWarnings("unused")
    private static LocalDate atStart(LocalDate d) { return d; } // reserved for future date utilities
}
