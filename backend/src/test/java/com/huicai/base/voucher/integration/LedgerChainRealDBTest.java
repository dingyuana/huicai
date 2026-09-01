package com.huicai.base.voucher.integration;

import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherVO;
import com.huicai.base.voucher.service.LedgerService;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 账簿查询链路真实 DB 测试（凭证过账 → 余额快照 → 账簿查询贯通）。
 *
 * <p>覆盖评估报告 T5 缺口：跨实体链路必须真实贯通，不能只测单模块 CRUD。
 * 链路：创建凭证 → 提交 → 审核 → 过账（写入 t_subject_balance 快照）→
 * 科目余额表 / 总分类账 / 明细账查询。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
@SlowTest
@DisplayName("账簿查询 - 过账后全链路集成测试")
public class LedgerChainRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private SubjectBalanceService subjectBalanceService;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private PeriodMapper periodMapper;

    @Autowired
    private SubjectBalanceMapper subjectBalanceMapper;

    private static final Long USER_ID = 1L;
    private static final Long VOUCHER_TYPE_ID = 1L;
    private static final String TEST_PERIOD = "202608";
    private Long debitSubjectId;
    private Long creditSubjectId;

    @BeforeEach
    void setUp() {
        EnterpriseContextHolder.set(1L);

        PeriodEntity period = new PeriodEntity();
        period.setYear(2026);
        period.setMonth(8);
        period.setPeriodCode(TEST_PERIOD);
        period.setStartDate(java.time.LocalDate.of(2026, 8, 1));
        period.setEndDate(java.time.LocalDate.of(2026, 8, 31));
        period.setStatus("open");
        period.setEnterpriseId(1L);
        period.setDeleted(0);
        periodMapper.insert(period);

        Subject s1 = new Subject();
        s1.setCode("CHAIN-1001");
        s1.setName("链路测试借方科目");
        s1.setDirection("debit");
        s1.setLevel(1);
        s1.setIsLeaf(true);
        s1.setIsActive(true);
        s1.setEnterpriseId(1L);
        s1.setDeleted(0);
        subjectMapper.insert(s1);
        debitSubjectId = s1.getId();

        Subject s2 = new Subject();
        s2.setCode("CHAIN-2001");
        s2.setName("链路测试贷方科目");
        s2.setDirection("credit");
        s2.setLevel(1);
        s2.setIsLeaf(true);
        s2.setIsActive(true);
        s2.setEnterpriseId(1L);
        s2.setDeleted(0);
        subjectMapper.insert(s2);
        creditSubjectId = s2.getId();

        // 期初前置强制：202608 为企业最早期，过账前必须已建账（零余额确认）
        subjectBalanceService.initOpeningBalances(TEST_PERIOD, new java.util.HashMap<>());
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    private VoucherVO createAndPost(String summary, String debitAmount, String creditAmount) {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(TEST_PERIOD);
        dto.setVoucherTypeId(VOUCHER_TYPE_ID);
        dto.setSummary(summary);

        VoucherCreateDTO.EntryDTO entry1 = new VoucherCreateDTO.EntryDTO();
        entry1.setSubjectId(debitSubjectId);
        entry1.setDebit(new BigDecimal(debitAmount));
        entry1.setCredit(BigDecimal.ZERO);
        entry1.setSummary("借：" + summary);

        VoucherCreateDTO.EntryDTO entry2 = new VoucherCreateDTO.EntryDTO();
        entry2.setSubjectId(creditSubjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal(creditAmount));
        entry2.setSummary("贷：" + summary);

        dto.setEntries(List.of(entry1, entry2));

        VoucherVO created = voucherService.create(dto, USER_ID);
        Long id = created.getId();
        voucherService.submit(id, USER_ID);
        voucherService.audit(id, USER_ID);
        voucherService.post(id, USER_ID);
        return voucherService.getDetail(id);
    }

    @Test
    @DisplayName("过账后：t_subject_balance 快照写入正确的借方/贷方发生额")
    void post_shouldWriteBalanceSnapshot() {
        createAndPost("链路凭证1", "1000.00", "1000.00");

        SubjectBalanceEntity debitBalance = subjectBalanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getSubjectId, debitSubjectId)
                        .eq(SubjectBalanceEntity::getPeriod, TEST_PERIOD));
        assertNotNull(debitBalance, "借方科目过账后应生成余额快照");
        assertEquals(0, new BigDecimal("1000.00").compareTo(debitBalance.getDebitTotal()), "借方科目借方发生额=1000");
        assertEquals(0, BigDecimal.ZERO.compareTo(debitBalance.getCreditTotal()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(debitBalance.getEndBalance()), "借方科目期末余额=1000");

        SubjectBalanceEntity creditBalance = subjectBalanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubjectBalanceEntity>()
                        .eq(SubjectBalanceEntity::getSubjectId, creditSubjectId)
                        .eq(SubjectBalanceEntity::getPeriod, TEST_PERIOD));
        assertNotNull(creditBalance, "贷方科目过账后应生成余额快照");
        assertEquals(0, BigDecimal.ZERO.compareTo(creditBalance.getDebitTotal()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(creditBalance.getCreditTotal()), "贷方科目贷方发生额=1000");
        assertEquals(0, new BigDecimal("1000.00").compareTo(creditBalance.getEndBalance()), "贷方科目期末余额=1000");
    }

    @Test
    @DisplayName("科目余额表：过账后返回快照数据（期初/发生/期末）")
    void subjectBalance_shouldReturnPostedData() {
        createAndPost("链路凭证2", "2000.00", "2000.00");

        List<Map<String, Object>> rows = ledgerService.subjectBalance(TEST_PERIOD);

        Map<String, Object> debitRow = rows.stream()
                .filter(r -> r.get("subjectId").equals(debitSubjectId))
                .findFirst()
                .orElse(null);
        assertNotNull(debitRow, "科目余额表应包含借方科目");
        assertEquals(0, new BigDecimal("2000.00").compareTo((BigDecimal) debitRow.get("debitTotal")));
        assertEquals(0, new BigDecimal("2000.00").compareTo((BigDecimal) debitRow.get("endBalance")));

        Map<String, Object> creditRow = rows.stream()
                .filter(r -> r.get("subjectId").equals(creditSubjectId))
                .findFirst()
                .orElse(null);
        assertNotNull(creditRow, "科目余额表应包含贷方科目");
        assertEquals(0, new BigDecimal("2000.00").compareTo((BigDecimal) creditRow.get("creditTotal")));
        assertEquals(0, new BigDecimal("2000.00").compareTo((BigDecimal) creditRow.get("endBalance")));
    }

    @Test
    @DisplayName("总分类账：过账后返回期初+分录+本期合计，滚动余额正确")
    void generalLedger_shouldReturnPostedChain() {
        createAndPost("链路凭证3", "500.00", "500.00");

        List<Map<String, Object>> rows = ledgerService.generalLedger(debitSubjectId, TEST_PERIOD);

        assertEquals(3, rows.size(), "应为 期初 + 1笔分录 + 本期合计");
        assertEquals("OPENING", rows.get(0).get("type"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) rows.get(0).get("running")), "期初为0（期初建账零余额）");

        assertEquals("ENTRY", rows.get(1).get("type"));
        assertEquals(0, new BigDecimal("500.00").compareTo((BigDecimal) rows.get(1).get("debit")));
        assertEquals(0, new BigDecimal("500.00").compareTo((BigDecimal) rows.get(1).get("running")), "借方科目滚动余额=500");

        assertEquals("CLOSING", rows.get(2).get("type"));
        assertEquals(0, new BigDecimal("500.00").compareTo((BigDecimal) rows.get(2).get("debit")));
        assertEquals(0, new BigDecimal("500.00").compareTo((BigDecimal) rows.get(2).get("running")));
    }

    @Test
    @DisplayName("明细账：过账后返回对应科目分录")
    void subsidiaryLedger_shouldReturnPostedEntries() {
        createAndPost("链路凭证4", "800.00", "800.00");

        List<Map<String, Object>> rows = ledgerService.subsidiaryLedger(debitSubjectId, TEST_PERIOD);

        assertEquals(1, rows.size(), "应返回1条过账分录");
        assertEquals(debitSubjectId, rows.get(0).get("subjectId"));
        assertEquals(0, new BigDecimal("800.00").compareTo((BigDecimal) rows.get(0).get("debit")));
        assertEquals("借：链路凭证4", rows.get(0).get("summary"), "分录摘要为创建时的分录摘要");
    }
}
