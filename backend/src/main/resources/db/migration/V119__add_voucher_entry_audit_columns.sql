-- V119: t_voucher_entry 补充审计字段
-- BaseEntity 定义了 updatedAt 和 deleted 字段，但 t_voucher_entry 表缺少这两列。
-- MyBatis-Plus 的 @TableField(fill = FieldFill.INSERT_UPDATE) 和 @TableLogic 依赖这些列。

ALTER TABLE t_voucher_entry
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS deleted     INTEGER                  NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_voucher_entry.updated_at IS '更新时间';
COMMENT ON COLUMN t_voucher_entry.deleted    IS '逻辑删除：0=正常，1=删除';