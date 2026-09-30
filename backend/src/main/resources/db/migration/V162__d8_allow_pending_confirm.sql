-- V162: D8 Allow PENDING_CONFIRM in chk_stmt_match_status
-- 原 CHECK 不含 PENDING_CONFIRM，而 runMatching 60-84 分档写入该值
ALTER TABLE t_bank_statement DROP CONSTRAINT IF EXISTS chk_stmt_match_status;
ALTER TABLE t_bank_statement
  ADD CONSTRAINT chk_stmt_match_status
  CHECK ((match_status)::text = ANY (
    ARRAY['UNMATCHED'::character varying,
          'MATCHED'::character varying,
          'MANUAL_MATCHED'::character varying,
          'PENDING_CONFIRM'::character varying,
          'IGNORED'::character varying]
  ));
COMMENT ON CONSTRAINT chk_stmt_match_status ON t_bank_statement IS '银行流水匹配状态：UNMATCHED/MATCHED/MANUAL_MATCHED/PENDING_CONFIRM/IGNORED（V162 补充 PENDING_CONFIRM）';
