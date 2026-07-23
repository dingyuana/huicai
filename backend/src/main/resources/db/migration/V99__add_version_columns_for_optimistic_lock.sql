-- V99: 为 5 个核心写操作表添加 version 列，支持 MyBatis-Plus 乐观锁
-- 涉及: t_voucher, t_bank_statement, t_period, t_asset_disposal, t_output_invoice

ALTER TABLE t_voucher
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE t_bank_statement
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE t_period
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE t_asset_disposal
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE t_output_invoice
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1;