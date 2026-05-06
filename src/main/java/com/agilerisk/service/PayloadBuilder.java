package com.agilerisk.service;

import com.agilerisk.domain.RequirementChange;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.SprintMetric;
import com.agilerisk.domain.UserStory;
import com.agilerisk.domain.enums.ChangeType;
import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import com.agilerisk.repository.CommentRepository;
import com.agilerisk.repository.RequirementChangeRepository;
import com.agilerisk.repository.SprintMetricRepository;
import com.agilerisk.repository.UserStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PayloadBuilder {

    private final SprintMetricRepository metricRepo;
    private final UserStoryRepository storyRepo;
    private final CommentRepository commentRepo;
    private final RequirementChangeRepository changeRepo;

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

    @Transactional(readOnly = true)
    public RequirementChangeModelRequest buildRequirementChange(Sprint sprint) {
        List<UserStory> stories = storyRepo.findBySprintId(sprint.getId());

        List<RequirementChangeModelRequest.StoryText> storyTexts = stories.stream()
                .map(s -> new RequirementChangeModelRequest.StoryText(
                        s.getId(), s.getExternalKey(), s.getTitle(), s.getDescription()))
                .toList();

        List<RequirementChange> changes = changeRepo.findBySprintIdOrderByChangedAtDesc(sprint.getId());
        List<RequirementChangeModelRequest.ChangeRecord> changeRecords = changes.stream()
                .map(c -> new RequirementChangeModelRequest.ChangeRecord(
                        c.getChangeType().name(), c.getDescription(), c.getRequestedBy(),
                        c.getChangedAt() == null ? null : c.getChangedAt().toString()))
                .toList();

        List<RequirementChangeModelRequest.CommentText> commentTexts = stories.stream()
                .flatMap(s -> commentRepo.findByStoryId(s.getId()).stream()
                        .map(c -> new RequirementChangeModelRequest.CommentText(
                                s.getId(), c.getAuthor(), c.getBody())))
                .sorted(Comparator.comparing(RequirementChangeModelRequest.CommentText::storyId))
                .toList();

        return new RequirementChangeModelRequest(
                sprint.getId(),
                sprint.getGoal(),
                storyTexts,
                changeRecords,
                commentTexts
        );
    }

    private int nz(Integer v) { return v == null ? 0 : v; }
    private double nz(Double v) { return v == null ? 0.0 : v; }
    private String emptyIfNull(String v) { return v == null ? "" : v; }
}
