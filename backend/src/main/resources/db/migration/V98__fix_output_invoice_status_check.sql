-- V98: 扩展 t_output_invoice.status 约束，支持 PENDING_CONFIRM 等状态（与 t_input_invoice 对齐）

-- 1. 添加缺失的状态 CHECK 约束（当前只有 DRAFT/ISSUED/VOID/RED_INK，缺少 PENDING_CONFIRM 等）
ALTER TABLE t_output_invoice 
  DROP CONSTRAINT IF EXISTS chk_output_invoice_status;

ALTER TABLE t_output_invoice
  ADD CONSTRAINT chk_output_invoice_status
  CHECK (status::text = ANY (ARRAY[
    'PENDING_CONFIRM'::text,
    'PENDING_REVIEW'::text,
    'CONFIRMED'::text,
    'VOUCHERED'::text,
    'ISSUED'::text,
    'VOID'::text,
    'RED_INK'::text,
    'REVERSED'::text
  ]));

-- 2. 为已有数据设置默认状态（防止已有 NULL 或旧值违反新约束）
UPDATE t_output_invoice
SET status = 'PENDING_CONFIRM'
WHERE status IS NULL OR status NOT IN (
  'PENDING_CONFIRM','PENDING_REVIEW','CONFIRMED','VOUCHERED',
  'ISSUED','VOID','RED_INK','REVERSED'
);
