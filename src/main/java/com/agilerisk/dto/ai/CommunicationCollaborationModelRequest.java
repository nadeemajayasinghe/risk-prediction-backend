package com.agilerisk.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CommunicationCollaborationModelRequest(
        @JsonProperty("avg_response_time") int avgResponseTime,
        @JsonProperty("comments_per_task") int commentsPerTask,
        @JsonProperty("inactive_days")     int inactiveDays,
        @JsonProperty("blockers")          int blockers,
        @JsonProperty("reopened_tasks")    int reopenedTasks
) { }
