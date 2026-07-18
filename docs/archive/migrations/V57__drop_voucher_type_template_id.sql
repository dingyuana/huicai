-- V57: 删除 t_voucher_type.template_id 列（凭证类型与模板解耦）
-- 2026-06-26
-- 背景: VoucherTypeEntity.templateId 字段让凭证类型与模板产生不必要的耦合。
--       选类型时自动加载模板的功能已被模板匹配引擎（source + businessType + direction）替代。
--       手工制证时只选类型、不自动加载模板，概念更清晰。
-- 影响: 删除列后，VoucherTypeEntity 不再包含 templateId 字段。

ALTER TABLE t_voucher_type DROP COLUMN IF EXISTS template_id;
