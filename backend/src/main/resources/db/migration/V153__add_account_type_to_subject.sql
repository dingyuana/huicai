-- V153: t_subject 增加 account_type 列，支持资产负债表按流动/非流动/其他分类小计（P92-B）
-- 口径（老丁拍板 2026-09-24）：三分 + 科目表人工维护 + 小计行不折叠
--
-- 分类值（仅资产/负债适用，权益 4x / 成本 5x / 收入 6x 留 NULL）：
--   CURRENT_ASSET          流动资产：10 货币资金 / 11 结算备付金 / 12 应收款项 / 14 存货
--   NON_CURRENT_ASSET      非流动资产：13 在建工程 / 15 投资性房地产 /
--                          16 固定资产 / 17 无形资产 / 18 长期待摊费用 / 19 待处理财产损溢
--   CURRENT_LIABILITY      流动负债：20 短期借款 / 21 应付票据 / 22 其他应付款
--   NON_CURRENT_LIABILITY  非流动负债：24 长期借款 / 25 应付债券 / 27 长期应付 / 28 预计负债 / 29 递延收益
--   NULL                   权益(4x) / 成本(5x) / 收入 6x 不适用流动分类
--
-- 重要：14 段整体归流动资产。本套科目表的 1408 是"委托加工物资"（属存货），
-- 不是通用准则里的"持有待售资产"。科目分类必须按实际科目名称而非段码常识判断。
--
-- 默认值故意留 NULL 而非 CHECK NOT NULL：
--   1. 4x/5x/6x 科目在报表中不属于资产/负债的流动分类，强制归类会产生错误小计
--   2. 报表端对未分类的资产/负债科目走科目段兜底（P92B-BD3 要求不得静默丢弃），不靠本列判断
--   3. 人工维护是"修改默认值"，不是"从零填写"——迁移已按标准科目段灌入 seed
--
-- 5x 成本科目（50/51/52/54 存货类→流动、53 研发支出→非流动）在 DB 留 NULL，
-- 由报表端科目段兜底；人工在科目维护界面可覆盖为具体值。
--
-- 正则陷阱（BUG，已在生产库由 V154 修复存量）：PostgreSQL 的 ~ 是 POSIX 正则，
-- LIKE 的 % 通配符在正则里是字面量字符。写成 '^(10|11|12|14)%' 会匹配 0 行，
-- 而 UPDATE 0 行不算失败，Flyway 照样记 success——静默回填失败，
-- 列建成了但 412 条资产/负债 account_type 全为 NULL。
-- 必须写 '^(10|11|12|14)'，且不能用 $ 锚点（科目码 4 位以上）。

ALTER TABLE t_subject ADD COLUMN account_type VARCHAR(32);

COMMENT ON COLUMN t_subject.account_type IS
  '资产/负债流动分类：CURRENT_ASSET/NON_CURRENT_ASSET/CURRENT_LIABILITY/NON_CURRENT_LIABILITY；权益与成本收入类为 NULL';

-- 存量回填：按本套科目表实际结构回填（人工可在科目维护界面修改）
UPDATE t_subject SET account_type = CASE
    WHEN code ~ '^(10|11|12|14)'            THEN 'CURRENT_ASSET'
    WHEN code ~ '^(13|15|16|17|18|19)'      THEN 'NON_CURRENT_ASSET'
    WHEN code ~ '^(20|21|22)'               THEN 'CURRENT_LIABILITY'
    WHEN code ~ '^(24|25|27|28|29)'         THEN 'NON_CURRENT_LIABILITY'
    ELSE account_type
  END
WHERE deleted = 0
  AND code ~ '^(1|2)';
-- 注：2701 专项储备按会计准则属所有者权益（科目编号却以 2 开头），
-- 归 NON_CURRENT_LIABILITY 是编号口径。报表端另设"其他"兜底，不会静默丢弃。
