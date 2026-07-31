-- V121: 软删除表的唯一索引必须排除 deleted=1 行，否则孤儿记录永远阻塞同编号插入
-- 原因：autoGenerateInNewTx(REQUIRES_NEW) 外层事务回滚后孤儿凭证残留，
--       uq_voucher_no 是全量索引导致后续审核 INSERT 报 duplicate key

-- 先删除约束（uq_voucher_no 是 UNIQUE CONSTRAINT 创建的索引，不能直接 DROP INDEX）
ALTER TABLE t_voucher DROP CONSTRAINT IF EXISTS uq_voucher_no;
DROP INDEX IF EXISTS uq_voucher_no;
CREATE UNIQUE INDEX uq_voucher_no ON t_voucher (voucher_no) WHERE deleted = 0;

ALTER TABLE t_business_doc DROP CONSTRAINT IF EXISTS uq_doc_no_type;
DROP INDEX IF EXISTS uq_doc_no_type;
CREATE UNIQUE INDEX uq_doc_no_type ON t_business_doc (doc_type, doc_no) WHERE deleted = 0;
