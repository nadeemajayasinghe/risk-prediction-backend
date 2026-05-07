package com.agilerisk.service.explain;

import com.agilerisk.dto.ai.OverBudgetModelRequest;
import com.agilerisk.dto.response.RiskFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based explainer for the over-budget risk model.
 * <p>
 * Looks at the same 13 features the model received and emits human-readable
 * findings (reason + suggestion). Thresholds are intentionally conservative
 * — tune them once you've calibrated against your historical sprints.
 */
@Service
public class OverBudgetExplainer {

    private static final String INFO     = "INFO";
    private static final String WARNING  = "WARNING";
    private static final String CRITICAL = "CRITICAL";

    public List<RiskFinding> explain(OverBudgetModelRequest r) {
        List<RiskFinding> findings = new ArrayList<>();

        // 1. Effort deviation (planned vs. actual hours)
        if (r.effortDeviation() >= 0.30) {
            findings.add(new RiskFinding("EFFORT_OVERRUN", CRITICAL,
                    String.format("Actual effort is %.0f%% above plan.", r.effortDeviation() * 100),
                    "Re-baseline estimates with the team. Inspect tasks where actual hours far exceed estimates and capture the cause in the retro."));
        } else if (r.effortDeviation() >= 0.15) {
            findings.add(new RiskFinding("EFFORT_OVERRUN", WARNING,
                    String.format("Actual effort is %.0f%% above plan.", r.effortDeviation() * 100),
                    "Hold a quick estimation review; check whether scope expanded mid-sprint or estimates were optimistic."));
        }

        // 2. Velocity below baseline
        if (r.baseVelocity() > 0) {
            double drop = 1.0 - (r.velocity() / r.baseVelocity());
            if (drop >= 0.30) {
                findings.add(new RiskFinding("VELOCITY_DROP", CRITICAL,
                        String.format("Velocity %.1f is %.0f%% below the team's baseline (%.1f).",
                                r.velocity(), drop * 100, r.baseVelocity()),
                        "Investigate slowdowns immediately: blockers, sick leave, dependency churn, or unclear requirements."));
            } else if (drop >= 0.15) {
                findings.add(new RiskFinding("VELOCITY_DROP", WARNING,
                        String.format("Velocity %.1f trending below baseline %.1f.", r.velocity(), r.baseVelocity()),
                        "Watch closely; if it continues, descope optional stories before sprint review."));
            }
        }

        // 3. Insufficient remaining capacity
        if (r.velocity() > 0 && r.remainingWork() > r.velocity() * 1.5) {
            findings.add(new RiskFinding("INSUFFICIENT_CAPACITY", CRITICAL,
                    String.format("Remaining work (%.0f points) exceeds projected capacity (%.1f points).",
                            r.remainingWork(), r.velocity()),
                    "Descope optional stories, split work into smaller increments, or extend the sprint."));
        }

        // 4. Blocked tasks
        if (r.blockedTasks() >= 5) {
            findings.add(new RiskFinding("BLOCKED_TASKS", CRITICAL,
                    r.blockedTasks() + " tasks are currently blocked.",
                    "Run a blocker-removal session today. Escalate cross-team dependencies to the lead."));
        } else if (r.blockedTasks() >= 3) {
            findings.add(new RiskFinding("BLOCKED_TASKS", WARNING,
                    r.blockedTasks() + " tasks are currently blocked.",
                    "Surface blockers in the next standup; pair the blocked owner with someone who can unstick it."));
        }

        // 5. Reopened tasks (quality signal)
        if (r.reopenedTasks() >= 3) {
            findings.add(new RiskFinding("QUALITY_ISSUES", CRITICAL,
                    r.reopenedTasks() + " tasks were reopened — repeated rejection from review or QA.",
                    "Tighten Definition of Done. Add a peer-review checklist and require automated tests before marking work complete."));
        } else if (r.reopenedTasks() >= 1) {
            findings.add(new RiskFinding("QUALITY_ISSUES", WARNING,
                    r.reopenedTasks() + " tasks reopened — possible quality signal.",
                    "Hold a 15-minute design review for upcoming work; ensure acceptance criteria are explicit."));
        }

        // 6. Rework score
        if (r.reworkScore() >= 0.30) {
            findings.add(new RiskFinding("HIGH_REWORK", CRITICAL,
                    String.format("Rework score %.2f is high — significant unplanned redo.", r.reworkScore()),
                    "Identify the top reworked stories in retro and capture root cause: ambiguous requirements, missing tests, or design churn."));
        } else if (r.reworkScore() >= 0.15) {
            findings.add(new RiskFinding("HIGH_REWORK", WARNING,
                    String.format("Rework score %.2f indicates noticeable redo.", r.reworkScore()),
                    "Sharpen acceptance criteria during refinement; add a 'definition of ready' gate."));
        }

        // 7. Mid-sprint scope additions
        if (r.scopeAdded() >= 5) {
            findings.add(new RiskFinding("SCOPE_CREEP", CRITICAL,
                    String.format("%.0f scope additions logged after sprint start.", r.scopeAdded()),
                    "Lock scope at planning. Route new requests to the backlog and only pull them into the current sprint with a removed item."));
        } else if (r.scopeAdded() >= 2) {
            findings.add(new RiskFinding("SCOPE_CREEP", WARNING,
                    String.format("%.0f scope additions mid-sprint.", r.scopeAdded()),
                    "Discuss with PO whether each addition is truly urgent or can move to the next sprint."));
        }

        // 8. Team fatigue
        if (r.fatigue() >= 0.80) {
            findings.add(new RiskFinding("TEAM_FATIGUE", CRITICAL,
                    String.format("Team fatigue indicator %.2f is very high — burnout risk.", r.fatigue()),
                    "Reduce next sprint's commitment by ~20%. Encourage time off, lower work-in-progress limits, and shield the team from new asks."));
        } else if (r.fatigue() >= 0.60) {
            findings.add(new RiskFinding("TEAM_FATIGUE", WARNING,
                    String.format("Team fatigue indicator %.2f is elevated.", r.fatigue()),
                    "Watch for sustained overtime; consider a lighter next sprint and rotate ownership of high-effort tasks."));
        }

        // 9. High complexity
        if (r.complexity() >= 4.0) {
            findings.add(new RiskFinding("HIGH_COMPLEXITY", WARNING,
                    String.format("Sprint complexity %.1f is on the high end.", r.complexity()),
                    "Break large stories into thinner vertical slices. Run spikes on unknowns before committing implementation."));
        }

        // 10. Small team — limited shock absorption
        if (r.teamSize() > 0 && r.teamSize() < 3) {
            findings.add(new RiskFinding("SMALL_TEAM", INFO,
                    "Team size " + r.teamSize() + " — limited capacity to absorb absences.",
                    "Build a 10-15% buffer into the next plan; avoid overcommitting on stretch goals."));
        }

        // 11. Low completion rate vs. plan
        double total = r.completedStoryPoints() + r.remainingWork();
        if (total > 0) {
            double rate = r.completedStoryPoints() / total;
            if (rate < 0.40) {
                findings.add(new RiskFinding("LOW_COMPLETION", CRITICAL,
                        String.format("Only %.0f%% of total work completed so far.", rate * 100),
                        "Triage the remaining stories. Identify the smallest set that delivers the sprint goal and defer the rest."));
            }
        }

        return findings;
    }
}
