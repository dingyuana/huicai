-- ============================================================
-- V6: 固定资产模块
-- 资产类别、资产卡片、变动、折旧、盘点、处置
-- ============================================================

-- 1. 资产类别表
CREATE TABLE IF NOT EXISTS t_asset_category (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    parent_id       BIGINT,
    level           INTEGER      NOT NULL DEFAULT 1,
    depreciation_method VARCHAR(20) DEFAULT 'STRAIGHT_LINE',
    useful_life     INTEGER      DEFAULT 5,
    residual_rate   NUMERIC(5,4) DEFAULT 0.05,
    asset_subject_id BIGINT,
    depreciation_subject_id BIGINT,
    expense_subject_id BIGINT,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_asset_category_code UNIQUE (code),
    CONSTRAINT chk_dep_method CHECK (depreciation_method IN ('STRAIGHT_LINE', 'DOUBLE_DECLINING', 'SUM_OF_YEARS'))
);

COMMENT ON TABLE  t_asset_category IS '资产类别表';
COMMENT ON COLUMN t_asset_category.depreciation_method IS '折旧方法: STRAIGHT_LINE-平均年限法, DOUBLE_DECLINING-双倍余额法, SUM_OF_YEARS-年数总和法';
COMMENT ON COLUMN t_asset_category.residual_rate IS '残值率';
COMMENT ON COLUMN t_asset_category.asset_subject_id IS '资产科目ID(固定资产)';
COMMENT ON COLUMN t_asset_category.depreciation_subject_id IS '累计折旧科目ID';
COMMENT ON COLUMN t_asset_category.expense_subject_id IS '折旧费用科目ID';

-- 2. 资产卡片表
CREATE TABLE IF NOT EXISTS t_asset_card (
    id              BIGINT PRIMARY KEY,
    asset_code      VARCHAR(32)  NOT NULL,
    asset_name      VARCHAR(200) NOT NULL,
    category_id     BIGINT       NOT NULL,
    spec            VARCHAR(200),
    dept_id         BIGINT,
    custodian_id    BIGINT,
    acquisition_date DATE         NOT NULL,
    original_value  NUMERIC(18,2) NOT NULL,
    residual_value  NUMERIC(18,2) NOT NULL DEFAULT 0,
    useful_life     INTEGER      NOT NULL,
    depreciation_method VARCHAR(20) DEFAULT 'STRAIGHT_LINE',
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_USE',
    location        VARCHAR(200),
    serial_no       VARCHAR(100),
    remark          VARCHAR(500),
    accumulated_depreciation NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_value       NUMERIC(18,2) NOT NULL DEFAULT 0,
    last_depreciation_period VARCHAR(6),
    voucher_id      BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_asset_code UNIQUE (asset_code),
    CONSTRAINT fk_asset_category FOREIGN KEY (category_id) REFERENCES t_asset_category(id),
    CONSTRAINT chk_asset_status CHECK (status IN ('IN_USE', 'IDLE', 'DISPOSED', 'SCRAPPED'))
);

CREATE INDEX IF NOT EXISTS idx_asset_status ON t_asset_card(status);
CREATE INDEX IF NOT EXISTS idx_asset_category ON t_asset_card(category_id);
CREATE INDEX IF NOT EXISTS idx_asset_acq_date ON t_asset_card(acquisition_date);

COMMENT ON TABLE  t_asset_card IS '资产卡片';
COMMENT ON COLUMN t_asset_card.status IS '状态: IN_USE-在用, IDLE-闲置, DISPOSED-已处置, SCRAPPED-已报废';
COMMENT ON COLUMN t_asset_card.accumulated_depreciation IS '累计折旧';
COMMENT ON COLUMN t_asset_card.net_value IS '净值';
COMMENT ON COLUMN t_asset_card.last_depreciation_period IS '最后折旧期间(YYYYMM)';

-- 3. 资产变动记录表
CREATE TABLE IF NOT EXISTS t_asset_change (
    id              BIGINT PRIMARY KEY,
    asset_id        BIGINT       NOT NULL,
    change_type     VARCHAR(20)  NOT NULL,
    before_value    VARCHAR(500),
    after_value     VARCHAR(500),
    change_date     DATE         NOT NULL,
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_change_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT chk_change_type CHECK (change_type IN ('VALUE_ADJUST', 'DEPT_TRANSFER', 'STATUS_CHANGE', 'DEPRECIATION'))
);

CREATE INDEX IF NOT EXISTS idx_change_asset ON t_asset_change(asset_id);
CREATE INDEX IF NOT EXISTS idx_change_date ON t_asset_change(change_date);

COMMENT ON TABLE  t_asset_change IS '资产变动记录';
COMMENT ON COLUMN t_asset_change.change_type IS '变动类型: VALUE_ADJUST-原值调整, DEPT_TRANSFER-部门转移, STATUS_CHANGE-状态变更, DEPRECIATION-折旧';

-- 4. 资产折旧明细表
CREATE TABLE IF NOT EXISTS t_asset_depreciation (
    id              BIGINT PRIMARY KEY,
    asset_id        BIGINT       NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    depreciation_amount NUMERIC(18,2) NOT NULL,
    accumulated_depreciation NUMERIC(18,2) NOT NULL,
    net_value       NUMERIC(18,2) NOT NULL,
    voucher_id      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dep_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT uq_dep_asset_period UNIQUE (asset_id, period)
);

CREATE INDEX IF NOT EXISTS idx_dep_period ON t_asset_depreciation(period);

COMMENT ON TABLE  t_asset_depreciation IS '资产折旧明细';

-- 5. 资产盘点表
CREATE TABLE IF NOT EXISTS t_asset_inventory (
    id              BIGINT PRIMARY KEY,
    inventory_no    VARCHAR(32)  NOT NULL,
    inventory_date  DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    total_count     INTEGER      NOT NULL DEFAULT 0,
    profit_count    INTEGER      NOT NULL DEFAULT 0,
    loss_count      INTEGER      NOT NULL DEFAULT 0,
    voucher_id      BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_no UNIQUE (inventory_no),
    CONSTRAINT chk_inv_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'VOUCHERED'))
);

COMMENT ON TABLE  t_asset_inventory IS '资产盘点单';

-- 6. 资产盘点明细表
CREATE TABLE IF NOT EXISTS t_asset_inventory_entry (
    id              BIGINT PRIMARY KEY,
    inventory_id    BIGINT       NOT NULL,
    asset_id        BIGINT       NOT NULL,
    book_quantity   INTEGER      NOT NULL DEFAULT 1,
    actual_quantity INTEGER      NOT NULL DEFAULT 0,
    diff_quantity   INTEGER      NOT NULL DEFAULT 0,
    diff_type       VARCHAR(10),
    diff_amount     NUMERIC(18,2),
    remark          VARCHAR(500),
    CONSTRAINT fk_inv_entry_inv FOREIGN KEY (inventory_id) REFERENCES t_asset_inventory(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_entry_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT chk_diff_type CHECK (diff_type IS NULL OR diff_type IN ('PROFIT', 'LOSS', 'NORMAL'))
);

CREATE INDEX IF NOT EXISTS idx_inv_entry_inv ON t_asset_inventory_entry(inventory_id);

COMMENT ON TABLE  t_asset_inventory_entry IS '资产盘点明细';

-- 7. 资产处置表
CREATE TABLE IF NOT EXISTS t_asset_disposal (
    id              BIGINT PRIMARY KEY,
    disposal_no     VARCHAR(32)  NOT NULL,
    asset_id        BIGINT       NOT NULL,
    disposal_type   VARCHAR(20)  NOT NULL,
    disposal_date   DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    original_value  NUMERIC(18,2) NOT NULL,
    accumulated_depreciation NUMERIC(18,2) NOT NULL,
    net_value       NUMERIC(18,2) NOT NULL,
    disposal_income NUMERIC(18,2) NOT NULL DEFAULT 0,
    disposal_expense NUMERIC(18,2) NOT NULL DEFAULT 0,
    gain_loss       NUMERIC(18,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    voucher_id      BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_disposal_no UNIQUE (disposal_no),
    CONSTRAINT fk_disposal_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT chk_disposal_type CHECK (disposal_type IN ('SCRAP', 'SALE', 'DONATE', 'INV_LOSS')),
    CONSTRAINT chk_disposal_status CHECK (status IN ('DRAFT', 'APPROVED', 'VOUCHERED'))
);

COMMENT ON TABLE  t_asset_disposal IS '资产处置单';
COMMENT ON COLUMN t_asset_disposal.disposal_type IS '处置类型: SCRAP-报废, SALE-出售, DONATE-捐赠, INV_LOSS-盘亏';
COMMENT ON COLUMN t_asset_disposal.gain_loss IS '处置损益(收入-净值-费用)';
