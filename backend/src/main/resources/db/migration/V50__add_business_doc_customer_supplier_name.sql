-- V50 — BusinessDoc 客户/供应商名称字段补齐
-- 依据: P27b 任务（关联查 customer_name/supplier_name 反查时的"软失败"兜底）
-- 实施时间: 2026-06-23 20:39:22 通过 psql 手工跑过，flyway_schema_history rank=50 已注册
-- 本文件为漂移修复（2026-06-24 P28），与 PG 现网 schema 保持一致
ALTER TABLE t_business_doc ADD COLUMN customer_name varchar(200);
ALTER TABLE t_business_doc ADD COLUMN supplier_name varchar(200);
COMMENT ON COLUMN t_business_doc.customer_name IS '客户名称（冗余字段，P27b 后代码已改用 customerId 关联查）';
COMMENT ON COLUMN t_business_doc.supplier_name IS '供应商名称（冗余字段，P27b 后代码已改用 supplierId 关联查）';
