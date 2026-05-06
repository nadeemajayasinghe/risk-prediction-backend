"""Over-budget sprint risk prediction service.

Loads a trained CatBoostClassifier from model.pkl and serves predictions
on POST /predict, matching the contract used by the Spring Boot backend.
"""
from __future__ import annotations

import logging
import os
import pickle
from contextlib import asynccontextmanager
from typing import Literal

import numpy as np
import pandas as pd
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

LABELS: dict[int, str] = {0: "Low", 1: "Medium", 2: "High"}

state: dict[str, object] = {}


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


class PredictResponse(BaseModel):
    riskLabel: Literal["Low", "Medium", "High"]
    confidence: float
    probabilities: Probabilities


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not os.path.exists(MODEL_PATH):
        raise RuntimeError(f"Model file not found at {MODEL_PATH}")
    with open(MODEL_PATH, "rb") as f:
        state["model"] = pickle.load(f)
    log.info("Loaded CatBoost model from %s", MODEL_PATH)
    yield
    state.clear()


app = FastAPI(title="Over-Budget Risk Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


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

    if len(proba) != 3:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Expected 3-class output, got {len(proba)} classes",
        )

    idx = int(np.argmax(proba))
    return PredictResponse(
        riskLabel=LABELS[idx],
        confidence=round(float(proba[idx]) * 100.0, 2),
        probabilities=Probabilities(
            low=round(float(proba[0]), 4),
            medium=round(float(proba[1]), 4),
            high=round(float(proba[2]), 4),
        ),
    )
