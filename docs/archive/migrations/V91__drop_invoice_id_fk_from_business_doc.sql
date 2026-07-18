-- V91: 删除 t_business_doc.invoice_id 的外键约束
-- 
-- 问题：V71 将 invoice_id 外键约束指向 t_output_invoice，但业务单据既关联销项发票(INVOICE_OUT)也关联进项发票(INVOICE_IN)
-- 当进项发票审核创建业务单据时，写入的 invoice_id 来自 t_input_invoice，导致外键约束冲突：
--   ERROR: insert or update on table "t_business_doc" violates foreign key constraint "t_business_doc_invoice_id_fkey"
--   Detail: Key (invoice_id)=(1) is not present in table "t_output_invoice".
--
-- 解决方案：删除该外键约束。业务单据通过 docType + invoiceId 区分关联的发票类型，
-- 数据一致性由业务逻辑保证，而非数据库外键约束。

ALTER TABLE t_business_doc DROP CONSTRAINT IF EXISTS t_business_doc_invoice_id_fkey;