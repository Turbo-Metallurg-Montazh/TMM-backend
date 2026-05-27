-- =========================================================
-- TENDER WORKFLOW
-- =========================================================

CREATE TABLE IF NOT EXISTS tender_workflow
(
    id                     BIGSERIAL PRIMARY KEY,
    purchase_tender_id     BIGINT       NOT NULL REFERENCES purchase_tenders (db_id) ON DELETE CASCADE,
    status                 VARCHAR(64)  NOT NULL DEFAULT 'NEW',
    priority               VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    responsible_manager_id BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    supply_user_id         BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    lawyer_user_id         BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    approved_by_id         BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    rejection_reason       TEXT,
    lost_reason            TEXT,
    result_comment         TEXT,
    created_by_id          BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    updated_by_id          BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_tender_workflow_purchase_tender UNIQUE (purchase_tender_id),
    CONSTRAINT chk_tender_workflow_status CHECK (status IN (
        'NEW',
        'PROFILE_REVIEW',
        'FEASIBILITY_REVIEW',
        'CONTRACTOR_CHECK',
        'PRICE_CALCULATION',
        'APPROVAL',
        'COMMERCIAL_PROPOSAL_PREPARATION',
        'READY_FOR_BIDDING',
        'BIDDING',
        'CONTRACT_EXECUTION',
        'WAITING_PAYMENT',
        'COMPLETED',
        'LOST',
        'REJECTED'
    )),
    CONSTRAINT chk_tender_workflow_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_status
    ON tender_workflow (status);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_priority
    ON tender_workflow (priority);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_responsible_manager
    ON tender_workflow (responsible_manager_id);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_supply_user
    ON tender_workflow (supply_user_id);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_lawyer_user
    ON tender_workflow (lawyer_user_id);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_created_at
    ON tender_workflow (created_at DESC);

CREATE TABLE IF NOT EXISTS tender_workflow_status_history
(
    id            BIGSERIAL PRIMARY KEY,
    workflow_id   BIGINT      NOT NULL REFERENCES tender_workflow (id) ON DELETE CASCADE,
    old_status    VARCHAR(64),
    new_status    VARCHAR(64) NOT NULL,
    changed_by_id BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    comment       TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_status_history_workflow
    ON tender_workflow_status_history (workflow_id, created_at DESC);

CREATE TABLE IF NOT EXISTS tender_workflow_comment
(
    id           BIGSERIAL PRIMARY KEY,
    workflow_id  BIGINT      NOT NULL REFERENCES tender_workflow (id) ON DELETE CASCADE,
    author_id    BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    comment_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    text         TEXT        NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT chk_tender_workflow_comment_type CHECK (comment_type IN (
        'GENERAL',
        'PROFILE_REVIEW',
        'FEASIBILITY_REVIEW',
        'LEGAL',
        'SUPPLY',
        'APPROVAL',
        'BIDDING',
        'EXECUTION'
    ))
);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_comment_workflow
    ON tender_workflow_comment (workflow_id, created_at DESC);

CREATE TABLE IF NOT EXISTS tender_workflow_file
(
    id            BIGSERIAL PRIMARY KEY,
    workflow_id   BIGINT        NOT NULL REFERENCES tender_workflow (id) ON DELETE CASCADE,
    uploaded_by_id BIGINT REFERENCES user_info (user_id) ON DELETE SET NULL,
    file_name     VARCHAR(512)  NOT NULL,
    file_type     VARCHAR(255),
    storage_path  VARCHAR(2048) NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tender_workflow_file_workflow
    ON tender_workflow_file (workflow_id, created_at DESC);

WITH workflow_permissions(code, description) AS (
    VALUES ('TENDER_WORKFLOW_VIEW', 'Просмотр процессов обработки тендеров'),
           ('TENDER_WORKFLOW_CREATE', 'Создание процесса обработки тендера'),
           ('TENDER_WORKFLOW_EDIT', 'Редактирование процесса обработки тендера'),
           ('TENDER_WORKFLOW_CHANGE_STATUS', 'Смена статуса процесса обработки тендера'),
           ('TENDER_WORKFLOW_ASSIGN_USERS', 'Назначение ответственных в процессе обработки тендера'),
           ('TENDER_WORKFLOW_COMMENT', 'Работа с комментариями процесса обработки тендера'),
           ('TENDER_WORKFLOW_UPLOAD_FILE', 'Прикрепление файлов к процессу обработки тендера'),
           ('TENDER_WORKFLOW_APPROVE', 'Согласование процесса обработки тендера'),
           ('TENDER_WORKFLOW_REJECT', 'Отклонение процесса обработки тендера')
)
INSERT
INTO permission (code, description)
SELECT code, description
FROM workflow_permissions
ON CONFLICT (code) DO NOTHING;

WITH mapping(role_code, permission_code) AS (
    VALUES
        ('SALES_HEAD', 'TENDER_WORKFLOW_VIEW'),
        ('SALES_HEAD', 'TENDER_WORKFLOW_EDIT'),
        ('SALES_HEAD', 'TENDER_WORKFLOW_CHANGE_STATUS'),
        ('SALES_HEAD', 'TENDER_WORKFLOW_ASSIGN_USERS'),
        ('SALES_HEAD', 'TENDER_WORKFLOW_COMMENT'),
        ('SALES_HEAD', 'TENDER_WORKFLOW_APPROVE'),
        ('SALES_HEAD', 'TENDER_WORKFLOW_REJECT'),

        ('SALES_MANAGER', 'TENDER_WORKFLOW_VIEW'),
        ('SALES_MANAGER', 'TENDER_WORKFLOW_CREATE'),
        ('SALES_MANAGER', 'TENDER_WORKFLOW_EDIT'),
        ('SALES_MANAGER', 'TENDER_WORKFLOW_CHANGE_STATUS'),
        ('SALES_MANAGER', 'TENDER_WORKFLOW_COMMENT'),
        ('SALES_MANAGER', 'TENDER_WORKFLOW_UPLOAD_FILE'),

        ('PROCUREMENT_SPECIALIST', 'TENDER_WORKFLOW_VIEW'),
        ('PROCUREMENT_SPECIALIST', 'TENDER_WORKFLOW_EDIT'),
        ('PROCUREMENT_SPECIALIST', 'TENDER_WORKFLOW_CHANGE_STATUS'),
        ('PROCUREMENT_SPECIALIST', 'TENDER_WORKFLOW_COMMENT'),
        ('PROCUREMENT_SPECIALIST', 'TENDER_WORKFLOW_UPLOAD_FILE'),

        ('LAWYER', 'TENDER_WORKFLOW_VIEW'),
        ('LAWYER', 'TENDER_WORKFLOW_EDIT'),
        ('LAWYER', 'TENDER_WORKFLOW_CHANGE_STATUS'),
        ('LAWYER', 'TENDER_WORKFLOW_COMMENT'),
        ('LAWYER', 'TENDER_WORKFLOW_UPLOAD_FILE'),
        ('LAWYER', 'TENDER_WORKFLOW_REJECT'),

        ('RBAC_ADMIN', 'TENDER_WORKFLOW_VIEW'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_CREATE'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_EDIT'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_CHANGE_STATUS'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_ASSIGN_USERS'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_COMMENT'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_UPLOAD_FILE'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_APPROVE'),
        ('RBAC_ADMIN', 'TENDER_WORKFLOW_REJECT')
)
INSERT
INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM roles r
         JOIN mapping m ON m.role_code = r.code
         JOIN permission p ON p.code = m.permission_code
ON CONFLICT DO NOTHING;

INSERT
INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM roles r
         CROSS JOIN permission p
WHERE r.code = 'DEVELOPER'
  AND p.code LIKE 'TENDER_WORKFLOW_%'
ON CONFLICT DO NOTHING;
