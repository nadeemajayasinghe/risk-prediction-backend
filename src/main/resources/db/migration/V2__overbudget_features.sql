-- Sprint-level features required by the over-budget model
ALTER TABLE sprints
    ADD COLUMN IF NOT EXISTS team_type     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_size     INTEGER,
    ADD COLUMN IF NOT EXISTS complexity    DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS base_velocity DOUBLE PRECISION;

-- Per-snapshot features required by the over-budget model
ALTER TABLE sprint_metrics
    ADD COLUMN IF NOT EXISTS rework_score   DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS blocked_tasks  INTEGER,
    ADD COLUMN IF NOT EXISTS reopened_tasks INTEGER,
    ADD COLUMN IF NOT EXISTS fatigue        DOUBLE PRECISION;
