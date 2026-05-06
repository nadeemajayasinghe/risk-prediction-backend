package com.agilerisk.controller;

import com.agilerisk.dto.request.CreateSprintRequest;
import com.agilerisk.dto.request.UpdateSprintRequest;
import com.agilerisk.dto.response.SprintResponse;
import com.agilerisk.service.SprintService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sprints")
@RequiredArgsConstructor
@Tag(name = "Sprints")
public class SprintController {

    private final SprintService service;

    @PostMapping
    public ResponseEntity<SprintResponse> create(@Valid @RequestBody CreateSprintRequest req) {
        SprintResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/sprints/" + created.id())).body(created);
    }

    @GetMapping
    public List<SprintResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public SprintResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public SprintResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSprintRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
