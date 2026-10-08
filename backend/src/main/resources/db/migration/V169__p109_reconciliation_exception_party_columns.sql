-- ============================================================
-- V169: P109 —— 把 t_reconciliation_exception 从「银行账户-centric」补齐到「往来单位-centric」
--
-- 关联：REQ-2026-138 / SPC-P109 §0（取证）/ §2 D-109-1（裁定：表迁就 Entity）
-- 三方对照：PG（本文件）↔ Entity（ReconciliationExceptionEntity）↔ 业务代码（ReconciliationServiceImpl）
--
-- 【缺陷背景（真库实测，非读 migration 推断）】
--   取证载体 ReconciliationExceptionEntityDbProbeTest（4 例全绿 = 缺陷坐实）：
--     F1 createException() 写入必挂：
--        ERROR: null value in column "account_id" of relation
--        "t_reconciliation_exception" violates not-null constraint
--     F2 retryException() 对任何记录都抛「异常记录缺少目标单据信息」——
--        ReconciliationServiceImpl:972 读的 targetDocType/targetDocId 是
--        @TableField(exist=false)，读回恒 null
--     F3 表 count(*) = 0 ⇒ 缺陷从未被真实写入触发
--   根因（AGENTS §4.5 第 5 条同型）：ReconciliationExceptionEntity 被整体重写成
--   「往来单位-centric」，但**配套的 migration 从未写过**。V1 baseline 建的是
--   「银行账户-centric」（account_id / description / period），两者从未对齐。
--   ⇒ 15 处 @TableField(exist=false) 是**症状**不是病因；只删注解会让报错
--      从「撞 NOT NULL」变成「Unknown column」，问题原地不动。
--
-- 【为什么这么修（老丁裁定 D-109-1 = 方案 A）】
--   Entity / Service / 前端页面三者都已是「往来单位-centric」，只有表停在旧模型；
--   且表 0 行（F3）⇒ 无历史数据回填负担，是成本最低的一条路。
--   account_id / period 是银行流水时代的遗留语义，降为可空而非删除（非破坏性 DDL，
--   AGENTS §7），保留列以便将来若需回溯银行账户视角仍有据可依。
--
-- 【幂等】全部 ADD COLUMN IF NOT EXISTS / DROP NOT IF EXISTS，可重复执行。
--
-- 【不做】
--   - 不删 account_id / period / description 三列（非破坏性，且删列需老丁单独确认）
--   - 不改 chk_exception_type 的允许集（D-109-2 裁定为「改前端对齐 DB」，后端零改动）
--   - 不碰 t_reconciliation_log（那是另一张表，P106 批次 1a-2 已收口）
-- ============================================================

-- ---------- ① 补齐往来单位-centric 的列 ----------
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS source_doc_type   VARCHAR(50);
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS source_doc_id     BIGINT;
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS target_doc_type   VARCHAR(50);
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS target_doc_id     BIGINT;
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS party_id          BIGINT;
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS party_type        VARCHAR(20);
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS unsettled_amount  NUMERIC(18,2);
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS exception_reason  VARCHAR(500);
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS match_suggestion  VARCHAR(500);
-- retry_count 前端 {{ row.retryCount ?? 0 }} 已在用；给默认值以免 Service 未赋值时插入失败
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS retry_count       INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS assigned_to       BIGINT;
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS remark            VARCHAR(500);
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS created_by        BIGINT;

COMMENT ON COLUMN t_reconciliation_exception.source_doc_type  IS '来源单据类型（RECEIPT/PAYMENT/BUSINESS_DOC/INVOICE 等）';
COMMENT ON COLUMN t_reconciliation_exception.source_doc_id    IS '来源单据 ID';
COMMENT ON COLUMN t_reconciliation_exception.target_doc_type  IS '目标单据类型；retryException 依赖它，非空才可重试';
COMMENT ON COLUMN t_reconciliation_exception.target_doc_id    IS '目标单据 ID；retryException 依赖它';
COMMENT ON COLUMN t_reconciliation_exception.party_id         IS '往来单位 ID（客户或供应商）';
COMMENT ON COLUMN t_reconciliation_exception.party_type       IS '往来单位类型：CUSTOMER / VENDOR';
COMMENT ON COLUMN t_reconciliation_exception.unsettled_amount IS '未核销金额';
COMMENT ON COLUMN t_reconciliation_exception.exception_reason IS '异常原因（替代遗留的 description）';
COMMENT ON COLUMN t_reconciliation_exception.match_suggestion IS '系统给出的匹配建议';
COMMENT ON COLUMN t_reconciliation_exception.retry_count      IS '已重试次数；前端展示用';
COMMENT ON COLUMN t_reconciliation_exception.assigned_to      IS '指派处理人';
COMMENT ON COLUMN t_reconciliation_exception.remark           IS '处理备注';
COMMENT ON COLUMN t_reconciliation_exception.created_by       IS '创建人';

-- ---------- ② 遗留必填列降为可空 ----------
-- account_id：银行账户视角遗留。可空化后，Entity 无需映射该字段即可插入。
-- period：同理；核销异常按期间筛选不是当前页面的需求（页面只按 status/exceptionType 筛）。
ALTER TABLE t_reconciliation_exception ALTER COLUMN account_id DROP NOT NULL;
ALTER TABLE t_reconciliation_exception ALTER COLUMN period     DROP NOT NULL;

COMMENT ON COLUMN t_reconciliation_exception.account_id IS
    '【P109 已降级为可空】银行流水时代遗留字段；往来单位视角请用 party_id/party_type。FK 保留以便回溯';
COMMENT ON COLUMN t_reconciliation_exception.period IS
    '【P109 已降级为可空】银行流水时代遗留字段；异常池当前不按期间筛选';
COMMENT ON COLUMN t_reconciliation_exception.description IS
    '【遗留】银行流水时代字段，语义已被 exception_reason 取代；Entity 未映射，保持不写入';

-- ---------- ③ 支撑「异常池」页面的查询形状 ----------
-- 页面按 status + exception_type 过滤并按 created_at 倒序；原表只有主键索引。
CREATE INDEX IF NOT EXISTS idx_t_reconciliation_exception_status_type
    ON t_reconciliation_exception (status, exception_type, deleted);

COMMENT ON INDEX idx_t_reconciliation_exception_status_type IS
    'P109：异常池页面按 status/exception_type 过滤 + created_at 倒序（ReconciliationServiceImpl:921-924）';

-- ---------- ④ 便于按来源/目标单据回溯（页面「来源」列依赖 source_doc_*）----------
CREATE INDEX IF NOT EXISTS idx_t_reconciliation_exception_source
    ON t_reconciliation_exception (source_doc_type, source_doc_id);

COMMENT ON INDEX idx_t_reconciliation_exception_source IS
    'P109：页面「来源」列渲染 row.sourceDocType/sourceDocId，此前两列恒 null（幽灵字段）';
