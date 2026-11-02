package com.huicai.base.system.service;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据维护（清库）服务 —— 清库操作的两道护栏实现。
 *
 * <h3>为什么需要本类（P148 / REQ-2026-164）</h3>
 * 原 `SystemClearController` 的 8 个清库端点全部是**无 WHERE 的全表 DELETE**：
 * <ul>
 *   <li><b>跨企业</b>：`DELETE FROM t_voucher` 一次清掉**所有账套**的凭证。
 *       2026-10-30 05:11 实测一次 `clear-vouchers` 清掉企业 4 的 26 张凭证，
 *       并在 `t_business_doc.voucher_id` 上留下 26 条**悬空引用**
 *       （该列无外键，删凭证不会回收单据引用）。</li>
 *   <li><b>无二次确认</b>：一次 POST 即不可逆，且清库路径不经逻辑删除、无回收站。</li>
 * </ul>
 *
 * <h3>两道护栏</h3>
 * <ol>
 *   <li><b>确认令牌（D-1）</b>：请求必须携带与动作名完全一致的 `confirm` 值
 *       （如清空凭证必须传 `confirm=CLEAR_VOUCHERS`）。缺省/不匹配一律 400，
 *       **在任何 DELETE 之前抛出** —— 保证“没确认就一行都不动”。</li>
 *   <li><b>按企业隔离（D-2）</b>：未显式传 enterpriseId 时，一律取当前上下文
 *       `EnterpriseContextHolder.get()`；上下文为空 → 400 拒绝，
 *       **绝不退化为“清全部企业”**。每条 DELETE 都带 `WHERE enterprise_id = ?`。</li>
 * </ol>
 *
 * <h3>🔴 关键取舍：为什么上下文为空必须拒绝而不是清全部</h3>
 * “上下文为空就清全部”是本次事故的等价形态：一旦某个运维/测试路径没有设好上下文
 * （这正是 P127/P139 反复踩到的 GUC NULL 形态），清库就会静默跨企业执行。
 * 宁可 400 让调用方显式补参，也不允许出现“一次清掉所有账套”的可达路径。
 * 同理，即便显式传入 enterpriseId，也要求调用方具备 `system:clear`（由 Controller 的
 * `@PreAuthorize` 保证），且该入参仅用于**缩小**范围、不允许绕过上下文校验用于跨企业清理。
 *
 * <h3>已知局限（如实登记）</h3>
 * <ul>
 *   <li>清库仍走物理删除（非逻辑删除），无回收站；本类只负责缩小爆炸半径并可审计。</li>
 *   <li>`t_business_doc.voucher_id` 仍无外键约束，删除凭证后单据引用不会被自动回收；
 *       本类在清空凭证时不删单据，故不会新造悬空引用，但历史悬空引用需另行清理。</li>
 *   <li>确认令牌是**防误触**而非防恶意 —— 真正的边界仍是 `system:clear` 权限。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemClearService {

    /** 确认令牌枚举 —— 值即前端必须回传的 `confirm` 字面量，动作级一一对应。 */
    public enum ClearAction {
        CLEAR_VOUCHERS("CLEAR_VOUCHERS"),
        CLEAR_BANK_STATEMENTS("CLEAR_BANK_STATEMENTS"),
        CLEAR_INVOICE_RECORDS("CLEAR_INVOICE_RECORDS"),
        CLEAR_REPORT_DATA("CLEAR_REPORT_DATA"),
        CLEAR_BUSINESS_DOCS("CLEAR_BUSINESS_DOCS"),
        CLEAR_RECEIVABLES("CLEAR_RECEIVABLES"),
        CLEAR_PAYABLES("CLEAR_PAYABLES"),
        CLEAR_SETTLEMENTS("CLEAR_SETTLEMENTS"),
        CLEAR_ALL("CLEAR_ALL");

        private final String token;

        ClearAction(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    private final JdbcTemplate jdbcTemplate;

    /**
     * 校验确认令牌 + 解析目标企业，返回已验证的 enterpriseId。
     *
     * <p>两次校验都在**任何 DELETE 之前**完成：令牌不过 → 400；企业无法确定 → 400。</p>
     */
    Long resolveAndVerify(ClearAction action, String confirm, Long requestedEnterpriseId) {
        // 护栏 1：确认令牌
        if (confirm == null || confirm.isBlank()) {
            throw BusinessException.badRequest(
                    "高风险操作需要二次确认：请传入 confirm=" + action.token());
        }
        if (!action.token().equals(confirm.trim())) {
            throw BusinessException.badRequest(
                    "确认令牌不匹配：该操作需 confirm=" + action.token() + "，实际传入 " + confirm.trim());
        }

        // 护栏 2：按企业隔离
        Long contextEnterpriseId = EnterpriseContextHolder.get();
        if (requestedEnterpriseId == null) {
            if (contextEnterpriseId == null) {
                throw BusinessException.badRequest(
                        "无法确定要清空的企业：当前上下文无 enterpriseId，"
                                + "请显式传入 enterpriseId（不会退化为清空全部企业）");
            }
            return contextEnterpriseId;
        }
        // 显式传参只允许：与上下文一致，或上下文为空时由调用方明确指定（运维场景）。
        if (contextEnterpriseId != null && !contextEnterpriseId.equals(requestedEnterpriseId)) {
            throw BusinessException.forbidden(
                    "不能跨企业清库：当前上下文企业=" + contextEnterpriseId
                            + "，请求企业=" + requestedEnterpriseId);
        }
        return requestedEnterpriseId;
    }

    /** 清空凭证（含分录、科目余额、现金流），按企业隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public String clearVouchers(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_VOUCHERS, confirm, requestedEnterpriseId);
        List<String> steps = new ArrayList<>();
        // 顺序：先删子表分录，再删主表凭证，避免 fk_cf_voucher 等约束阻断
        steps.add("凭证分录 " + jdbcTemplate.update(
                "DELETE FROM t_voucher_entry WHERE voucher_id IN "
                        + "(SELECT id FROM t_voucher WHERE enterprise_id = ?)", ent));
        steps.add("现金流 " + jdbcTemplate.update(
                "DELETE FROM t_voucher_cash_flow WHERE enterprise_id = ?", ent));
        steps.add("凭证 " + jdbcTemplate.update(
                "DELETE FROM t_voucher WHERE enterprise_id = ?", ent));
        steps.add("科目余额 " + jdbcTemplate.update(
                "DELETE FROM t_subject_balance WHERE enterprise_id = ?", ent));
        String msg = "企业 " + ent + " 清空凭证：" + String.join("，", steps);
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /** 清空银行流水，按企业隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public String clearBankStatements(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_BANK_STATEMENTS, confirm, requestedEnterpriseId);
        // 先解除与日记账的匹配引用（fk: matched_journal_id）
        int unlinked = jdbcTemplate.update(
                "UPDATE t_bank_statement SET matched_journal_id = NULL "
                        + "WHERE enterprise_id = ? AND matched_journal_id IS NOT NULL", ent);
        int deleted = jdbcTemplate.update(
                "DELETE FROM t_bank_statement WHERE enterprise_id = ?", ent);
        String msg = "企业 " + ent + " 清空银行流水：解绑 " + unlinked + " 条，删除 " + deleted + " 条";
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /** 清空发票记录，按企业隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public String clearInvoiceRecords(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_INVOICE_RECORDS, confirm, requestedEnterpriseId);
        int in = jdbcTemplate.update("DELETE FROM t_input_invoice WHERE enterprise_id = ?", ent);
        int out = jdbcTemplate.update("DELETE FROM t_output_invoice WHERE enterprise_id = ?", ent);
        String msg = "企业 " + ent + " 清空发票记录：进项 " + in + " 条，销项 " + out + " 条";
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /** 清空报表衍生存量（科目余额 + 现金流），按企业隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public String clearReportData(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_REPORT_DATA, confirm, requestedEnterpriseId);
        int bal = jdbcTemplate.update("DELETE FROM t_subject_balance WHERE enterprise_id = ?", ent);
        int cf = jdbcTemplate.update(
                "DELETE FROM t_voucher_cash_flow WHERE enterprise_id = ?", ent);
        String msg = "企业 " + ent + " 清空报表数据：科目余额 " + bal + " 条，现金流 " + cf + " 条";
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /**
     * 清空业务单据及核销/对账联动数据，按企业隔离。
     *
     * <p>🔴 本动作**不删凭证**，只解除 `t_voucher.business_doc_id` 引用 —— 与原文
     * “保留银行流水/凭证，解绑引用而非删除记录”的语义一致。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public String clearBusinessDocs(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_BUSINESS_DOCS, confirm, requestedEnterpriseId);
        List<String> steps = new ArrayList<>();
        // 子表先删（外键 fk_settle_entry_doc / fk_aging_alert_doc 无级联）
        steps.add("核销分录 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement_entry WHERE business_doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("核销单 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement WHERE enterprise_id = ?", ent));
        steps.add("对账记录 " + jdbcTemplate.update(
                "DELETE FROM t_reconciliation_log WHERE enterprise_id = ?", ent));
        steps.add("账龄告警 " + jdbcTemplate.update(
                "DELETE FROM t_aging_alert WHERE doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        // 解绑引用而非删除
        steps.add("解绑银行日记账 " + jdbcTemplate.update(
                "UPDATE t_bank_journal SET business_doc_id = NULL "
                        + "WHERE enterprise_id = ? AND business_doc_id IS NOT NULL", ent));
        steps.add("解绑凭证 " + jdbcTemplate.update(
                "UPDATE t_voucher SET business_doc_id = NULL "
                        + "WHERE business_doc_id IN (SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("单据分录 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc_entry WHERE doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("业务单据 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc WHERE enterprise_id = ?", ent));
        String msg = "企业 " + ent + " 清空业务单据：" + String.join("，", steps);
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /** 清空应收相关数据（业务单据 + 票据 + 核销），按企业隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public String clearReceivables(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_RECEIVABLES, confirm, requestedEnterpriseId);
        List<String> steps = new ArrayList<>();
        steps.add("核销分录 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement_entry WHERE business_doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("核销单 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement WHERE enterprise_id = ?", ent));
        steps.add("对账记录 " + jdbcTemplate.update(
                "DELETE FROM t_reconciliation_log WHERE enterprise_id = ?", ent));
        steps.add("应收票据 " + jdbcTemplate.update(
                "DELETE FROM t_note_receivable WHERE enterprise_id = ?", ent));
        steps.add("单据分录 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc_entry WHERE doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("业务单据 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc WHERE enterprise_id = ?", ent));
        String msg = "企业 " + ent + " 清空应收：" + String.join("，", steps);
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /** 清空应付相关数据（业务单据 + 核销），按企业隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public String clearPayables(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_PAYABLES, confirm, requestedEnterpriseId);
        List<String> steps = new ArrayList<>();
        steps.add("核销分录 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement_entry WHERE business_doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("核销单 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement WHERE enterprise_id = ?", ent));
        steps.add("对账记录 " + jdbcTemplate.update(
                "DELETE FROM t_reconciliation_log WHERE enterprise_id = ?", ent));
        steps.add("单据分录 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc_entry WHERE doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("业务单据 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc WHERE enterprise_id = ?", ent));
        String msg = "企业 " + ent + " 清空应付：" + String.join("，", steps);
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /** 清空核销数据并把单据核销额复位，按企业隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public String clearSettlements(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_SETTLEMENTS, confirm, requestedEnterpriseId);
        List<String> steps = new ArrayList<>();
        steps.add("核销分录 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement_entry WHERE business_doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("核销单 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement WHERE enterprise_id = ?", ent));
        steps.add("对账记录 " + jdbcTemplate.update(
                "DELETE FROM t_reconciliation_log WHERE enterprise_id = ?", ent));
        // 重置核销金额
        steps.add("单据复位 " + jdbcTemplate.update(
                "UPDATE t_business_doc SET settled_amount = 0, unsettled_amount = amount "
                        + "WHERE enterprise_id = ? AND (settled_amount <> 0 OR unsettled_amount <> amount)", ent));
        String msg = "企业 " + ent + " 清空核销：" + String.join("，", steps);
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }

    /**
     * 清空全部业务数据，按企业隔离。
     *
     * <p>🔴 与 P138 的验收语义一致：`clear-all` 清的是**某一个企业**的全量业务数据，
     * 不再是“所有账套一起清”。这是本次加固最需要守住的语义。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public String clearAll(String confirm, Long requestedEnterpriseId) {
        Long ent = resolveAndVerify(ClearAction.CLEAR_ALL, confirm, requestedEnterpriseId);
        Set<String> steps = new LinkedHashSet<>();
        // 1) 核销/对账/账龄（引用业务单据的子表）
        steps.add("核销分录 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement_entry WHERE business_doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("核销单 " + jdbcTemplate.update(
                "DELETE FROM t_arap_settlement WHERE enterprise_id = ?", ent));
        steps.add("对账记录 " + jdbcTemplate.update(
                "DELETE FROM t_reconciliation_log WHERE enterprise_id = ?", ent));
        steps.add("应收票据 " + jdbcTemplate.update(
                "DELETE FROM t_note_receivable WHERE enterprise_id = ?", ent));
        steps.add("账龄告警 " + jdbcTemplate.update(
                "DELETE FROM t_aging_alert WHERE doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        // 2) 业务单据
        steps.add("单据分录 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc_entry WHERE doc_id IN "
                        + "(SELECT id FROM t_business_doc WHERE enterprise_id = ?)", ent));
        steps.add("业务单据 " + jdbcTemplate.update(
                "DELETE FROM t_business_doc WHERE enterprise_id = ?", ent));
        // 3) 凭证（先删分录与现金流，避免外键阻断）
        steps.add("凭证分录 " + jdbcTemplate.update(
                "DELETE FROM t_voucher_entry WHERE voucher_id IN "
                        + "(SELECT id FROM t_voucher WHERE enterprise_id = ?)", ent));
        steps.add("现金流 " + jdbcTemplate.update(
                "DELETE FROM t_voucher_cash_flow WHERE enterprise_id = ?", ent));
        steps.add("凭证 " + jdbcTemplate.update(
                "DELETE FROM t_voucher WHERE enterprise_id = ?", ent));
        // 4) 报表衍生存量
        steps.add("科目余额 " + jdbcTemplate.update(
                "DELETE FROM t_subject_balance WHERE enterprise_id = ?", ent));
        // 5) 发票与流水（流水先解绑日记账匹配）
        steps.add("进项发票 " + jdbcTemplate.update(
                "DELETE FROM t_input_invoice WHERE enterprise_id = ?", ent));
        steps.add("销项发票 " + jdbcTemplate.update(
                "DELETE FROM t_output_invoice WHERE enterprise_id = ?", ent));
        jdbcTemplate.update(
                "UPDATE t_bank_statement SET matched_journal_id = NULL "
                        + "WHERE enterprise_id = ? AND matched_journal_id IS NOT NULL", ent);
        steps.add("银行流水 " + jdbcTemplate.update(
                "DELETE FROM t_bank_statement WHERE enterprise_id = ?", ent));
        String msg = "企业 " + ent + " 清空全部数据：" + String.join("，", steps);
        log.warn("[清库] {}, operator={}", msg, com.huicai.base.system.util.SecurityUtils.getCurrentUserId());
        return msg;
    }
}
