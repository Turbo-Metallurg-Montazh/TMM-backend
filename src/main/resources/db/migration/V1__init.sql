-- =========================================================
-- SINGLE ENTERPRISE INIT SCHEMA
-- =========================================================

-- ==========================
-- USERS
-- ==========================
CREATE TABLE IF NOT EXISTS user_info
(
    user_id     BIGSERIAL PRIMARY KEY,
    username    VARCHAR(255) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    first_name  VARCHAR(255) NOT NULL,
    middle_name VARCHAR(255),
    last_name   VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS password_reset_token
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES user_info (user_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user_id
    ON password_reset_token (user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_expires_at
    ON password_reset_token (expires_at);

CREATE TABLE IF NOT EXISTS refresh_token
(
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT      NOT NULL REFERENCES user_info (user_id) ON DELETE CASCADE,
    token_hash             VARCHAR(64) NOT NULL UNIQUE,
    expires_at             TIMESTAMP   NOT NULL,
    revoked_at             TIMESTAMP,
    last_used_at           TIMESTAMP,
    replaced_by_token_hash VARCHAR(64),
    created_at             TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id
    ON refresh_token (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_expires_at
    ON refresh_token (expires_at);

-- ==========================
-- ROLES
-- ==========================
CREATE TABLE IF NOT EXISTS roles
(
    role_id     BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL UNIQUE,
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ==========================
-- PERMISSIONS
-- ==========================
CREATE TABLE IF NOT EXISTS permission
(
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT
);

-- ==========================
-- ROLE -> PERMISSION
-- ==========================
CREATE TABLE IF NOT EXISTS role_permission
(
    role_id       BIGINT NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_role_permission_role_id
    ON role_permission (role_id);

-- ==========================
-- USER -> ROLES
-- ==========================
CREATE TABLE IF NOT EXISTS user_role
(
    user_id  BIGINT NOT NULL REFERENCES user_info (user_id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_role_role_id
    ON user_role (role_id);

-- ==========================
-- USER PERMISSION OVERRIDES
-- ==========================
CREATE TABLE IF NOT EXISTS user_permission_override
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT    NOT NULL REFERENCES user_info (user_id) ON DELETE CASCADE,
    permission_id BIGINT    NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    is_granted    BOOLEAN   NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_permission_override_user_permission UNIQUE (user_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_user_permission_override_user_id
    ON user_permission_override (user_id);

-- ==========================
-- USER GROUPS
-- ==========================
CREATE TABLE IF NOT EXISTS user_group
(
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_group_member
(
    user_id       BIGINT NOT NULL REFERENCES user_info (user_id) ON DELETE CASCADE,
    user_group_id BIGINT NOT NULL REFERENCES user_group (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, user_group_id)
);

CREATE INDEX IF NOT EXISTS idx_user_group_member_group_id
    ON user_group_member (user_group_id);

CREATE TABLE IF NOT EXISTS user_group_role
(
    user_group_id BIGINT NOT NULL REFERENCES user_group (id) ON DELETE CASCADE,
    role_id       BIGINT NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    PRIMARY KEY (user_group_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_group_role_role_id
    ON user_group_role (role_id);

-- ==========================
-- ROLE CHANGE AUDIT
-- ==========================
CREATE TABLE IF NOT EXISTS role_change_log
(
    id              BIGSERIAL PRIMARY KEY,
    actor_username  VARCHAR(255) NOT NULL,
    target_username VARCHAR(255) NOT NULL,
    action          VARCHAR(128) NOT NULL,
    role_code       VARCHAR(255),
    details         TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_role_change_log_created_at
    ON role_change_log (created_at DESC);

-- =========================================================
-- BUSINESS TABLES
-- =========================================================

CREATE TABLE IF NOT EXISTS illiquid_assets
(
    id                       BIGSERIAL PRIMARY KEY,
    commodity_material_value VARCHAR(255) NOT NULL,
    article_number           VARCHAR(255),
    quantity                 REAL,
    units_of_measurement     VARCHAR(255),
    price                    REAL,
    currency                 VARCHAR(255),
    summary_price            REAL,
    arrival_date             VARCHAR(255) NOT NULL,
    creating_date            TIMESTAMP,
    last_update_date         TIMESTAMP,
    responsible_employee     VARCHAR(255),
    created_by_id            BIGINT       NOT NULL,
    commentary               TEXT,
    avito                    VARCHAR(255),
    asset_type               VARCHAR(255),
    asset_status             VARCHAR(255) NOT NULL,
    created_at               TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tender_filter
(
    id                                  BIGSERIAL PRIMARY KEY,
    name                                VARCHAR(255) NOT NULL,
    user_id                             BIGINT       NOT NULL,
    is_active                           BOOLEAN      NOT NULL DEFAULT TRUE,
    text_values                         TEXT[],
    exclude_values                      TEXT[],
    categories                          INTEGER[],
    include_inns                        TEXT[],
    exclude_inns                        TEXT[],
    date_time_from                      TIMESTAMPTZ,
    date_time_to                        TIMESTAMPTZ,
    participants_inns                   TEXT[],
    participants_state                  INTEGER,
    enable_participants_from_documents  BOOLEAN,
    region_ids                          TEXT[],
    purchase_statuses                   INTEGER[],
    laws                                INTEGER[],
    procedures                          INTEGER[],
    electronic_places                   INTEGER[],
    category_ids                        INTEGER[],
    strict_search                       BOOLEAN,
    attachments                         BOOLEAN,
    max_price_from                      BIGINT,
    max_price_to                        BIGINT,
    max_price_none                      BOOLEAN,
    advance_44                          BOOLEAN,
    advance_223                         BOOLEAN,
    non_advance                         BOOLEAN,
    smp                                 INTEGER,
    allow_foreign_currency              BOOLEAN,
    page_number                         INTEGER,
    sort_order                          INTEGER,
    application_deadline_from           TIMESTAMPTZ,
    application_deadline_to             TIMESTAMPTZ,
    application_deadline_type           VARCHAR(64),
    included_requirement_ids            INTEGER[],
    excluded_requirement_ids            INTEGER[],
    created_at                          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_tender_filter_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS unloading_date
(
    id          BIGSERIAL PRIMARY KEY,
    filter_id   BIGINT NOT NULL,
    unload_date TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tenders
(
    id                     VARCHAR(255) PRIMARY KEY,
    notification_number    VARCHAR(255),
    order_name             VARCHAR(500),
    notification_type_desc VARCHAR(255),
    type_of_trading        INTEGER,
    max_price              DOUBLE PRECISION,
    currency               VARCHAR(64),
    ep_uri                 VARCHAR(500),
    link                   VARCHAR(500),
    application_deadline   TIMESTAMP,
    is_cancelled           BOOLEAN,
    create_date            TIMESTAMP,
    other_information      TEXT,
    commission_deadline    TIMESTAMP,
    is_abandoned           BOOLEAN,
    is_planning            BOOLEAN,
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS purchase_tenders
(
    db_id                     BIGSERIAL PRIMARY KEY,
    purchase_id               VARCHAR(255)  NOT NULL,
    source_type               VARCHAR(16)   NOT NULL,
    updated_datetime          TIMESTAMP,
    notification_type         VARCHAR(255),
    notification_placing_way  VARCHAR(255),
    auction_date_time         TIMESTAMP,
    etp_link                  VARCHAR(2048),
    eis_link                  VARCHAR(2048),
    link                      VARCHAR(2048),
    cancel_reason             TEXT,
    planned_publish_date      TIMESTAMP,
    notification_number       VARCHAR(255),
    title                     VARCHAR(1024),
    smp                       BOOLEAN,
    publication_datetime_utc  TIMESTAMP,
    application_deadline      TIMESTAMP,
    commission_deadline       TIMESTAMP,
    payload_json              TEXT          NOT NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_purchase_tenders_purchase_source UNIQUE (purchase_id, source_type)
);

CREATE INDEX IF NOT EXISTS idx_purchase_tenders_purchase_id
    ON purchase_tenders (purchase_id);

CREATE TABLE IF NOT EXISTS purchase_results
(
    db_id                   BIGSERIAL PRIMARY KEY,
    purchase_id             VARCHAR(255) NOT NULL,
    link                    VARCHAR(2048),
    protocols_count         INTEGER,
    contract_projects_count INTEGER,
    contracts_count         INTEGER,
    payload_json            TEXT         NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_purchase_results_purchase_id UNIQUE (purchase_id)
);

CREATE TABLE IF NOT EXISTS favorite_tenders
(
    db_id       BIGSERIAL PRIMARY KEY,
    purchase_id VARCHAR(255) NOT NULL,
    marker_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(16)  NOT NULL,
    page_number INTEGER,
    total_count BIGINT,
    payload_json TEXT        NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_favorite_tenders_purchase_source_marker UNIQUE (purchase_id, source_type, marker_name)
);

CREATE INDEX IF NOT EXISTS idx_favorite_tenders_purchase_id
    ON favorite_tenders (purchase_id);

CREATE TABLE IF NOT EXISTS favorite_markers
(
    db_id       BIGSERIAL PRIMARY KEY,
    marker_id   VARCHAR(255) NOT NULL,
    name        VARCHAR(255),
    payload_json TEXT        NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_favorite_markers_marker_id UNIQUE (marker_id)
);

CREATE INDEX IF NOT EXISTS idx_favorite_markers_marker_id
    ON favorite_markers (marker_id);

-- ==========================
-- LEGAL COUNTERPARTY REGISTRY
-- ==========================
CREATE TABLE IF NOT EXISTS legal_counterparty
(
    id                  BIGSERIAL PRIMARY KEY,
    company_name        VARCHAR(512) NOT NULL,
    short_name          VARCHAR(255),
    inn                 VARCHAR(12)  NOT NULL,
    kpp                 VARCHAR(9),
    ogrn                VARCHAR(15),
    registry_type       VARCHAR(16)  NOT NULL,
    general_risks       TEXT,
    overall_score       INTEGER      NOT NULL DEFAULT 0,
    risk_level          VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    work_prohibited     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_checked_at     TIMESTAMP,
    legal_comment       TEXT,
    created_by_username VARCHAR(255),
    updated_by_username VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_legal_counterparty_inn UNIQUE (inn),
    CONSTRAINT chk_legal_counterparty_score CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT chk_legal_counterparty_registry_type CHECK (registry_type IN ('CUSTOMER', 'SUPPLIER', 'BOTH')),
    CONSTRAINT chk_legal_counterparty_risk_level CHECK (risk_level IN ('UNKNOWN', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_company_name
    ON legal_counterparty (lower(company_name));

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_short_name
    ON legal_counterparty (lower(short_name));

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_inn
    ON legal_counterparty (inn);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_kpp
    ON legal_counterparty (kpp);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_ogrn
    ON legal_counterparty (ogrn);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_registry_type
    ON legal_counterparty (registry_type);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_risk_level
    ON legal_counterparty (risk_level);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_work_prohibited
    ON legal_counterparty (work_prohibited);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_last_checked_at
    ON legal_counterparty (last_checked_at DESC);

CREATE TABLE IF NOT EXISTS legal_counterparty_check
(
    id                  BIGSERIAL PRIMARY KEY,
    counterparty_id     BIGINT      NOT NULL REFERENCES legal_counterparty (id) ON DELETE CASCADE,
    checked_at          TIMESTAMP   NOT NULL,
    overall_score       INTEGER     NOT NULL,
    risk_level          VARCHAR(16) NOT NULL,
    work_prohibited     BOOLEAN     NOT NULL DEFAULT FALSE,
    risks               TEXT,
    comment             TEXT,
    checked_by_username VARCHAR(255),
    checked_by_user_id  BIGINT,
    checked_by_full_name VARCHAR(512),
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT chk_legal_counterparty_check_score CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT chk_legal_counterparty_check_risk_level CHECK (risk_level IN ('UNKNOWN', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_check_counterparty
    ON legal_counterparty_check (counterparty_id, checked_at DESC);

CREATE TABLE IF NOT EXISTS legal_counterparty_incident
(
    id                  BIGSERIAL PRIMARY KEY,
    counterparty_id     BIGINT       NOT NULL REFERENCES legal_counterparty (id) ON DELETE CASCADE,
    incident_date       DATE         NOT NULL,
    title               VARCHAR(512) NOT NULL,
    description         TEXT,
    impact_level        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    created_by_username VARCHAR(255),
    created_by_user_id  BIGINT,
    created_by_full_name VARCHAR(512),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_legal_counterparty_incident_impact CHECK (impact_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_incident_counterparty
    ON legal_counterparty_incident (counterparty_id, incident_date DESC);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_incident_created_by_user
    ON legal_counterparty_incident (created_by_user_id);

CREATE TABLE IF NOT EXISTS legal_counterparty_tender_link
(
    id              BIGSERIAL PRIMARY KEY,
    counterparty_id BIGINT        NOT NULL REFERENCES legal_counterparty (id) ON DELETE CASCADE,
    tender_id       VARCHAR(255),
    tender_name     VARCHAR(1024),
    tender_url      VARCHAR(2048) NOT NULL,
    relation_type   VARCHAR(32),
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_legal_counterparty_tender_link_counterparty
    ON legal_counterparty_tender_link (counterparty_id);

-- =========================================================
-- RBAC SEED
-- =========================================================
WITH permission_catalog(code, description) AS (
    VALUES ('RBAC.ROLE.READ', 'Просмотр ролей'),
           ('RBAC.ROLE.WRITE', 'Создание и изменение ролей'),
           ('RBAC.ROLE.DELETE', 'Удаление ролей'),
           ('RBAC.USER.READ', 'Просмотр пользователей'),
           ('RBAC.USER.WRITE', 'Управление пользователями и назначением ролей'),
           ('RBAC.PERMISSION.READ', 'Просмотр справочника прав доступа'),

           ('TENDER.SEARCH', 'Поиск тендеров'),
           ('TENDER.VIEW', 'Просмотр карточек тендеров'),
           ('TENDER.EDIT', 'Редактирование тендеров'),
           ('TENDER.EXPORT', 'Экспорт данных тендеров'),
           ('TENDER_FILTER.WRITE', 'Управление фильтрами тендеров'),

           ('OFFER.APPROVE', 'Согласование коммерческих предложений'),
           ('OFFER.VIEW_ALL', 'Просмотр всех коммерческих предложений'),
           ('OFFER.CALCULATE', 'Расчет коммерческих предложений'),
           ('OFFER.EDIT', 'Редактирование коммерческих предложений'),
           ('OFFER.GENERATE_CP', 'Формирование коммерческих предложений'),
           ('OFFER.SUBMIT', 'Отправка коммерческих предложений'),

           ('REPORTS.VIEW', 'Просмотр отчетов'),
           ('PROCUREMENT.SELECT_ANALOGS', 'Подбор аналогов товаров'),
           ('PROCUREMENT.EDIT_NONDEALER_POSITIONS', 'Редактирование позиций закупки не у дилера'),
           ('CONTRACTOR.CHECK_RELIABILITY', 'Проверка благонадежности контрагента'),
           ('CONTRACTOR.VIEW_REPORTS', 'Просмотр отчетов по контрагентам'),
           ('CONTRACTOR.REGISTRY.READ', 'Просмотр юридического реестра контрагентов и поставщиков'),
           ('CONTRACTOR.REGISTRY.WRITE', 'Ведение юридического реестра контрагентов и поставщиков'),
           ('INVENTORY.NOLIQUID.VIEW', 'Просмотр неликвидных остатков'),
           ('INVENTORY.NOLIQUID.MANAGE', 'Управление неликвидными остатками')
)
INSERT
INTO permission (code, description)
SELECT code, description
FROM permission_catalog
ON CONFLICT (code) DO NOTHING;

WITH role_templates(code, name, is_system) AS (
    VALUES ('SALES_HEAD', 'Руководитель отдела продаж', TRUE),
           ('SALES_MANAGER', 'Менеджер отдела продаж', TRUE),
           ('PROCUREMENT_SPECIALIST', 'Специалист отдела снабжения', TRUE),
           ('LAWYER', 'Юрист', TRUE),
           ('STOREKEEPER', 'Кладовщик', TRUE),
           ('RBAC_ADMIN', 'Администратор прав доступа', TRUE),
           ('DEVELOPER', 'Разработчик системы', TRUE)
)
INSERT
INTO roles (code, name, is_system, created_at, updated_at)
SELECT code, name, is_system, now(), now()
FROM role_templates
ON CONFLICT (code) DO NOTHING;

WITH mapping(role_code, permission_code) AS (
    VALUES
        ('SALES_HEAD', 'OFFER.APPROVE'),
        ('SALES_HEAD', 'REPORTS.VIEW'),
        ('SALES_HEAD', 'OFFER.VIEW_ALL'),
        ('SALES_HEAD', 'TENDER.VIEW'),
        ('SALES_HEAD', 'CONTRACTOR.REGISTRY.READ'),

        ('SALES_MANAGER', 'TENDER.SEARCH'),
        ('SALES_MANAGER', 'OFFER.CALCULATE'),
        ('SALES_MANAGER', 'OFFER.EDIT'),
        ('SALES_MANAGER', 'OFFER.GENERATE_CP'),
        ('SALES_MANAGER', 'OFFER.SUBMIT'),
        ('SALES_MANAGER', 'TENDER_FILTER.WRITE'),
        ('SALES_MANAGER', 'CONTRACTOR.REGISTRY.READ'),

        ('PROCUREMENT_SPECIALIST', 'PROCUREMENT.SELECT_ANALOGS'),
        ('PROCUREMENT_SPECIALIST', 'PROCUREMENT.EDIT_NONDEALER_POSITIONS'),
        ('PROCUREMENT_SPECIALIST', 'CONTRACTOR.REGISTRY.READ'),

        ('LAWYER', 'CONTRACTOR.CHECK_RELIABILITY'),
        ('LAWYER', 'CONTRACTOR.VIEW_REPORTS'),
        ('LAWYER', 'CONTRACTOR.REGISTRY.READ'),
        ('LAWYER', 'CONTRACTOR.REGISTRY.WRITE'),

        ('STOREKEEPER', 'INVENTORY.NOLIQUID.VIEW'),
        ('STOREKEEPER', 'INVENTORY.NOLIQUID.MANAGE'),
        ('STOREKEEPER', 'CONTRACTOR.REGISTRY.READ'),

        ('RBAC_ADMIN', 'RBAC.ROLE.READ'),
        ('RBAC_ADMIN', 'RBAC.ROLE.WRITE'),
        ('RBAC_ADMIN', 'RBAC.ROLE.DELETE'),
        ('RBAC_ADMIN', 'RBAC.USER.READ'),
        ('RBAC_ADMIN', 'RBAC.USER.WRITE'),
        ('RBAC_ADMIN', 'RBAC.PERMISSION.READ'),
        ('RBAC_ADMIN', 'CONTRACTOR.CHECK_RELIABILITY'),
        ('RBAC_ADMIN', 'CONTRACTOR.VIEW_REPORTS'),
        ('RBAC_ADMIN', 'CONTRACTOR.REGISTRY.READ'),
        ('RBAC_ADMIN', 'CONTRACTOR.REGISTRY.WRITE')
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
ON CONFLICT DO NOTHING;
