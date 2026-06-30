-- V70: 为 t_business_doc 和 t_voucher 添加 reversed_from 字段
-- 用途: 红冲链路溯源 — 业务单据/凭证指向被红冲的原始记录 ID

-- 业务单据：被哪张蓝字业务单据红冲
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS reversed_from BIGINT;
COMMENT ON COLUMN t_business_doc.reversed_from IS '被红冲的原始业务单据ID';
CREATE INDEX IF NOT EXISTS idx_business_doc_reversed_from
    ON t_business_doc(reversed_from);

-- 凭证：被哪张蓝字凭证红冲
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS reversed_from BIGINT;
COMMENT ON COLUMN t_voucher.reversed_from IS '被红冲的原始凭证ID';
CREATE INDEX IF NOT EXISTS idx_voucher_reversed_from
    ON t_voucher(reversed_from);
