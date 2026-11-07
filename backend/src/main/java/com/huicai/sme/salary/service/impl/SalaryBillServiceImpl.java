package com.huicai.sme.salary.service.impl;

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
import com.huicai.sme.salary.service.SalaryBillService;
import com.huicai.sme.salary.support.IndividualIncomeTaxCalculator;
import com.huicai.sme.salary.vo.SalaryBillDetailVO;
import com.huicai.sme.salary.vo.SalaryBillItemVO;
import com.huicai.sme.salary.vo.SalaryBillVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SPEC-P143：工资单实现 —— 计提/发放凭证与个税累计预扣。
 *
 * <h3>凭证口径（D-2，科目 fail-loud 固定，P85 先例）</h3>
 * <ul>
 *   <li>计提 SALC-{id}：借 6602 管理费用(应发) / 贷 2211 应付职工薪酬(应发)；
 *       费用按部门分摊、按工资性质拆销售费用 6601 —— 登记遗留，先固定 6602；</li>
 *   <li>发放 SALP-{id}：借 2211(应发) / 贷 2221 应交税费(个税)
 *       + 贷 2241 其他应付款(个人社保公积金) + 贷 1002 银行存款(实发)；
 *       <b>单位部分社保公积金本轮不做</b>（登记遗留，无对应分录）。</li>
 * </ul>
 *
 * <h3>个税累计预扣（D-1）</h3>
 * 累计口径来源 = 本年该员工已确认/已发放工资单的员工级快照表
 * （t_salary_bill_employee 即台账）；月份不足（年中入职）按已有单据累计，
 * 减除费用按日历月份数（含当月）× 5000 计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SalaryBillServiceImpl implements SalaryBillService {

    private static final String SUBJECT_EXPENSE = "6602";
    private static final String SUBJECT_PAYABLE = "2211";
    private static final String SUBJECT_TAX_DUE = "2221";
    private static final String SUBJECT_OTHER_PAYABLE = "2241";
    private static final String SUBJECT_BANK = "1002";
    public static final String PREFIX_CONFIRM = "SALC-";
    public static final String PREFIX_PAY = "SALP-";

    private final SalaryBillMapper billMapper;
    private final SalaryBillItemMapper itemMapper;
    private final SalaryBillEmployeeMapper billEmployeeMapper;
    private final SalaryItemMapper salaryItemMapper;
    private final EmployeeMapper employeeMapper;
    private final SubjectMapper subjectMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherTypeService voucherTypeService;
    private final PeriodService periodService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalaryBillSaveDTO dto, Long userId) {
        SalaryBillEntity bill = billMapper.selectOne(new LambdaQueryWrapper<SalaryBillEntity>()
                .eq(SalaryBillEntity::getPeriod, dto.getPeriod()));
        if (bill != null && !"DRAFT".equals(bill.getStatus())) {
            throw BusinessException.badRequest(
                    "期间 " + dto.getPeriod() + " 的工资单已" + statusCn(bill.getStatus()) + "，不可修改");
        }
        validateItems(dto.getItems());

        if (bill == null) {
            bill = new SalaryBillEntity();
            bill.setPeriod(dto.getPeriod());
            bill.setStatus("DRAFT");
            bill.setCreatedBy(userId);
            bill.setCreatedAt(LocalDateTime.now());
            bill.setRemark(dto.getRemark());
            billMapper.insert(bill);
        } else {
            bill.setRemark(dto.getRemark());
            bill.setUpdatedBy(userId);
            billMapper.updateById(bill);
        }

        // 整单替换明细（@TableLogic 软删 + uq 部分索引允许重插）
        itemMapper.delete(new LambdaQueryWrapper<SalaryBillItemEntity>()
                .eq(SalaryBillItemEntity::getBillId, bill.getId()));
        for (SalaryBillSaveDTO.ItemDTO item : dto.getItems()) {
            SalaryBillItemEntity row = new SalaryBillItemEntity();
            row.setBillId(bill.getId());
            row.setEmployeeId(item.getEmployeeId());
            row.setItemId(item.getItemId());
            row.setAmount(item.getAmount());
            row.setCreatedBy(userId);
            itemMapper.insert(row);
        }
        log.info("工资单保存: billId={}, period={}, items={}, userId={}",
                bill.getId(), dto.getPeriod(), dto.getItems().size(), userId);
        return bill.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalaryBillVO> list() {
        return billMapper.selectList(new LambdaQueryWrapper<SalaryBillEntity>()
                .orderByDesc(SalaryBillEntity::getPeriod))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SalaryBillDetailVO detail(Long billId) {
        SalaryBillEntity bill = mustGet(billId);
        SalaryBillDetailVO vo = new SalaryBillDetailVO();
        copyBill(vo, bill);
        List<SalaryBillDetailVO.EmployeeRowVO> rows = billEmployeeMapper.selectList(
                        new LambdaQueryWrapper<SalaryBillEmployeeEntity>()
                                .eq(SalaryBillEmployeeEntity::getBillId, billId)
                                .orderByAsc(SalaryBillEmployeeEntity::getEmployeeId))
                .stream().map(e -> {
                    SalaryBillDetailVO.EmployeeRowVO r = new SalaryBillDetailVO.EmployeeRowVO();
                    r.setEmployeeId(e.getEmployeeId());
                    r.setGross(e.getGross());
                    r.setTaxableGross(e.getTaxableGross());
                    r.setSocial(e.getSocial());
                    r.setSpecialAdd(e.getSpecialAdd());
                    r.setOtherDeduct(e.getOtherDeduct());
                    r.setTax(e.getTax());
                    r.setNet(e.getNet());
                    EmployeeEntity emp = employeeMapper.selectById(e.getEmployeeId());
                    r.setEmployeeName(emp == null ? null : emp.getName());
                    return r;
                }).collect(Collectors.toList());
        vo.setEmployees(rows);
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalaryBillItemVO> items(Long billId) {
        mustGet(billId);
        List<SalaryBillItemEntity> rows = itemMapper.selectList(
                new LambdaQueryWrapper<SalaryBillItemEntity>()
                        .eq(SalaryBillItemEntity::getBillId, billId)
                        .orderByAsc(SalaryBillItemEntity::getId));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> employeeNames = employeeMapper.selectList(
                        new LambdaQueryWrapper<EmployeeEntity>()
                                .in(EmployeeEntity::getId,
                                        rows.stream().map(SalaryBillItemEntity::getEmployeeId).distinct().toList()))
                .stream().collect(Collectors.toMap(EmployeeEntity::getId, EmployeeEntity::getName));
        // 展示路径宽容：项目被软删也照常回显（名字留空），不沿用 loadItemDefs 的 fail-loud
        Map<Long, SalaryItemEntity> itemDefs = salaryItemMapper.selectList(
                        new LambdaQueryWrapper<SalaryItemEntity>()
                                .in(SalaryItemEntity::getId,
                                        rows.stream().map(SalaryBillItemEntity::getItemId).distinct().toList()))
                .stream().collect(Collectors.toMap(SalaryItemEntity::getId, i -> i));
        return rows.stream().map(r -> {
            SalaryBillItemVO vo = new SalaryBillItemVO();
            vo.setEmployeeId(r.getEmployeeId());
            vo.setEmployeeName(employeeNames.get(r.getEmployeeId()));
            vo.setItemId(r.getItemId());
            SalaryItemEntity def = itemDefs.get(r.getItemId());
            vo.setItemName(def == null ? null : def.getItemName());
            vo.setAmount(r.getAmount());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long billId, Long userId) {
        SalaryBillEntity bill = mustGet(billId);
        if (!"DRAFT".equals(bill.getStatus())) {
            throw BusinessException.badRequest("仅草稿状态可确认，当前：" + statusCn(bill.getStatus()));
        }
        String period = bill.getPeriod();
        assertPeriodOpen(period);

        List<SalaryBillItemEntity> items = itemMapper.selectList(
                new LambdaQueryWrapper<SalaryBillItemEntity>()
                        .eq(SalaryBillItemEntity::getBillId, billId));
        if (items.isEmpty()) {
            throw BusinessException.badRequest("工资单无明细，先录入员工工资");
        }
        Map<Long, SalaryItemEntity> itemDefs = loadItemDefs(items);
        List<Long> employeeIds = items.stream().map(SalaryBillItemEntity::getEmployeeId)
                .distinct().toList();
        validateEmployees(employeeIds);

        String yearPrefix = period.substring(0, 4) + "%";

        // 逐员工聚合 + 累计预扣个税
        Map<Long, List<SalaryBillItemEntity>> byEmployee = items.stream()
                .collect(Collectors.groupingBy(SalaryBillItemEntity::getEmployeeId));
        List<SalaryBillEmployeeEntity> employeeRows = new ArrayList<>();
        for (Map.Entry<Long, List<SalaryBillItemEntity>> en : byEmployee.entrySet()) {
            BigDecimal gross = BigDecimal.ZERO, taxableGross = BigDecimal.ZERO,
                    social = BigDecimal.ZERO, specialAdd = BigDecimal.ZERO, otherDeduct = BigDecimal.ZERO;
            for (SalaryBillItemEntity it : en.getValue()) {
                SalaryItemEntity def = itemDefs.get(it.getItemId());
                BigDecimal amt = it.getAmount() == null ? BigDecimal.ZERO : it.getAmount();
                switch (def.getCategory()) {
                    case "EARN" -> {
                        gross = gross.add(amt);
                        if (Boolean.TRUE.equals(def.getIsTaxable())) {
                            taxableGross = taxableGross.add(amt);
                        }
                    }
                    case "SOCIAL_INS" -> social = social.add(amt);
                    case "SPECIAL_ADD" -> specialAdd = specialAdd.add(amt);
                    case "OTHER_DEDUCT" -> otherDeduct = otherDeduct.add(amt);
                    default -> throw BusinessException.badRequest("未知项目类别：" + def.getCategory());
                }
            }

            SalaryBillEmployeeMapper.CumulativeRow prior =
                    billEmployeeMapper.selectCumulative(en.getKey(), yearPrefix, period);
            // 累计应纳税所得额 = 截至上月累计运行值 + 本月计税应发 - 本月专项扣除/专项附加
            // （减除费用 5000 已在 prior 里按「有工资单的月份」逐月抵扣，本月再抵一档：
            //   任职月份数 = 历史单据数 + 本月）
            BigDecimal cumTaxable = prior.cumTaxableRunning.add(taxableGross)
                    .subtract(social).subtract(specialAdd)
                    .subtract(IndividualIncomeTaxCalculator.MONTHLY_STANDARD_DEDUCTION);
            BigDecimal cumTax = IndividualIncomeTaxCalculator.taxOnCumulative(cumTaxable);
            BigDecimal tax = IndividualIncomeTaxCalculator.monthlyWithholding(cumTax, prior.cumTax);
            BigDecimal net = gross.subtract(social).subtract(otherDeduct).subtract(tax);
            if (net.signum() < 0) {
                throw BusinessException.badRequest("员工实发为负（扣款+个税超过应发），请检查扣款项");
            }

            SalaryBillEmployeeEntity row = new SalaryBillEmployeeEntity();
            row.setBillId(billId);
            row.setEmployeeId(en.getKey());
            row.setGross(gross);
            row.setTaxableGross(taxableGross);
            row.setSocial(social);
            row.setSpecialAdd(specialAdd);
            row.setOtherDeduct(otherDeduct);
            row.setTax(tax);
            row.setNet(net);
            row.setCreatedBy(userId);
            row.setCreatedAt(LocalDateTime.now());
            employeeRows.add(row);
        }
        billEmployeeMapper.delete(new LambdaQueryWrapper<SalaryBillEmployeeEntity>()
                .eq(SalaryBillEmployeeEntity::getBillId, billId));
        employeeRows.forEach(billEmployeeMapper::insert);

        // 合计回填 + 状态推进 + 计提凭证
        bill.setTotalGross(sum(employeeRows, SalaryBillEmployeeEntity::getGross));
        bill.setTotalSocial(sum(employeeRows, SalaryBillEmployeeEntity::getSocial));
        bill.setTotalSpecialAdd(sum(employeeRows, SalaryBillEmployeeEntity::getSpecialAdd));
        bill.setTotalOtherDeduct(sum(employeeRows, SalaryBillEmployeeEntity::getOtherDeduct));
        bill.setTotalTax(sum(employeeRows, SalaryBillEmployeeEntity::getTax));
        bill.setTotalNet(sum(employeeRows, SalaryBillEmployeeEntity::getNet));
        bill.setStatus("CONFIRMED");
        bill.setUpdatedBy(userId);
        billMapper.updateById(bill);

        VoucherEntity voucher = newVoucher(PREFIX_CONFIRM + billId, period,
                "工资计提 " + period, bill.getTotalGross(), userId);
        Subject expense = requireSubject(SUBJECT_EXPENSE);
        Subject payable = requireSubject(SUBJECT_PAYABLE);
        voucherEntryMapper.insert(newEntry(voucher.getId(), expense.getId(),
                bill.getTotalGross(), BigDecimal.ZERO, 1, voucher.getSummary()));
        voucherEntryMapper.insert(newEntry(voucher.getId(), payable.getId(),
                BigDecimal.ZERO, bill.getTotalGross(), 2, voucher.getSummary()));

        bill.setVoucherId(voucher.getId());
        billMapper.updateById(bill);
        log.info("工资单确认: billId={}, period={}, gross={}, tax={}, net={}, voucherNo={}, userId={}",
                billId, period, bill.getTotalGross(), bill.getTotalTax(), bill.getTotalNet(),
                voucher.getVoucherNo(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long billId, Long userId) {
        SalaryBillEntity bill = mustGet(billId);
        if (!"CONFIRMED".equals(bill.getStatus())) {
            throw BusinessException.badRequest("仅已确认(计提)的工资单可发放，当前：" + statusCn(bill.getStatus()));
        }
        assertPeriodOpen(bill.getPeriod());

        VoucherEntity voucher = newVoucher(PREFIX_PAY + billId, bill.getPeriod(),
                "工资发放 " + bill.getPeriod(), bill.getTotalGross(), userId);
        Subject payable = requireSubject(SUBJECT_PAYABLE);
        Subject taxDue = requireSubject(SUBJECT_TAX_DUE);
        Subject otherPayable = requireSubject(SUBJECT_OTHER_PAYABLE);
        Subject bank = requireSubject(SUBJECT_BANK);
        voucherEntryMapper.insert(newEntry(voucher.getId(), payable.getId(),
                bill.getTotalGross(), BigDecimal.ZERO, 1, voucher.getSummary()));
        voucherEntryMapper.insert(newEntry(voucher.getId(), taxDue.getId(),
                BigDecimal.ZERO, bill.getTotalTax(), 2, voucher.getSummary()));
        voucherEntryMapper.insert(newEntry(voucher.getId(), otherPayable.getId(),
                BigDecimal.ZERO, bill.getTotalSocial(), 3, voucher.getSummary()));
        voucherEntryMapper.insert(newEntry(voucher.getId(), bank.getId(),
                BigDecimal.ZERO, bill.getTotalNet(), 4, voucher.getSummary()));

        bill.setStatus("PAID");
        bill.setPayVoucherId(voucher.getId());
        bill.setUpdatedBy(userId);
        billMapper.updateById(bill);
        log.info("工资单发放: billId={}, period={}, voucherNo={}, userId={}",
                billId, bill.getPeriod(), voucher.getVoucherNo(), userId);
    }

    // ==================== 内部 ====================

    private void validateItems(List<SalaryBillSaveDTO.ItemDTO> items) {
        for (SalaryBillSaveDTO.ItemDTO item : items) {
            if (item.getEmployeeId() == null || item.getItemId() == null) {
                throw BusinessException.badRequest("明细缺员工或薪资项目");
            }
            if (item.getAmount() == null || item.getAmount().signum() < 0) {
                throw BusinessException.badRequest("明细金额必须 ≥ 0");
            }
        }
        List<Long> itemIds = items.stream().map(SalaryBillSaveDTO.ItemDTO::getItemId).distinct().toList();
        Long found = salaryItemMapper.selectCount(new LambdaQueryWrapper<SalaryItemEntity>()
                .in(SalaryItemEntity::getId, itemIds));
        if (found == null || found < itemIds.size()) {
            throw BusinessException.badRequest("存在无效的薪资项目");
        }
    }

    private void validateEmployees(List<Long> employeeIds) {
        Long found = employeeMapper.selectCount(new LambdaQueryWrapper<EmployeeEntity>()
                .in(EmployeeEntity::getId, employeeIds)
                .eq(EmployeeEntity::getIsActive, true));
        if (found == null || found < employeeIds.size()) {
            throw BusinessException.badRequest("存在无效或停用的员工");
        }
    }

    private Map<Long, SalaryItemEntity> loadItemDefs(List<SalaryBillItemEntity> items) {
        List<Long> ids = items.stream().map(SalaryBillItemEntity::getItemId).distinct().toList();
        Map<Long, SalaryItemEntity> defs = salaryItemMapper.selectList(
                        new LambdaQueryWrapper<SalaryItemEntity>().in(SalaryItemEntity::getId, ids))
                .stream().collect(Collectors.toMap(SalaryItemEntity::getId, i -> i));
        for (Long id : ids) {
            if (!defs.containsKey(id)) {
                throw BusinessException.badRequest("薪资项目已被删除，无法确认工资单（itemId=" + id + "）");
            }
        }
        return defs;
    }

    private VoucherEntity newVoucher(String no, String period, String summary, BigDecimal total, Long userId) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(no);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(voucherTypeService.requireByCode("ZZ").getId());
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(summary);
        voucher.setTotalDebit(total);
        voucher.setTotalCredit(total);
        voucher.setCreatedBy(userId);
        voucher.setCreatedAt(LocalDateTime.now());
        voucherMapper.insert(voucher);
        return voucher;
    }

    private VoucherEntryEntity newEntry(Long voucherId, Long subjectId, BigDecimal debit,
                                        BigDecimal credit, int sort, String summary) {
        VoucherEntryEntity line = new VoucherEntryEntity();
        line.setVoucherId(voucherId);
        line.setSubjectId(subjectId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setSummary(summary);
        line.setSortOrder(sort);
        return line;
    }

    private Subject requireSubject(String code) {
        Subject s = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code)
                .eq(Subject::getDeleted, 0)
                .last("LIMIT 1"));
        if (s == null) {
            throw BusinessException.badRequest("未配置科目 " + code + "，无法生成工资凭证");
        }
        return s;
    }

    private void assertPeriodOpen(String period) {
        // 与资产调整同口径：期间未配置视为开放，结账锁由 DEPR/CLOSE 步兜底
        com.huicai.base.system.entity.PeriodEntity p = null;
        try {
            p = periodService.getByPeriodCode(period);
        } catch (Exception ignored) {
            // PeriodService 不可用不阻断，凭证期间锁由结账流程兜底
        }
        if (p != null && !"open".equals(p.getStatus())) {
            throw BusinessException.badRequest("期间 " + period + " 已结账，无法生成工资凭证");
        }
    }

    private SalaryBillEntity mustGet(Long billId) {
        SalaryBillEntity bill = billMapper.selectById(billId);
        if (bill == null || Integer.valueOf(1).equals(bill.getDeleted())) {
            throw BusinessException.notFound("工资单不存在");
        }
        return bill;
    }

    private BigDecimal sum(List<SalaryBillEmployeeEntity> rows,
                           java.util.function.Function<SalaryBillEmployeeEntity, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SalaryBillVO toVO(SalaryBillEntity b) {
        SalaryBillVO vo = new SalaryBillVO();
        copyBill(vo, b);
        return vo;
    }

    private void copyBill(SalaryBillVO vo, SalaryBillEntity b) {
        vo.setId(b.getId());
        vo.setPeriod(b.getPeriod());
        vo.setStatus(b.getStatus());
        vo.setTotalGross(b.getTotalGross());
        vo.setTotalSocial(b.getTotalSocial());
        vo.setTotalSpecialAdd(b.getTotalSpecialAdd());
        vo.setTotalOtherDeduct(b.getTotalOtherDeduct());
        vo.setTotalTax(b.getTotalTax());
        vo.setTotalNet(b.getTotalNet());
        vo.setVoucherId(b.getVoucherId());
        vo.setPayVoucherId(b.getPayVoucherId());
        vo.setRemark(b.getRemark());
        if (b.getVoucherId() != null) {
            VoucherEntity v = voucherMapper.selectById(b.getVoucherId());
            if (v != null) {
                vo.setVoucherNo(v.getVoucherNo());
            }
        }
        if (b.getPayVoucherId() != null) {
            VoucherEntity v = voucherMapper.selectById(b.getPayVoucherId());
            if (v != null) {
                vo.setPayVoucherNo(v.getVoucherNo());
            }
        }
    }

    private String statusCn(String status) {
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "CONFIRMED" -> "已确认";
            case "PAID" -> "已发放";
            default -> status;
        };
    }
}
