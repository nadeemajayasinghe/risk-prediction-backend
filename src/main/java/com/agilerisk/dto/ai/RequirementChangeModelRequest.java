package com.agilerisk.dto.ai;

import java.util.List;

public record RequirementChangeModelRequest(
        Long sprintId,
        String sprintGoal,
        List<StoryText> stories,
        List<ChangeRecord> changes,
        List<CommentText> comments
) {
    public record StoryText(Long id, String externalKey, String title, String description) { }
    public record ChangeRecord(String changeType, String description, String requestedBy, String changedAt) { }
    public record CommentText(Long storyId, String author, String body) { }
}
