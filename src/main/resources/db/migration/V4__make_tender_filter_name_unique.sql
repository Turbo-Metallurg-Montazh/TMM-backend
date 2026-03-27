UPDATE tender_filter
SET name = 'tender-filter-' || id
WHERE name IS NULL OR btrim(name) = '';

WITH ranked_names AS (
    SELECT id,
           name,
           ROW_NUMBER() OVER (PARTITION BY name ORDER BY id) AS rn
    FROM tender_filter
)
UPDATE tender_filter tf
SET name = tf.name || '-' || tf.id
FROM ranked_names rn
WHERE tf.id = rn.id
  AND rn.rn > 1;

ALTER TABLE tender_filter
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE tender_filter
    ADD CONSTRAINT uk_tender_filter_name UNIQUE (name);
