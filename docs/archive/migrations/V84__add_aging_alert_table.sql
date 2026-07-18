-- ============================================================
-- V84: 账龄分析与逾期预警（P51）
-- ============================================================
-- 说明：
--   创建账龄预警表 t_aging_alert，用于记录逾期应收款的预警信息。
--   账龄分析本身不存储中间结果，每次请求从业务单据实时计算。
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS t_aging_alert (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_id         BIGINT        NOT NULL,
    doc_id              BIGINT        NOT NULL,           -- 关联业务单据
    doc_no              VARCHAR(64),
    unsettled_amount    NUMERIC(18,2) NOT NULL,
    due_date            DATE          NOT NULL,
    overdue_days        INTEGER       NOT NULL,
    alert_level         VARCHAR(20)   NOT NULL,           -- MILD / MODERATE / SEVERE / CRITICAL
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / DISMISSED / RESOLVED
    notified_at         TIMESTAMP,
    dismissed_at        TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_aging_alert_customer   ON t_aging_alert(customer_id);
CREATE INDEX idx_aging_alert_status     ON t_aging_alert(status);
CREATE INDEX idx_aging_alert_level      ON t_aging_alert(alert_level);

COMMENT ON TABLE  t_aging_alert IS '账龄预警记录';
COMMENT ON COLUMN t_aging_alert.alert_level IS 'MILD-轻度, MODERATE-中度, SEVERE-严重, CRITICAL-极严重';
COMMENT ON COLUMN t_aging_alert.status IS 'ACTIVE-生效中, DISMISSED-已忽略, RESOLVED-已解决';

COMMIT;