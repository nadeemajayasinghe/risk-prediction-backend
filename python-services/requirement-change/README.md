# Requirement-Change Risk Service

FastAPI wrapper around your trained CatBoost classifier for requirement-change risk.
Mirrors the over-budget service: same SHAP-based feature attribution, same softmax
linearisation, same response shape — only the input feature schema differs.

## Setup

```powershell
cd python-services\requirement-change
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# Drop your trained model here (filename must be model.pkl, or set MODEL_PATH env var)
copy "<full path to>\requirement_change_catboost.pkl" model.pkl

uvicorn main:app --host 0.0.0.0 --port 9002
```

## Smoke test

```powershell
$body = '{
  "baseline_story_count": 12,
  "updated_story_count": 4,
  "story_change_ratio": 0.33,
  "acceptance_criteria_changes": 3,
  "change_requests_count": 5,
  "comments_on_stories": 18,
  "requirement_volatility_score": 0.42
}'
Invoke-RestMethod -Uri http://localhost:9002/predict -Method Post -ContentType 'application/json' -Body $body
```

Expected response (PascalCase keys for `probabilities` per the contract; `confidence` in [0,1]):

```json
{
  "riskLabel": "Medium",
  "confidence": 0.71,
  "probabilities": { "Low": 0.18, "Medium": 0.71, "High": 0.11 },
  "featureImpacts": [ ... ],
  "baselineRiskScore": 24.5
}
```

## Notes

- Spring Boot calls this via `REQCHANGE_API_URL=http://localhost:9002` and `REQCHANGE_API_PATH=/predict`.
- The 7 features must be sent in any order — Pydantic validates by name.
- Restart the service to load a new pickle.
