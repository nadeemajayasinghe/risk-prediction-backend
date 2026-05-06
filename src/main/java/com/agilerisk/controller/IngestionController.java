package com.agilerisk.controller;

import com.agilerisk.dto.request.*;
import com.agilerisk.service.IngestionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Ingestion")
public class IngestionController {

    private final IngestionService service;

    @PostMapping("/sprints/{sprintId}/metrics")
    public ResponseEntity<Long> addMetric(@PathVariable Long sprintId, @Valid @RequestBody SprintMetricRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMetric(sprintId, req).getId());
    }

    @PostMapping("/sprints/{sprintId}/stories")
    public ResponseEntity<Long> addStory(@PathVariable Long sprintId, @Valid @RequestBody UserStoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addStory(sprintId, req).getId());
    }

    @PostMapping("/stories/{storyId}/tasks")
    public ResponseEntity<Long> addTask(@PathVariable Long storyId, @Valid @RequestBody TaskRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTask(storyId, req).getId());
    }

    @PostMapping("/stories/{storyId}/comments")
    public ResponseEntity<Long> addComment(@PathVariable Long storyId, @Valid @RequestBody CommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addComment(storyId, req).getId());
    }

    @PostMapping("/sprints/{sprintId}/requirement-changes")
    public ResponseEntity<Long> addRequirementChange(@PathVariable Long sprintId,
                                                     @Valid @RequestBody RequirementChangeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addRequirementChange(sprintId, req).getId());
    }
}
