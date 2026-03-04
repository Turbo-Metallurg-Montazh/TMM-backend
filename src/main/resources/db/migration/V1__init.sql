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
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255),
    user_id     BIGINT,
    is_active   BOOLEAN,
    json_filter TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
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

-- =========================================================
-- RBAC SEED
-- =========================================================
WITH permission_catalog(code, description) AS (
    VALUES ('RBAC.ROLE.READ', 'Просмотр ролей'),
           ('RBAC.ROLE.WRITE', 'Создание и изменение ролей'),
           ('RBAC.ROLE.DELETE', 'Удаление ролей'),
           ('RBAC.USER.READ', 'Просмотр пользователей'),
           ('RBAC.USER.WRITE', 'Управление пользователями и назначением ролей'),
           ('RBAC.PERMISSION.READ', 'Просмотр каталога прав доступа'),

           ('TENDER.SEARCH', 'Поиск тендеров'),
           ('TENDER.VIEW', 'Просмотр деталей тендера'),
           ('TENDER.EDIT', 'Редактирование тендеров'),
           ('TENDER.EXPORT', 'Экспорт данных тендеров'),
           ('TENDER_FILTER.WRITE', 'Управление фильтрами тендеров'),

           ('OFFER.APPROVE', 'Согласование предложений'),
           ('OFFER.VIEW_ALL', 'Просмотр всех предложений'),
           ('OFFER.CALCULATE', 'Расчет предложений'),
           ('OFFER.EDIT', 'Редактирование предложений'),
           ('OFFER.GENERATE_CP', 'Формирование коммерческих предложений'),
           ('OFFER.SUBMIT', 'Отправка предложений'),

           ('REPORTS.VIEW', 'Просмотр отчетов'),
           ('PROCUREMENT.SELECT_ANALOGS', 'Подбор аналогов товаров'),
           ('PROCUREMENT.EDIT_NONDEALER_POSITIONS', 'Редактирование позиций закупки не у дилера'),
           ('CONTRACTOR.CHECK_RELIABILITY', 'Проверка надежности контрагента'),
           ('CONTRACTOR.VIEW_REPORTS', 'Просмотр отчетов по контрагенту'),
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
           ('PROCUREMENT_SPECIALIST', 'Сотрудник снабжения', TRUE),
           ('LAWYER', 'Юрист', TRUE),
           ('STOREKEEPER', 'Кладовщик', TRUE),
           ('RBAC_ADMIN', 'Администратор RBAC', TRUE)
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

        ('SALES_MANAGER', 'TENDER.SEARCH'),
        ('SALES_MANAGER', 'OFFER.CALCULATE'),
        ('SALES_MANAGER', 'OFFER.EDIT'),
        ('SALES_MANAGER', 'OFFER.GENERATE_CP'),
        ('SALES_MANAGER', 'OFFER.SUBMIT'),
        ('SALES_MANAGER', 'TENDER_FILTER.WRITE'),

        ('PROCUREMENT_SPECIALIST', 'PROCUREMENT.SELECT_ANALOGS'),
        ('PROCUREMENT_SPECIALIST', 'PROCUREMENT.EDIT_NONDEALER_POSITIONS'),

        ('LAWYER', 'CONTRACTOR.CHECK_RELIABILITY'),
        ('LAWYER', 'CONTRACTOR.VIEW_REPORTS'),

        ('STOREKEEPER', 'INVENTORY.NOLIQUID.VIEW'),
        ('STOREKEEPER', 'INVENTORY.NOLIQUID.MANAGE'),

        ('RBAC_ADMIN', 'RBAC.ROLE.READ'),
        ('RBAC_ADMIN', 'RBAC.ROLE.WRITE'),
        ('RBAC_ADMIN', 'RBAC.ROLE.DELETE'),
        ('RBAC_ADMIN', 'RBAC.USER.READ'),
        ('RBAC_ADMIN', 'RBAC.USER.WRITE'),
        ('RBAC_ADMIN', 'RBAC.PERMISSION.READ')
)
INSERT
INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM roles r
         JOIN mapping m ON m.role_code = r.code
         JOIN permission p ON p.code = m.permission_code
ON CONFLICT DO NOTHING;
