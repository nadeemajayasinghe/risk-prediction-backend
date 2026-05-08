# Communication & Collaboration Risk Service

Rule-based risk scorer + LLM-powered explanation generator. **No ML model**,
no `.pkl` to drop in — pure deterministic scoring plus an LLM call for
human-readable explanations and recommendations.

## Setup

```powershell
cd python-services\communication-collaboration
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# Optional: configure LLM (defaults to OpenAI gpt-4o-mini)
# Without an API key the service still works and uses a deterministic fallback.
$env:OPENAI_API_KEY = "sk-..."
$env:OPENAI_MODEL   = "gpt-4o-mini"   # optional; default model

uvicorn main:app --host 0.0.0.0 --port 9003
```

## Smoke test

```powershell
$body = '{
  "avg_response_time": 18,
  "comments_per_task": 1,
  "inactive_days": 5,
  "blockers": 7,
  "reopened_tasks": 3
}'
Invoke-RestMethod -Uri http://localhost:9003/predict -Method Post -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 4
```

Expected response shape (all five rules trigger here, so risk_score = 25 + 20 + 25 + 20 + 10 = 100):

```json
{
  "risk_level": "HIGH",
  "risk_score": 100,
  "detected_causes": [
    "Slow team response time (...)",
    "Low communication activity (...)",
    "Extended team inactivity (...)",
    "High number of unresolved blockers (...)",
    "Frequent task reopenings indicating unclear requirements (...)"
  ],
  "recommendations": [ "...", "...", "..." ],
  "llm_explanation": "..."
}
```

## Scoring rules

| Trigger | Points |
|---|---|
| `avg_response_time > 12` | +25 |
| `comments_per_task < 2` | +20 |
| `inactive_days > 3` | +25 |
| `blockers > 5` | +20 |
| `reopened_tasks > 2` | +10 |

Total is capped at 100. Levels: 0–29 LOW, 30–59 MEDIUM, 60–100 HIGH.

## LLM provider

Set `OPENAI_API_KEY` to use OpenAI. If no key is set (or the call fails for any
reason — timeout, rate limit, invalid response), the service automatically
falls back to a deterministic templated explanation so the API stays
available. Spring Boot integration treats the service identically either way.

## Wired into Spring Boot via

- `CC_API_URL=http://localhost:9003`
- `CC_API_PATH=/predict`
