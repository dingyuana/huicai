-- ============================================================
-- V92: 修复 t_subject 表 id 列缺少自增属性
-- Subject 实体使用 @TableId(type = IdType.AUTO) 依赖自增
-- 但 V1 baseline 中 id 列定义为 BIGINT PRIMARY KEY 而非 IDENTITY
-- 导致 importStandard() 插入时因 id=null 报错
-- ============================================================

-- 由于表为空（无数据），直接修改列类型
ALTER TABLE t_subject
    ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;