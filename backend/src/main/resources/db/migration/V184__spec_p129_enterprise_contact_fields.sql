-- SPEC-P129：企业建档补全联系信息
--
-- 背景：用户报障「新增企业只能填编码/名称/税号，没有地址、联系人、电话」。
--       实测 t_enterprise 实测无任何联系信息列，EnterpriseCreateDTO 亦只有 4 字段
--       ⇒ 业务需要而 DB/Entity/DTO 三处都没有。
--
-- ⚠️ 必须显式写类型：电话用 varchar（固话含区号/连字符），禁止 bigint；
--    邮箱同理（AGENTS §4.5 第 53 条：批量 DDL 前先看类型分布，别假设全库同型）。
--
-- ⚠️ 全部可空：建档是高频轻操作，强制填全是门槛倒退（SPEC-P129 §7 排除项）。

ALTER TABLE t_enterprise
    ADD COLUMN IF NOT EXISTS contact_name  varchar(64),
    ADD COLUMN IF NOT EXISTS contact_phone varchar(32),
    ADD COLUMN IF NOT EXISTS contact_email varchar(128),
    ADD COLUMN IF NOT EXISTS address       varchar(255);

COMMENT ON COLUMN t_enterprise.contact_name  IS '联系人';
COMMENT ON COLUMN t_enterprise.contact_phone IS '电话（手机或含区号固话）';
COMMENT ON COLUMN t_enterprise.contact_email IS '邮箱';
COMMENT ON COLUMN t_enterprise.address       IS '地址';
