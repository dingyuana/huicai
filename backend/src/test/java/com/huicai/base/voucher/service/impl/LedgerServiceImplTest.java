package com.huicai.base.voucher.service.impl;

import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.service.SubjectService;
import com.huicai.base.voucher.dto.AuxiliarySummaryRow;
import com.huicai.base.voucher.dto.LedgerEntryRowDTO;
import com.huicai.base.voucher.dto.vo.AuxiliaryLedgerRowVO;
import com.huicai.base.voucher.dto.vo.LedgerRowVO;
import com.huicai.base.voucher.dto.vo.SubjectBalanceRowVO;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("总账服务 LedgerService 单元测试")
class LedgerServiceImplTest {

    @Mock
    private SubjectBalanceMapper subjectBalanceMapper;

    @Mock
    private VoucherEntryMapper voucherEntryMapper;

    @Mock
    private SubjectService subjectService;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private VendorMapper vendorMapper;

    @Mock
    private DeptMapper deptMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    // ─── subjectBalance ─────────────────────────────────────────────────────

    @Test
    @DisplayName("科目余额表 - 正常返回余额列表，过滤非叶子节点")
    void subjectBalance_shouldReturnLeafBalances() {
        // Arrange
        String period = "202607";

        SubjectBalanceEntity leafDebit = new SubjectBalanceEntity();
        leafDebit.setSubjectId(1L);
        leafDebit.setPeriod(period);
        leafDebit.setBeginBalance(new BigDecimal("1000.00"));
        leafDebit.setDebitTotal(new BigDecimal("500.00"));
        leafDebit.setCreditTotal(new BigDecimal("200.00"));
        leafDebit.setEndBalance(new BigDecimal("1300.00"));

        SubjectBalanceEntity leafCredit = new SubjectBalanceEntity();
        leafCredit.setSubjectId(2L);
        leafCredit.setPeriod(period);
        leafCredit.setBeginBalance(new BigDecimal("2000.00"));
        leafCredit.setDebitTotal(new BigDecimal("300.00"));
        leafCredit.setCreditTotal(new BigDecimal("400.00"));
        leafCredit.setEndBalance(new BigDecimal("2100.00"));

        SubjectBalanceEntity nonLeaf = new SubjectBalanceEntity();
        nonLeaf.setSubjectId(3L);
        nonLeaf.setPeriod(period);
        nonLeaf.setBeginBalance(new BigDecimal("5000.00"));
        nonLeaf.setDebitTotal(BigDecimal.ZERO);
        nonLeaf.setCreditTotal(BigDecimal.ZERO);
        nonLeaf.setEndBalance(new BigDecimal("5000.00"));

        when(subjectBalanceMapper.selectList(any())).thenReturn(List.of(leafDebit, leafCredit, nonLeaf));

        Subject debitSubject = new Subject();
        debitSubject.setId(1L);
        debitSubject.setCode("1001");
        debitSubject.setName("库存现金");
        debitSubject.setDirection("debit");
        debitSubject.setIsLeaf(true);

        Subject creditSubject = new Subject();
        creditSubject.setId(2L);
        creditSubject.setCode("2001");
        creditSubject.setName("短期借款");
        creditSubject.setDirection("credit");
        creditSubject.setIsLeaf(true);

        Subject nonLeafSubject = new Subject();
        nonLeafSubject.setId(3L);
        nonLeafSubject.setCode("1002");
        nonLeafSubject.setName("银行存款");
        nonLeafSubject.setDirection("debit");
        nonLeafSubject.setIsLeaf(false);

        // T9-批量查科目（替代逐行 getById）
        when(subjectService.listByIds(any())).thenReturn(List.of(debitSubject, creditSubject, nonLeafSubject));

        // 本年累计聚合（T4）：第二条 selectList 返回本年快照（此处复用同一批余额行，简化断言）
        List<SubjectBalanceEntity> allBalances = List.of(leafDebit, leafCredit, nonLeaf);
        when(subjectBalanceMapper.selectList(any())).thenReturn(allBalances);

        // Act
        List<SubjectBalanceRowVO> result = ledgerService.subjectBalance(period);

        // Assert
        assertEquals(2, result.size(), "应只包含末级科目");

        SubjectBalanceRowVO row1 = result.get(0);
        assertEquals(1L, row1.getSubjectId());
        assertEquals("1001", row1.getSubjectCode());
        assertEquals("库存现金", row1.getSubjectName());
        assertEquals("debit", row1.getDirection());
        assertEquals(new BigDecimal("1000.00"), row1.getBeginBalance());
        assertEquals(new BigDecimal("500.00"), row1.getDebitTotal());
        assertEquals(new BigDecimal("200.00"), row1.getCreditTotal());
        assertEquals(new BigDecimal("1300.00"), row1.getEndBalance());
        // 本年累计列（T4）
        assertEquals(new BigDecimal("1000.00"), row1.getYearBeginBalance());
        assertEquals(new BigDecimal("500.00"), row1.getYearDebitTotal());
        assertEquals(new BigDecimal("200.00"), row1.getYearCreditTotal());

        SubjectBalanceRowVO row2 = result.get(1);
        assertEquals(2L, row2.getSubjectId());
        assertEquals("2001", row2.getSubjectCode());
        assertEquals("短期借款", row2.getSubjectName());
        assertEquals("credit", row2.getDirection());

        verify(subjectBalanceMapper, times(2)).selectList(any());
        verify(subjectService).listByIds(any());
    }

    @Test
    @DisplayName("科目余额表 - 本年累计无快照时按0处理")
    void subjectBalance_yearTotalsZeroWhenNoYearSnapshot() {
        String period = "202608";

        SubjectBalanceEntity leaf = new SubjectBalanceEntity();
        leaf.setSubjectId(1L);
        leaf.setPeriod(period);
        leaf.setBeginBalance(new BigDecimal("100.00"));
        leaf.setDebitTotal(BigDecimal.ZERO);
        leaf.setCreditTotal(BigDecimal.ZERO);
        leaf.setEndBalance(new BigDecimal("100.00"));

        // 第一条 selectList：当前期间 → 有 1 行；第二条（本年聚合）：空 → 本年累计按 0
        when(subjectBalanceMapper.selectList(any()))
                .thenReturn(List.of(leaf))
                .thenReturn(List.of());

        Subject subject = new Subject();
        subject.setId(1L);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.listByIds(any())).thenReturn(List.of(subject));

        // fixture 为无发生额科目，需显式 includeNoMovement=true 防止被 T5 默认过滤排除
        List<SubjectBalanceRowVO> result = ledgerService.subjectBalance(period, true, true, null);

        assertEquals(1, result.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getYearBeginBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getYearDebitTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getYearCreditTotal()));
    }

    @Test
    @DisplayName("科目余额表 - 本年累计按年初最早快照期初+本年发生汇总")
    void subjectBalance_yearTotalsAggregatedFromYearSnapshots() {
        String period = "202607";

        SubjectBalanceEntity jan = new SubjectBalanceEntity();
        jan.setSubjectId(1L);
        jan.setPeriod("202601");
        jan.setBeginBalance(new BigDecimal("200.00"));
        jan.setDebitTotal(new BigDecimal("100.00"));
        jan.setCreditTotal(new BigDecimal("50.00"));

        SubjectBalanceEntity jun = new SubjectBalanceEntity();
        jun.setSubjectId(1L);
        jun.setPeriod("202606");
        jun.setBeginBalance(new BigDecimal("250.00"));
        jun.setDebitTotal(new BigDecimal("300.00"));
        jun.setCreditTotal(new BigDecimal("0.00"));

        // 第一条 selectList：当前期间(202607) → 空；第二条（本年聚合）：jan+jun 快照
        when(subjectBalanceMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(jan, jun));

        // 当前期间无余额快照时 subjectBalance 无行，故单独验证聚合逻辑经由 generalLedger 更直观；
        // 此处以「当前期间存在快照」构造聚合口径验证
        SubjectBalanceEntity cur = new SubjectBalanceEntity();
        cur.setSubjectId(1L);
        cur.setPeriod(period);
        cur.setBeginBalance(new BigDecimal("550.00"));
        cur.setDebitTotal(BigDecimal.ZERO);
        cur.setCreditTotal(BigDecimal.ZERO);
        cur.setEndBalance(new BigDecimal("550.00"));
        when(subjectBalanceMapper.selectList(any()))
                .thenReturn(List.of(cur))
                .thenReturn(List.of(jan, jun));

        Subject subject = new Subject();
        subject.setId(1L);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.listByIds(any())).thenReturn(List.of(subject));

        List<SubjectBalanceRowVO> result = ledgerService.subjectBalance(period, true, true, null);

        assertEquals(1, result.size());
        // 年初余额 = 最早期间(202601)快照期初 = 200
        assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).getYearBeginBalance()));
        // 本年发生 = 100 + 300 = 400
        assertEquals(0, new BigDecimal("400.00").compareTo(result.get(0).getYearDebitTotal()));
        // 本年贷方 = 50 + 0 = 50
        assertEquals(0, new BigDecimal("50.00").compareTo(result.get(0).getYearCreditTotal()));
    }

    @Test
    @DisplayName("科目余额表 - 编码前缀过滤(T5)")
    void subjectBalance_filterByCodePrefix() {
        String period = "202607";

        SubjectBalanceEntity b1002 = new SubjectBalanceEntity();
        b1002.setSubjectId(1L);
        b1002.setPeriod(period);
        b1002.setBeginBalance(new BigDecimal("100.00"));
        b1002.setDebitTotal(new BigDecimal("50.00"));
        b1002.setCreditTotal(BigDecimal.ZERO);
        b1002.setEndBalance(new BigDecimal("150.00"));

        SubjectBalanceEntity b2202 = new SubjectBalanceEntity();
        b2202.setSubjectId(2L);
        b2202.setPeriod(period);
        b2202.setBeginBalance(new BigDecimal("200.00"));
        b2202.setDebitTotal(BigDecimal.ZERO);
        b2202.setCreditTotal(new BigDecimal("80.00"));
        b2202.setEndBalance(new BigDecimal("120.00"));

        when(subjectBalanceMapper.selectList(any()))
                .thenReturn(List.of(b1002, b2202))
                .thenReturn(List.of());

        Subject s1002 = new Subject();
        s1002.setId(1L);
        s1002.setCode("100299");
        s1002.setName("银行存款-工行");
        s1002.setDirection("debit");
        s1002.setIsLeaf(true);

        Subject s2202 = new Subject();
        s2202.setId(2L);
        s2202.setCode("220299");
        s2202.setName("应付账款-某供应商");
        s2202.setDirection("credit");
        s2202.setIsLeaf(true);

        when(subjectService.listByIds(any())).thenReturn(List.of(s1002, s2202));

        List<SubjectBalanceRowVO> result = ledgerService.subjectBalance(period, true, true, "1002");

        assertEquals(1, result.size(), "应只返回 1002 前缀科目");
        assertEquals("100299", result.get(0).getSubjectCode());
    }

    @Test
    @DisplayName("科目余额表 - includeZero/includeNoMovement 过滤(T5)")
    void subjectBalance_filterByZeroAndNoMovement() {
        String period = "202607";

        // 科目1：期末余额>0 且有发生额 → 默认含 / 各种组合均含
        SubjectBalanceEntity active = new SubjectBalanceEntity();
        active.setSubjectId(1L);
        active.setPeriod(period);
        active.setBeginBalance(new BigDecimal("100.00"));
        active.setDebitTotal(new BigDecimal("50.00"));
        active.setCreditTotal(BigDecimal.ZERO);
        active.setEndBalance(new BigDecimal("150.00"));

        // 科目2：期末余额=0 → includeZero=false 时排除
        SubjectBalanceEntity zeroEnd = new SubjectBalanceEntity();
        zeroEnd.setSubjectId(2L);
        zeroEnd.setPeriod(period);
        zeroEnd.setBeginBalance(new BigDecimal("100.00"));
        zeroEnd.setDebitTotal(new BigDecimal("100.00"));
        zeroEnd.setCreditTotal(BigDecimal.ZERO);
        zeroEnd.setEndBalance(BigDecimal.ZERO);

        // 科目3：无发生额 → includeNoMovement=false 时排除
        SubjectBalanceEntity noMove = new SubjectBalanceEntity();
        noMove.setSubjectId(3L);
        noMove.setPeriod(period);
        noMove.setBeginBalance(new BigDecimal("500.00"));
        noMove.setDebitTotal(BigDecimal.ZERO);
        noMove.setCreditTotal(BigDecimal.ZERO);
        noMove.setEndBalance(new BigDecimal("500.00"));

        // 每次 selectList 都返回同一批 3 行（本测试不涉及本年累计断言，重复返回无副作用）
        when(subjectBalanceMapper.selectList(any())).thenReturn(List.of(active, zeroEnd, noMove));

        Subject s1 = new Subject();
        s1.setId(1L);
        s1.setCode("1001");
        s1.setDirection("debit");
        s1.setIsLeaf(true);
        Subject s2 = new Subject();
        s2.setId(2L);
        s2.setCode("1002");
        s2.setDirection("debit");
        s2.setIsLeaf(true);
        Subject s3 = new Subject();
        s3.setId(3L);
        s3.setCode("1003");
        s3.setDirection("debit");
        s3.setIsLeaf(true);
        when(subjectService.listByIds(any())).thenReturn(List.of(s1, s2, s3));

        // 默认（false/false）：只含 科目1
        List<SubjectBalanceRowVO> defaultRows = ledgerService.subjectBalance(period, false, false, null);
        assertEquals(1, defaultRows.size(), "默认应排除零余额与无发生额科目");
        assertEquals(1L, defaultRows.get(0).getSubjectId());

        // includeZero=true：加回科目2
        List<SubjectBalanceRowVO> withZero = ledgerService.subjectBalance(period, true, false, null);
        assertEquals(2, withZero.size(), "includeZero=true 应含零余额科目2");
        assertTrue(withZero.stream().anyMatch(r -> r.getSubjectId().equals(2L)));

        // includeNoMovement=true：加回科目3
        List<SubjectBalanceRowVO> withMove = ledgerService.subjectBalance(period, false, true, null);
        assertEquals(2, withMove.size(), "includeNoMovement=true 应含无发生额科目3");
        assertTrue(withMove.stream().anyMatch(r -> r.getSubjectId().equals(3L)));

        // 全开：3 个科目全含
        List<SubjectBalanceRowVO> allRows = ledgerService.subjectBalance(period, true, true, null);
        assertEquals(3, allRows.size(), "全开应含全部3个科目");
    }

    @Test
    @DisplayName("科目余额表 - 空期间返回空列表")
    void subjectBalance_shouldReturnEmptyList_whenNoData() {
        // Arrange
        when(subjectBalanceMapper.selectList(any())).thenReturn(List.of());

        // Act
        List<SubjectBalanceRowVO> result = ledgerService.subjectBalance("202608");

        // Assert
        assertTrue(result.isEmpty());
        verify(subjectBalanceMapper).selectList(any());
        verifyNoInteractions(subjectService);
    }

    // ─── generalLedger ─────────────────────────────────────────────────────

    @Test
    @DisplayName("总分类账 - 借方科目返回完整的期初+分录+本期合计")
    void generalLedger_shouldReturnOpeningEntriesAndClosing_whenDebitDirection() {
        // Arrange
        Long subjectId = 1L;
        String period = "202607";

        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);

        SubjectBalanceEntity balance = new SubjectBalanceEntity();
        balance.setSubjectId(subjectId);
        balance.setPeriod(period);
        balance.setBeginBalance(new BigDecimal("1000.00"));
        when(subjectBalanceMapper.selectOne(any())).thenReturn(balance);

        LedgerEntryRowDTO entry1 = new LedgerEntryRowDTO();
        entry1.setVoucherId(10L);
        entry1.setVoucherNo("V-202607-0001");
        entry1.setVoucherDate(java.time.LocalDate.of(2026, 7, 1));
        entry1.setSubjectId(subjectId);
        entry1.setDebit(new BigDecimal("500.00"));
        entry1.setCredit(BigDecimal.ZERO);
        entry1.setSummary("销售收入");

        LedgerEntryRowDTO entry2 = new LedgerEntryRowDTO();
        entry2.setVoucherId(20L);
        entry2.setVoucherNo("V-202607-0002");
        entry2.setVoucherDate(java.time.LocalDate.of(2026, 7, 10));
        entry2.setSubjectId(subjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("200.00"));
        entry2.setSummary("银行取现");

        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, period, null, null, false))
                .thenReturn(List.of(entry1, entry2));

        // Act
        List<LedgerRowVO> result = ledgerService.generalLedger(subjectId, period);

        // Assert
        assertEquals(5, result.size(), "应为 期初 + 2笔分录 + 本期合计 + 本年累计");

        // 第1行：期初余额
        LedgerRowVO opening = result.get(0);
        assertEquals("OPENING", opening.getType());
        assertEquals("期初余额", opening.getSummary());
        assertEquals(BigDecimal.ZERO, opening.getDebit());
        assertEquals(BigDecimal.ZERO, opening.getCredit());
        assertEquals(new BigDecimal("1000.00"), opening.getRunning());

        // 第2行：分录1
        LedgerRowVO r1 = result.get(1);
        assertEquals("ENTRY", r1.getType());
        assertEquals(10L, r1.getVoucherId());
        assertEquals("V-202607-0001", r1.getVoucherNo(), "分录1应带凭证号(T6)");
        assertEquals(java.time.LocalDate.of(2026, 7, 1), r1.getVoucherDate(), "分录1应带凭证日期(T6)");
        assertEquals("销售收入", r1.getSummary());
        assertEquals(new BigDecimal("500.00"), r1.getDebit());
        assertEquals(BigDecimal.ZERO, r1.getCredit());
        // running = 1000 + 500 - 0 = 1500
        assertEquals(new BigDecimal("1500.00"), r1.getRunning());

        // 第3行：分录2
        LedgerRowVO r2 = result.get(2);
        assertEquals("ENTRY", r2.getType());
        assertEquals(20L, r2.getVoucherId());
        assertEquals("V-202607-0002", r2.getVoucherNo(), "分录2应带凭证号(T6)");
        assertEquals("银行取现", r2.getSummary());
        assertEquals(BigDecimal.ZERO, r2.getDebit());
        assertEquals(new BigDecimal("200.00"), r2.getCredit());
        // running = 1500 + 0 - 200 = 1300
        assertEquals(new BigDecimal("1300.00"), r2.getRunning());

        // 第4行：本期合计
        LedgerRowVO closing = result.get(3);
        assertEquals("CLOSING", closing.getType());
        assertEquals("本期合计", closing.getSummary());
        assertEquals(new BigDecimal("500.00"), closing.getDebit());
        assertEquals(new BigDecimal("200.00"), closing.getCredit());
        assertEquals(new BigDecimal("1300.00"), closing.getRunning());

        // 第5行：本年累计（T4），无本年快照 → 全 0
        LedgerRowVO yearTotal = result.get(4);
        assertEquals("YEAR_TOTAL", yearTotal.getType());
        assertEquals("本年累计", yearTotal.getSummary());
        assertEquals(BigDecimal.ZERO, yearTotal.getDebit());
        assertEquals(BigDecimal.ZERO, yearTotal.getCredit());
        assertEquals(BigDecimal.ZERO, yearTotal.getRunning());

        verify(subjectService).getById(subjectId);
        verify(subjectBalanceMapper).selectOne(any());
        verify(subjectBalanceMapper).selectList(any());
        verify(voucherEntryMapper).selectSubsidiaryRows(subjectId, period, null, null, false);
    }

    @Test
    @DisplayName("总分类账 - 贷方科目方向，余额计算用 running = running + credit - debit")
    void generalLedger_shouldCalculateRunningForCreditDirection() {
        // Arrange
        Long subjectId = 2L;
        String period = "202607";

        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("2001");
        subject.setName("短期借款");
        subject.setDirection("credit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);

        SubjectBalanceEntity balance = new SubjectBalanceEntity();
        balance.setSubjectId(subjectId);
        balance.setPeriod(period);
        balance.setBeginBalance(new BigDecimal("5000.00"));
        when(subjectBalanceMapper.selectOne(any())).thenReturn(balance);

        LedgerEntryRowDTO entry1 = new LedgerEntryRowDTO();
        entry1.setVoucherId(30L);
        entry1.setVoucherNo("V-202607-0003");
        entry1.setVoucherDate(java.time.LocalDate.of(2026, 7, 2));
        entry1.setSubjectId(subjectId);
        entry1.setDebit(BigDecimal.ZERO);
        entry1.setCredit(new BigDecimal("1000.00"));
        entry1.setSummary("新增借款");

        LedgerEntryRowDTO entry2 = new LedgerEntryRowDTO();
        entry2.setVoucherId(40L);
        entry2.setVoucherNo("V-202607-0004");
        entry2.setVoucherDate(java.time.LocalDate.of(2026, 7, 5));
        entry2.setSubjectId(subjectId);
        entry2.setDebit(new BigDecimal("500.00"));
        entry2.setCredit(BigDecimal.ZERO);
        entry2.setSummary("归还借款");

        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, period, null, null, false))
                .thenReturn(List.of(entry1, entry2));

        // Act
        List<LedgerRowVO> result = ledgerService.generalLedger(subjectId, period);

        // Assert
        assertEquals(5, result.size(), "应为 期初 + 2笔分录 + 本期合计 + 本年累计");

        // 期初：running = 5000
        assertEquals(new BigDecimal("5000.00"), result.get(0).getRunning());

        // 分录1：running = 5000 + 1000 - 0 = 6000
        assertEquals(new BigDecimal("6000.00"), result.get(1).getRunning());

        // 分录2：running = 6000 + 0 - 500 = 5500
        assertEquals(new BigDecimal("5500.00"), result.get(2).getRunning());

        // 本期合计
        LedgerRowVO closing = result.get(3);
        assertEquals("CLOSING", closing.getType());
        assertEquals(new BigDecimal("500.00"), closing.getDebit());
        assertEquals(new BigDecimal("1000.00"), closing.getCredit());
        assertEquals(new BigDecimal("5500.00"), closing.getRunning());

        // 本年累计行（T4），无本年快照 → 全 0
        LedgerRowVO yearTotal = result.get(4);
        assertEquals("YEAR_TOTAL", yearTotal.getType());

        verify(subjectService).getById(subjectId);
        verify(subjectBalanceMapper).selectOne(any());
        verify(subjectBalanceMapper).selectList(any());
        verify(voucherEntryMapper).selectSubsidiaryRows(subjectId, period, null, null, false);
    }

    @Test
    @DisplayName("总分类账 - 科目不存在返回空列表")
    void generalLedger_shouldReturnEmptyList_whenSubjectNotFound() {
        // Arrange
        when(subjectService.getById(999L)).thenReturn(null);

        // Act
        List<LedgerRowVO> result = ledgerService.generalLedger(999L, "202607");

        // Assert
        assertTrue(result.isEmpty());
        verify(subjectService).getById(999L);
        verifyNoInteractions(subjectBalanceMapper, voucherEntryMapper);
    }

    @Test
    @DisplayName("总分类账 - 无余额快照(期初为null)时期初余额按0处理")
    void generalLedger_shouldTreatNullBalanceAsZeroOpening() {
        // Arrange
        Long subjectId = 1L;
        String period = "202607";

        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);

        // 该期间无余额快照行 → selectOne 返回 null（正常业务：余额快照只在过账时写入）
        when(subjectBalanceMapper.selectOne(any())).thenReturn(null);

        LedgerEntryRowDTO entry = new LedgerEntryRowDTO();
        entry.setVoucherId(10L);
        entry.setVoucherNo("V-202607-0005");
        entry.setVoucherDate(java.time.LocalDate.of(2026, 7, 1));
        entry.setSubjectId(subjectId);
        entry.setDebit(new BigDecimal("500.00"));
        entry.setCredit(BigDecimal.ZERO);
        entry.setSummary("销售收入");
        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, period, null, null, false)).thenReturn(List.of(entry));

        // Act
        List<LedgerRowVO> result = ledgerService.generalLedger(subjectId, period);

// Assert
        assertEquals(4, result.size(), "应为 期初 + 1笔分录 + 本期合计 + 本年累计");

        // 期初行 running = 0（余额快照不存在）
        assertEquals(BigDecimal.ZERO, result.get(0).getRunning());

        // 分录行 running = 0 + 500 - 0 = 500
        assertEquals(new BigDecimal("500.00"), result.get(1).getRunning());

        // 本期合计
        LedgerRowVO closing = result.get(2);
        assertEquals("CLOSING", closing.getType());
        assertEquals(new BigDecimal("500.00"), closing.getDebit());
        assertEquals(BigDecimal.ZERO, closing.getCredit());
        assertEquals(new BigDecimal("500.00"), closing.getRunning());

        // 本年累计行（T4），无本年快照 → 全 0
        LedgerRowVO yearTotal = result.get(3);
        assertEquals("YEAR_TOTAL", yearTotal.getType());

        verify(subjectService).getById(subjectId);
        verify(subjectBalanceMapper).selectOne(any());
        verify(subjectBalanceMapper).selectList(any());
        verify(voucherEntryMapper).selectSubsidiaryRows(subjectId, period, null, null, false);
    }

    @Test
    @DisplayName("总分类账 - includeUnposted=true 透传给 Mapper(T8)")
    void generalLedger_passesIncludeUnpostedToMapper() {
        Long subjectId = 1L;
        String period = "202607";
        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);
        when(subjectBalanceMapper.selectOne(any())).thenReturn(null);
        when(subjectBalanceMapper.selectList(any())).thenReturn(List.of());
        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, period, null, null, true)).thenReturn(List.of());

        ledgerService.generalLedger(subjectId, period, true);

        verify(voucherEntryMapper).selectSubsidiaryRows(subjectId, period, null, null, true);
    }

    // ─── subsidiaryLedger ──────────────────────────────────────────────────

    @Test
    @DisplayName("明细账 - 返回期初行+分录行(滚动余额)")
    void subsidiaryLedger_shouldReturnOpeningAndEntries() {
        // Arrange
        Long subjectId = 1L;

        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);

        SubjectBalanceEntity balance = new SubjectBalanceEntity();
        balance.setSubjectId(subjectId);
        balance.setPeriod("202607");
        balance.setBeginBalance(new BigDecimal("1000.00"));
        when(subjectBalanceMapper.selectOne(any())).thenReturn(balance);

        LedgerEntryRowDTO entry1 = new LedgerEntryRowDTO();
        entry1.setVoucherId(10L);
        entry1.setVoucherNo("V-202607-0001");
        entry1.setVoucherDate(java.time.LocalDate.of(2026, 7, 1));
        entry1.setSubjectId(subjectId);
        entry1.setSummary("销售收入");
        entry1.setDebit(new BigDecimal("500.00"));
        entry1.setCredit(BigDecimal.ZERO);

        LedgerEntryRowDTO entry2 = new LedgerEntryRowDTO();
        entry2.setVoucherId(20L);
        entry2.setVoucherNo("V-202607-0002");
        entry2.setVoucherDate(java.time.LocalDate.of(2026, 7, 10));
        entry2.setSubjectId(subjectId);
        entry2.setSummary("银行取现");
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("200.00"));

        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, "202607", null, null, false))
                .thenReturn(List.of(entry1, entry2));

        // Act
        List<LedgerRowVO> result = ledgerService.subsidiaryLedger(subjectId, "202607", null, null);

        // Assert: 期初行 + 2 分录行
        assertEquals(3, result.size());

        // 期初行
        LedgerRowVO opening = result.get(0);
        assertEquals("OPENING", opening.getType());
        assertEquals("期初余额", opening.getSummary());
        assertEquals(new BigDecimal("1000.00"), opening.getRunning());
        assertEquals(BigDecimal.ZERO, opening.getDebit());
        assertEquals(BigDecimal.ZERO, opening.getCredit());

        // 分录1: running = 1000 + 500 - 0 = 1500
        LedgerRowVO row1 = result.get(1);
        assertEquals("ENTRY", row1.getType());
        assertEquals(10L, row1.getVoucherId());
        assertEquals("V-202607-0001", row1.getVoucherNo());
        assertEquals(java.time.LocalDate.of(2026, 7, 1), row1.getVoucherDate());
        assertEquals(subjectId, row1.getSubjectId());
        assertEquals("1001", row1.getSubjectCode());
        assertEquals("库存现金", row1.getSubjectName());
        assertEquals("销售收入", row1.getSummary());
        assertEquals(new BigDecimal("500.00"), row1.getDebit());
        assertEquals(BigDecimal.ZERO, row1.getCredit());
        assertEquals(new BigDecimal("1500.00"), row1.getRunning());

        // 分录2: running = 1500 + 0 - 200 = 1300
        LedgerRowVO row2 = result.get(2);
        assertEquals("ENTRY", row2.getType());
        assertEquals(20L, row2.getVoucherId());
        assertEquals("V-202607-0002", row2.getVoucherNo());
        assertEquals("银行取现", row2.getSummary());
        assertEquals(BigDecimal.ZERO, row2.getDebit());
        assertEquals(new BigDecimal("200.00"), row2.getCredit());
        assertEquals(new BigDecimal("1300.00"), row2.getRunning());

        verify(subjectService).getById(subjectId);
        verify(subjectBalanceMapper).selectOne(any());
        verify(voucherEntryMapper).selectSubsidiaryRows(subjectId, "202607", null, null, false);
    }

    @Test
    @DisplayName("明细账 - 无余额快照时期初行按0处理")
    void subsidiaryLedger_zeroOpeningWhenNoBalanceSnapshot() {
        Long subjectId = 1L;
        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);
        when(subjectBalanceMapper.selectOne(any())).thenReturn(null);

        LedgerEntryRowDTO entry = new LedgerEntryRowDTO();
        entry.setVoucherId(10L);
        entry.setVoucherNo("V-202607-0001");
        entry.setSubjectId(subjectId);
        entry.setSummary("销售收入");
        entry.setDebit(new BigDecimal("500.00"));
        entry.setCredit(BigDecimal.ZERO);
        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, "202607", null, null, false))
                .thenReturn(List.of(entry));

        List<LedgerRowVO> result = ledgerService.subsidiaryLedger(subjectId, "202607", null, null);

        assertEquals(2, result.size(), "期初行 + 1 分录行");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getRunning()), "无快照期初=0");
        assertEquals(0, new BigDecimal("500.00").compareTo(result.get(1).getRunning()), "滚动余额=0+500");
    }

    @Test
    @DisplayName("明细账 - 日期范围参数透传给 Mapper")
    void subsidiaryLedger_passesDateRangeToMapper() {
        Long subjectId = 1L;
        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);
        when(subjectBalanceMapper.selectOne(any())).thenReturn(null);
        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, "202607",
                java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 15), false))
                .thenReturn(List.of());

        List<LedgerRowVO> result = ledgerService.subsidiaryLedger(subjectId, "202607",
                java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 15));

        assertEquals(1, result.size(), "无分录时仅期初行");
        assertEquals("OPENING", result.get(0).getType());
        verify(voucherEntryMapper).selectSubsidiaryRows(subjectId, "202607",
                java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 15), false);
    }

    @Test
    @DisplayName("明细账 - includeUnposted=true 透传给 Mapper")
    void subsidiaryLedger_passesIncludeUnpostedToMapper() {
        Long subjectId = 1L;
        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);
        when(subjectBalanceMapper.selectOne(any())).thenReturn(null);
        when(voucherEntryMapper.selectSubsidiaryRows(subjectId, "202607", null, null, true))
                .thenReturn(List.of());

        ledgerService.subsidiaryLedger(subjectId, "202607", null, null, true);

        verify(voucherEntryMapper).selectSubsidiaryRows(subjectId, "202607", null, null, true);
    }

    @Test
    @DisplayName("明细账 - 科目不存在返回空列表")
    void subsidiaryLedger_shouldReturnEmptyList_whenSubjectNotFound() {
        // Arrange
        when(subjectService.getById(999L)).thenReturn(null);

        // Act
        List<LedgerRowVO> result = ledgerService.subsidiaryLedger(999L, "202607", null, null);

        // Assert
        assertTrue(result.isEmpty());
        verify(subjectService).getById(999L);
        verifyNoInteractions(voucherEntryMapper);
    }

    // ─── auxiliaryLedger（辅助核算账）─────────────────────────────────────

    private Subject newDebitLeafSubject(Long id) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setCode("1122");
        subject.setName("应收账款");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        return subject;
    }

    @Test
    @DisplayName("辅助核算账 - 按客户维度+指定客户查询，维度过滤生效")
    void auxiliaryLedger_byCustomer_dimensionFiltered() {
        // Given: customer 维度，指定 customerId=1001
        AuxiliarySummaryRow movement = new AuxiliarySummaryRow();
        movement.setSubjectId(1L);
        movement.setDimensionValue("1001");
        movement.setDebitTotal(new BigDecimal("700.00"));
        movement.setCreditTotal(BigDecimal.ZERO);

        when(voucherEntryMapper.selectAuxiliaryMovement("customerId", 1001L, "202608"))
                .thenReturn(List.of(movement));
        when(voucherEntryMapper.selectAuxiliaryOpening("customerId", 1001L, "202608"))
                .thenReturn(List.of());
        when(subjectService.listByIds(any())).thenReturn(List.of(newDebitLeafSubject(1L)));
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(customerOf(1001L, "北京华信")));

        // When
        List<AuxiliaryLedgerRowVO> result = ledgerService.auxiliaryLedger("customer", "202608", 1001L);

        // Then
        assertEquals(1, result.size());
        AuxiliaryLedgerRowVO row = result.get(0);
        assertEquals(1L, row.getSubjectId());
        assertEquals("customer", row.getDimensionType());
        assertEquals(1001L, row.getDimensionValue());
        assertEquals("北京华信", row.getDimensionName());
        assertEquals(0, new BigDecimal("700.00").compareTo(row.getDebitTotal()));
        assertEquals("debit", row.getDirection());
        // And: 该客户无期初 → begin=0, end=700
        assertEquals(0, BigDecimal.ZERO.compareTo(row.getBeginBalance()));
        assertEquals(0, new BigDecimal("700.00").compareTo(row.getEndBalance()));

        verify(voucherEntryMapper).selectAuxiliaryMovement("customerId", 1001L, "202608");
        verify(voucherEntryMapper).selectAuxiliaryOpening("customerId", 1001L, "202608");
    }

    private CustomerEntity customerOf(Long id, String name) {
        CustomerEntity c = new CustomerEntity();
        c.setId(id);
        c.setName(name);
        return c;
    }

    @Test
    @DisplayName("辅助核算账 - 维度无数据返回空列表")
    void auxiliaryLedger_noData_returnsEmpty() {
        when(voucherEntryMapper.selectAuxiliaryMovement("customerId", 9999L, "202608"))
                .thenReturn(List.of());
        when(voucherEntryMapper.selectAuxiliaryOpening("customerId", 9999L, "202608"))
                .thenReturn(List.of());

        List<AuxiliaryLedgerRowVO> result = ledgerService.auxiliaryLedger("customer", "202608", 9999L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(subjectService, customerMapper);
    }

    @Test
    @DisplayName("辅助核算账 - dimensionValue为空按维度值分组")
    void auxiliaryLedger_groupByDimensionValue() {
        AuxiliarySummaryRow r1 = new AuxiliarySummaryRow();
        r1.setSubjectId(1L);
        r1.setDimensionValue("1001");
        r1.setDebitTotal(new BigDecimal("500.00"));
        r1.setCreditTotal(BigDecimal.ZERO);

        AuxiliarySummaryRow r2 = new AuxiliarySummaryRow();
        r2.setSubjectId(1L);
        r2.setDimensionValue("2001");
        r2.setDebitTotal(new BigDecimal("300.00"));
        r2.setCreditTotal(BigDecimal.ZERO);

        when(voucherEntryMapper.selectAuxiliaryMovement(eq("customerId"), isNull(), eq("202608")))
                .thenReturn(List.of(r1, r2));
        when(voucherEntryMapper.selectAuxiliaryOpening(eq("customerId"), isNull(), eq("202608")))
                .thenReturn(List.of());
        when(subjectService.listByIds(any())).thenReturn(List.of(newDebitLeafSubject(1L)));
        when(customerMapper.selectBatchIds(any()))
                .thenReturn(List.of(customerOf(1001L, "北京华信"), customerOf(2001L, "上海腾达")));

        List<AuxiliaryLedgerRowVO> result = ledgerService.auxiliaryLedger("customer", "202608", null);

        assertEquals(2, result.size());
        AuxiliaryLedgerRowVO row1 = result.stream().filter(r -> r.getDimensionValue() == 1001L).findFirst().orElse(null);
        AuxiliaryLedgerRowVO row2 = result.stream().filter(r -> r.getDimensionValue() == 2001L).findFirst().orElse(null);
        assertNotNull(row1);
        assertNotNull(row2);
        assertEquals("北京华信", row1.getDimensionName());
        assertEquals("上海腾达", row2.getDimensionName());
        assertEquals(0, new BigDecimal("500.00").compareTo(row1.getDebitTotal()));
        assertEquals(0, new BigDecimal("300.00").compareTo(row2.getDebitTotal()));
    }

    @Test
    @DisplayName("辅助核算账 - 非法维度类型抛业务异常")
    void auxiliaryLedger_invalidDimensionType_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ledgerService.auxiliaryLedger("unknown", "202608", null));
        assertTrue(ex.getMessage().contains("不支持的辅助核算维度类型"));
        verifyNoInteractions(voucherEntryMapper);
    }

    @Test
    @DisplayName("辅助核算账 - 期初+发生=期末恒等式（debit科目）")
    void auxiliaryLedger_balanceIdentity() {
        // 历史期累计借方 300 → 期初推算 begin=300（debit 科目：opDebit - opCredit = 300）
        AuxiliarySummaryRow opening = new AuxiliarySummaryRow();
        opening.setSubjectId(1L);
        opening.setDimensionValue("1001");
        opening.setDebitTotal(new BigDecimal("300.00"));
        opening.setCreditTotal(BigDecimal.ZERO);

        AuxiliarySummaryRow movement = new AuxiliarySummaryRow();
        movement.setSubjectId(1L);
        movement.setDimensionValue("1001");
        movement.setDebitTotal(new BigDecimal("700.00"));
        movement.setCreditTotal(BigDecimal.ZERO);

        when(voucherEntryMapper.selectAuxiliaryMovement("customerId", 1001L, "202608"))
                .thenReturn(List.of(movement));
        when(voucherEntryMapper.selectAuxiliaryOpening("customerId", 1001L, "202608"))
                .thenReturn(List.of(opening));
        when(subjectService.listByIds(any())).thenReturn(List.of(newDebitLeafSubject(1L)));
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(customerOf(1001L, "北京华信")));

        List<AuxiliaryLedgerRowVO> result = ledgerService.auxiliaryLedger("customer", "202608", 1001L);

        assertEquals(1, result.size());
        AuxiliaryLedgerRowVO row = result.get(0);
        assertEquals(0, new BigDecimal("300.00").compareTo(row.getBeginBalance()));
        assertEquals(0, new BigDecimal("700.00").compareTo(row.getDebitTotal()));
        // end = 300 + 700 - 0 = 1000
        assertEquals(0, new BigDecimal("1000.00").compareTo(row.getEndBalance()));
    }
}