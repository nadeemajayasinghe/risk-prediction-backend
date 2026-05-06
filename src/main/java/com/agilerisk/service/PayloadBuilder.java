package com.agilerisk.service;

import com.agilerisk.domain.RequirementChange;
import com.agilerisk.domain.Sprint;
import com.agilerisk.domain.SprintMetric;
import com.agilerisk.domain.UserStory;
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

        List<UserStory> stories = storyRepo.findBySprintId(sprint.getId());
        List<OverBudgetModelRequest.TaskFeature> features = stories.stream()
                .flatMap(s -> s.getTasks().stream().map(t -> new OverBudgetModelRequest.TaskFeature(
                        s.getId(),
                        s.getStoryPoints(),
                        t.getEstimatedHours(),
                        t.getActualHours(),
                        t.getStatus())))
                .toList();

        return new OverBudgetModelRequest(
                sprint.getId(),
                latest != null ? latest.getPlannedPoints() : null,
                latest != null ? latest.getCompletedPoints() : null,
                sprint.getCapacityPoints(),
                latest != null ? latest.getEffortDeviation() : null,
                latest != null ? latest.getBugsCount() : null,
                latest != null ? latest.getScopeChangesCount() : null,
                latest != null ? latest.getVelocity() : null,
                features
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
}
