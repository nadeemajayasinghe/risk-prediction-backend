"""Over-budget sprint risk prediction service (v2.1 — bundled multiclass + binary models).

Loads a trained CatBoost bundle that may include:

    bundle = {
        "model_multiclass":     CatBoostClassifier (3-class: Low/Medium/High),
        "model_binary":         CatBoostClassifier (binary: not-overbudget vs overbudget) [optional],
        "label_encoder":        sklearn LabelEncoder used at training time             [optional],
        "predictive_features":  ordered list of feature names                          [optional],
        "categorical_features": list of categorical feature names                       [optional],
        "class_names":          ordered string class labels                             [optional],
        "metadata":              training-time metadata                                  [optional],
    }

The multiclass model drives `class_probabilities` and SHAP explanations.
The binary model (if present) drives `probability_overbudget`; otherwise it falls
back to P(Medium) + P(High).

Loader is robust to:
- Raw model pickles, joblib, CatBoost native files
- Bundle dicts with various model-key names
- Auto-detection by API shape when no key matches
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
    "team_size",
    "base_velocity",
    "complexity",
    "hours_per_point",
    "sprint_capacity_hours",
    "planned_hours",
    "committed_story_points",
    "velocity_rolling_prev",
    "velocity_trend",
    "prev_carry_over_points",
    "prev_carry_over_rate",
    "prev_rework_score",
    "prev_blocked_tasks",
    "prev_reopened_tasks",
    "fatigue",
    "scope_added",
]
N_FEATURES = len(FEATURES)
N_CLASSES = 3

_CANONICAL_LABELS = ["Low", "Medium", "High"]

state: dict[str, Any] = {}


# ---------------------------------------------------------------------------
# Schema
# ---------------------------------------------------------------------------

class PredictRequest(BaseModel):
    team_type: str
    team_size: int
    base_velocity: float
    complexity: float
    hours_per_point: float
    sprint_capacity_hours: float
    planned_hours: float
    committed_story_points: float
    velocity_rolling_prev: float
    velocity_trend: float
    prev_carry_over_points: float
    prev_carry_over_rate: float
    prev_rework_score: float
    prev_blocked_tasks: float
    prev_reopened_tasks: float
    fatigue: float
    scope_added: float


class ClassProbabilities(BaseModel):
    High: float
    Low: float
    Medium: float


class ShapByClass(BaseModel):
    low: float
    medium: float
    high: float


class FeatureImpact(BaseModel):
    feature: str
    value: Any
    risk_contribution: float = Field(..., alias="riskContribution")
    shap_by_class: ShapByClass = Field(..., alias="shapByClass")
    direction: Literal["INCREASES_RISK", "DECREASES_RISK", "NEUTRAL"]

    class Config:
        populate_by_name = True


class PredictResponse(BaseModel):
    risk_level: Literal["Low", "Medium", "High"]
    probability_overbudget: float
    class_probabilities: ClassProbabilities
    feature_impacts: list[FeatureImpact] | None = Field(default=None, alias="featureImpacts")
    baseline_risk_score: float | None = Field(default=None, alias="baselineRiskScore")

    class Config:
        populate_by_name = True


# ---------------------------------------------------------------------------
# Bundle loader
# ---------------------------------------------------------------------------

_MODEL_MULTICLASS_KEYS = (
    "model_multiclass", "multiclass_model",
    "model", "estimator", "classifier", "clf",
    "trained_model", "catboost_model", "cb_model", "ml_model",
)
_MODEL_BINARY_KEYS = (
    "model_binary", "binary_model", "overbudget_model", "binary_classifier",
)
_FEATURES_KEYS = ("predictive_features", "features", "feature_names", "feature_list", "columns")
_CLASS_NAMES_KEYS = ("class_names", "labels")


def _is_model_like(value: Any) -> bool:
    return hasattr(value, "predict_proba") and hasattr(value, "get_feature_importance")


def _pick(dct: dict, keys, predicate=None):
    for k in keys:
        if k in dct:
            v = dct[k]
            if predicate is None or predicate(v):
                return k, v
    return None, None


def _load_raw(path: str):
    """Try pickle, joblib, CatBoost native — return the raw object."""
    errors: list[str] = []
    try:
        with open(path, "rb") as f:
            obj = pickle.load(f)
        log.info("Loaded raw object via pickle.load()")
        return obj
    except Exception as ex:
        errors.append(f"pickle.load: {type(ex).__name__}: {ex}")
    try:
        import joblib
        obj = joblib.load(path)
        log.info("Loaded raw object via joblib.load()")
        return obj
    except Exception as ex:
        errors.append(f"joblib.load: {type(ex).__name__}: {ex}")
    try:
        from catboost import CatBoostClassifier
        m = CatBoostClassifier()
        m.load_model(path)
        log.info("Loaded raw object via CatBoostClassifier.load_model()")
        return m
    except Exception as ex:
        errors.append(f"CatBoost.load_model: {type(ex).__name__}: {ex}")
    raise RuntimeError(
        "Could not load model from " + path + ". Tried:\n  - " + "\n  - ".join(errors)
    )


def _initialise_state(path: str) -> None:
    raw = _load_raw(path)

    multiclass = None
    binary = None
    label_encoder = None
    class_names = None
    bundle_features = None

    if isinstance(raw, dict):
        log.info("Loaded bundle dict with top-level keys: %s", list(raw.keys()))

        mc_key, multiclass = _pick(raw, _MODEL_MULTICLASS_KEYS, _is_model_like)
        if multiclass is None:
            for k, v in raw.items():
                if _is_model_like(v):
                    mc_key, multiclass = k, v
                    log.info("Auto-detected multiclass model under key '%s'", k)
                    break

        bin_key, binary = _pick(raw, _MODEL_BINARY_KEYS, _is_model_like)
        if binary is not None:
            log.info("Detected binary overbudget model under key '%s'", bin_key)

        label_encoder = raw.get("label_encoder")
        _, bundle_features = _pick(raw, _FEATURES_KEYS)
        _, class_names = _pick(raw, _CLASS_NAMES_KEYS)

        if mc_key:
            log.info("Using '%s' as the multiclass model", mc_key)
        if bundle_features and list(bundle_features) != FEATURES:
            log.warning(
                "Bundle features differ from service FEATURES order!\n  bundle:  %s\n  service: %s",
                list(bundle_features), FEATURES)
    else:
        multiclass = raw if _is_model_like(raw) else None

    if multiclass is None:
        raise RuntimeError("Could not locate a multiclass CatBoost model in the loaded object.")

    state["model"] = multiclass
    state["binary_model"] = binary
    state["label_encoder"] = label_encoder
    state["class_names"] = class_names
    state["class_label_order"] = _resolve_class_label_order(multiclass, class_names, label_encoder)
    log.info("Resolved class label order (model column index → label): %s", state["class_label_order"])


def _resolve_class_label_order(model, class_names, label_encoder) -> list[str]:
    """Return string class labels in the order the model's predict_proba columns are emitted."""
    raw_classes = list(model.classes_)

    # Case 1 — already strings, just normalise case
    if all(isinstance(c, str) for c in raw_classes):
        return [_normalise_label(c) for c in raw_classes]

    # Case 2 — integers, try the label encoder
    if label_encoder is not None and hasattr(label_encoder, "inverse_transform"):
        try:
            decoded = label_encoder.inverse_transform(raw_classes)
            return [_normalise_label(str(c)) for c in decoded]
        except Exception as ex:
            log.warning("label_encoder.inverse_transform failed: %s", ex)

    # Case 3 — fall back to bundle class_names indexed by integer class
    if class_names is not None:
        try:
            return [_normalise_label(str(class_names[int(c)])) for c in raw_classes]
        except Exception as ex:
            log.warning("class_names lookup failed: %s", ex)

    # Case 4 — last resort: assume canonical 0=Low, 1=Medium, 2=High
    log.warning("Falling back to canonical [Low, Medium, High] order for classes %s", raw_classes)
    return list(_CANONICAL_LABELS)


def _normalise_label(s: str) -> str:
    s = s.strip()
    low = s.lower()
    if low == "low": return "Low"
    if low == "medium": return "Medium"
    if low == "high": return "High"
    return s


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not os.path.exists(MODEL_PATH):
        raise RuntimeError(f"Model file not found at {MODEL_PATH}")
    _initialise_state(MODEL_PATH)
    log.info("Loaded CatBoost model from %s", MODEL_PATH)
    try:
        idx = list(state["model"].get_cat_feature_indices())
        log.info("Model categorical feature indices: %s", idx)
    except Exception as e:
        log.warning("Could not introspect cat feature indices: %s", e)
    yield
    state.clear()


app = FastAPI(title="Over-Budget Risk Service", version="2.1.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "binary_model_loaded": "true" if state.get("binary_model") is not None else "false",
    }


# ---------------------------------------------------------------------------
# SHAP helpers (probability-space via softmax linearisation)
# ---------------------------------------------------------------------------

def _resolve_cat_features(model) -> list[str]:
    try:
        indices = list(model.get_cat_feature_indices())
        return [FEATURES[i] for i in indices]
    except Exception:
        return ["team_type"]


def _normalise_shap_shape(shap: np.ndarray) -> np.ndarray | None:
    expected_per_class = N_FEATURES + 1
    expected_flat = N_CLASSES * expected_per_class
    if shap.ndim == 3:
        if shap.shape[0] >= 1 and shap.shape[1] == N_CLASSES and shap.shape[2] == expected_per_class:
            return shap[0]
        log.warning("SHAP 3D shape %s does not match expected", shap.shape)
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


def _reorder_to_canonical(matrix: np.ndarray, model_label_order: list[str]) -> np.ndarray | None:
    """Reorder a (N_CLASSES, N_FEATURES+1) matrix from model column order → [Low, Medium, High]."""
    try:
        idx_map = [model_label_order.index(lbl) for lbl in _CANONICAL_LABELS]
        return matrix[idx_map, :]
    except ValueError as ex:
        log.warning("Could not remap model classes %s to canonical order: %s", model_label_order, ex)
        return None


def _compute_feature_impacts(model, row: pd.DataFrame, p_canonical: np.ndarray):
    """p_canonical is in [P(Low), P(Medium), P(High)] order (already remapped)."""
    shap_raw = _compute_shap(model, row)
    if shap_raw is None:
        return None, None
    matrix = _normalise_shap_shape(shap_raw)
    if matrix is None:
        return None, None
    matrix = _reorder_to_canonical(matrix, state["class_label_order"])
    if matrix is None:
        return None, None

    shap_log = matrix[:, :N_FEATURES]
    p = np.asarray(p_canonical, dtype=float)
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
    impacts.sort(key=lambda x: abs(x.risk_contribution), reverse=True)
    return impacts, round(baseline_risk, 4)


# ---------------------------------------------------------------------------
# Endpoint
# ---------------------------------------------------------------------------

def _multiclass_proba_to_canonical(proba: np.ndarray) -> tuple[float, float, float]:
    """Map model's predict_proba columns to (P(Low), P(Medium), P(High))."""
    label_order = state["class_label_order"]
    by_label = {lbl: float(p) for lbl, p in zip(label_order, proba)}
    return (
        by_label.get("Low", 0.0),
        by_label.get("Medium", 0.0),
        by_label.get("High", 0.0),
    )


@app.post("/predict", response_model=PredictResponse, response_model_by_alias=False)
def predict(req: PredictRequest) -> PredictResponse:
    model = state.get("model")
    if model is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Model not loaded")

    row = pd.DataFrame([[getattr(req, f) for f in FEATURES]], columns=FEATURES)

    try:
        proba = model.predict_proba(row)[0]
    except Exception as exc:
        log.exception("Multiclass prediction failed")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))

    if len(proba) != N_CLASSES:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Expected {N_CLASSES}-class output, got {len(proba)} classes",
        )

    p_low, p_med, p_high = _multiclass_proba_to_canonical(proba)
    p_canonical = np.array([p_low, p_med, p_high])

    # risk_level = predicted class from multiclass model
    label_order = state["class_label_order"]
    predicted_label = label_order[int(np.argmax(proba))]
    if predicted_label not in _CANONICAL_LABELS:
        # Fallback if normalisation missed something weird
        predicted_label = _CANONICAL_LABELS[int(np.argmax(p_canonical))]

    # probability_overbudget — prefer the dedicated binary classifier if available
    binary_model = state.get("binary_model")
    if binary_model is not None:
        try:
            bin_proba = binary_model.predict_proba(row)[0]
            # Positive class is whichever index isn't 0 / "Low" / "negative"
            bin_classes = list(getattr(binary_model, "classes_", [0, 1]))
            positive_idx = _resolve_binary_positive_index(bin_classes)
            probability_overbudget = round(float(bin_proba[positive_idx]), 4)
        except Exception as ex:
            log.warning("Binary model prediction failed: %s — falling back to P(Med)+P(High)", ex)
            probability_overbudget = round(p_med + p_high, 4)
    else:
        probability_overbudget = round(p_med + p_high, 4)

    impacts, baseline = _compute_feature_impacts(model, row, p_canonical)

    return PredictResponse(
        risk_level=predicted_label,
        probability_overbudget=probability_overbudget,
        class_probabilities=ClassProbabilities(
            High=round(p_high, 4),
            Low=round(p_low, 4),
            Medium=round(p_med, 4),
        ),
        feature_impacts=impacts,
        baseline_risk_score=baseline,
    )


def _resolve_binary_positive_index(classes: list) -> int:
    """Return the column index of the positive (overbudget) class."""
    if len(classes) != 2:
        return min(1, len(classes) - 1)
    # If labels are strings, look for the over-budget one
    str_classes = [str(c).lower() for c in classes]
    for keyword in ("overbudget", "over_budget", "over-budget", "yes", "true", "positive", "1"):
        for i, c in enumerate(str_classes):
            if c == keyword or c.endswith(keyword):
                return i
    # If labels are 0/1, use 1 as positive
    try:
        ints = [int(c) for c in classes]
        return ints.index(max(ints))
    except (ValueError, TypeError):
        pass
    # Default: column 1
    return 1
