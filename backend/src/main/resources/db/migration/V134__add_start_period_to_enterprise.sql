-- ============================================================
-- V134: t_enterprise 增加建账期间 start_period
--
-- 背景：
--   系统原无"企业建账期间"概念，全链路隐含假设"建账从当前月开始"：
--   - 前端 11 处写死 dayjs().format('YYYYMM') 作为默认期间
--   - 期初建账页默认选中最新期间，用户退后几年建账时误录
--   - 过账校验用"最早期期间"判断，企业从中间期间建账时失效
--
-- 方案：
--   start_period 记录企业实际建账起点（YYYYMM），期初建账成功时自动回填，
--   前端默认期间、过账校验均基于该字段。NULL 表示未建账（存量企业兼容）。
-- ============================================================

ALTER TABLE t_enterprise
    ADD COLUMN IF NOT EXISTS start_period VARCHAR(6);

COMMENT ON COLUMN t_enterprise.start_period IS
    '建账期间(YYYYMM): 期初建账成功时自动回填, NULL=未建账(存量企业兼容)';

-- 存量回填：已存在期初余额数据的企业视为已建账，取最早余额期间
UPDATE t_enterprise e
   SET start_period = sub.min_period
  FROM (
        SELECT sb.enterprise_id, MIN(sb.period) AS min_period
          FROM t_subject_balance sb
         GROUP BY sb.enterprise_id
       ) sub
 WHERE e.id = sub.enterprise_id
   AND e.start_period IS NULL
   AND sub.min_period IS NOT NULL;
