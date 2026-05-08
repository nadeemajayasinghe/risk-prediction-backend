package com.agilerisk.service;

import com.agilerisk.domain.*;
import com.agilerisk.dto.request.*;
import com.agilerisk.exception.ResourceNotFoundException;
import com.agilerisk.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final SprintRepository sprintRepo;
    private final SprintMetricRepository metricRepo;
    private final UserStoryRepository storyRepo;
    private final TaskRepository taskRepo;
    private final CommentRepository commentRepo;
    private final RequirementChangeRepository changeRepo;

    @Transactional
    public SprintMetric addMetric(Long sprintId, SprintMetricRequest req) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        return metricRepo.save(SprintMetric.builder()
                .sprint(sprint)
                .plannedPoints(req.plannedPoints())
                .completedPoints(req.completedPoints())
                .effortDeviation(req.effortDeviation())
                .bugsCount(req.bugsCount())
                .scopeChangesCount(req.scopeChangesCount())
                .velocity(req.velocity())
                .reworkScore(req.reworkScore())
                .blockedTasks(req.blockedTasks())
                .reopenedTasks(req.reopenedTasks())
                .fatigue(req.fatigue())
                .avgResponseTimeHours(req.avgResponseTimeHours())
                .inactiveDays(req.inactiveDays())
                .build());
    }

    @Transactional
    public UserStory addStory(Long sprintId, UserStoryRequest req) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        return storyRepo.save(UserStory.builder()
                .sprint(sprint)
                .externalKey(req.externalKey())
                .title(req.title())
                .description(req.description())
                .storyPoints(req.storyPoints())
                .priority(req.priority())
                .status(req.status())
                .build());
    }

    @Transactional
    public Task addTask(Long storyId, TaskRequest req) {
        UserStory story = storyRepo.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));
        return taskRepo.save(Task.builder()
                .story(story)
                .title(req.title())
                .description(req.description())
                .estimatedHours(req.estimatedHours())
                .actualHours(req.actualHours())
                .status(req.status())
                .assignee(req.assignee())
                .build());
    }

    @Transactional
    public Comment addComment(Long storyId, CommentRequest req) {
        UserStory story = storyRepo.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));
        return commentRepo.save(Comment.builder()
                .story(story)
                .author(req.author())
                .body(req.body())
                .build());
    }

    @Transactional
    public RequirementChange addRequirementChange(Long sprintId, RequirementChangeRequest req) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        UserStory story = req.storyId() == null ? null : storyRepo.findById(req.storyId())
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + req.storyId()));
        return changeRepo.save(RequirementChange.builder()
                .sprint(sprint)
                .story(story)
                .changeType(req.changeType())
                .description(req.description())
                .requestedBy(req.requestedBy())
                .build());
    }
}
