-- V22: 银行流水自动生成单据与凭证: 添加 generated_doc_id / generated_voucher_id

ALTER TABLE t_bank_statement
    ADD COLUMN IF NOT EXISTS generated_doc_id     BIGINT       DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS generated_voucher_id BIGINT       DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS generated_at         TIMESTAMP    DEFAULT NULL;

COMMENT ON COLUMN t_bank_statement.generated_doc_id     IS '生成的业务单据 ID (关联 t_business_doc)';
COMMENT ON COLUMN t_bank_statement.generated_voucher_id IS '生成的会计凭证 ID (关联 t_voucher)';
COMMENT ON COLUMN t_bank_statement.generated_at         IS '生成时间';