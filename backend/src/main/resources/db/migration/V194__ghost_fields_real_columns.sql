-- SPEC-P153 幽灵字段收口（A 类：insert/update 前对实体 setXxx 的落库意图 + 读回恒 null）。
-- 取证三判据（AGENTS §4.5 方法论，逐字段核真库 information_schema）：
--   ① 表有无该列（有列但实体 exist=false = 反向错位，只改实体不加列）
--   ② 写点在 insert/updateById 之前 = 落库意图（否则是查询回填派生，保留幽灵并注释）
--   ③ 读回方（VO/前端/后端逻辑）恒 null 的危害面
-- 全仓 79 个 exist=false 字段三判定性（真丢失 / 反向错位 / 查询回填派生保留）。
--
-- 刻意不建（诚实的空，防凭空空列）：
-- * t_voucher.reverse_reason —— 全仓无写方/读方（扫描器把 InputInvoiceEntity 的
--   setReverseReason 误配给 VoucherEntity，人工复核推翻）
-- * 发票 docNo/voucherNo/originalInvoiceId/reversedByInvoiceNo —— fillXxxDetails
--   查询时实时回填的派生字段，建列反而制造双真值源
-- * bank_statement.direction —— 由 tx_type 严格可派生
-- * bank_statement.ai_business_scene/ai_confidence/ai_suggested_action ——
--   AI 分类溯源字段，按用户裁定「AI 项目除外」暂缓（登记遗留）

-- ═══ 1) t_voucher：凭证溯源与驳回意见 ═══
-- 危害：红冲级联 cascadeReverseToSourceDocs 读 sourceDoc* 恒 null ⇒「红冲后回写
-- 源发票/单据状态为 REVERSED」对全部存量凭证从未生效；数字穿透 buildNextUpstream
-- 恒断链；按源单号筛凭证恒 0 行；驳回意见页端恒空。
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS source_doc_id    BIGINT;
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS source_doc_no    VARCHAR(64);
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS source_doc_type  VARCHAR(32);
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS template_id      BIGINT;
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS rejected_reason  VARCHAR(500);

COMMENT ON COLUMN t_voucher.source_doc_id   IS '溯源单据ID（生成该凭证的原始单据）——红冲级联回写源单状态依赖';
COMMENT ON COLUMN t_voucher.source_doc_no   IS '溯源单据编号（冗余，数字穿透/列表按源单号查询用）';
COMMENT ON COLUMN t_voucher.source_doc_type IS '溯源单据类型 BUSINESS_DOC/SETTLEMENT/OUTPUT_INVOICE/INPUT_INVOICE/TAX_DECLARATION';
COMMENT ON COLUMN t_voucher.template_id     IS '生成该凭证的凭证模板ID（配置驱动制证溯源）';
COMMENT ON COLUMN t_voucher.rejected_reason IS '驳回原因（审核驳回时填写，页端展示）';

-- 溯源三元组是「按源单查凭证」与穿透图的主访问路径
CREATE INDEX IF NOT EXISTS idx_voucher_source    ON t_voucher (source_doc_type, source_doc_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_voucher_source_no ON t_voucher (source_doc_no) WHERE deleted = 0;

-- 存量回补：business_doc_id 是真实列（一直有写），据此反推业务单据来源；
-- 发票/结算来源无残留信息，保持 NULL —— 不做猜测式回补。
UPDATE t_voucher
   SET source_doc_type = 'BUSINESS_DOC',
       source_doc_id   = business_doc_id,
       source_doc_no   = (SELECT d.doc_no FROM t_business_doc d
                           WHERE d.id = t_voucher.business_doc_id LIMIT 1)
 WHERE business_doc_id IS NOT NULL
   AND source_doc_id IS NULL;

-- ═══ 2) t_expense_reimbursement：报销审批链 ═══
-- 危害：前端 ExpenseList/ExpenseEdit 渲染的驳回原因/提交时间恒空；
-- findByBankStmtId 幂等防重查询查不存在列（PG 下 wrapper 直接 500 或恒假）
-- ⇒ 同一流水重复自动报账风险。
ALTER TABLE t_expense_reimbursement ADD COLUMN IF NOT EXISTS bank_stmt_id   BIGINT;
ALTER TABLE t_expense_reimbursement ADD COLUMN IF NOT EXISTS attachment_ids TEXT;
ALTER TABLE t_expense_reimbursement ADD COLUMN IF NOT EXISTS submitted_at   TIMESTAMP;
ALTER TABLE t_expense_reimbursement ADD COLUMN IF NOT EXISTS approved_at    TIMESTAMP;
ALTER TABLE t_expense_reimbursement ADD COLUMN IF NOT EXISTS approved_by    VARCHAR(64);
ALTER TABLE t_expense_reimbursement ADD COLUMN IF NOT EXISTS reject_reason  VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_expense_bank_stmt ON t_expense_reimbursement (bank_stmt_id)
    WHERE bank_stmt_id IS NOT NULL AND deleted = 0;

COMMENT ON COLUMN t_expense_reimbursement.bank_stmt_id   IS '来源银行流水ID（流水自动生成报销单的幂等键）';
COMMENT ON COLUMN t_expense_reimbursement.attachment_ids IS '附件ID列表（逗号分隔）';
COMMENT ON COLUMN t_expense_reimbursement.submitted_at   IS '提交时间';
COMMENT ON COLUMN t_expense_reimbursement.approved_at    IS '审批时间';
COMMENT ON COLUMN t_expense_reimbursement.approved_by    IS '审批人姓名（Service 传姓名而非 id，见 SPEC-P119 注释）';
COMMENT ON COLUMN t_expense_reimbursement.reject_reason  IS '驳回原因（页端红字提示）';

-- ═══ 3) t_bank_statement：审核留痕 + 自动制证回填 + 分类溯源 + 审计列 ═══
-- 反向错位半边：created_by/updated_by 列早已存在（V178 等），但实体用
-- @TableField(exist=false) 覆盖 BaseEntity（P117 修 createdBy 时漏了本表）
-- ⇒ 实体侧删除覆盖即可；created_at/updated_at 历史上就没有，此处补齐。
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS purpose              VARCHAR(255);
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS transaction_remark   VARCHAR(500);
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS rule_id              BIGINT;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS reviewed_by          BIGINT;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS reviewed_at          TIMESTAMP;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS generated_doc_id     BIGINT;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS generated_voucher_id BIGINT;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS generated_voucher_no VARCHAR(64);
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS generated_at         TIMESTAMP;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS batch_id             VARCHAR(64);
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS created_at           TIMESTAMP;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS updated_at           TIMESTAMP;

COMMENT ON COLUMN t_bank_statement.purpose              IS '银行附言（Excel 导入用途列）';
COMMENT ON COLUMN t_bank_statement.transaction_remark   IS '交易备注（Excel 导入备注列）';
COMMENT ON COLUMN t_bank_statement.rule_id              IS '命中的分类规则ID（分类结果可溯源到规则）';
COMMENT ON COLUMN t_bank_statement.reviewed_by          IS '确认/复核人（自动制证以复核人身份发起，见 autoGenerateInNewTx）';
COMMENT ON COLUMN t_bank_statement.reviewed_at          IS '确认/复核时间';
COMMENT ON COLUMN t_bank_statement.generated_doc_id     IS '自动生成的业务单据ID';
COMMENT ON COLUMN t_bank_statement.generated_voucher_id IS '自动生成的凭证ID';
COMMENT ON COLUMN t_bank_statement.generated_voucher_no IS '自动生成的凭证号（流水行直接展示）';
COMMENT ON COLUMN t_bank_statement.generated_at         IS '自动制证时间';
COMMENT ON COLUMN t_bank_statement.batch_id             IS 'Excel 导入批号（导入批次追溯）';
COMMENT ON COLUMN t_bank_statement.created_at           IS '创建时间（SPEC-P153：实体覆盖删除后由 BaseEntity 映射）';
COMMENT ON COLUMN t_bank_statement.updated_at           IS '更新时间（同上）';

-- ═══ 4) t_arap_settlement(_entry)：核销凭证号回写 + 红冲链 + 余额快照 ═══
ALTER TABLE t_arap_settlement ADD COLUMN IF NOT EXISTS voucher_no                  VARCHAR(64);
ALTER TABLE t_arap_settlement ADD COLUMN IF NOT EXISTS reversed_from_settlement_id BIGINT;
ALTER TABLE t_arap_settlement_entry ADD COLUMN IF NOT EXISTS before_balance NUMERIC(18,2);
ALTER TABLE t_arap_settlement_entry ADD COLUMN IF NOT EXISTS after_balance  NUMERIC(18,2);

COMMENT ON COLUMN t_arap_settlement.voucher_no IS '核销生成的凭证号（冗余回写；VO 是否外露另行裁定，见 ArapSettlementVO 注释）';
COMMENT ON COLUMN t_arap_settlement.reversed_from_settlement_id IS '被对冲的原核销单ID（红冲链 红→蓝 对应关系）';
COMMENT ON COLUMN t_arap_settlement_entry.before_balance IS '核销前单据余额快照（对账审计依据）';
COMMENT ON COLUMN t_arap_settlement_entry.after_balance  IS '核销后单据余额快照（对账审计依据）';

-- ═══ 5) t_output_invoice：审核人/审核时间（confirm 落库意图，列缺失）═══
-- 取证修正：实体注释写「仅 VO 回填」，实为 OutputInvoiceStateMachineServiceImpl
-- confirm/review 在 updateById 之前 setAuditedBy/setAuditedAt —— 是落库意图，
-- 与进项侧（t_input_invoice 有 audited_by/audited_at 真实列）同构，销项漏列。
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS audited_by BIGINT;
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS audited_at TIMESTAMP;

COMMENT ON COLUMN t_output_invoice.audited_by IS '审核人（confirm 确认时记录；此前写了不落库）';
COMMENT ON COLUMN t_output_invoice.audited_at IS '审核时间';

-- ═══ 6) t_customer_statement：确认时间留痕 ═══
ALTER TABLE t_customer_statement ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;
COMMENT ON COLUMN t_customer_statement.confirmed_at IS '客户确认时间（此前 setConfirmedAt 不落库）';

-- ═══ 7) t_bad_debt_provision：计提快照与凭证号（计算依据审计留痕）═══
-- 注：schemeId 刻意不建 —— 真写方是 SchemeItemEntity（其 scheme_id 为真实列），
-- 扫描器把 item.setSchemeId 误配给 provision；provision 本身无此写方。
ALTER TABLE t_bad_debt_provision ADD COLUMN IF NOT EXISTS voucher_no        VARCHAR(64);
ALTER TABLE t_bad_debt_provision ADD COLUMN IF NOT EXISTS expected_balance  NUMERIC(18,2);
ALTER TABLE t_bad_debt_provision ADD COLUMN IF NOT EXISTS existing_balance  NUMERIC(18,2);
ALTER TABLE t_bad_debt_provision ADD COLUMN IF NOT EXISTS adjustment_amount NUMERIC(18,2);
ALTER TABLE t_bad_debt_provision ADD COLUMN IF NOT EXISTS adjustment_type   VARCHAR(16);

COMMENT ON COLUMN t_bad_debt_provision.voucher_no        IS '计提生成的凭证号（冗余回写）';
COMMENT ON COLUMN t_bad_debt_provision.expected_balance  IS '测算应提余额（计提依据快照）';
COMMENT ON COLUMN t_bad_debt_provision.existing_balance  IS '计提前已提余额（依据快照）';
COMMENT ON COLUMN t_bad_debt_provision.adjustment_amount IS '补提/冲回金额 = expected - existing';
COMMENT ON COLUMN t_bad_debt_provision.adjustment_type   IS 'ADJUST_UP 补提 / ADJUST_DOWN 冲回';
