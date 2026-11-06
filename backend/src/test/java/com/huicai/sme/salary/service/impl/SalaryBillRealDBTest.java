package com.huicai.sme.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.salary.dto.SalaryBillSaveDTO;
import com.huicai.sme.salary.entity.SalaryBillEmployeeEntity;
import com.huicai.sme.salary.entity.SalaryBillEntity;
import com.huicai.sme.salary.entity.SalaryItemEntity;
import com.huicai.sme.salary.mapper.SalaryBillEmployeeMapper;
import com.huicai.sme.salary.mapper.SalaryBillItemMapper;
import com.huicai.sme.salary.mapper.SalaryBillMapper;
import com.huicai.sme.salary.mapper.SalaryItemMapper;
import com.huicai.sme.salary.service.SalaryBillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P143 真库面：工资单全链（save→confirm→pay）+ 个税累计预扣跨月跳档。
 *
 * <p>锁五件事：①confirm 后员工级快照落库且首月个税 = (计税应发-社保-专项附加-5000)×3%；
 * ②计提凭证 SALC-{id} 借6602/贷2211 且回填 voucherId；③发放凭证 SALP-{id}
 * 借2211/贷2221+2241+1002；④**第二个月税额跨档累计**（3% 档→10% 档，速算扣除参与）；
 * ⑤状态机守卫（confirm 两次/pay 两次 400）。</p>
 */
@DisplayName("SPEC-P143：工资单真库全链 —— 计提/发放凭证 + 累计预扣跨月跳档")
class SalaryBillRealDBTest extends AbstractMapperTest {

    @Autowired private SalaryBillService salaryBillService;
    @Autowired private SalaryBillMapper billMapper;
    @Autowired private SalaryBillItemMapper itemMapper;
    @Autowired private SalaryBillEmployeeMapper billEmployeeMapper;
    @Autowired private SalaryItemMapper salaryItemMapper;
    @Autowired private EmployeeMapper employeeMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private VoucherEntryMapper voucherEntryMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final BigDecimal BASIC = new BigDecimal("30000");
    private static final BigDecimal SOCIAL = new BigDecimal("2000");
    private static final BigDecimal SPECIAL = new BigDecimal("1500");

    private Long makeEmployee() {
        EmployeeEntity e = new EmployeeEntity();
        e.setCode("D1-" + (System.nanoTime() % 100000000));
        e.setName("工资测试员工");
        e.setIsActive(true);
        employeeMapper.insert(e);
        return e.getId();
    }

    /** 三个最简薪资项目：基本工资(EARN 计税)/个人社保(SOCIAL_INS)/专项附加(SPECIAL_ADD) */
    private void makeItems() {
        insertItem("BASIC", "基本工资", "EARN", true);
        insertItem("SSI", "个人社保公积金", "SOCIAL_INS", false);
        insertItem("SPADD", "专项附加扣除", "SPECIAL_ADD", false);
    }

    private void insertItem(String code, String name, String category, boolean taxable) {
        SalaryItemEntity e = new SalaryItemEntity();
        // 全量 nanoTime：uq_salary_item_code(enterprise_id,item_code) 撞码即假红，不留概率
        e.setItemCode(code + "-" + System.nanoTime());
        e.setItemName(name);
        e.setCategory(category);
        e.setIsTaxable(taxable);
        e.setSortOrder(1);
        salaryItemMapper.insert(e);
    }

    private Long saveBill(String period, Long employeeId, Long basicItem, Long socialItem, Long specialItem) {
        SalaryBillSaveDTO dto = new SalaryBillSaveDTO();
        dto.setPeriod(period);
        dto.setRemark("SPEC-P143 真库测试");
        dto.setItems(List.of(
                entry(employeeId, basicItem, BASIC),
                entry(employeeId, socialItem, SOCIAL),
                entry(employeeId, specialItem, SPECIAL)));
        return salaryBillService.save(dto, 9L);
    }

    private SalaryBillSaveDTO.ItemDTO entry(Long employeeId, Long itemId, BigDecimal amount) {
        SalaryBillSaveDTO.ItemDTO e = new SalaryBillSaveDTO.ItemDTO();
        e.setEmployeeId(employeeId);
        e.setItemId(itemId);
        e.setAmount(amount);
        return e;
    }

    private Long subjectId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject WHERE code=? AND deleted=0 AND enterprise_id=1 LIMIT 1",
                Long.class, code);
    }

    @Test
    @DisplayName("全链：confirm 首月个税 645 + SALC 借6602/贷2211 + pay SALP 四行 + 状态机")
    void fullChainConfirmPay() {
        makeItems();
        Long employeeId = makeEmployee();
        Long basic = jdbcTemplate.queryForObject(
                "SELECT id FROM t_salary_item WHERE item_name='基本工资' AND deleted=0 ORDER BY id DESC LIMIT 1",
                Long.class);
        Long social = jdbcTemplate.queryForObject(
                "SELECT id FROM t_salary_item WHERE item_name='个人社保公积金' AND deleted=0 ORDER BY id DESC LIMIT 1",
                Long.class);
        Long special = jdbcTemplate.queryForObject(
                "SELECT id FROM t_salary_item WHERE item_name='专项附加扣除' AND deleted=0 ORDER BY id DESC LIMIT 1",
                Long.class);

        Long billId = saveBill("202601", employeeId, basic, social, special);

        salaryBillService.confirm(billId, 9L);

        // ① 员工级快照：累计应纳税所得额 = 30000-2000-1500-5000 = 21500 → 3% → 645
        SalaryBillEmployeeEntity row = billEmployeeMapper.selectOne(
                new LambdaQueryWrapper<SalaryBillEmployeeEntity>()
                        .eq(SalaryBillEmployeeEntity::getBillId, billId));
        assertNotNull(row, "员工级快照未落库");
        assertEquals(0, new BigDecimal("645.00").compareTo(row.getTax()),
                "首月个税 = (30000-2000-1500-5000)×3% = 645，实际：" + row.getTax());
        assertEquals(0, new BigDecimal("27355.00").compareTo(row.getNet()),
                "实发 = 30000-2000-0-645");

        // ② 计提凭证 SALC-{id}：借 6602 / 贷 2211，voucherId 回填
        SalaryBillEntity bill = billMapper.selectById(billId);
        assertEquals("CONFIRMED", bill.getStatus());
        assertNotNull(bill.getVoucherId(), "计提凭证 voucherId 未回填");
        VoucherEntity salc = voucherMapper.selectById(bill.getVoucherId());
        assertEquals("SALC-" + billId, salc.getVoucherNo());
        List<VoucherEntryEntity> salcLines = voucherEntryMapper.selectList(
                new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getVoucherId, salc.getId())
                        .orderByAsc(VoucherEntryEntity::getSortOrder));
        assertEquals(2, salcLines.size());
        assertEquals(subjectId("6602"), salcLines.get(0).getSubjectId(), "借方科目必须是 6602 管理费用");
        assertEquals(0, new BigDecimal("30000").compareTo(salcLines.get(0).getDebit()), "借 6602 应发全额");
        assertEquals(subjectId("2211"), salcLines.get(1).getSubjectId(), "贷方科目必须是 2211 应付职工薪酬");
        assertEquals(0, new BigDecimal("30000").compareTo(salcLines.get(1).getCredit()), "贷 2211 应发全额");

        // ⑤ 状态机：confirm 两次 400
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> salaryBillService.confirm(billId, 9L));
        assertTrue(ex1.getMessage().contains("草稿"));

        // ③ 发放：SALP-{id} 四行 借2211/贷2221+2241+1002
        salaryBillService.pay(billId, 9L);
        SalaryBillEntity paid = billMapper.selectById(billId);
        assertEquals("PAID", paid.getStatus());
        assertNotNull(paid.getPayVoucherId(), "发放凭证未回填");
        VoucherEntity salp = voucherMapper.selectById(paid.getPayVoucherId());
        assertEquals("SALP-" + billId, salp.getVoucherNo());
        List<VoucherEntryEntity> salpLines = voucherEntryMapper.selectList(
                new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getVoucherId, salp.getId())
                        .orderByAsc(VoucherEntryEntity::getSortOrder));
        assertEquals(4, salpLines.size(), "发放凭证必须四行");
        assertEquals(subjectId("2211"), salpLines.get(0).getSubjectId(), "第一行借 2211");
        assertEquals(0, new BigDecimal("30000").compareTo(salpLines.get(0).getDebit()), "借 2211 应发全额");
        assertEquals(subjectId("2221"), salpLines.get(1).getSubjectId(), "贷 2221 应交税费(个税)");
        assertEquals(0, new BigDecimal("645.00").compareTo(salpLines.get(1).getCredit()), "贷 2221 个税");
        assertEquals(subjectId("2241"), salpLines.get(2).getSubjectId(), "贷 2241 其他应付款(社保个人)");
        assertEquals(0, new BigDecimal("2000").compareTo(salpLines.get(2).getCredit()), "贷 2241 社保个人");
        assertEquals(subjectId("1002"), salpLines.get(3).getSubjectId(), "贷 1002 银行存款(实发)");
        assertEquals(0, new BigDecimal("27355.00").compareTo(salpLines.get(3).getCredit()), "贷 1002 实发");

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> salaryBillService.pay(billId, 9L));
        assertTrue(ex2.getMessage().contains("已确认"));
    }

    @Test
    @DisplayName("跨月累计预扣：第二个月跳 10% 档（速算扣除参与），tax = 1780-645 = 1135")
    void cumulativeTaxAcrossMonths() {
        makeItems();
        Long employeeId = makeEmployee();
        Long basic = jdbcTemplate.queryForObject(
                "SELECT id FROM t_salary_item WHERE item_name='基本工资' AND deleted=0 ORDER BY id DESC LIMIT 1",
                Long.class);
        Long social = jdbcTemplate.queryForObject(
                "SELECT id FROM t_salary_item WHERE item_name='个人社保公积金' AND deleted=0 ORDER BY id DESC LIMIT 1",
                Long.class);
        Long special = jdbcTemplate.queryForObject(
                "SELECT id FROM t_salary_item WHERE item_name='专项附加扣除' AND deleted=0 ORDER BY id DESC LIMIT 1",
                Long.class);

        Long bill1 = saveBill("202601", employeeId, basic, social, special);
        salaryBillService.confirm(bill1, 9L);

        Long bill2 = saveBill("202602", employeeId, basic, social, special);
        salaryBillService.confirm(bill2, 9L);

        // 月 2：累计应纳税所得额 = 21500 + 21500 = 43000 → 10% 档：43000×10%-2520 = 1780
        // 本月预扣 = 1780 - 645 = 1135
        SalaryBillEmployeeEntity row2 = billEmployeeMapper.selectOne(
                new LambdaQueryWrapper<SalaryBillEmployeeEntity>()
                        .eq(SalaryBillEmployeeEntity::getBillId, bill2));
        assertEquals(0, new BigDecimal("1135.00").compareTo(row2.getTax()),
                "跨月累计预扣（3%→10% 跳档 + 速算扣除）错误，实际：" + row2.getTax());

        // 月 1 个税不变（累计口径不回溯改单）
        SalaryBillEmployeeEntity row1 = billEmployeeMapper.selectOne(
                new LambdaQueryWrapper<SalaryBillEmployeeEntity>()
                        .eq(SalaryBillEmployeeEntity::getBillId, bill1));
        assertEquals(0, new BigDecimal("645.00").compareTo(row1.getTax()));
    }
}
