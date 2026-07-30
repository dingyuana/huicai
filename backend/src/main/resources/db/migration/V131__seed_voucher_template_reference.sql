-- ============================================================
-- V131: 建立凭证模板参考库 (enterprise_id = 0)
--
-- 改动：
--   1. 将唯一约束 uq_tpl_code(template_code) 改为
--      uq_tpl_code_enterprise(template_code, enterprise_id)
--   2. 将现有模板复制到 enterprise_id = 0 作为参考库
-- ============================================================

-- ─── 1. 更新唯一约束：允许各企业有相同 template_code ───
ALTER TABLE t_voucher_template DROP CONSTRAINT IF EXISTS uq_tpl_code;
ALTER TABLE t_voucher_template ADD CONSTRAINT uq_tpl_code_enterprise UNIQUE (template_code, enterprise_id);

-- ─── 2. 复制模板到参考库 (enterprise_id = 0) ───
DO $$
DECLARE
    v_ref RECORD;
    v_new_id BIGINT;
    v_line RECORD;
    v_new_subject_id BIGINT;
BEGIN
    FOR v_ref IN
        SELECT * FROM t_voucher_template
        WHERE enterprise_id = 1 AND deleted = 0
        ORDER BY id
    LOOP
        -- 检查是否已存在（幂等）
        IF NOT EXISTS (
            SELECT 1 FROM t_voucher_template
            WHERE template_code = v_ref.template_code AND enterprise_id = 0
        ) THEN
            INSERT INTO t_voucher_template (template_code, template_name, doc_type, voucher_type_code, summary, entries, is_active, remark, enterprise_id)
            VALUES (v_ref.template_code, v_ref.template_name, v_ref.doc_type, v_ref.voucher_type_code, v_ref.summary, v_ref.entries, v_ref.is_active, v_ref.remark, 0)
            RETURNING id INTO v_new_id;

            -- 复制分录行，通过科目编码重新映射 subject_id
            FOR v_line IN
                SELECT * FROM t_voucher_template_line
                WHERE template_id = v_ref.id AND deleted = 0
                ORDER BY line_order, id
            LOOP
                -- 查找 enterprise_id = 0 下相同科目编码的 subject_id
                v_new_subject_id := NULL;
                BEGIN
                    SELECT id INTO STRICT v_new_subject_id FROM t_subject
                    WHERE code = (SELECT code FROM t_subject WHERE id = v_line.subject_id)
                      AND enterprise_id = 0;
                EXCEPTION WHEN NO_DATA_FOUND THEN
                    v_new_subject_id := v_line.subject_id;  -- 降级
                END;

                INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, enterprise_id)
                VALUES (v_new_id, COALESCE(v_new_subject_id, v_line.subject_id), v_line.dr_amount_template, v_line.cr_amount_template, v_line.summary_template, v_line.direction, v_line.assist_type, v_line.assist_required, v_line.line_order, 0);
            END LOOP;
        END IF;
    END LOOP;
END $$;