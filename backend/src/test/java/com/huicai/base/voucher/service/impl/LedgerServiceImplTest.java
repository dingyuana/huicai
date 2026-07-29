package com.huicai.base.voucher.service.impl;

import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.service.SubjectService;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
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

        when(subjectService.getById(1L)).thenReturn(debitSubject);
        when(subjectService.getById(2L)).thenReturn(creditSubject);
        when(subjectService.getById(3L)).thenReturn(nonLeafSubject);

        // Act
        List<Map<String, Object>> result = ledgerService.subjectBalance(period);

        // Assert
        assertEquals(2, result.size(), "应只包含末级科目");

        Map<String, Object> row1 = result.get(0);
        assertEquals(1L, row1.get("subjectId"));
        assertEquals("1001", row1.get("subjectCode"));
        assertEquals("库存现金", row1.get("subjectName"));
        assertEquals("debit", row1.get("direction"));
        assertEquals(new BigDecimal("1000.00"), row1.get("beginBalance"));
        assertEquals(new BigDecimal("500.00"), row1.get("debitTotal"));
        assertEquals(new BigDecimal("200.00"), row1.get("creditTotal"));
        assertEquals(new BigDecimal("1300.00"), row1.get("endBalance"));

        Map<String, Object> row2 = result.get(1);
        assertEquals(2L, row2.get("subjectId"));
        assertEquals("2001", row2.get("subjectCode"));
        assertEquals("短期借款", row2.get("subjectName"));
        assertEquals("credit", row2.get("direction"));

        verify(subjectBalanceMapper).selectList(any());
        verify(subjectService, times(3)).getById(anyLong());
    }

    @Test
    @DisplayName("科目余额表 - 空期间返回空列表")
    void subjectBalance_shouldReturnEmptyList_whenNoData() {
        // Arrange
        when(subjectBalanceMapper.selectList(any())).thenReturn(List.of());

        // Act
        List<Map<String, Object>> result = ledgerService.subjectBalance("202608");

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

        VoucherEntryEntity entry1 = new VoucherEntryEntity();
        entry1.setVoucherId(10L);
        entry1.setSubjectId(subjectId);
        entry1.setDebit(new BigDecimal("500.00"));
        entry1.setCredit(BigDecimal.ZERO);
        entry1.setSummary("销售收入");

        VoucherEntryEntity entry2 = new VoucherEntryEntity();
        entry2.setVoucherId(20L);
        entry2.setSubjectId(subjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("200.00"));
        entry2.setSummary("银行取现");

        when(voucherEntryMapper.selectList(any())).thenReturn(List.of(entry1, entry2));

        // Act
        List<Map<String, Object>> result = ledgerService.generalLedger(subjectId, period);

        // Assert
        assertEquals(4, result.size(), "应为 期初 + 2笔分录 + 本期合计");

        // 第1行：期初余额
        Map<String, Object> opening = result.get(0);
        assertEquals("OPENING", opening.get("type"));
        assertEquals("期初余额", opening.get("summary"));
        assertEquals(BigDecimal.ZERO, opening.get("debit"));
        assertEquals(BigDecimal.ZERO, opening.get("credit"));
        assertEquals(new BigDecimal("1000.00"), opening.get("running"));

        // 第2行：分录1
        Map<String, Object> r1 = result.get(1);
        assertEquals("ENTRY", r1.get("type"));
        assertEquals(10L, r1.get("voucherId"));
        assertEquals("销售收入", r1.get("summary"));
        assertEquals(new BigDecimal("500.00"), r1.get("debit"));
        assertEquals(BigDecimal.ZERO, r1.get("credit"));
        // running = 1000 + 500 - 0 = 1500
        assertEquals(new BigDecimal("1500.00"), r1.get("running"));

        // 第3行：分录2
        Map<String, Object> r2 = result.get(2);
        assertEquals("ENTRY", r2.get("type"));
        assertEquals(20L, r2.get("voucherId"));
        assertEquals("银行取现", r2.get("summary"));
        assertEquals(BigDecimal.ZERO, r2.get("debit"));
        assertEquals(new BigDecimal("200.00"), r2.get("credit"));
        // running = 1500 + 0 - 200 = 1300
        assertEquals(new BigDecimal("1300.00"), r2.get("running"));

        // 第4行：本期合计
        Map<String, Object> closing = result.get(3);
        assertEquals("CLOSING", closing.get("type"));
        assertEquals("本期合计", closing.get("summary"));
        assertEquals(new BigDecimal("500.00"), closing.get("debit"));
        assertEquals(new BigDecimal("200.00"), closing.get("credit"));
        assertEquals(new BigDecimal("1300.00"), closing.get("running"));

        verify(subjectService).getById(subjectId);
        verify(subjectBalanceMapper).selectOne(any());
        verify(voucherEntryMapper).selectList(any());
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

        VoucherEntryEntity entry1 = new VoucherEntryEntity();
        entry1.setVoucherId(30L);
        entry1.setSubjectId(subjectId);
        entry1.setDebit(BigDecimal.ZERO);
        entry1.setCredit(new BigDecimal("1000.00"));
        entry1.setSummary("新增借款");

        VoucherEntryEntity entry2 = new VoucherEntryEntity();
        entry2.setVoucherId(40L);
        entry2.setSubjectId(subjectId);
        entry2.setDebit(new BigDecimal("500.00"));
        entry2.setCredit(BigDecimal.ZERO);
        entry2.setSummary("归还借款");

        when(voucherEntryMapper.selectList(any())).thenReturn(List.of(entry1, entry2));

        // Act
        List<Map<String, Object>> result = ledgerService.generalLedger(subjectId, period);

        // Assert
        assertEquals(4, result.size());

        // 期初：running = 5000
        assertEquals(new BigDecimal("5000.00"), result.get(0).get("running"));

        // 分录1：running = 5000 + 1000 - 0 = 6000
        assertEquals(new BigDecimal("6000.00"), result.get(1).get("running"));

        // 分录2：running = 6000 + 0 - 500 = 5500
        assertEquals(new BigDecimal("5500.00"), result.get(2).get("running"));

        // 本期合计
        Map<String, Object> closing = result.get(3);
        assertEquals("CLOSING", closing.get("type"));
        assertEquals(new BigDecimal("500.00"), closing.get("debit"));
        assertEquals(new BigDecimal("1000.00"), closing.get("credit"));
        assertEquals(new BigDecimal("5500.00"), closing.get("running"));

        verify(subjectService).getById(subjectId);
        verify(subjectBalanceMapper).selectOne(any());
        verify(voucherEntryMapper).selectList(any());
    }

    @Test
    @DisplayName("总分类账 - 科目不存在返回空列表")
    void generalLedger_shouldReturnEmptyList_whenSubjectNotFound() {
        // Arrange
        when(subjectService.getById(999L)).thenReturn(null);

        // Act
        List<Map<String, Object>> result = ledgerService.generalLedger(999L, "202607");

        // Assert
        assertTrue(result.isEmpty());
        verify(subjectService).getById(999L);
        verifyNoInteractions(subjectBalanceMapper, voucherEntryMapper);
    }

    // ─── subsidiaryLedger ──────────────────────────────────────────────────

    @Test
    @DisplayName("明细账 - 返回科目明细账")
    void subsidiaryLedger_shouldReturnEntries() {
        // Arrange
        Long subjectId = 1L;

        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setCode("1001");
        subject.setName("库存现金");
        subject.setDirection("debit");
        subject.setIsLeaf(true);
        when(subjectService.getById(subjectId)).thenReturn(subject);

        VoucherEntryEntity entry1 = new VoucherEntryEntity();
        entry1.setVoucherId(10L);
        entry1.setSubjectId(subjectId);
        entry1.setDebit(new BigDecimal("500.00"));
        entry1.setCredit(BigDecimal.ZERO);
        entry1.setSummary("销售收入");
        entry1.setAssistJson("{}");

        VoucherEntryEntity entry2 = new VoucherEntryEntity();
        entry2.setVoucherId(20L);
        entry2.setSubjectId(subjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("200.00"));
        entry2.setSummary("银行取现");
        entry2.setAssistJson("{\"dept\":\"财务部\"}");

        when(voucherEntryMapper.selectList(any())).thenReturn(List.of(entry1, entry2));

        // Act
        List<Map<String, Object>> result = ledgerService.subsidiaryLedger(subjectId, "202607");

        // Assert
        assertEquals(2, result.size());

        Map<String, Object> row1 = result.get(0);
        assertEquals(10L, row1.get("voucherId"));
        assertEquals(subjectId, row1.get("subjectId"));
        assertEquals("1001", row1.get("subjectCode"));
        assertEquals("库存现金", row1.get("subjectName"));
        assertEquals("销售收入", row1.get("summary"));
        assertEquals(new BigDecimal("500.00"), row1.get("debit"));
        assertEquals(BigDecimal.ZERO, row1.get("credit"));
        assertEquals("{}", row1.get("assistJson"));

        Map<String, Object> row2 = result.get(1);
        assertEquals(20L, row2.get("voucherId"));
        assertEquals("银行取现", row2.get("summary"));
        assertEquals(BigDecimal.ZERO, row2.get("debit"));
        assertEquals(new BigDecimal("200.00"), row2.get("credit"));
        assertEquals("{\"dept\":\"财务部\"}", row2.get("assistJson"));

        verify(subjectService).getById(subjectId);
        verify(voucherEntryMapper).selectList(any());
    }

    @Test
    @DisplayName("明细账 - 科目不存在返回空列表")
    void subsidiaryLedger_shouldReturnEmptyList_whenSubjectNotFound() {
        // Arrange
        when(subjectService.getById(999L)).thenReturn(null);

        // Act
        List<Map<String, Object>> result = ledgerService.subsidiaryLedger(999L, "202607");

        // Assert
        assertTrue(result.isEmpty());
        verify(subjectService).getById(999L);
        verifyNoInteractions(voucherEntryMapper);
    }
}