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
 * <p>@SlowTest — 需要 Docker + Testcontainers
 */
@SlowTest
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

    @Test
    @DisplayName("回归: 存在核销明细引用时清空业务单据应成功而非 500")
    void clearBusinessDocs_withSettlementEntry_shouldSucceed() {
        BusinessDocEntity doc = createBusinessDoc("STL");
        ArapSettlementEntity settlement = createSettlement("STL");
        createSettlementEntry(settlement.getId(), doc.getId());

        R<Map<String, Object>> result = clearController.clearBusinessDocs();

        assertNotNull(result, "返回结果不应为 null");
        assertEquals(200, result.getCode(), "清空业务单据应返回 code=200");
        Integer deleted = (Integer) result.getData().get("deleted");
        assertNotNull(deleted, "deleted 统计不应为 null");
        assertEquals(3, deleted.intValue(), "应清理 1 条核销明细 + 1 条核销单 + 1 条业务单据");

        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_arap_settlement_entry", Integer.class);
        Integer settlementCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_arap_settlement", Integer.class);
        Integer docCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_business_doc", Integer.class);
        assertEquals(0, entryCount, "核销明细应被全部清理");
        assertEquals(0, settlementCount, "核销单应被全部清理");
        assertEquals(0, docCount, "业务单据应被全部清理");
    }

    @Test
    @DisplayName("保留银行日记账: 清空业务单据后 journal 存在且 business_doc_id 置空")
    void clearBusinessDocs_shouldUnlinkBankJournalNotDelete() {
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
        Integer journalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_bank_journal", Integer.class);
        assertEquals(1, journalCount, "银行日记账应保留");
        Integer unlinked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_bank_journal WHERE business_doc_id IS NOT NULL", Integer.class);
        assertEquals(0, unlinked, "银行日记账的 business_doc_id 应被置空");
        Integer docCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_business_doc", Integer.class);
        assertEquals(0, docCount, "业务单据应被全部清理");
    }

    @Test
    @DisplayName("保留凭证: 清空业务单据后凭证存在且 business_doc_id 置空")
    void clearBusinessDocs_shouldUnlinkVoucherNotDelete() {
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
        Integer voucherCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher", Integer.class);
        assertEquals(1, voucherCount, "凭证应保留");
        Integer unlinked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher WHERE business_doc_id IS NOT NULL", Integer.class);
        assertEquals(0, unlinked, "凭证的 business_doc_id 应被置空");
        Integer docCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_business_doc", Integer.class);
        assertEquals(0, docCount, "业务单据应被全部清理");
    }
}
