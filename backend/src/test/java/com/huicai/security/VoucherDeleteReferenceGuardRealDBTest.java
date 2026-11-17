package com.huicai.security;

import com.huicai.base.system.service.SystemClearService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.mapper.VoucherTypeMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P153 / A-3 悬空引用治理：🔴 凭证删除与清库路径的引用完整性守卫。
 *
 * <h2>可达的悬空链（不是理论风险）</h2>
 * <ol>
 *   <li>发票确认/单据审核 → 生成凭证（非 DRAFT）→ 回填 {@code t_output_invoice.voucher_id} /
 *       {@code t_business_doc.voucher_id}（OutputInvoiceStateMachineServiceImpl#149 等）；</li>
 *   <li>管理员驳回凭证：{@code VoucherServiceImpl#reject} L384 把凭证打回 <b>DRAFT</b>，
 *       而单据/发票侧的 voucher_id <b>不回退</b>；</li>
 *   <li>用户删这张 DRAFT 凭证 —— 原 {@code delete} 只查 DRAFT 状态、零引用检查
 *       ⇒ 单据侧 voucher_id 悬空（V1.99 清库事故 26 条悬空同构）。</li>
 * </ol>
 *
 * <h2>两路收口</h2>
 * <ul>
 *   <li><b>日常删除</b>（{@code VoucherServiceImpl#delete}）：挂 {@code ReferenceGuard}
 *       注解（19 个跨生命周期引用方；t_voucher_entry/t_voucher_cash_flow 随凭证同删，
 *       不登记）——有引用即 400 并列出引用方；</li>
 *   <li><b>运维清库</b>（{@code SystemClearService#clearVouchers}）：语义是「清空重来」，
 *       拒绝会挡死重置场景 ⇒ 删凭证前<b>解绑</b>全部引用方（SET voucher_id=NULL）。
 *       原 L49 注释「清空凭证不删单据故不会新造悬空引用」方向搞反 —— 悬空的是
 *       单据→凭证方向的引用，与删不删单据无关，V1.99 事故实锤。</li>
 * </ul>
 *
 * <p>RLS 说明：Testcontainers 库 owner(test) 豁免 RLS ⇒ 引用计数按全局口径，
 * 与 Aspect 默认 fail-closed 口径一致（企业 2 造数、企业上下文也切 2，双保险）。</p>
 */
@DisplayName("SPEC-P153 A-3：凭证删除/清库的引用完整性（驳回回 DRAFT 后删除 → 悬空链封死）")
class VoucherDeleteReferenceGuardRealDBTest extends AbstractMapperTest {

    private static final Long ENT = 2L;

    @Autowired
    private VoucherMapper voucherMapper;
    @Autowired
    private VoucherTypeMapper voucherTypeMapper;
    @Autowired
    private com.huicai.base.voucher.service.VoucherService voucherService;
    @Autowired
    private SystemClearService systemClearService;

    /** 自造 DRAFT 凭证（全新测试库企业 2 无种子凭证类型，V183 冷启动差异 ⇒ 类型自造）。 */
    private VoucherEntity newDraftVoucher(String no) {
        VoucherTypeEntity vt = new VoucherTypeEntity();
        vt.setCode("A3T" + no.substring(no.length() - 3));
        vt.setName("A-3 探针类型");
        vt.setIsActive(true);
        voucherTypeMapper.insert(vt);

        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo(no);
        v.setPeriod("202407");
        v.setVoucherTypeId(vt.getId());
        v.setStatus("DRAFT");
        voucherMapper.insert(v);
        return v;
    }

    @Test
    @DisplayName("删除被业务单据引用的 DRAFT 凭证 ⇒ 400 并点名引用方（驳回回 DRAFT 后删除的悬空链）")
    void deleteRejectsReferencedDraftVoucher() {
        useEnterprise(ENT);
        VoucherEntity v = newDraftVoucher("A3-REF-1");

        jdbcTemplate.update(
                "INSERT INTO t_business_doc (doc_no, doc_type, doc_date, period, enterprise_id, voucher_id) "
                        + "VALUES ('A3DOC-1', 'PAYMENT', '2024-07-10', '202407', ?, ?)",
                ENT, v.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> voucherService.delete(v.getId()),
                "被引用的 DRAFT 凭证必须拒绝删除（否则单据侧 voucher_id 悬空）");
        assertTrue(ex.getMessage().contains("业务单据"),
                "400 消息必须点名引用方（业务单据），实际：" + ex.getMessage());

        // 凭证原样保留（拒绝 = 无任何副作用）
        assertNotNull(voucherMapper.selectById(v.getId()), "守卫拒绝后凭证不得被删");
    }

    @Test
    @DisplayName("无引用的 DRAFT 凭证可正常删除（守卫不误伤正常清理路径）")
    void deleteAllowsUnreferencedDraftVoucher() {
        useEnterprise(ENT);
        VoucherEntity v = newDraftVoucher("A3-FREE-1");

        voucherService.delete(v.getId());

        assertEquals(null, voucherMapper.selectById(v.getId()), "无引用 DRAFT 应删干净");
        Integer entries = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_entry WHERE voucher_id = ?", Integer.class, v.getId());
        assertEquals(0, entries, "分录级联删除");
    }

    @Test
    @DisplayName("clearVouchers 解绑全部跨生命周期引用方（清库不再新造悬空引用）")
    void clearVouchersUnlinksAllVoucherReferences() {
        useEnterprise(ENT);
        VoucherEntity v = newDraftVoucher("A3-CLR-1");

        // 三类代表性引用方：单据 / 银行流水（真 FK account_id 须造真账户）/ 发票
        jdbcTemplate.update(
                "INSERT INTO t_business_doc (doc_no, doc_type, doc_date, period, enterprise_id, voucher_id) "
                        + "VALUES ('A3DOC-2', 'RECEIPT', '2024-07-11', '202407', ?, ?)",
                ENT, v.getId());
        jdbcTemplate.update(
                "INSERT INTO t_bank_account (account_name, account_no, enterprise_id) "
                        + "VALUES ('A3探针账户', 'A3-ACCT', ?)", ENT);
        Long acctId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_bank_account WHERE account_no = 'A3-ACCT'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO t_bank_statement (account_id, amount, tx_date, tx_type, enterprise_id, generated_voucher_id) "
                        + "VALUES (?, 100.00, '2024-07-12', 'INCOME', ?, ?)",
                acctId, ENT, v.getId());
        jdbcTemplate.update(
                "INSERT INTO t_output_invoice (invoice_no, invoice_date, invoice_type, period, status, "
                        + "amount, tax_amount, tax_rate, total_amount, enterprise_id, voucher_id) "
                        + "VALUES ('A3INV-1', '2024-07-12', 'SPECIAL', '202407', 'VOUCHERED', "
                        + "100.00, 6.00, 0.06, 106.00, ?, ?)",
                ENT, v.getId());

        // 上下文已 useEnterprise(2) ⇒ resolveAndVerify 通过上下文取企业；
        // confirm 走 V1.99 护栏（缺令牌 400——顺带验证护栏本身活着）；
        // SecurityUtils.getCurrentUserId 需登录态 ⇒ 注入 LoginUser 主（L2 惯例同 ContractTestSecurityConfig）
        com.huicai.base.system.entity.UserEntity probeUser = new com.huicai.base.system.entity.UserEntity();
        probeUser.setUsername("a3-probe");
        probeUser.setPassword("n/a");
        com.huicai.config.security.LoginUser principal =
                new com.huicai.config.security.LoginUser(probeUser, List.of(), ENT, null, "ENTERPRISE", null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, "n/a", List.of()));
        try {
            String msg = systemClearService.clearVouchers("CLEAR_VOUCHERS", null);

            assertTrue(msg.contains("凭证"), "清库消息应包含凭证步骤：" + msg);
            Integer vouchersLeft = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM t_voucher WHERE enterprise_id = ?", Integer.class, ENT);
            assertEquals(0, vouchersLeft, "企业内凭证应清空");

            // 🔴 核心断言：三类引用方全部解绑（修复前它们各自悬空指向已删除的凭证）
            for (String[] ref : new String[][]{
                    {"t_business_doc", "voucher_id", "doc_no='A3DOC-2'"},
                    {"t_bank_statement", "generated_voucher_id", "account_id=" + acctId},
                    {"t_output_invoice", "voucher_id", "invoice_no='A3INV-1'"}}) {
                Integer dangling = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + ref[0] + " WHERE " + ref[2]
                                + " AND " + ref[1] + " = ?",
                        Integer.class, v.getId());
                assertEquals(0, dangling,
                        ref[0] + "." + ref[1] + " 未解绑 ⇒ 清库后悬空引用（V1.99 事故形态）");
                Integer unlinked = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + ref[0] + " WHERE " + ref[2]
                                + " AND " + ref[1] + " IS NULL",
                        Integer.class);
                assertEquals(1, unlinked, ref[0] + "." + ref[1] + " 应被置 NULL 而非删行");
            }
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("clearReceivables 删单据但保留凭证 ⇒ 凭证的 business_doc_id 必须解绑而非悬空")
    void clearReceivablesUnlinksVoucherDocReference() {
        useEnterprise(ENT);
        VoucherEntity v = newDraftVoucher("A3-RCV-1");
        // 凭证 → 单据方向的引用（t_voucher.business_doc_id，无外键）
        jdbcTemplate.update(
                "INSERT INTO t_business_doc (doc_no, doc_type, doc_date, period, enterprise_id) "
                        + "VALUES ('A3DOC-3', 'RECEIPT', '2024-07-13', '202407', ?)", ENT);
        Long docId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_business_doc WHERE doc_no = 'A3DOC-3'", Long.class);
        jdbcTemplate.update("UPDATE t_voucher SET business_doc_id = ? WHERE id = ?", docId, v.getId());

        com.huicai.base.system.entity.UserEntity probeUser = new com.huicai.base.system.entity.UserEntity();
        probeUser.setUsername("a3-probe");
        probeUser.setPassword("n/a");
        com.huicai.config.security.LoginUser principal =
                new com.huicai.config.security.LoginUser(probeUser, List.of(), ENT, null, "ENTERPRISE", null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, "n/a", List.of()));
        try {
            systemClearService.clearReceivables("CLEAR_RECEIVABLES", null);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        Integer docsLeft = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_business_doc WHERE doc_no = 'A3DOC-3'", Integer.class);
        assertEquals(0, docsLeft, "单据应删除");
        Integer voucherLeft = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher WHERE id = ?", Integer.class, v.getId());
        assertEquals(1, voucherLeft, "本动作不删凭证（与 clearBusinessDocs 语义一致）");
        Integer dangling = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher WHERE id = ? AND business_doc_id IS NOT NULL",
                Integer.class, v.getId());
        assertEquals(0, dangling,
                "t_voucher.business_doc_id 未解绑 ⇒ 凭证悬挂指向已删除单据（A-3 同族悬空）");
    }
}
