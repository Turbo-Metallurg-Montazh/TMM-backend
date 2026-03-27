ALTER TABLE tender_filter
    ADD COLUMN IF NOT EXISTS text_values TEXT[],
    ADD COLUMN IF NOT EXISTS exclude_values TEXT[],
    ADD COLUMN IF NOT EXISTS categories INTEGER[],
    ADD COLUMN IF NOT EXISTS include_inns TEXT[],
    ADD COLUMN IF NOT EXISTS exclude_inns TEXT[],
    ADD COLUMN IF NOT EXISTS date_time_from TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS date_time_to TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS participants_inns TEXT[],
    ADD COLUMN IF NOT EXISTS participants_state INTEGER,
    ADD COLUMN IF NOT EXISTS enable_participants_from_documents BOOLEAN,
    ADD COLUMN IF NOT EXISTS region_ids TEXT[],
    ADD COLUMN IF NOT EXISTS purchase_statuses INTEGER[],
    ADD COLUMN IF NOT EXISTS laws INTEGER[],
    ADD COLUMN IF NOT EXISTS procedures INTEGER[],
    ADD COLUMN IF NOT EXISTS electronic_places INTEGER[],
    ADD COLUMN IF NOT EXISTS category_ids INTEGER[],
    ADD COLUMN IF NOT EXISTS strict_search BOOLEAN,
    ADD COLUMN IF NOT EXISTS attachments BOOLEAN,
    ADD COLUMN IF NOT EXISTS max_price_from BIGINT,
    ADD COLUMN IF NOT EXISTS max_price_to BIGINT,
    ADD COLUMN IF NOT EXISTS max_price_none BOOLEAN,
    ADD COLUMN IF NOT EXISTS advance_44 BOOLEAN,
    ADD COLUMN IF NOT EXISTS advance_223 BOOLEAN,
    ADD COLUMN IF NOT EXISTS non_advance BOOLEAN,
    ADD COLUMN IF NOT EXISTS smp INTEGER,
    ADD COLUMN IF NOT EXISTS allow_foreign_currency BOOLEAN,
    ADD COLUMN IF NOT EXISTS page_number INTEGER,
    ADD COLUMN IF NOT EXISTS application_deadline_from TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS application_deadline_to TIMESTAMPTZ;

WITH parsed_filters AS (
    SELECT id,
           CASE
               WHEN json_filter IS NULL OR btrim(json_filter) = '' THEN '{}'::jsonb
               ELSE json_filter::jsonb
               END AS payload
    FROM tender_filter
)
UPDATE tender_filter tf
SET text_values = CASE
                      WHEN pf.payload ? 'text' OR pf.payload ? 'Text' THEN ARRAY(
                              SELECT item
                              FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'text', pf.payload -> 'Text')) AS values(item)
                      )
                      ELSE NULL
    END,
    exclude_values = CASE
                         WHEN pf.payload ? 'exclude' OR pf.payload ? 'Exclude' THEN ARRAY(
                                 SELECT item
                                 FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'exclude', pf.payload -> 'Exclude')) AS values(item)
                         )
                         ELSE NULL
        END,
    categories = CASE
                     WHEN pf.payload ? 'categories' OR pf.payload ? 'Categories' THEN ARRAY(
                             SELECT item::INTEGER
                             FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'categories', pf.payload -> 'Categories')) AS values(item)
                     )
                     ELSE NULL
        END,
    include_inns = CASE
                       WHEN pf.payload ? 'includeInns' OR pf.payload ? 'IncludeInns' THEN ARRAY(
                               SELECT item
                               FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'includeInns', pf.payload -> 'IncludeInns')) AS values(item)
                       )
                       ELSE NULL
        END,
    exclude_inns = CASE
                       WHEN pf.payload ? 'excludeInns' OR pf.payload ? 'ExcludeInns' THEN ARRAY(
                               SELECT item
                               FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'excludeInns', pf.payload -> 'ExcludeInns')) AS values(item)
                       )
                       ELSE NULL
        END,
    date_time_from = COALESCE(
            NULLIF(COALESCE(pf.payload ->> 'dateTimeFrom', pf.payload ->> 'DateTimeFrom'), '')::TIMESTAMPTZ,
            '2011-12-30T07:43:31.681Z'::TIMESTAMPTZ
    ),
    date_time_to = COALESCE(
            NULLIF(COALESCE(pf.payload ->> 'dateTimeTo', pf.payload ->> 'DateTimeTo'), '')::TIMESTAMPTZ,
            '2031-12-30T07:43:31.681Z'::TIMESTAMPTZ
    ),
    participants_inns = CASE
                            WHEN pf.payload ? 'participantsInns' OR pf.payload ? 'ParticipantsInns' THEN ARRAY(
                                    SELECT item
                                    FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'participantsInns', pf.payload -> 'ParticipantsInns')) AS values(item)
                            )
                            ELSE NULL
        END,
    participants_state = NULLIF(COALESCE(pf.payload ->> 'participantsState', pf.payload ->> 'ParticipantsState'), '')::INTEGER,
    enable_participants_from_documents = NULLIF(
            COALESCE(
                    pf.payload ->> 'enableParticipantsFromDocuments',
                    pf.payload ->> 'EnableParticipantsFromDocuments'
            ),
            ''
                                        )::BOOLEAN,
    region_ids = CASE
                     WHEN pf.payload ? 'regionIds' OR pf.payload ? 'RegionIds' THEN ARRAY(
                             SELECT item
                             FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'regionIds', pf.payload -> 'RegionIds')) AS values(item)
                     )
                     ELSE NULL
        END,
    purchase_statuses = CASE
                            WHEN pf.payload ? 'purchaseStatuses' OR pf.payload ? 'PurchaseStatuses' THEN ARRAY(
                                    SELECT item::INTEGER
                                    FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'purchaseStatuses', pf.payload -> 'PurchaseStatuses')) AS values(item)
                            )
                            ELSE NULL
        END,
    laws = CASE
               WHEN pf.payload ? 'laws' OR pf.payload ? 'Laws' THEN ARRAY(
                       SELECT item::INTEGER
                       FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'laws', pf.payload -> 'Laws')) AS values(item)
               )
               ELSE NULL
    END,
    procedures = CASE
                     WHEN pf.payload ? 'procedures' OR pf.payload ? 'Procedures' THEN ARRAY(
                             SELECT item::INTEGER
                             FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'procedures', pf.payload -> 'Procedures')) AS values(item)
                     )
                     ELSE NULL
        END,
    electronic_places = CASE
                            WHEN pf.payload ? 'electronicPlaces' OR pf.payload ? 'ElectronicPlaces' THEN ARRAY(
                                    SELECT item::INTEGER
                                    FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'electronicPlaces', pf.payload -> 'ElectronicPlaces')) AS values(item)
                            )
                            ELSE NULL
        END,
    category_ids = CASE
                       WHEN pf.payload ? 'categoryIds' OR pf.payload ? 'CategoryIds' THEN ARRAY(
                               SELECT item::INTEGER
                               FROM jsonb_array_elements_text(COALESCE(pf.payload -> 'categoryIds', pf.payload -> 'CategoryIds')) AS values(item)
                       )
                       ELSE NULL
        END,
    strict_search = NULLIF(COALESCE(pf.payload ->> 'strictSearch', pf.payload ->> 'StrictSearch'), '')::BOOLEAN,
    attachments = NULLIF(COALESCE(pf.payload ->> 'attachments', pf.payload ->> 'Attachments'), '')::BOOLEAN,
    max_price_from = NULLIF(COALESCE(pf.payload ->> 'maxPriceFrom', pf.payload ->> 'MaxPriceFrom'), '')::BIGINT,
    max_price_to = NULLIF(COALESCE(pf.payload ->> 'maxPriceTo', pf.payload ->> 'MaxPriceTo'), '')::BIGINT,
    max_price_none = NULLIF(COALESCE(pf.payload ->> 'maxPriceNone', pf.payload ->> 'MaxPriceNone'), '')::BOOLEAN,
    advance_44 = NULLIF(COALESCE(pf.payload ->> 'advance44', pf.payload ->> 'Advance44'), '')::BOOLEAN,
    advance_223 = NULLIF(COALESCE(pf.payload ->> 'advance223', pf.payload ->> 'Advance223'), '')::BOOLEAN,
    non_advance = NULLIF(COALESCE(pf.payload ->> 'nonAdvance', pf.payload ->> 'NonAdvance'), '')::BOOLEAN,
    smp = NULLIF(COALESCE(pf.payload ->> 'smp', pf.payload ->> 'Smp'), '')::INTEGER,
    allow_foreign_currency = NULLIF(COALESCE(
            pf.payload ->> 'allowForeignCurrency',
            pf.payload ->> 'AllowForeignCurrency'
                                    ), '')::BOOLEAN,
    page_number = COALESCE(NULLIF(COALESCE(pf.payload ->> 'pageNumber', pf.payload ->> 'PageNumber'), '')::INTEGER, 1),
    application_deadline_from = NULLIF(
            COALESCE(
                    pf.payload ->> 'applicationDeadlineFrom',
                    pf.payload ->> 'ApplicationDeadlineFrom'
            ),
            ''
                                )::TIMESTAMPTZ,
    application_deadline_to = NULLIF(
            COALESCE(
                    pf.payload ->> 'applicationDeadlineTo',
                    pf.payload ->> 'ApplicationDeadlineTo'
            ),
            ''
                              )::TIMESTAMPTZ,
    is_active = COALESCE(tf.is_active, TRUE)
FROM parsed_filters pf
WHERE tf.id = pf.id;

ALTER TABLE tender_filter
    ALTER COLUMN is_active SET DEFAULT TRUE,
    ALTER COLUMN is_active SET NOT NULL,
    ALTER COLUMN date_time_from SET NOT NULL,
    ALTER COLUMN date_time_to SET NOT NULL;

ALTER TABLE tender_filter
    DROP COLUMN IF EXISTS json_filter;
