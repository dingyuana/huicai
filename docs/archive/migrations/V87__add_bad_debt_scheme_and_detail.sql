-- ============================================================
-- V87: 坏账计提方案表 + 计提明细表 + provision 表扩展（P43）
-- ============================================================
BEGIN;

-- 1. 计提方案表
CREATE TABLE IF NOT EXISTS t_bad_debt_provision_scheme (
    id              BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name            VARCHAR(100)  NOT NULL,
    is_default      BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  t_bad_debt_provision_scheme IS '坏账计提方案';
COMMENT ON COLUMN t_bad_debt_provision_scheme.is_default IS '是否默认方案';

-- 2. 计提方案区间明细
CREATE TABLE IF NOT EXISTS t_bad_debt_provision_scheme_item (
    id              BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    scheme_id       BIGINT        NOT NULL REFERENCES t_bad_debt_provision_scheme(id),
    aging_from      INTEGER,       -- 起始天数（含），NULL=无下限
    aging_to        INTEGER,       -- 结束天数（不含），NULL=无上限
    label           VARCHAR(50)   NOT NULL,
    ratio           NUMERIC(5,4)  NOT NULL,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  t_bad_debt_provision_scheme_item IS '计提方案区间明细';
COMMENT ON COLUMN t_bad_debt_provision_scheme_item.ratio IS '计提比例 0-1';

-- 3. 预置默认计提方案
INSERT INTO t_bad_debt_provision_scheme (name, is_default, is_active)
SELECT '系统默认方案', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_bad_debt_provision_scheme WHERE is_default = TRUE);

-- 插入默认区间比例
DO $$
DECLARE
    v_scheme_id BIGINT;
BEGIN
    SELECT id INTO v_scheme_id FROM t_bad_debt_provision_scheme WHERE is_default = TRUE LIMIT 1;
    IF v_scheme_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM t_bad_debt_provision_scheme_item WHERE scheme_id = v_scheme_id) THEN
        INSERT INTO t_bad_debt_provision_scheme_item (scheme_id, aging_from, aging_to, label, ratio, sort_order) VALUES
            (v_scheme_id, 0,    0,    '信用期内',   0.0000, 1),
            (v_scheme_id, 1,    31,   '1-30天',     0.0500, 2),
            (v_scheme_id, 31,   61,   '31-60天',    0.2000, 3),
            (v_scheme_id, 61,   91,   '61-90天',    0.5000, 4),
            (v_scheme_id, 91,   181,  '91-180天',   0.8000, 5),
            (v_scheme_id, 181,  366,  '181-365天',  1.0000, 6),
            (v_scheme_id, 366,  NULL, '365天以上',   1.0000, 7);
    END IF;
END $$;

-- 4. 计提明细表
CREATE TABLE IF NOT EXISTS t_bad_debt_provision_detail (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    provision_id        BIGINT        NOT NULL REFERENCES t_bad_debt_provision(id),
    source_type         VARCHAR(30)   NOT NULL,
    source_id           BIGINT,
    source_no           VARCHAR(64),
    customer_name       VARCHAR(200),
    due_date            DATE,
    aging_days          INTEGER       NOT NULL,
    bucket_label        VARCHAR(50)   NOT NULL,
    unsettled_amount    NUMERIC(18,2) NOT NULL,
    provision_amount    NUMERIC(18,2) NOT NULL,
    ratio               NUMERIC(5,4)  NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bad_debt_detail_provision ON t_bad_debt_provision_detail(provision_id);
CREATE INDEX idx_bad_debt_detail_source    ON t_bad_debt_provision_detail(source_type, source_id);

COMMENT ON TABLE  t_bad_debt_provision_detail IS '坏账计提明细';
COMMENT ON COLUMN t_bad_debt_provision_detail.source_type IS 'INVOICE_OUT/PREPAYMENT/OTHER_RECEIVABLE/NOTE_RECEIVABLE';
COMMENT ON COLUMN t_bad_debt_provision_detail.aging_days IS '截至计提日的逾期天数';

-- 5. t_bad_debt_provision 扩展字段
ALTER TABLE t_bad_debt_provision
  ADD COLUMN IF NOT EXISTS expected_balance     NUMERIC(18,2),
  ADD COLUMN IF NOT EXISTS existing_balance     NUMERIC(18,2),
  ADD COLUMN IF NOT EXISTS adjustment_amount    NUMERIC(18,2),
  ADD COLUMN IF NOT EXISTS adjustment_type      VARCHAR(10),
  ADD COLUMN IF NOT EXISTS scheme_id            BIGINT;

COMMENT ON COLUMN t_bad_debt_provision.expected_balance IS '应有余额（按账龄计算）';
COMMENT ON COLUMN t_bad_debt_provision.existing_balance IS '科目已有余额';
COMMENT ON COLUMN t_bad_debt_provision.adjustment_amount IS '补提/冲回金额';
COMMENT ON COLUMN t_bad_debt_provision.adjustment_type IS 'PROVISION-补提, REVERSAL-冲回';

COMMIT;