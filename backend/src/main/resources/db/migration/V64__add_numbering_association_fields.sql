-- V64__add_numbering_association_fields.sql
-- 编号关联体系字段补充
-- 日期: 2026-06-28
-- 说明: 补充编号冗余字段，实现全链路双向追溯

-- ============================================
-- 0. t_output_invoice 表补充字段
-- ============================================

-- 补充 doc_no 字段（业务单据编号，冗余存储）
ALTER TABLE t_output_invoice
ADD COLUMN doc_no VARCHAR(64);
COMMENT ON COLUMN t_output_invoice.doc_no IS '业务单据编号';

-- 补充 voucher_no 字段（凭证编号，冗余存储）
ALTER TABLE t_output_invoice
ADD COLUMN voucher_no VARCHAR(64);
COMMENT ON COLUMN t_output_invoice.voucher_no IS '凭证编号';

-- 创建索引
CREATE INDEX idx_output_invoice_doc_no ON t_output_invoice(doc_no);
CREATE INDEX idx_output_invoice_voucher_no ON t_output_invoice(voucher_no);

-- ============================================
-- 1. t_input_invoice 表补充字段
-- ============================================

-- 补充 doc_no 字段（业务单据编号，冗余存储）
ALTER TABLE t_input_invoice
ADD COLUMN doc_no VARCHAR(64);
COMMENT ON COLUMN t_input_invoice.doc_no IS '业务单据编号';

-- 补充 voucher_no 字段（凭证编号，冗余存储）
ALTER TABLE t_input_invoice
ADD COLUMN voucher_no VARCHAR(64);
COMMENT ON COLUMN t_input_invoice.voucher_no IS '凭证编号';

-- 创建索引
CREATE INDEX idx_input_invoice_doc_no ON t_input_invoice(doc_no);
CREATE INDEX idx_input_invoice_voucher_no ON t_input_invoice(voucher_no);

-- ============================================
-- 2. t_receivable 表补充字段
-- ============================================

-- 补充 doc_no 字段（业务单据编号，冗余存储）
ALTER TABLE t_receivable
ADD COLUMN doc_no VARCHAR(64);
COMMENT ON COLUMN t_receivable.doc_no IS '业务单据编号';

-- 补充 voucher_no 字段（凭证编号，冗余存储）
ALTER TABLE t_receivable
ADD COLUMN voucher_no VARCHAR(64);
COMMENT ON COLUMN t_receivable.voucher_no IS '凭证编号';

-- 补充 invoice_no 字段（发票编号，冗余存储）
ALTER TABLE t_receivable
ADD COLUMN invoice_no VARCHAR(64);
COMMENT ON COLUMN t_receivable.invoice_no IS '发票编号';

-- 创建索引
CREATE INDEX idx_receivable_doc_no ON t_receivable(doc_no);
CREATE INDEX idx_receivable_voucher_no ON t_receivable(voucher_no);
CREATE INDEX idx_receivable_invoice_no ON t_receivable(invoice_no);

-- ============================================
-- 3. t_payable 表补充字段
-- ============================================

-- 补充 doc_no 字段（业务单据编号，冗余存储）
ALTER TABLE t_payable
ADD COLUMN doc_no VARCHAR(64);
COMMENT ON COLUMN t_payable.doc_no IS '业务单据编号';

-- 补充 voucher_no 字段（凭证编号，冗余存储）
ALTER TABLE t_payable
ADD COLUMN voucher_no VARCHAR(64);
COMMENT ON COLUMN t_payable.voucher_no IS '凭证编号';

-- 补充 invoice_no 字段（发票编号，冗余存储）
ALTER TABLE t_payable
ADD COLUMN invoice_no VARCHAR(64);
COMMENT ON COLUMN t_payable.invoice_no IS '发票编号';

-- 创建索引
CREATE INDEX idx_payable_doc_no ON t_payable(doc_no);
CREATE INDEX idx_payable_voucher_no ON t_payable(voucher_no);
CREATE INDEX idx_payable_invoice_no ON t_payable(invoice_no);

-- ============================================
-- 4. t_voucher 表补充字段（溯源字段）
-- ============================================

-- 补充 source_doc_id 字段（溯源单据ID）
ALTER TABLE t_voucher
ADD COLUMN source_doc_id BIGINT;
COMMENT ON COLUMN t_voucher.source_doc_id IS '溯源单据ID';

-- 补充 source_doc_no 字段（溯源单据编号，冗余存储）
ALTER TABLE t_voucher
ADD COLUMN source_doc_no VARCHAR(64);
COMMENT ON COLUMN t_voucher.source_doc_no IS '溯源单据编号';

-- 补充 source_doc_type 字段（溯源单据类型）
ALTER TABLE t_voucher
ADD COLUMN source_doc_type VARCHAR(32);
COMMENT ON COLUMN t_voucher.source_doc_type IS '溯源单据类型: BUSINESS_DOC, OUTPUT_INVOICE, INPUT_INVOICE, RECEIVABLE, PAYABLE';

-- 创建索引
CREATE INDEX idx_voucher_source_doc_id ON t_voucher(source_doc_id);
CREATE INDEX idx_voucher_source_doc_no ON t_voucher(source_doc_no);
CREATE INDEX idx_voucher_source_doc_type ON t_voucher(source_doc_type);

-- ============================================
-- 5. t_arap_settlement 表补充字段（核销单凭证编号）
-- ============================================

-- 补充 voucher_no 字段（凭证编号，冗余存储）
ALTER TABLE t_arap_settlement
ADD COLUMN voucher_no VARCHAR(64);
COMMENT ON COLUMN t_arap_settlement.voucher_no IS '凭证编号';

-- 创建索引
CREATE INDEX idx_arap_settlement_voucher_no ON t_arap_settlement(voucher_no);

-- ============================================
-- 5. t_business_doc 表补充字段
-- ============================================

-- 补充 voucher_no 字段（凭证编号，冗余存储）
ALTER TABLE t_business_doc
ADD COLUMN voucher_no VARCHAR(64);
COMMENT ON COLUMN t_business_doc.voucher_no IS '凭证编号';

-- 创建索引
CREATE INDEX idx_business_doc_voucher_no ON t_business_doc(voucher_no);

-- ============================================
-- 6. 核销明细表索引补充（追溯查询优化）
-- ============================================

-- 核销明细按应收单 ID 查询（追溯：应收单 → 核销单）
CREATE INDEX IF NOT EXISTS idx_settle_entry_receivable ON t_arap_settlement_entry(receivable_id);

-- 核销明细按应付单 ID 查询（追溯：应付单 → 核销单）
CREATE INDEX IF NOT EXISTS idx_settle_entry_payable ON t_arap_settlement_entry(payable_id);

-- ============================================
-- 历史数据补全脚本说明
-- ============================================
-- 注意：以下脚本用于补全历史数据，根据实际业务数据执行
-- 由于不同环境数据不同，这里仅提供示例模板
--
-- -- 补全 t_input_invoice.doc_no (通过 doc_id 关联 t_business_doc)
-- UPDATE t_input_invoice ii
-- JOIN t_business_doc bd ON ii.doc_id = bd.id
-- SET ii.doc_no = bd.doc_no
-- WHERE ii.doc_no IS NULL AND ii.doc_id IS NOT NULL;
--
-- -- 补全 t_input_invoice.voucher_no (通过 voucher_id 关联 t_voucher)
-- UPDATE t_input_invoice ii
-- JOIN t_voucher v ON ii.voucher_id = v.id
-- SET ii.voucher_no = v.voucher_no
-- WHERE ii.voucher_no IS NULL AND ii.voucher_id IS NOT NULL;
--
-- -- 其他表同理，根据实际关联关系补全
