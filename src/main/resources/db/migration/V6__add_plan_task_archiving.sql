ALTER TABLE plan_task
    ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX ix_plan_task_career_plan_active_due_date
    ON plan_task (career_plan_id, due_date)
    WHERE archived_at IS NULL;
