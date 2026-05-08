"""Requirement-change risk prediction service.

Loads a trained CatBoostClassifier from model.pkl and serves predictions
on POST /predict, matching the contract:

  Input: 7 numeric sprint features.
  Output: { riskLabel, confidence (0-1), probabilities {Low,Medium,High} }
          + optional SHAP-based featureImpacts and baselineRiskScore
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
log = logging.getLogger("requirement_change")

MODEL_PATH = os.getenv("MODEL_PATH", os.path.join(os.path.dirname(__file__), "model.pkl"))

FEATURES: list[str] = [
    "baseline_story_count",
    "updated_story_count",
    "story_change_ratio",
    "acceptance_criteria_changes",
    "change_requests_count",
    "comments_on_stories",
    "requirement_volatility_score",
]
N_FEATURES = len(FEATURES)
N_CLASSES = 3

LABELS: dict[int, str] = {0: "Low", 1: "Medium", 2: "High"}

state: dict[str, Any] = {}


class PredictRequest(BaseModel):
    baseline_story_count: int
    updated_story_count: int
    story_change_ratio: float
    acceptance_criteria_changes: int
    change_requests_count: int
    comments_on_stories: int
    requirement_volatility_score: float


class Probabilities(BaseModel):
    """PascalCase keys per the contract (Low/Medium/High instead of low/medium/high)."""
    Low: float
    Medium: float
    High: float


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
    confidence: float = Field(..., description="Highest class probability, in [0,1]")
    probabilities: Probabilities
    featureImpacts: list[FeatureImpact] | None = None
    baselineRiskScore: float | None = None


def _load_model(path: str):
    """Try every common save format used in ML notebooks: raw pickle, joblib, CatBoost native."""
    errors: list[str] = []

    # 1) raw pickle.dump(model, f)
    try:
        with open(path, "rb") as f:
            m = pickle.load(f)
        log.info("Loaded model via pickle.load()")
        return m
    except Exception as ex:
        errors.append(f"pickle.load: {type(ex).__name__}: {ex}")

    # 2) joblib.dump(model, path) — common in sklearn / sometimes used for CatBoost too
    try:
        import joblib
        m = joblib.load(path)
        log.info("Loaded model via joblib.load()")
        return m
    except Exception as ex:
        errors.append(f"joblib.load: {type(ex).__name__}: {ex}")

    # 3) model.save_model(path) — CatBoost's native binary format
    try:
        from catboost import CatBoostClassifier
        m = CatBoostClassifier()
        m.load_model(path)
        log.info("Loaded model via CatBoostClassifier.load_model()")
        return m
    except Exception as ex:
        errors.append(f"CatBoost.load_model: {type(ex).__name__}: {ex}")

    raise RuntimeError(
        "Could not load model from "
        + path
        + " using pickle, joblib, or CatBoost.load_model. Errors:\n  - "
        + "\n  - ".join(errors)
    )


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not os.path.exists(MODEL_PATH):
        raise RuntimeError(f"Model file not found at {MODEL_PATH}")
    state["model"] = _load_model(MODEL_PATH)
    log.info("Loaded CatBoost model from %s", MODEL_PATH)
    try:
        idx = list(state["model"].get_cat_feature_indices())
        log.info("Model categorical feature indices: %s", idx)
    except Exception as e:
        log.warning("Could not introspect cat feature indices: %s", e)
    yield
    state.clear()


app = FastAPI(title="Requirement-Change Risk Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


def _resolve_cat_features(model) -> list[str]:
    """Return categorical feature names the model was trained with (none expected for this model)."""
    try:
        indices = list(model.get_cat_feature_indices())
        return [FEATURES[i] for i in indices]
    except Exception:
        return []


def _normalise_shap_shape(shap: np.ndarray) -> np.ndarray | None:
    """Coerce CatBoost SHAP output into shape (N_CLASSES, N_FEATURES + 1)."""
    expected_per_class = N_FEATURES + 1
    expected_flat = N_CLASSES * expected_per_class

    if shap.ndim == 3:
        if shap.shape[0] >= 1 and shap.shape[1] == N_CLASSES and shap.shape[2] == expected_per_class:
            return shap[0]
        log.warning("SHAP 3D shape %s does not match expected (n,%d,%d)", shap.shape, N_CLASSES, expected_per_class)
        return None

    if shap.ndim == 2:
        if shap.shape[0] >= 1 and shap.shape[1] == expected_flat:
            return shap[0].reshape(N_CLASSES, expected_per_class)
        log.warning("SHAP 2D shape %s — unable to interpret", shap.shape)
        return None

    log.warning("SHAP unexpected ndim=%d shape=%s", shap.ndim, shap.shape)
    return None


def _compute_shap(model, row: pd.DataFrame) -> np.ndarray | None:
    cat_names = _resolve_cat_features(model)
    pool = Pool(data=row, cat_features=cat_names if cat_names else None)
    try:
        shap = model.get_feature_importance(pool, type="ShapValues")
        log.info("SHAP computed, shape=%s", getattr(shap, "shape", None))
        return shap
    except Exception as ex:
        log.warning("SHAP computation failed: %s", ex)
        return None


def _compute_feature_impacts(model, row: pd.DataFrame, proba: np.ndarray) -> tuple[list[FeatureImpact] | None, float | None]:
    """Convert CatBoost log-odds SHAP into probability-space risk contributions
    via softmax linearisation, matching the over-budget service.

        dP[c] / dz[c'] = P[c] * (delta_{c,c'} - P[c'])
        risk_score = 50*P(Medium) + 100*P(High)
    """
    shap_raw = _compute_shap(model, row)
    if shap_raw is None:
        return None, None

    matrix = _normalise_shap_shape(shap_raw)
    if matrix is None:
        return None, None

    shap_log = matrix[:, :N_FEATURES]
    p = np.asarray(proba, dtype=float)
    weighted_mean = (p[:, None] * shap_log).sum(axis=0)
    delta_p = p[:, None] * (shap_log - weighted_mean[None, :])

    risk_contributions = 50.0 * delta_p[1, :] + 100.0 * delta_p[2, :]
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
    confidence = round(float(proba[idx]), 4)  # 0-1 per the contract
    impacts, baseline = _compute_feature_impacts(model, row, proba)

    return PredictResponse(
        riskLabel=LABELS[idx],
        confidence=confidence,
        probabilities=Probabilities(
            Low=round(float(proba[0]), 4),
            Medium=round(float(proba[1]), 4),
            High=round(float(proba[2]), 4),
        ),
        featureImpacts=impacts,
        baselineRiskScore=baseline,
    )
