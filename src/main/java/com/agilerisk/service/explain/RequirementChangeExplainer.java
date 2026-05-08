package com.agilerisk.service.explain;

import com.agilerisk.dto.ai.RequirementChangeModelRequest;
import com.agilerisk.dto.response.RiskFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based explainer for the requirement-change risk model.
 * <p>
 * Looks at the same 7 features the model received and emits human-readable
 * findings (reason + suggestion). Tunable thresholds.
 */
@Service
public class RequirementChangeExplainer {

    private static final String INFO     = "INFO";
    private static final String WARNING  = "WARNING";
    private static final String CRITICAL = "CRITICAL";

    public List<RiskFinding> explain(RequirementChangeModelRequest r) {
        List<RiskFinding> findings = new ArrayList<>();

        // 1. Story change ratio — how many baseline stories were modified
        if (r.storyChangeRatio() >= 0.50) {
            findings.add(new RiskFinding("HIGH_STORY_CHURN", CRITICAL,
                    String.format("%.0f%% of baseline stories were modified after sprint start.", r.storyChangeRatio() * 100),
                    "Lock the sprint backlog after planning. Route new edits to the next sprint unless they are blockers."));
        } else if (r.storyChangeRatio() >= 0.25) {
            findings.add(new RiskFinding("HIGH_STORY_CHURN", WARNING,
                    String.format("%.0f%% of baseline stories were modified.", r.storyChangeRatio() * 100),
                    "Review with the PO whether mid-sprint edits were truly necessary; capture in retro."));
        }

        // 2. Acceptance criteria changes
        if (r.acceptanceCriteriaChanges() >= 5) {
            findings.add(new RiskFinding("AC_INSTABILITY", CRITICAL,
                    r.acceptanceCriteriaChanges() + " acceptance-criteria edits — definitions of done shifting under the team.",
                    "Tighten the 'definition of ready' gate at refinement: don't accept stories without stable acceptance criteria."));
        } else if (r.acceptanceCriteriaChanges() >= 2) {
            findings.add(new RiskFinding("AC_INSTABILITY", WARNING,
                    r.acceptanceCriteriaChanges() + " acceptance-criteria changes mid-sprint.",
                    "Add an explicit AC review step in refinement; involve QA earlier in story shaping."));
        }

        // 3. Change requests
        if (r.changeRequestsCount() >= 8) {
            findings.add(new RiskFinding("HIGH_CHANGE_REQUESTS", CRITICAL,
                    r.changeRequestsCount() + " change requests logged in this sprint.",
                    "Establish a change-control board for in-flight sprints; defer non-urgent CRs to backlog grooming."));
        } else if (r.changeRequestsCount() >= 4) {
            findings.add(new RiskFinding("HIGH_CHANGE_REQUESTS", WARNING,
                    r.changeRequestsCount() + " change requests this sprint.",
                    "Triage which requests are sprint-critical vs. backlog candidates with the PO."));
        }

        // 4. Comments-on-stories — surrogate for confusion / discussion
        if (r.baselineStoryCount() > 0) {
            double commentsPerStory = r.commentsOnStories() / (double) r.baselineStoryCount();
            if (commentsPerStory >= 4.0) {
                findings.add(new RiskFinding("HIGH_DISCUSSION", WARNING,
                        String.format("Average %.1f comments per story — likely ambiguity or open questions.", commentsPerStory),
                        "Schedule a backlog refinement session focused on the most-commented stories; resolve questions in writing."));
            }
        }

        // 5. Volatility score
        if (r.requirementVolatilityScore() >= 0.60) {
            findings.add(new RiskFinding("HIGH_VOLATILITY", CRITICAL,
                    String.format("Requirement volatility %.2f is high — expectations are shifting fast.", r.requirementVolatilityScore()),
                    "Pause new requirement intake and run a stakeholder alignment session before the next sprint."));
        } else if (r.requirementVolatilityScore() >= 0.40) {
            findings.add(new RiskFinding("HIGH_VOLATILITY", WARNING,
                    String.format("Requirement volatility %.2f is elevated.", r.requirementVolatilityScore()),
                    "Track volatility week over week; if it doesn't drop, escalate to PM/PO leadership."));
        }

        // 6. Tiny baseline — small samples make any change look big
        if (r.baselineStoryCount() > 0 && r.baselineStoryCount() <= 3) {
            findings.add(new RiskFinding("THIN_BASELINE", INFO,
                    "Only " + r.baselineStoryCount() + " baseline stories — sprint is small and any edit has outsized impact.",
                    "Consider grouping with another sprint for trend analysis, or raise minimum sprint commitment."));
        }

        // 7. Story growth (more stories than baseline = scope expanded mid-sprint)
        if (r.baselineStoryCount() > 0 && r.updatedStoryCount() > r.baselineStoryCount()) {
            int delta = r.updatedStoryCount() - r.baselineStoryCount();
            findings.add(new RiskFinding("SCOPE_GROWTH", WARNING,
                    delta + " net new stories added since the baseline.",
                    "Confirm with PO that new stories are critical; otherwise move to the next sprint."));
        }

        return findings;
    }
}
