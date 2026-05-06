CREATE TABLE sprints (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    goal            TEXT,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(32) NOT NULL,
    team_id         VARCHAR(64),
    capacity_points INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_sprints_status ON sprints (status);
CREATE INDEX idx_sprints_team   ON sprints (team_id);

CREATE TABLE sprint_metrics (
    id                    BIGSERIAL PRIMARY KEY,
    sprint_id             BIGINT NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    planned_points        INTEGER,
    completed_points      INTEGER,
    effort_deviation      DOUBLE PRECISION,
    bugs_count            INTEGER,
    scope_changes_count   INTEGER,
    velocity              DOUBLE PRECISION,
    recorded_at           TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_metrics_sprint ON sprint_metrics (sprint_id, recorded_at DESC);

CREATE TABLE user_stories (
    id           BIGSERIAL PRIMARY KEY,
    sprint_id    BIGINT NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    external_key VARCHAR(64),
    title        VARCHAR(300) NOT NULL,
    description  TEXT,
    story_points INTEGER,
    priority     VARCHAR(16),
    status       VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_stories_sprint ON user_stories (sprint_id);

CREATE TABLE tasks (
    id              BIGSERIAL PRIMARY KEY,
    story_id        BIGINT NOT NULL REFERENCES user_stories (id) ON DELETE CASCADE,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    estimated_hours DOUBLE PRECISION,
    actual_hours    DOUBLE PRECISION,
    status          VARCHAR(32),
    assignee        VARCHAR(120),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_story ON tasks (story_id);

CREATE TABLE requirement_changes (
    id            BIGSERIAL PRIMARY KEY,
    sprint_id     BIGINT NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    story_id      BIGINT REFERENCES user_stories (id) ON DELETE SET NULL,
    change_type   VARCHAR(32) NOT NULL,
    description   TEXT,
    requested_by  VARCHAR(120),
    changed_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_reqchg_sprint ON requirement_changes (sprint_id, changed_at DESC);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    story_id   BIGINT NOT NULL REFERENCES user_stories (id) ON DELETE CASCADE,
    author     VARCHAR(120),
    body       TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_story ON comments (story_id);

CREATE TABLE ai_model_responses (
    id               BIGSERIAL PRIMARY KEY,
    sprint_id        BIGINT NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    model_type       VARCHAR(40) NOT NULL,
    request_payload  JSONB,
    response_payload JSONB,
    latency_ms       INTEGER,
    http_status      INTEGER,
    degraded         BOOLEAN NOT NULL DEFAULT false,
    error_message    TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_aires_sprint ON ai_model_responses (sprint_id, created_at DESC);
CREATE INDEX idx_aires_model  ON ai_model_responses (model_type);

CREATE TABLE risk_predictions (
    id              BIGSERIAL PRIMARY KEY,
    sprint_id       BIGINT NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    model_type      VARCHAR(40) NOT NULL,
    risk_score      DOUBLE PRECISION NOT NULL,
    risk_level      VARCHAR(16) NOT NULL,
    probability     DOUBLE PRECISION,
    explanation     TEXT,
    ai_response_id  BIGINT REFERENCES ai_model_responses (id) ON DELETE SET NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_pred_sprint ON risk_predictions (sprint_id, created_at DESC);

CREATE TABLE aggregated_risk_results (
    id                          BIGSERIAL PRIMARY KEY,
    sprint_id                   BIGINT NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    evaluation_id               UUID NOT NULL,
    overall_score               DOUBLE PRECISION NOT NULL,
    overall_level               VARCHAR(16) NOT NULL,
    over_budget_score           DOUBLE PRECISION,
    requirement_change_score    DOUBLE PRECISION,
    combined_explanation        TEXT,
    degraded                    BOOLEAN NOT NULL DEFAULT false,
    created_at                  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_agg_sprint     ON aggregated_risk_results (sprint_id, created_at DESC);
CREATE INDEX idx_agg_evaluation ON aggregated_risk_results (evaluation_id);
