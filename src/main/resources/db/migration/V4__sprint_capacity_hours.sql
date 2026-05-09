-- Sprint-level planning input required by the new (v2) over-budget model.
-- Total team capacity in hours for the sprint (capacity_points × hours_per_point estimate).
ALTER TABLE sprints
    ADD COLUMN IF NOT EXISTS sprint_capacity_hours DOUBLE PRECISION;
