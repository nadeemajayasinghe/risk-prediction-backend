package com.agilerisk.domain;

import com.agilerisk.domain.enums.SprintStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sprints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sprint extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String goal;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SprintStatus status;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "capacity_points")
    private Integer capacityPoints;

    @Column(name = "team_type", length = 64)
    private String teamType;

    @Column(name = "team_size")
    private Integer teamSize;

    @Column(name = "complexity")
    private Double complexity;

    @Column(name = "base_velocity")
    private Double baseVelocity;

    @Column(name = "sprint_capacity_hours")
    private Double sprintCapacityHours;

    @OneToMany(mappedBy = "sprint", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SprintMetric> metrics = new ArrayList<>();

    @OneToMany(mappedBy = "sprint", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserStory> stories = new ArrayList<>();

    @OneToMany(mappedBy = "sprint", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RequirementChange> requirementChanges = new ArrayList<>();
}
