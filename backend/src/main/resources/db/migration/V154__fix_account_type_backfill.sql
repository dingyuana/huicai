-- V154: 修复 V153 存量回填静默失败（P92-B BUG）
--
-- 根因：V153 把 LIKE 的 % 通配符写进了 PostgreSQL 正则——
--     code ~ '^(10|11|12|14)%'
-- 在 POSIX 正则中 % 是字面量字符，科目码里没有 %，四条 UPDATE 全部匹配 0 行。
-- 而 UPDATE 0 行不算失败，Flyway 照样记 success，导致列建成但存量数据
-- account_type 全为 NULL（生产库实测：1x 资产 280 条、2x 负债 132 条全未分类）。
--
-- 静默失败的原因链：migration 无影响行数断言 + Flyway 只报 SQL 执行错误。
-- 本文件末尾补一条 DO 断言，让同类问题以后在 migration 阶段就失败暴露。
--
-- V153 已改为修正版（供全新库使用）；本文件负责修复已应用过 V153 的存量库。
-- 回填规则与 V153 一致，均按本套科目表实际结构：14 段整体归流动
--（1408 在本套科目表是"委托加工物资"属存货，非通用准则的"持有待售资产"）。
-- 重复执行无害（只覆盖 1x/2x 段，CASE 幂等）。

UPDATE t_subject SET account_type = CASE
    WHEN code ~ '^(10|11|12|14)'            THEN 'CURRENT_ASSET'
    WHEN code ~ '^(13|15|16|17|18|19)'      THEN 'NON_CURRENT_ASSET'
    WHEN code ~ '^(20|21|22)'               THEN 'CURRENT_LIABILITY'
    WHEN code ~ '^(24|25|27|28|29)'         THEN 'NON_CURRENT_LIABILITY'
    ELSE account_type
  END
WHERE deleted = 0
  AND code ~ '^(1|2)';

-- 断言：回填后资产/负债段不得有未分类项（权益 4x/成本 5x/收入 6x 留 NULL 是预期）
-- 异常时使 migration 失败，避免再次静默通过。
DO $$
DECLARE
    unclassified INT;
    checked INT;
BEGIN
    SELECT count(*) INTO checked FROM t_subject
    WHERE deleted = 0 AND code ~ '^(1|2)';

    SELECT count(*) INTO unclassified
    FROM t_subject
    WHERE deleted = 0
      AND code ~ '^(1|2)'
      AND account_type IS NULL;

    IF unclassified > 0 THEN
        RAISE EXCEPTION 'V154 回填不完整：%/% 条资产负债科目未分类，剩余编号: %',
            unclassified, checked,
            (SELECT string_agg(DISTINCT LEFT(code, 2), ',')
             FROM t_subject WHERE deleted = 0 AND code ~ '^(1|2)' AND account_type IS NULL);
    END IF;
    RAISE NOTICE 'V154 回填校验通过：% 条资产/负债全部已分类', checked;
END $$;
