package com.agilerisk.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sprint_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SprintMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;

    @Column(name = "planned_points")
    private Integer plannedPoints;

    @Column(name = "completed_points")
    private Integer completedPoints;

    @Column(name = "effort_deviation")
    private Double effortDeviation;

    @Column(name = "bugs_count")
    private Integer bugsCount;

    @Column(name = "scope_changes_count")
    private Integer scopeChangesCount;

    private Double velocity;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) recordedAt = Instant.now();
    }
}
