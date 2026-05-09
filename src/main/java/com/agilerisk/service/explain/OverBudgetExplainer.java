package com.agilerisk.service.explain;

import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.response.RiskFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based explainer for the over-budget v2 model (17 leakage-free features).
 *
 * <p>Rules use only planning + historical inputs — no post-hoc/leakage features.
 * Thresholds are conservative defaults; tune against your dataset.</p>
 */
@Service
public class OverBudgetExplainer {

    private static final String INFO     = "INFO";
    private static final String WARNING  = "WARNING";
    private static final String CRITICAL = "CRITICAL";

    public List<RiskFinding> explain(OverBudgetModelRequest r) {
        List<RiskFinding> findings = new ArrayList<>();

        // 1. Over-commitment vs baseline velocity
        if (r.baseVelocity() > 0) {
            double commitmentRatio = r.committedStoryPoints() / r.baseVelocity();
            if (commitmentRatio >= 1.30) {
                findings.add(new RiskFinding("OVER_COMMITMENT", CRITICAL,
                        String.format("Committed %.0f points is %.0f%% of base velocity %.1f.",
                                r.committedStoryPoints(), commitmentRatio * 100, r.baseVelocity()),
                        "Trim sprint scope back toward baseline velocity. Carry stretch goals as optional, not committed."));
            } else if (commitmentRatio >= 1.15) {
                findings.add(new RiskFinding("OVER_COMMITMENT", WARNING,
                        String.format("Committed %.0f points is above baseline %.1f.",
                                r.committedStoryPoints(), r.baseVelocity()),
                        "Reconsider stretch items; ensure highest-priority work fits comfortably within capacity."));
            }
        }

        // 2. Capacity / hours mismatch
        if (r.sprintCapacityHours() > 0 && r.plannedHours() > 0) {
            double utilisation = r.plannedHours() / r.sprintCapacityHours();
            if (utilisation >= 1.10) {
                findings.add(new RiskFinding("CAPACITY_OVERLOAD", CRITICAL,
                        String.format("Planned %.1f hours exceeds team capacity %.1f hours (%.0f%% utilisation).",
                                r.plannedHours(), r.sprintCapacityHours(), utilisation * 100),
                        "Re-estimate or descope tasks until planned hours fit within capacity, leaving 10-15% buffer."));
            } else if (utilisation >= 0.95) {
                findings.add(new RiskFinding("CAPACITY_TIGHT", WARNING,
                        String.format("Planned hours fill %.0f%% of capacity — minimal buffer.", utilisation * 100),
                        "Build a small buffer for unplanned work and absences (~10%)."));
            }
        }

        // 3. Hours-per-point estimation outliers
        if (r.hoursPerPoint() >= 12.0) {
            findings.add(new RiskFinding("HIGH_HOURS_PER_POINT", WARNING,
                    String.format("Estimation density %.1f h/point is high — stories may be too coarse.", r.hoursPerPoint()),
                    "Break large stories into smaller increments. Re-estimate on a thinner slice."));
        } else if (r.hoursPerPoint() > 0 && r.hoursPerPoint() < 2.0) {
            findings.add(new RiskFinding("LOW_HOURS_PER_POINT", INFO,
                    String.format("Estimation density %.1f h/point is unusually low.", r.hoursPerPoint()),
                    "Sanity-check that task hours reflect realistic effort — under-estimating leads to silent overruns."));
        }

        // 4. Sprint complexity
        if (r.complexity() >= 4.0) {
            findings.add(new RiskFinding("HIGH_COMPLEXITY", WARNING,
                    String.format("Sprint complexity %.1f is on the high end.", r.complexity()),
                    "Run spikes on unknowns before committing implementation. Pair on the riskiest stories."));
        }

        // 5. Velocity trend (negative trend = team slowing down)
        if (r.velocityTrend() <= -3.0) {
            findings.add(new RiskFinding("VELOCITY_TREND_DOWN", CRITICAL,
                    String.format("Velocity dropped %.1f points sprint-over-sprint.", -r.velocityTrend()),
                    "Diagnose causes in retro: blockers, attrition, dependency churn, unclear requirements."));
        } else if (r.velocityTrend() <= -1.5) {
            findings.add(new RiskFinding("VELOCITY_TREND_DOWN", WARNING,
                    String.format("Recent velocity is trending down (%.1f point decline).", -r.velocityTrend()),
                    "Watch closely. If decline continues for another sprint, hold a focused retrospective."));
        }

        // 6. Rolling velocity well below baseline
        if (r.baseVelocity() > 0 && r.velocityRollingPrev() < r.baseVelocity() * 0.7) {
            findings.add(new RiskFinding("ROLLING_VELOCITY_LOW", WARNING,
                    String.format("Rolling velocity %.1f is well below baseline %.1f.",
                            r.velocityRollingPrev(), r.baseVelocity()),
                    "Recalibrate baseline_velocity if the team's true throughput has shifted; otherwise investigate root causes."));
        }

        // 7. Carry-over from previous sprint
        if (r.prevCarryOverRate() >= 0.30) {
            findings.add(new RiskFinding("HIGH_PREV_CARRY_OVER", CRITICAL,
                    String.format("%.0f%% of last sprint's plan carried over (%.0f points).",
                            r.prevCarryOverRate() * 100, r.prevCarryOverPoints()),
                    "Triage carry-over before adding new work; consider it priority-zero for the current sprint."));
        } else if (r.prevCarryOverRate() >= 0.15) {
            findings.add(new RiskFinding("HIGH_PREV_CARRY_OVER", WARNING,
                    String.format("%.0f%% of last sprint carried over.", r.prevCarryOverRate() * 100),
                    "Investigate whether estimates were optimistic or scope grew mid-sprint."));
        }

        // 8. Previous sprint's rework score
        if (r.prevReworkScore() >= 0.30) {
            findings.add(new RiskFinding("PREV_HIGH_REWORK", WARNING,
                    String.format("Last sprint had a high rework score %.2f.", r.prevReworkScore()),
                    "Tighten Definition of Done. Surface root causes of rework in retro."));
        }

        // 9. Previous sprint's blockers
        if (r.prevBlockedTasks() >= 5) {
            findings.add(new RiskFinding("PREV_HIGH_BLOCKERS", WARNING,
                    String.format("Last sprint had %d blocked tasks.", (int) r.prevBlockedTasks()),
                    "Address the underlying dependencies before this sprint hits the same wall."));
        }

        // 10. Previous sprint's reopens
        if (r.prevReopenedTasks() >= 3) {
            findings.add(new RiskFinding("PREV_QUALITY_ISSUES", WARNING,
                    String.format("Last sprint had %d reopened tasks.", (int) r.prevReopenedTasks()),
                    "Add a peer-review checklist and/or automated tests in DoD."));
        }

        // 11. Mid-sprint scope additions
        if (r.scopeAdded() >= 5) {
            findings.add(new RiskFinding("SCOPE_CREEP", CRITICAL,
                    String.format("%.0f scope additions logged after sprint start.", r.scopeAdded()),
                    "Lock scope at planning. Route new requests to the backlog and only swap in with a removed item."));
        } else if (r.scopeAdded() >= 2) {
            findings.add(new RiskFinding("SCOPE_CREEP", WARNING,
                    String.format("%.0f scope additions mid-sprint.", r.scopeAdded()),
                    "Discuss with PO whether each addition is truly urgent or can move to the next sprint."));
        }

        // 12. Team fatigue
        if (r.fatigue() >= 0.80) {
            findings.add(new RiskFinding("TEAM_FATIGUE", CRITICAL,
                    String.format("Fatigue indicator %.2f is very high — burnout risk.", r.fatigue()),
                    "Reduce next sprint's commitment by ~20%. Encourage time off, lower WIP limits."));
        } else if (r.fatigue() >= 0.60) {
            findings.add(new RiskFinding("TEAM_FATIGUE", WARNING,
                    String.format("Fatigue indicator %.2f is elevated.", r.fatigue()),
                    "Watch for sustained overtime; consider a lighter next sprint and rotate ownership."));
        }

        // 13. Small team — limited shock absorption
        if (r.teamSize() > 0 && r.teamSize() < 3) {
            findings.add(new RiskFinding("SMALL_TEAM", INFO,
                    "Team size " + r.teamSize() + " — limited capacity to absorb absences.",
                    "Build a 10-15% buffer into the next plan; avoid overcommitting on stretch goals."));
        }

        return findings;
    }
}
