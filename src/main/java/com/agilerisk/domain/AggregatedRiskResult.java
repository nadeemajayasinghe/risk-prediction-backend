package com.agilerisk.domain;

import com.agilerisk.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aggregated_risk_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregatedRiskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;

    @Column(name = "evaluation_id", nullable = false, columnDefinition = "uuid")
    private UUID evaluationId;

    @Column(name = "overall_score", nullable = false)
    private Double overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_level", nullable = false, length = 16)
    private RiskLevel overallLevel;

    @Column(name = "over_budget_score")
    private Double overBudgetScore;

    @Column(name = "requirement_change_score")
    private Double requirementChangeScore;

    @Column(name = "communication_collaboration_score")
    private Double communicationCollaborationScore;

    @Column(name = "combined_explanation", columnDefinition = "TEXT")
    private String combinedExplanation;

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (evaluationId == null) evaluationId = UUID.randomUUID();
    }
}
