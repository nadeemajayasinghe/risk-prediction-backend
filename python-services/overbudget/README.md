# Over-Budget Risk Service

FastAPI wrapper around your trained CatBoost classifier.

## Setup

```powershell
cd python-services\overbudget
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# Drop your trained model here (filename must be model.pkl, or set MODEL_PATH env var)
copy <your-pickle-file> model.pkl

uvicorn main:app --host 0.0.0.0 --port 9001
```

## Smoke test

```powershell
$body = '{
  "team_type": "agile",
  "base_velocity": 35.0,
  "team_size": 7,
  "complexity": 3.2,
  "remaining_work": 18,
  "velocity": 28.0,
  "completed_story_points": 22,
  "effort_deviation": 0.18,
  "rework_score": 0.12,
  "blocked_tasks": 2,
  "reopened_tasks": 1,
  "scope_added": 5,
  "fatigue": 0.4
}'
Invoke-RestMethod -Uri http://localhost:9001/predict -Method Post -ContentType 'application/json' -Body $body
```

Expected response shape:

```json
{
  "riskLabel": "Medium",
  "confidence": 67.4,
  "probabilities": { "low": 0.12, "medium": 0.674, "high": 0.206 }
}
```

## Notes

- Pickle is loaded once on app startup. Restart the service to load a new model.
- `team_type` is passed straight to CatBoost — make sure the model was trained with `cat_features=["team_type"]`.
- Spring Boot calls this via `OVERBUDGET_API_URL=http://localhost:9001` and `OVERBUDGET_API_PATH=/predict`.
