"""Over-budget sprint risk prediction service.

Loads a trained CatBoostClassifier from model.pkl and serves predictions
on POST /predict, matching the contract used by the Spring Boot backend.

Also returns SHAP-based per-feature contributions so the backend / dashboard
can show *why* the model made the prediction it did.
"""
from __future__ import annotations

import logging
import os
import pickle
from contextlib import asynccontextmanager
from typing import Any, Literal

import numpy as np
import pandas as pd
from catboost import Pool
from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("overbudget")

MODEL_PATH = os.getenv("MODEL_PATH", os.path.join(os.path.dirname(__file__), "model.pkl"))

FEATURES: list[str] = [
    "team_type",
    "base_velocity",
    "team_size",
    "complexity",
    "remaining_work",
    "velocity",
    "completed_story_points",
    "effort_deviation",
    "rework_score",
    "blocked_tasks",
    "reopened_tasks",
    "scope_added",
    "fatigue",
]
N_FEATURES = len(FEATURES)
N_CLASSES = 3  # Low / Medium / High

LABELS: dict[int, str] = {0: "Low", 1: "Medium", 2: "High"}

state: dict[str, Any] = {}


class PredictRequest(BaseModel):
    team_type: str = Field(..., description="Categorical team identifier handled natively by CatBoost")
    base_velocity: float
    team_size: int
    complexity: float
    remaining_work: float
    velocity: float
    completed_story_points: float
    effort_deviation: float
    rework_score: float
    blocked_tasks: int
    reopened_tasks: int
    scope_added: float
    fatigue: float


class Probabilities(BaseModel):
    low: float
    medium: float
    high: float


class ShapByClass(BaseModel):
    low: float
    medium: float
    high: float


class FeatureImpact(BaseModel):
    feature: str
    value: Any
    riskContribution: float
    shapByClass: ShapByClass
    direction: Literal["INCREASES_RISK", "DECREASES_RISK", "NEUTRAL"]


class PredictResponse(BaseModel):
    riskLabel: Literal["Low", "Medium", "High"]
    confidence: float
    probabilities: Probabilities
    featureImpacts: list[FeatureImpact] | None = None
    baselineRiskScore: float | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not os.path.exists(MODEL_PATH):
        raise RuntimeError(f"Model file not found at {MODEL_PATH}")
    with open(MODEL_PATH, "rb") as f:
        state["model"] = pickle.load(f)
    log.info("Loaded CatBoost model from %s", MODEL_PATH)
    try:
        idx = list(state["model"].get_cat_feature_indices())
        log.info("Model categorical feature indices: %s", idx)
    except Exception as e:
        log.warning("Could not introspect cat feature indices: %s", e)
    yield
    state.clear()


app = FastAPI(title="Over-Budget Risk Service", version="1.2.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


def _resolve_cat_features(model) -> list[str]:
    """Return the categorical feature names the model was trained with."""
    try:
        indices = list(model.get_cat_feature_indices())
        return [FEATURES[i] for i in indices]
    except Exception:
        return ["team_type"]  # we know team_type is the only categorical


def _normalise_shap_shape(shap: np.ndarray) -> np.ndarray | None:
    """Coerce CatBoost's SHAP output into shape (n_classes, n_features + 1).

    Different CatBoost versions / build types return:
      A) 3D (n_samples, n_classes, n_features + 1) — modern multiclass
      B) 2D flat (n_samples, n_classes * (n_features + 1)) — older multiclass
      C) 2D (n_samples, n_features + 1) — binary  (we don't expect this)
    """
    expected_per_class = N_FEATURES + 1
    expected_flat = N_CLASSES * expected_per_class

    if shap.ndim == 3:
        if shap.shape[0] >= 1 and shap.shape[1] == N_CLASSES and shap.shape[2] == expected_per_class:
            return shap[0]  # (n_classes, n_features + 1)
        log.warning("SHAP 3D shape %s does not match expected (n,%d,%d)",
                    shap.shape, N_CLASSES, expected_per_class)
        return None

    if shap.ndim == 2:
        if shap.shape[0] >= 1 and shap.shape[1] == expected_flat:
            return shap[0].reshape(N_CLASSES, expected_per_class)
        if shap.shape[0] >= 1 and shap.shape[1] == expected_per_class:
            log.warning("SHAP returned single-class shape %s — model may be binary, can't compute multi-class impacts", shap.shape)
            return None
        log.warning("SHAP 2D shape %s — unable to interpret", shap.shape)
        return None

    log.warning("SHAP unexpected ndim=%d shape=%s", shap.ndim, shap.shape)
    return None


def _compute_shap(model, row: pd.DataFrame) -> np.ndarray | None:
    """Run model.get_feature_importance() with several fallbacks across CatBoost versions."""
    cat_names = _resolve_cat_features(model)
    pool = Pool(data=row, cat_features=cat_names if cat_names else None)

    attempts = [
        {"type": "ShapValues"},  # CatBoost 1.x returns raw (log-odds) SHAP values
    ]
    last_err: Exception | None = None
    for kwargs in attempts:
        try:
            shap = model.get_feature_importance(pool, **kwargs)
            log.info("SHAP computed with kwargs=%s, shape=%s", kwargs, getattr(shap, "shape", None))
            return shap
        except Exception as ex:
            last_err = ex
            log.warning("SHAP attempt with kwargs=%s failed: %s", kwargs, ex)
    if last_err is not None:
        log.warning("All SHAP attempts failed; last error: %s", last_err)
    return None


def _compute_feature_impacts(model, row: pd.DataFrame, proba: np.ndarray) -> tuple[list[FeatureImpact] | None, float | None]:
    """Compute SHAP values and translate them into per-feature contributions on the 0-100 risk scale.

    CatBoost returns SHAP values in raw (log-odds) space. We convert each feature's per-class
    log-odds contribution into a probability-space contribution using the softmax linearisation:

        dP[c] / dz[c'] = P[c] * (delta_{c,c'} - P[c'])

    Then the risk score contribution per feature i is:

        50 * delta_P[Medium, i] + 100 * delta_P[High, i]

    matching the Java aggregator's risk score formula.
    """
    shap_raw = _compute_shap(model, row)
    if shap_raw is None:
        return None, None

    matrix = _normalise_shap_shape(shap_raw)
    if matrix is None:
        return None, None

    # matrix shape: (N_CLASSES, N_FEATURES + 1) — last column is bias (log-odds baseline)
    shap_log = matrix[:, :N_FEATURES]   # (N_CLASSES, N_FEATURES)
    bias_log = matrix[:, N_FEATURES]    # (N_CLASSES,)

    # Convert log-odds SHAP -> probability-space SHAP via softmax linearisation
    p = np.asarray(proba, dtype=float)  # (N_CLASSES,)
    weighted_mean = (p[:, None] * shap_log).sum(axis=0)              # (N_FEATURES,)
    delta_p = p[:, None] * (shap_log - weighted_mean[None, :])       # (N_CLASSES, N_FEATURES)

    # Per-feature risk-score contribution (matches OverBudgetRiskClient.computeRiskScore)
    risk_contributions = 50.0 * delta_p[1, :] + 100.0 * delta_p[2, :]

    # Baseline risk score = current_risk_score - sum of contributions
    current_risk = 50.0 * float(p[1]) + 100.0 * float(p[2])
    baseline_risk = current_risk - float(risk_contributions.sum())

    impacts: list[FeatureImpact] = []
    for i, name in enumerate(FEATURES):
        raw_val: Any = row.iloc[0, i]
        if hasattr(raw_val, "item"):
            raw_val = raw_val.item()
        rc = float(risk_contributions[i])
        if abs(rc) < 1e-4:
            direction = "NEUTRAL"
        elif rc > 0:
            direction = "INCREASES_RISK"
        else:
            direction = "DECREASES_RISK"
        impacts.append(FeatureImpact(
            feature=name,
            value=raw_val,
            riskContribution=round(rc, 4),
            shapByClass=ShapByClass(
                # Expose raw log-odds SHAP per class — researchers expect this when citing SHAP.
                low=round(float(shap_log[0, i]), 4),
                medium=round(float(shap_log[1, i]), 4),
                high=round(float(shap_log[2, i]), 4),
            ),
            direction=direction,
        ))

    impacts.sort(key=lambda x: abs(x.riskContribution), reverse=True)
    return impacts, round(baseline_risk, 4)


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> PredictResponse:
    model = state.get("model")
    if model is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Model not loaded")

    row = pd.DataFrame([[getattr(req, f) for f in FEATURES]], columns=FEATURES)

    try:
        proba = model.predict_proba(row)[0]
    except Exception as exc:
        log.exception("Prediction failed")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))

    if len(proba) != N_CLASSES:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Expected {N_CLASSES}-class output, got {len(proba)} classes",
        )

    idx = int(np.argmax(proba))
    impacts, baseline = _compute_feature_impacts(model, row, proba)

    return PredictResponse(
        riskLabel=LABELS[idx],
        confidence=round(float(proba[idx]) * 100.0, 2),
        probabilities=Probabilities(
            low=round(float(proba[0]), 4),
            medium=round(float(proba[1]), 4),
            high=round(float(proba[2]), 4),
        ),
        featureImpacts=impacts,
        baselineRiskScore=baseline,
    )
