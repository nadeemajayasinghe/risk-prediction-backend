package com.agilerisk.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RequirementChangeModelRequest(
        @JsonProperty("baseline_story_count")         int baselineStoryCount,
        @JsonProperty("updated_story_count")          int updatedStoryCount,
        @JsonProperty("story_change_ratio")           double storyChangeRatio,
        @JsonProperty("acceptance_criteria_changes") int acceptanceCriteriaChanges,
        @JsonProperty("change_requests_count")        int changeRequestsCount,
        @JsonProperty("comments_on_stories")          int commentsOnStories,
        @JsonProperty("requirement_volatility_score") double requirementVolatilityScore
) { }
