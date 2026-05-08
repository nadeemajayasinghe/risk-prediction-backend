"""Low Communication / Collaboration Risk service.

Rule-based scoring engine + LLM integration for cause-explanation and
mitigation recommendations. No ML model — fully deterministic risk score
plus an explainable AI layer powered by an LLM.

Pipeline:
  1. risk_scoring_engine(req)      → (score, [causes])
  2. classify_risk(score)          → "LOW" | "MEDIUM" | "HIGH"
  3. generate_llm_prompt(...)       → string
  4. call_llm_api(prompt)           → (explanation, [recommendations])

Provider: OpenAI by default. Falls back to a deterministic templated response
when no API key is configured or the LLM call fails — service stays available.
"""
from __future__ import annotations

import json
import logging
import os
from typing import Literal

from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("comm_collab")

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
LLM_TIMEOUT_S = float(os.getenv("LLM_TIMEOUT_S", "30"))


# ---------------------------------------------------------------------------
# Schema
# ---------------------------------------------------------------------------

class PredictRequest(BaseModel):
    avg_response_time: int = Field(..., ge=0, description="Average response time in hours")
    comments_per_task: int = Field(..., ge=0)
    inactive_days: int = Field(..., ge=0)
    blockers: int = Field(..., ge=0)
    reopened_tasks: int = Field(..., ge=0)


class PredictResponse(BaseModel):
    risk_level: Literal["LOW", "MEDIUM", "HIGH"]
    risk_score: int = Field(..., ge=0, le=100)
    detected_causes: list[str]
    recommendations: list[str]
    llm_explanation: str


# ---------------------------------------------------------------------------
# Step 1: Rule-based scoring engine (deterministic, transparent)
# ---------------------------------------------------------------------------

RULES = [
    # (predicate, points, cause_template)
    (lambda r: r.avg_response_time > 12, 25,
     "Slow team response time (avg {0}h, threshold 12h)"),
    (lambda r: r.comments_per_task < 2, 20,
     "Low communication activity ({0} comments per task, threshold 2)"),
    (lambda r: r.inactive_days > 3, 25,
     "Extended team inactivity ({0} days, threshold 3)"),
    (lambda r: r.blockers > 5, 20,
     "High number of unresolved blockers ({0} active, threshold 5)"),
    (lambda r: r.reopened_tasks > 2, 10,
     "Frequent task reopenings indicating unclear requirements ({0} reopened, threshold 2)"),
]
RULE_VALUE_PICKERS = [
    lambda r: r.avg_response_time,
    lambda r: r.comments_per_task,
    lambda r: r.inactive_days,
    lambda r: r.blockers,
    lambda r: r.reopened_tasks,
]


def risk_scoring_engine(req: PredictRequest) -> tuple[int, list[str]]:
    score = 0
    causes: list[str] = []
    for (predicate, points, cause_template), value_picker in zip(RULES, RULE_VALUE_PICKERS):
        if predicate(req):
            score += points
            causes.append(cause_template.format(value_picker(req)))
    score = max(0, min(100, score))
    return score, causes


# ---------------------------------------------------------------------------
# Step 2: Risk classification
# ---------------------------------------------------------------------------

def classify_risk(score: int) -> str:
    if score >= 60:
        return "HIGH"
    if score >= 30:
        return "MEDIUM"
    return "LOW"


# ---------------------------------------------------------------------------
# Step 3: LLM prompt builder
# ---------------------------------------------------------------------------

def generate_llm_prompt(level: str, causes: list[str], req: PredictRequest) -> str:
    causes_block = "\n".join(f"- {c}" for c in causes) if causes else "- (no rule triggered)"
    return f"""You are an Agile project management assistant.

Analyze the following sprint collaboration data:

Risk Level: {level}

Detected Issues:
{causes_block}

Sprint Metrics:
- Average Response Time: {req.avg_response_time} hours
- Comments per Task: {req.comments_per_task}
- Inactive Days: {req.inactive_days}
- Blockers: {req.blockers}
- Reopened Tasks: {req.reopened_tasks}

TASKS:
1. Explain why this sprint has collaboration risk in simple, clear terms.
2. Provide actionable solutions to fix the collaboration issues.
3. Suggest preventive practices for future sprints.
4. Ensure recommendations are practical for Scrum Masters and development teams.

RESPOND WITH VALID JSON ONLY in this exact shape:
{{
  "explanation": "<one short paragraph in plain English>",
  "recommendations": ["<actionable item>", "<actionable item>", ...]
}}
Do not include any markdown, prefatory text, or trailing commentary outside the JSON object.
"""


# ---------------------------------------------------------------------------
# Step 4: LLM call (OpenAI primary, deterministic fallback if unavailable)
# ---------------------------------------------------------------------------

def _templated_fallback(level: str, causes: list[str]) -> tuple[str, list[str]]:
    if not causes:
        return (
            "No collaboration risk was detected for this sprint. "
            "All measured collaboration signals are within healthy thresholds.",
            [],
        )
    explanation = (
        f"Sprint collaboration risk has been classified as {level}. "
        "The flagged signals indicate the team may be struggling with one or more of: slow "
        "response cycles, low discussion density, periods of inactivity, unresolved blockers, "
        "or repeated rework. Left unaddressed, these patterns typically lead to scope slippage "
        "and lower delivery confidence."
    )
    recommendations = [
        "Hold a short focused stand-up to surface and assign owners to all open blockers.",
        "Set a team SLA for code-review and message response (e.g. respond within 4 working hours).",
        "Encourage richer task discussion — leave at least 2 substantive comments per non-trivial story.",
        "Create a 'definition of ready' gate so reopened work decreases sprint over sprint.",
        "Configure inactivity alerts on tasks idle for more than 2 days.",
    ]
    return explanation, recommendations


def call_llm_api(prompt: str, level: str, causes: list[str]) -> tuple[str, list[str]]:
    if not OPENAI_API_KEY:
        log.info("OPENAI_API_KEY not set — using templated fallback")
        return _templated_fallback(level, causes)
    try:
        from openai import OpenAI
        client = OpenAI(api_key=OPENAI_API_KEY, timeout=LLM_TIMEOUT_S)
        completion = client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=[
                {"role": "system", "content": "You return strict JSON only. No prose outside the JSON."},
                {"role": "user", "content": prompt},
            ],
            response_format={"type": "json_object"},
            temperature=0.2,
        )
        content = completion.choices[0].message.content or "{}"
        data = json.loads(content)
        explanation = (data.get("explanation") or "").strip()
        recs_raw = data.get("recommendations") or []
        recommendations = [str(r).strip() for r in recs_raw if str(r).strip()]
        if not explanation:
            log.warning("LLM returned empty explanation — using fallback")
            return _templated_fallback(level, causes)
        return explanation, recommendations
    except Exception as ex:
        log.warning("LLM call failed (%s: %s) — using templated fallback", type(ex).__name__, ex)
        return _templated_fallback(level, causes)


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="Communication & Collaboration Risk Service", version="1.0.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "llm_configured": "true" if OPENAI_API_KEY else "false"}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> PredictResponse:
    try:
        score, causes = risk_scoring_engine(req)
        level = classify_risk(score)
        prompt = generate_llm_prompt(level, causes, req)
        explanation, recommendations = call_llm_api(prompt, level, causes)
        return PredictResponse(
            risk_level=level,
            risk_score=score,
            detected_causes=causes,
            recommendations=recommendations,
            llm_explanation=explanation,
        )
    except Exception as exc:
        log.exception("Unexpected error in /predict")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))
