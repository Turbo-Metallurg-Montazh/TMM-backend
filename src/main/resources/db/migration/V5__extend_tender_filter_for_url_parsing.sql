ALTER TABLE tender_filter
    ALTER COLUMN date_time_from DROP NOT NULL,
    ALTER COLUMN date_time_to DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS sort_order INTEGER,
    ADD COLUMN IF NOT EXISTS application_deadline_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS included_requirement_ids INTEGER[],
    ADD COLUMN IF NOT EXISTS excluded_requirement_ids INTEGER[];
