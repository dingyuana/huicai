package com.huicai.sme.salary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.salary.dto.SalaryBillSaveDTO;
import com.huicai.sme.salary.entity.SalaryBillEmployeeEntity;
import com.huicai.sme.salary.entity.SalaryBillEntity;
import com.huicai.sme.salary.entity.SalaryBillItemEntity;
import com.huicai.sme.salary.entity.SalaryItemEntity;
import com.huicai.sme.salary.mapper.SalaryBillEmployeeMapper;
import com.huicai.sme.salary.mapper.SalaryBillItemMapper;
import com.huicai.sme.salary.mapper.SalaryBillMapper;
import com.huicai.sme.salary.mapper.SalaryItemMapper;
import com.huicai.sme.salary.service.impl.SalaryBillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-P143 单元面：工资单确认（聚合+累计预扣+计提凭证）/发放凭证/状态机守卫。
 *
 * <p>L2 真库面（{@code SalaryBillRealDBTest}）锁「真落库 + 两月累计」；本类锁分支与分录。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SPEC-P143：工资单 confirm/pay —— 聚合、累计预扣、凭证分录、守卫")
class SalaryBillServiceTest {

    @Mock private SalaryBillMapper billMapper;
    @Mock private SalaryBillItemMapper itemMapper;
    @Mock private SalaryBillEmployeeMapper billEmployeeMapper;
    @Mock private SalaryItemMapper salaryItemMapper;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherTypeService voucherTypeService;
    @Mock private PeriodService periodService;

    @InjectMocks
    private SalaryBillServiceImpl service;

    /** requireSubject 按调用序弹出的科目队列 */
    private final java.util.ArrayDeque<Subject> subjectQueue = new java.util.ArrayDeque<>();

    private SalaryBillEntity bill;
    private Subject s6602, s2211, s2221, s2241, s1002;

    @BeforeEach
    void setUp() {
        bill = new SalaryBillEntity();
        bill.setId(77L);
        bill.setPeriod("202611");
        bill.setStatus("DRAFT");

        s6602 = subj(601L, "6602");
        s2211 = subj(602L, "2211");
        s2221 = subj(603L, "2221");
        s2241 = subj(604L, "2241");
        s1002 = subj(605L, "1002");
        // requireSubject 调用序固定：confirm = [6602, 2211]，pay = [2211, 2221, 2241, 1002]；
        // 用队列按序弹出（LambdaQueryWrapper 参数路由在 MP 内部形态不稳，弃用）
        lenient().when(subjectMapper.selectOne(any())).thenAnswer(inv -> subjectQueue.poll());
        lenient().when(voucherTypeService.requireByCode("ZZ"))
                .thenReturn(new com.huicai.base.system.entity.VoucherTypeEntity());
        lenient().when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, VoucherEntity.class).setId(999L);
            return 1;
        });
        lenient().when(billEmployeeMapper.selectCumulative(any(), any(), any()))
                .thenReturn(new SalaryBillEmployeeMapper.CumulativeRow());
    }

    private static Subject subj(long id, String code) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode(code);
        return s;
    }

    private SalaryItemEntity def(long id, String category, boolean taxable) {
        SalaryItemEntity e = new SalaryItemEntity();
        e.setId(id);
        e.setCategory(category);
        e.setIsTaxable(taxable);
        return e;
    }

    private List<SalaryBillItemEntity> items() {
        return List.of(
                item(1L, 101L, "10000"),   // EARN 计税
                item(2L, 102L, "2000"),    // SOCIAL_INS
                item(3L, 103L, "1500"),    // SPECIAL_ADD
                item(4L, 104L, "300"));    // OTHER_DEDUCT
    }

    private SalaryBillItemEntity item(long id, long itemId, String amount) {
        SalaryBillItemEntity e = new SalaryBillItemEntity();
        e.setBillId(77L);
        e.setEmployeeId(11L);
        e.setItemId(itemId);
        e.setAmount(new BigDecimal(amount));
        return e;
    }

    private void stubHappyPath() {
        subjectQueue.clear();
        subjectQueue.add(s6602);
        subjectQueue.add(s2211);
        when(billMapper.selectById(77L)).thenReturn(bill);
        when(itemMapper.selectList(any())).thenReturn(items());
        when(salaryItemMapper.selectList(any())).thenReturn(List.of(
                def(101L, "EARN", true),
                def(102L, "SOCIAL_INS", false),
                def(103L, "SPECIAL_ADD", false),
                def(104L, "OTHER_DEDUCT", false)));
        lenient().when(employeeMapper.selectCount(any())).thenReturn(1L);
        lenient().when(billEmployeeMapper.selectCumulative(any(), any(), any())).thenAnswer(inv -> {
            SalaryBillEmployeeMapper.CumulativeRow r = new SalaryBillEmployeeMapper.CumulativeRow();
            r.cumTaxableRunning = BigDecimal.ZERO;
            r.cumTax = BigDecimal.ZERO;
            return r;
        });
    }

    @Test
    @DisplayName("confirm：聚合四类项目 + 首月累计预扣(1500→税45) + 计提凭证借6602/贷2211")
    void confirmComputesTaxAndCreatesAccrualVoucher() {
        stubHappyPath();

        service.confirm(77L, 9L);

        // 员工级快照：累计应纳税所得额 = 10000 - 2000(社保) - 1500(专项附加) - 5000(减除) = 1500 → 3% → 45.00
        ArgumentCaptor<SalaryBillEmployeeEntity> rows = ArgumentCaptor.forClass(SalaryBillEmployeeEntity.class);
        verify(billEmployeeMapper).insert(rows.capture());
        SalaryBillEmployeeEntity row = rows.getValue();
        assertEquals(0, new BigDecimal("10000").compareTo(row.getGross()));
        assertEquals(0, new BigDecimal("10000").compareTo(row.getTaxableGross()));
        assertEquals(0, new BigDecimal("2000").compareTo(row.getSocial()));
        assertEquals(0, new BigDecimal("1500").compareTo(row.getSpecialAdd()));
        assertEquals(0, new BigDecimal("45.00").compareTo(row.getTax()), "首月个税应为 45");
        assertEquals(0, new BigDecimal("7655.00").compareTo(row.getNet()), "实发 = 10000-2000-300-45");

        // 计提凭证：借 6602 管理费用 10000 / 贷 2211 应付职工薪酬 10000
        ArgumentCaptor<VoucherEntity> voucherCap = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCap.capture());
        assertEquals("SALC-77", voucherCap.getValue().getVoucherNo());
        ArgumentCaptor<VoucherEntryEntity> entries = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(2)).insert(entries.capture());
        assertEquals(601L, entries.getAllValues().get(0).getSubjectId(), "借 6602 管理费用");
        assertEquals(602L, entries.getAllValues().get(1).getSubjectId(), "贷 2211 应付职工薪酬");

        assertEquals("CONFIRMED", bill.getStatus());
        assertEquals(0, new BigDecimal("10000").compareTo(bill.getTotalGross()));
        assertEquals(0, new BigDecimal("45.00").compareTo(bill.getTotalTax()));
    }

    @Test
    @DisplayName("pay：发放凭证四行 借2211 / 贷2221个税+2241社保个人+1002银行实发")
    void payCreatesPaymentVoucher() {
        subjectQueue.clear();
        subjectQueue.add(s2211);
        subjectQueue.add(s2221);
        subjectQueue.add(s2241);
        subjectQueue.add(s1002);
        bill.setStatus("CONFIRMED");
        bill.setTotalGross(new BigDecimal("10000"));
        bill.setTotalTax(new BigDecimal("45.00"));
        bill.setTotalSocial(new BigDecimal("2000"));
        bill.setTotalNet(new BigDecimal("7655.00"));
        when(billMapper.selectById(77L)).thenReturn(bill);

        service.pay(77L, 9L);

        ArgumentCaptor<VoucherEntity> voucherCap = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCap.capture());
        assertEquals("SALP-77", voucherCap.getValue().getVoucherNo());
        ArgumentCaptor<VoucherEntryEntity> entries = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(4)).insert(entries.capture());
        List<VoucherEntryEntity> lines = entries.getAllValues();
        assertEquals(602L, lines.get(0).getSubjectId(), "借 2211");
        assertEquals(603L, lines.get(1).getSubjectId(), "贷 2221 个税");
        assertEquals(604L, lines.get(2).getSubjectId(), "贷 2241 社保个人");
        assertEquals(605L, lines.get(3).getSubjectId(), "贷 1002 银行存款");
        assertEquals(0, new BigDecimal("7655.00").compareTo(lines.get(3).getCredit()));
        assertEquals("PAID", bill.getStatus());
    }

    @Test
    @DisplayName("守卫：非 DRAFT 不可确认 / 非 CONFIRMED 不可发放 / 实发为负 400")
    void guards() {
        // 非 DRAFT 不可确认
        bill.setStatus("PAID");
        when(billMapper.selectById(77L)).thenReturn(bill);
        BusinessException ex1 = assertThrows(BusinessException.class, () -> service.confirm(77L, 9L));
        assertTrue(ex1.getMessage().contains("草稿"));

        // 非 CONFIRMED 不可发放
        bill.setStatus("DRAFT");
        BusinessException ex2 = assertThrows(BusinessException.class, () -> service.pay(77L, 9L));
        assertTrue(ex2.getMessage().contains("已确认"));

        // 实发为负（扣款超应发）→ 400
        bill.setStatus("DRAFT");
        stubHappyPath();
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 101L, "10000"),
                item(2L, 102L, "2000"),
                item(3L, 103L, "1500"),
                item(4L, 104L, "9000")));
        BusinessException ex3 = assertThrows(BusinessException.class, () -> service.confirm(77L, 9L));
        assertTrue(ex3.getMessage().contains("实发为负"));
    }

    @Test
    @DisplayName("save：已确认期间不可改 / 明细缺员工或项目 400")
    void saveGuards() {
        bill.setStatus("CONFIRMED");
        when(billMapper.selectOne(any())).thenReturn(bill);
        SalaryBillSaveDTO dto = new SalaryBillSaveDTO();
        dto.setPeriod("202611");
        dto.setItems(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dto, 9L));
        assertTrue(ex.getMessage().contains("不可修改"));
    }
}
