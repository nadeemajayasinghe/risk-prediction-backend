package com.agilerisk.service;

import com.agilerisk.domain.Sprint;
import com.agilerisk.dto.request.CreateSprintRequest;
import com.agilerisk.dto.request.UpdateSprintRequest;
import com.agilerisk.dto.response.SprintResponse;
import com.agilerisk.exception.BusinessRuleException;
import com.agilerisk.exception.ResourceNotFoundException;
import com.agilerisk.mapper.SprintMapper;
import com.agilerisk.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository repo;
    private final SprintMapper mapper;

    @Transactional
    public SprintResponse create(CreateSprintRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessRuleException("endDate must be on/after startDate");
        }
        Sprint sprint = Sprint.builder()
                .name(req.name())
                .goal(req.goal())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .status(req.status())
                .teamId(req.teamId())
                .capacityPoints(req.capacityPoints())
                .teamType(req.teamType())
                .teamSize(req.teamSize())
                .complexity(req.complexity())
                .baseVelocity(req.baseVelocity())
                .sprintCapacityHours(req.sprintCapacityHours())
                .build();
        return mapper.toResponse(repo.save(sprint));
    }

    @Transactional(readOnly = true)
    public SprintResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<SprintResponse> list() {
        return repo.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public SprintResponse update(Long id, UpdateSprintRequest req) {
        Sprint s = load(id);
        if (req.name() != null) s.setName(req.name());
        if (req.goal() != null) s.setGoal(req.goal());
        if (req.startDate() != null) s.setStartDate(req.startDate());
        if (req.endDate() != null) s.setEndDate(req.endDate());
        if (req.status() != null) s.setStatus(req.status());
        if (req.teamId() != null) s.setTeamId(req.teamId());
        if (req.capacityPoints() != null) s.setCapacityPoints(req.capacityPoints());
        if (req.teamType() != null) s.setTeamType(req.teamType());
        if (req.teamSize() != null) s.setTeamSize(req.teamSize());
        if (req.complexity() != null) s.setComplexity(req.complexity());
        if (req.baseVelocity() != null) s.setBaseVelocity(req.baseVelocity());
        if (req.sprintCapacityHours() != null) s.setSprintCapacityHours(req.sprintCapacityHours());
        if (s.getEndDate().isBefore(s.getStartDate())) {
            throw new BusinessRuleException("endDate must be on/after startDate");
        }
        return mapper.toResponse(s);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Sprint not found: " + id);
        repo.deleteById(id);
    }

    public Sprint load(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));
    }
}
