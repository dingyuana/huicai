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
import com.huicai.sme.salary.entity.SocialInsuranceRateEntity;
import com.huicai.sme.salary.entity.DeptSubjectMapEntity;
import com.huicai.sme.salary.mapper.SalaryBillEmployeeMapper;
import com.huicai.sme.salary.mapper.SalaryBillItemMapper;
import com.huicai.sme.salary.mapper.SalaryBillMapper;
import com.huicai.sme.salary.mapper.SalaryItemMapper;
import com.huicai.sme.salary.service.impl.SalaryBillServiceImpl;
import com.huicai.sme.salary.vo.SalaryBillItemVO;
import com.huicai.sme.salary.vo.SalaryBillVO;
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
    @Mock private com.huicai.sme.salary.mapper.SocialInsuranceRateMapper socialRateMapper;
    @Mock private com.huicai.sme.salary.mapper.DeptSubjectMapMapper deptSubjectMapMapper;
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
        // SPEC-P155 后 confirm 的 requireSubject 调用序：payable(2211) 先取，
        // 再逐员工 resolveExpenseSubject（无映射回落 6602）
        subjectQueue.add(s2211);
        subjectQueue.add(s6602);
        when(billMapper.selectById(77L)).thenReturn(bill);
        when(itemMapper.selectList(any())).thenReturn(items());
        when(salaryItemMapper.selectList(any())).thenReturn(List.of(
                def(101L, "EARN", true),
                def(102L, "SOCIAL_INS", false),
                def(103L, "SPECIAL_ADD", false),
                def(104L, "OTHER_DEDUCT", false)));
        lenient().when(deptSubjectMapMapper.selectList(any())).thenReturn(List.of());
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

    // ==================== SPEC-P155 R1/R2 ====================

    /** DTO 明细行 */
    private SalaryBillSaveDTO.ItemDTO dtoItem(long employeeId, long itemId, String amount) {
        SalaryBillSaveDTO.ItemDTO i = new SalaryBillSaveDTO.ItemDTO();
        i.setEmployeeId(employeeId);
        i.setItemId(itemId);
        i.setAmount(new BigDecimal(amount));
        return i;
    }

    @Test
    @DisplayName("R1 save 自动算：剔除前端公司社保明细，按基数×比例逐员工重算落明细")
    void saveRecomputesCompanySocial() {
        SalaryItemEntity csItem = def(201L, "COMPANY_SOCIAL", false);
        csItem.setItemName("养老保险(单位)");
        SocialInsuranceRateEntity rate = new SocialInsuranceRateEntity();
        rate.setItemId(201L);
        rate.setCompanyRate(new BigDecimal("0.16"));
        // save 链 selectList 调用序（MP Wrapper 参数路由不稳，用调用序）：
        // ①recompute 查 COMPANY_SOCIAL 项目 ②recompute 查 EARN 项目（缴费基数）
        when(salaryItemMapper.selectList(any())).thenReturn(
                List.of(csItem),
                List.of(def(101L, "EARN", true)));
        lenient().when(salaryItemMapper.selectCount(any())).thenReturn(2L);
        when(socialRateMapper.selectList(any())).thenReturn(List.of(rate));
        when(billMapper.selectOne(any())).thenReturn(null);
        lenient().when(deptSubjectMapMapper.selectList(any())).thenReturn(List.of());

        SalaryBillSaveDTO dto = new SalaryBillSaveDTO();
        dto.setPeriod("202611");
        dto.setItems(List.of(
                dtoItem(11L, 101L, "10000"),          // EARN
                dtoItem(11L, 201L, "9999")));         // 前端传的假值，应被剔除后重算 1600.00
        service.save(dto, 9L);

        ArgumentCaptor<SalaryBillItemEntity> rows = ArgumentCaptor.forClass(SalaryBillItemEntity.class);
        verify(itemMapper, times(2)).insert(rows.capture());
        List<SalaryBillItemEntity> lines = rows.getAllValues();
        assertEquals(101L, lines.get(0).getItemId(), "EARN 行保留");
        assertEquals(0, new BigDecimal("10000").compareTo(lines.get(0).getAmount()));
        assertEquals(201L, lines.get(1).getItemId(), "公司社保行被自动重算");
        assertEquals(0, new BigDecimal("1600.00").compareTo(lines.get(1).getAmount()),
                "基数=应发 10000 × 16% = 1600.00");
        // 新单实体经 insert 回填断言（service 内部 new，测试字段引用不更新）
        ArgumentCaptor<SalaryBillEntity> billCap = ArgumentCaptor.forClass(SalaryBillEntity.class);
        verify(billMapper).insert(billCap.capture());
        assertEquals(Boolean.FALSE, billCap.getValue().getSocialPaid(), "新单 social_paid 初始 false");
    }

    @Test
    @DisplayName("R1 clamp：基数低于下限按下限、高于上限按上限")
    void saveClampsBase() {
        SalaryItemEntity csItem = def(201L, "COMPANY_SOCIAL", false);
        // save 链 selectList 调用序：①COMPANY_SOCIAL ②EARN
        when(salaryItemMapper.selectList(any())).thenReturn(
                List.of(csItem),
                List.of(def(101L, "EARN", true)));
        SocialInsuranceRateEntity rate = new SocialInsuranceRateEntity();
        rate.setItemId(201L);
        rate.setCompanyRate(new BigDecimal("0.10"));
        rate.setBaseFloor(new BigDecimal("5000"));
        rate.setBaseCap(new BigDecimal("20000"));
        when(socialRateMapper.selectList(any())).thenReturn(List.of(rate));
        when(billMapper.selectOne(any())).thenReturn(null);
        lenient().when(salaryItemMapper.selectCount(any())).thenReturn(2L);
        lenient().when(deptSubjectMapMapper.selectList(any())).thenReturn(List.of());

        SalaryBillSaveDTO dto = new SalaryBillSaveDTO();
        dto.setPeriod("202611");
        dto.setItems(List.of(
                dtoItem(11L, 101L, "3000"),    // 低于下限 5000 → 基数 5000 → 500.00
                dtoItem(12L, 101L, "50000"))); // 高于上限 20000 → 基数 20000 → 2000.00
        service.save(dto, 9L);

        ArgumentCaptor<SalaryBillItemEntity> rows = ArgumentCaptor.forClass(SalaryBillItemEntity.class);
        verify(itemMapper, times(4)).insert(rows.capture());
        List<SalaryBillItemEntity> lines = rows.getAllValues();
        // 行序：kept(EARN×2) 先插入，补算的公司社保行后插入
        assertEquals(0, new BigDecimal("500.00").compareTo(lines.get(2).getAmount()),
                "员工11 基数 clamp 下限 5000×10%");
        assertEquals(0, new BigDecimal("2000.00").compareTo(lines.get(3).getAmount()),
                "员工12 基数 clamp 上限 20000×10%");
    }

    @Test
    @DisplayName("R2 分摊：员工部门→映射科目，同科目合并一行；无映射回落 6602")
    void confirmSplitsByDeptSubject() {
        stubHappyPath();
        bill.setStatus("DRAFT");
        // 三员工：11/12 部门 7（映射到 6601 销售费用）、13 无部门（回落 6602）
        SalaryItemEntity csItem = def(201L, "COMPANY_SOCIAL", false);
        when(salaryItemMapper.selectList(any())).thenReturn(List.of(
                def(101L, "EARN", true), def(102L, "SOCIAL_INS", false),
                def(103L, "SPECIAL_ADD", false), def(104L, "OTHER_DEDUCT", false), csItem));
        SalaryBillItemEntity i11 = item(1L, 101L, "10000");
        SalaryBillItemEntity i12 = new SalaryBillItemEntity();
        i12.setBillId(77L);
        i12.setEmployeeId(12L);
        i12.setItemId(101L);
        i12.setAmount(new BigDecimal("8000"));
        SalaryBillItemEntity i13 = new SalaryBillItemEntity();
        i13.setBillId(77L);
        i13.setEmployeeId(13L);
        i13.setItemId(101L);
        i13.setAmount(new BigDecimal("6000"));
        when(itemMapper.selectList(any())).thenReturn(List.of(i11, i12, i13));
        lenient().when(socialRateMapper.selectList(any())).thenReturn(List.of());
        DeptSubjectMapEntity map = new DeptSubjectMapEntity();
        map.setDeptId(7L);
        map.setSubjectId(610L);
        when(deptSubjectMapMapper.selectList(any())).thenReturn(List.of(map));
        Subject s6601 = subj(610L, "6601");
        when(subjectMapper.selectList(any())).thenReturn(List.of(s6601)); // 映射科目校验
        // 队列：payable(2211) → 员工11/12 部门7直取映射不弹队列 → 员工13 回落 6602
        subjectQueue.clear();
        subjectQueue.add(s2211);
        subjectQueue.add(s6602);
        EmployeeEntity e11 = new EmployeeEntity();
        e11.setId(11L);
        e11.setDeptId(7L);
        EmployeeEntity e12 = new EmployeeEntity();
        e12.setId(12L);
        e12.setDeptId(7L);
        EmployeeEntity e13 = new EmployeeEntity();
        e13.setId(13L);
        when(employeeMapper.selectList(any())).thenReturn(List.of(e11, e12, e13));
        lenient().when(employeeMapper.selectCount(any())).thenReturn(3L);
        lenient().when(billEmployeeMapper.selectCumulative(any(), any(), any())).thenAnswer(inv -> {
            SalaryBillEmployeeMapper.CumulativeRow r = new SalaryBillEmployeeMapper.CumulativeRow();
            r.cumTaxableRunning = BigDecimal.ZERO;
            r.cumTax = BigDecimal.ZERO;
            return r;
        });

        service.confirm(77L, 9L);

        ArgumentCaptor<VoucherEntryEntity> entries = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(3)).insert(entries.capture());
        List<VoucherEntryEntity> lines = entries.getAllValues();
        assertEquals(610L, lines.get(0).getSubjectId(), "部门7→6601 销售费用");
        assertEquals(0, new BigDecimal("18000").compareTo(lines.get(0).getDebit()),
                "员工11+12 应发合并 10000+8000");
        assertEquals(601L, lines.get(1).getSubjectId(), "员工13 无映射回落 6602");
        assertEquals(0, new BigDecimal("6000").compareTo(lines.get(1).getDebit()));
        assertEquals(602L, lines.get(2).getSubjectId(), "贷 2211 = 应发合计 24000");
        assertEquals(0, new BigDecimal("24000").compareTo(lines.get(2).getCredit()));
    }

    @Test
    @DisplayName("R1 paySocial：借2211单位+借2241个人/贷1002；幂等拒绝；无金额拒绝")
    void paySocialVoucherAndGuards() {
        subjectQueue.clear();
        subjectQueue.add(s2211);
        subjectQueue.add(s2241);
        subjectQueue.add(s1002);
        bill.setStatus("PAID");
        bill.setTotalCompanySocial(new BigDecimal("1600.00"));
        bill.setTotalSocial(new BigDecimal("2000"));
        when(billMapper.selectById(77L)).thenReturn(bill);

        service.paySocial(77L, 9L);

        ArgumentCaptor<VoucherEntity> voucherCap = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCap.capture());
        assertEquals("SALJ-77", voucherCap.getValue().getVoucherNo());
        assertEquals(0, new BigDecimal("3600.00").compareTo(voucherCap.getValue().getTotalDebit()));
        ArgumentCaptor<VoucherEntryEntity> entries = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(3)).insert(entries.capture());
        List<VoucherEntryEntity> lines = entries.getAllValues();
        assertEquals(602L, lines.get(0).getSubjectId(), "借 2211 单位社保");
        assertEquals(0, new BigDecimal("1600.00").compareTo(lines.get(0).getDebit()));
        assertEquals(604L, lines.get(1).getSubjectId(), "借 2241 个人社保代扣");
        assertEquals(0, new BigDecimal("2000").compareTo(lines.get(1).getDebit()));
        assertEquals(605L, lines.get(2).getSubjectId(), "贷 1002");
        assertEquals(0, new BigDecimal("3600.00").compareTo(lines.get(2).getCredit()));
        assertEquals(Boolean.TRUE, bill.getSocialPaid());
        assertEquals("SALJ-77", bill.getSocialVoucherNo());

        // 幂等：已缴纳拒绝
        BusinessException ex = assertThrows(BusinessException.class, () -> service.paySocial(77L, 9L));
        assertTrue(ex.getMessage().contains("不可重复"));

        // 无金额拒绝
        bill.setSocialPaid(Boolean.FALSE);
        bill.setTotalCompanySocial(BigDecimal.ZERO);
        bill.setTotalSocial(BigDecimal.ZERO);
        BusinessException ex2 = assertThrows(BusinessException.class, () -> service.paySocial(77L, 9L));
        assertTrue(ex2.getMessage().contains("无社保金额"));
    }

    // ==================== SPEC-P157 R4 员工自助 ====================

    @Test
    @DisplayName("P157 R4 myBills：未绑定返空表；绑定后只见本人 PAID 单")
    void myBillsFiltering() {
        // ① 未绑定 → 空
        when(employeeMapper.selectOne(any())).thenReturn(null);
        assertTrue(service.listMyBills(99L).isEmpty(), "未绑定账号返空表不越权");

        // ② 绑定员工 11 → bill 77 PAID 放行
        EmployeeEntity me = new EmployeeEntity();
        me.setId(11L);
        me.setUserId(99L);
        when(employeeMapper.selectOne(any())).thenReturn(me);
        SalaryBillItemEntity it77 = new SalaryBillItemEntity();
        it77.setBillId(77L);
        it77.setEmployeeId(11L);
        when(itemMapper.selectList(any())).thenReturn(List.of(it77));
        SalaryBillEntity paid = new SalaryBillEntity();
        paid.setId(77L);
        paid.setPeriod("202611");
        paid.setStatus("PAID");
        when(billMapper.selectList(any())).thenReturn(List.of(paid));

        List<SalaryBillVO> bills = service.listMyBills(99L);
        assertEquals(1, bills.size());
        assertEquals(77L, bills.get(0).getId());
    }

    @Test
    @DisplayName("P157 R4 myItems：他人单 400 / 未发放 400 / 本人 PAID 单放行")
    void myItemsGuards() {
        EmployeeEntity me = new EmployeeEntity();
        me.setId(11L);
        me.setUserId(99L);
        when(employeeMapper.selectOne(any())).thenReturn(me);

        // 未发放（DRAFT）→ 400
        SalaryBillEntity draft = new SalaryBillEntity();
        draft.setId(78L);
        draft.setStatus("DRAFT");
        when(billMapper.selectById(78L)).thenReturn(draft);
        BusinessException ex1 = assertThrows(BusinessException.class, () -> service.myItems(99L, 78L));
        assertTrue(ex1.getMessage().contains("未发放"));

        // 他人的单（明细无员工 11）→ 400
        SalaryBillEntity paidOther = new SalaryBillEntity();
        paidOther.setId(79L);
        paidOther.setStatus("PAID");
        when(billMapper.selectById(79L)).thenReturn(paidOther);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        BusinessException ex2 = assertThrows(BusinessException.class, () -> service.myItems(99L, 79L));
        assertTrue(ex2.getMessage().contains("无权"));

        // 本人 PAID 单 → 放行（items 查询链）
        SalaryBillEntity paidMine = new SalaryBillEntity();
        paidMine.setId(77L);
        paidMine.setStatus("PAID");
        when(billMapper.selectById(77L)).thenReturn(paidMine);
        when(itemMapper.selectCount(any())).thenReturn(1L);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                itemRowForVo(101L, "10000")));
        when(salaryItemMapper.selectList(any())).thenReturn(List.of(
                def(101L, "EARN", true)));
        List<SalaryBillItemVO> rows = service.myItems(99L, 77L);
        assertEquals(1, rows.size());
    }

    /** myItems → items 链路用的明细行 */
    private SalaryBillItemEntity itemRowForVo(long itemId, String amount) {
        SalaryBillItemEntity e = new SalaryBillItemEntity();
        e.setBillId(77L);
        e.setEmployeeId(11L);
        e.setItemId(itemId);
        e.setAmount(new BigDecimal(amount));
        return e;
    }
}
