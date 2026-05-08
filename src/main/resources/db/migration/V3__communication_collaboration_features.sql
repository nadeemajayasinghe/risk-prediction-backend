-- Per-sprint signals required by the Communication & Collaboration risk module.
-- Stored on sprint_metrics so they snapshot over time alongside other observed metrics.
ALTER TABLE sprint_metrics
    ADD COLUMN IF NOT EXISTS avg_response_time_hours INTEGER,
    ADD COLUMN IF NOT EXISTS inactive_days           INTEGER;

-- Aggregated result needs to retain the CC score so /risk-summary and /trend can return it.
ALTER TABLE aggregated_risk_results
    ADD COLUMN IF NOT EXISTS communication_collaboration_score DOUBLE PRECISION;
