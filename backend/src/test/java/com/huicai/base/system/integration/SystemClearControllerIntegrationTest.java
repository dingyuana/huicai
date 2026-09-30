package com.huicai.base.system.integration;

import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.system.controller.SystemClearController;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.response.R;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.mapper.BankJournalMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据维护-清空业务单据 FK 链集成测试（真实 PostgreSQL + Flyway）。
 *
 * <p>回归验证修复（da8efc2）：clearBusinessDocs() 必须先清理引用
 * {@code t_business_doc} 的外键行（t_arap_settlement_entry / t_arap_settlement /
 * t_reconciliation_log / t_aging_alert），再解绑 t_bank_journal / t_voucher 的
 * business_doc_id，最后删除业务单据本身。修复前存在核销明细引用时抛
 * DataIntegrityViolationException（fk_settle_entry_doc），HTTP 500。
 *
 * <p>全部用例带 {@code @WithMockUser(authorities = "system:clear")}：
 * P102 起清库端点已加 {@code @PreAuthorize}（权限码种子见 V163），
 * 本类走生产 SecurityConfig（未激活 contract-test profile），
 * 无权限时方法级鉴权会抛 AccessDeniedException。
 * 本类验证的是 FK 链清理逻辑，故显式授予该权限而非放宽生产鉴权。
 *
 * <p>@SlowTest — 需要 Docker + Testcontainers
 *
 * <h3>为什么全部断言都是「相对基线」而不是固定绝对值</h3>
 * {@code clearBusinessDocs()} 是<b>全表维护操作</b>（无 WHERE 的 DELETE / 全表 UPDATE），
 * 其返回的 {@code deleted} 等于 {@link SystemClearController#clearBusinessDocs()} 里
 * 8 个 DML 的受影响行数之和 —— 其中包含 DB 里<b>本来就存在</b>的种子数据与
 * 其它 {@code @Transactional(NOT_SUPPORTED)} 测试已提交的行。
 * 因此断言固定值（如 {@code assertEquals(3, deleted)}）在本用例单独跑时成立、
 * 慢测全量跑时必然失败（实测得 10）。本类一律**先取基线、再断言增量**
 * （AGENTS §4.4 第 16 条）。
 */
@SlowTest
@WithMockUser(authorities = "system:clear")
@DisplayName("数据维护 - 清空业务单据 FK 链集成测试")
public class SystemClearControllerIntegrationTest extends AbstractMapperTest {

    @Autowired
    private SystemClearController clearController;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private ArapSettlementMapper settlementMapper;

    @Autowired
    private ArapSettlementEntryMapper settlementEntryMapper;

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Autowired
    private BankJournalMapper bankJournalMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BusinessDocEntity createBusinessDoc(String suffix) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("CLEAR-FK-" + suffix + "-" + System.currentTimeMillis());
        doc.setDocType("EXPENSE");
        doc.setDocDate(LocalDate.of(2026, 7, 1));
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("100.00"));
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(new BigDecimal("100.00"));
        doc.setStatus("APPROVED");
        doc.setSource("MANUAL");
        doc.setEnterpriseId(1L);
        doc.setDeleted(0);
        businessDocMapper.insert(doc);
        return doc;
    }

    private ArapSettlementEntity createSettlement(String suffix) {
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo("CLEAR-STL-" + suffix + "-" + System.currentTimeMillis());
        settlement.setSettlementType("RECEIVE");
        settlement.setSettlementDate(LocalDate.of(2026, 7, 1));
        settlement.setPeriod("202607");
        settlement.setPartyId(1L);
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(new BigDecimal("100.00"));
        settlement.setDiscountAmount(BigDecimal.ZERO);
        settlement.setStatus("DRAFT");
        settlement.setEnterpriseId(1L);
        settlement.setDeleted(0);
        settlementMapper.insert(settlement);
        return settlement;
    }

    private ArapSettlementEntryEntity createSettlementEntry(Long settlementId, Long businessDocId) {
        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(settlementId);
        entry.setBusinessDocId(businessDocId);
        entry.setSettledAmount(new BigDecimal("100.00"));
        entry.setDiscountAmount(BigDecimal.ZERO);
        entry.setEnterpriseId(1L);
        entry.setDeleted(0);
        settlementEntryMapper.insert(entry);
        return entry;
    }

    private BankAccountEntity createBankAccount(String suffix) {
        BankAccountEntity account = new BankAccountEntity();
        account.setAccountNo("6222" + suffix);
        account.setAccountName("清理测试账户" + suffix);
        account.setBalance(new BigDecimal("5000.00"));
        account.setEnterpriseId(1L);
        account.setDeleted(0);
        bankAccountMapper.insert(account);
        return account;
    }

    /** 单表行数。 */
    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    /**
     * {@code clearBusinessDocs()} 在调用前已存在的行数合计 —— 与被测方法里 8 个 DML
     * 一一对应（6 个 DELETE + 2 个「解绑 business_doc_id」的 UPDATE）。
     * 被测方法返回的 {@code deleted} 应当等于「本基线 + 本用例新建的行数」。
     */
    private int baselineClearOps() {
        return count("SELECT count(*) FROM t_arap_settlement_entry")
                + count("SELECT count(*) FROM t_arap_settlement")
                + count("SELECT count(*) FROM t_reconciliation_log")
                + count("SELECT count(*) FROM t_aging_alert")
                + count("SELECT count(*) FROM t_bank_journal WHERE business_doc_id IS NOT NULL")
                + count("SELECT count(*) FROM t_voucher WHERE business_doc_id IS NOT NULL")
                + count("SELECT count(*) FROM t_business_doc_entry")
                + count("SELECT count(*) FROM t_business_doc");
    }

    @Test
    @DisplayName("回归: 存在核销明细引用时清空业务单据应成功而非 500")
    void clearBusinessDocs_withSettlementEntry_shouldSucceed() {
        int baseline = baselineClearOps();

        BusinessDocEntity doc = createBusinessDoc("STL");
        ArapSettlementEntity settlement = createSettlement("STL");
        createSettlementEntry(settlement.getId(), doc.getId());

        R<Map<String, Object>> result = clearController.clearBusinessDocs();

        assertNotNull(result, "返回结果不应为 null");
        assertEquals(200, result.getCode(), "清空业务单据应返回 code=200");
        Integer deleted = (Integer) result.getData().get("deleted");
        assertNotNull(deleted, "deleted 统计不应为 null");
        // 增量断言：本用例新增 1 条核销明细 + 1 条核销单 + 1 条业务单据
        assertEquals(baseline + 3, deleted.intValue(),
                "应清理基线 " + baseline + " 条 + 本用例 1 条核销明细/1 条核销单/1 条业务单据");

        assertEquals(0, count("SELECT count(*) FROM t_arap_settlement_entry"), "核销明细应被全部清理");
        assertEquals(0, count("SELECT count(*) FROM t_arap_settlement"), "核销单应被全部清理");
        assertEquals(0, count("SELECT count(*) FROM t_business_doc"), "业务单据应被全部清理");
    }

    @Test
    @DisplayName("保留银行日记账: 清空业务单据后 journal 存在且 business_doc_id 置空")
    void clearBusinessDocs_shouldUnlinkBankJournalNotDelete() {
        int baseline = baselineClearOps();
        int journalBaseline = count("SELECT count(*) FROM t_bank_journal");

        BusinessDocEntity doc = createBusinessDoc("JNL");
        BankAccountEntity account = createBankAccount("JNL");

        BankJournalEntity journal = new BankJournalEntity();
        journal.setAccountId(account.getId());
        journal.setTxDate(LocalDate.of(2026, 7, 1));
        journal.setPeriod("202607");
        journal.setTxType("EXPENSE");
        journal.setAmount(new BigDecimal("100.00"));
        journal.setBusinessDocId(doc.getId());
        journal.setEnterpriseId(1L);
        journal.setDeleted(0);
        bankJournalMapper.insert(journal);

        R<Map<String, Object>> result = clearController.clearBusinessDocs();

        assertEquals(200, result.getCode(), "清空业务单据应返回 code=200");
        // 增量断言：本用例新增 1 条业务单据（删除）+ 1 条 journal 解绑（UPDATE）
        Integer deleted = (Integer) result.getData().get("deleted");
        assertEquals(baseline + 2, deleted.intValue(),
                "应清理基线 " + baseline + " 条 + 本用例 1 条业务单据/1 条 journal 解绑");
        assertEquals(journalBaseline + 1, count("SELECT count(*) FROM t_bank_journal"),
                "银行日记账应保留（本用例 1 条 + 基线 " + journalBaseline + " 条）");
        assertEquals(0, count("SELECT count(*) FROM t_bank_journal WHERE business_doc_id IS NOT NULL"),
                "银行日记账的 business_doc_id 应被置空");
        assertEquals(0, count("SELECT count(*) FROM t_business_doc"), "业务单据应被全部清理");
    }

    @Test
    @DisplayName("保留凭证: 清空业务单据后凭证存在且 business_doc_id 置空")
    void clearBusinessDocs_shouldUnlinkVoucherNotDelete() {
        int baseline = baselineClearOps();
        int voucherBaseline = count("SELECT count(*) FROM t_voucher");

        BusinessDocEntity doc = createBusinessDoc("VCH");

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("CLEAR-VCH-" + System.currentTimeMillis());
        voucher.setPeriod("202607");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(new BigDecimal("100.00"));
        voucher.setTotalCredit(new BigDecimal("100.00"));
        voucher.setSource("MANUAL");
        voucher.setBusinessDocId(doc.getId());
        voucher.setVersion(1);
        voucher.setEnterpriseId(1L);
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        R<Map<String, Object>> result = clearController.clearBusinessDocs();

        assertEquals(200, result.getCode(), "清空业务单据应返回 code=200");
        // 增量断言：本用例新增 1 条业务单据（删除）+ 1 张凭证解绑（UPDATE）
        Integer deleted = (Integer) result.getData().get("deleted");
        assertEquals(baseline + 2, deleted.intValue(),
                "应清理基线 " + baseline + " 条 + 本用例 1 条业务单据/1 张凭证解绑");
        assertEquals(voucherBaseline + 1, count("SELECT count(*) FROM t_voucher"),
                "凭证应保留（本用例 1 张 + 基线 " + voucherBaseline + " 张）");
        assertEquals(0, count("SELECT count(*) FROM t_voucher WHERE business_doc_id IS NOT NULL"),
                "凭证的 business_doc_id 应被置空");
        assertEquals(0, count("SELECT count(*) FROM t_business_doc"), "业务单据应被全部清理");
    }
}
