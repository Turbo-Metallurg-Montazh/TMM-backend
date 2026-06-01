-- =========================================================
-- ILLIQUID ASSET HISTORY
-- =========================================================

ALTER TABLE illiquid_assets
    ALTER COLUMN quantity SET DEFAULT 0;

UPDATE illiquid_assets
SET quantity = 0
WHERE quantity IS NULL OR quantity < 0;

ALTER TABLE illiquid_assets
    ALTER COLUMN quantity SET NOT NULL;

UPDATE illiquid_assets
SET asset_status = CASE WHEN quantity = 0 THEN 'CLOSED' ELSE 'OPEN' END;

ALTER TABLE illiquid_assets
    ADD CONSTRAINT chk_illiquid_assets_quantity_nonnegative CHECK (quantity >= 0),
    ADD CONSTRAINT chk_illiquid_assets_status CHECK (asset_status IN ('OPEN', 'CLOSED'));

CREATE TABLE IF NOT EXISTS illiquid_asset_history
(
    id                BIGSERIAL PRIMARY KEY,
    asset_id          BIGINT      NOT NULL REFERENCES illiquid_assets (id),
    operation_type    VARCHAR(32) NOT NULL,
    old_quantity      REAL        NOT NULL,
    new_quantity      REAL        NOT NULL,
    quantity_delta    REAL        NOT NULL,
    reason            VARCHAR(64) NOT NULL,
    comment           TEXT,
    related_tender_id BIGINT,
    changed_by_id     BIGINT      NOT NULL,
    changed_by_username VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT chk_illiquid_asset_history_operation CHECK (operation_type IN (
        'CREATION',
        'INCREASE',
        'DECREASE',
        'CLOSING'
    )),
    CONSTRAINT chk_illiquid_asset_history_quantity_nonnegative CHECK (old_quantity >= 0 AND new_quantity >= 0),
    CONSTRAINT chk_illiquid_asset_history_reason CHECK (reason IN (
        'CREATION',
        'RETAIL_SALE',
        'TENDER_SALE',
        'DAMAGE_DISPOSAL',
        'EXPIRED_DISPOSAL',
        'TENDER_PURCHASE',
        'INVENTORY_FOUND',
        'RETURN',
        'OTHER'
    ))
);

CREATE INDEX IF NOT EXISTS idx_illiquid_asset_history_asset
    ON illiquid_asset_history (asset_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_illiquid_asset_history_changed_by
    ON illiquid_asset_history (changed_by_id);

CREATE INDEX IF NOT EXISTS idx_illiquid_assets_status
    ON illiquid_assets (asset_status);

WITH inventory_roles(role_code, permission_code) AS (
    VALUES ('SALES_MANAGER', 'INVENTORY.NOLIQUID.VIEW'),
           ('SALES_MANAGER', 'INVENTORY.NOLIQUID.MANAGE'),
           ('SALES_HEAD', 'INVENTORY.NOLIQUID.VIEW'),
           ('SALES_HEAD', 'INVENTORY.NOLIQUID.MANAGE')
)
INSERT
INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM roles r
         JOIN inventory_roles ir ON ir.role_code = r.code
         JOIN permission p ON p.code = ir.permission_code
ON CONFLICT DO NOTHING;
