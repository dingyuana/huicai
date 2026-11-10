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
import com.huicai.sme.salary.entity.SocialInsuranceRateEntity;
import com.huicai.sme.salary.entity.DeptSubjectMapEntity;
import com.huicai.sme.salary.mapper.SalaryBillEmployeeMapper;
import com.huicai.sme.salary.mapper.SalaryBillItemMapper;
import com.huicai.sme.salary.mapper.SalaryBillMapper;
import com.huicai.sme.salary.mapper.SalaryItemMapper;
import com.huicai.sme.salary.mapper.SocialInsuranceRateMapper;
import com.huicai.sme.salary.mapper.DeptSubjectMapMapper;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SPEC-P143：工资单实现 —— 计提/发放凭证与个税累计预扣。
 *
 * <h3>凭证口径（D-2 + SPEC-P155，科目 fail-loud 固定，P85 先例）</h3>
 * <ul>
 *   <li>计提 SALC-{id}：借 {费用科目}(应发+单位社保) / 贷 2211 应付职工薪酬(应发+单位社保)；
 *       费用科目按员工部门查 t_dept_subject_map（R2），无映射回落 6602；
 *       同科目金额合并一行；</li>
 *   <li>发放 SALP-{id}：借 2211(应发) / 贷 2221 应交税费(个税)
 *       + 贷 2241 其他应付款(个人社保公积金) + 贷 1002 银行存款(实发)；
 *       2211 贷方余额（=单位社保）留待缴纳动作冲销，账理自洽；</li>
 *   <li>缴纳社保 SALJ-{id}（R1）：借 2211(单位社保) + 借 2241(个人社保代扣)
 *       / 贷 1002；幂等 social_paid 单标记；权限复用 salary:pay。</li>
 * </ul>
 *
 * <h3>单位社保按比例自动算（R1 裁定）</h3>
 * 保存工资单时：剔除前端传入的 COMPANY_SOCIAL 明细，按
 * t_social_insurance_rate 配置（基数=当期应发 clamp [floor,cap] × company_rate）
 * 逐员工重算并落明细（计算结果落库留痕）。
 * 🔴 基数口径 = 当期应发合计，与社保局合规申报基数可能不同（登记遗留）。
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
    public static final String PREFIX_SOCIAL_PAY = "SALJ-";
    /** SPEC-P155 R1：单位社保项目类别 */
    public static final String CATEGORY_COMPANY_SOCIAL = "COMPANY_SOCIAL";

    private final SalaryBillMapper billMapper;
    private final SalaryBillItemMapper itemMapper;
    private final SalaryBillEmployeeMapper billEmployeeMapper;
    private final SalaryItemMapper salaryItemMapper;
    private final SocialInsuranceRateMapper socialRateMapper;
    private final DeptSubjectMapMapper deptSubjectMapMapper;
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
        // SPEC-P155 R1：单位社保按比例自动算——剔除前端传入的 COMPANY_SOCIAL 明细，
        // 按配置比例逐员工重算（计算结果落库留痕；前端展示锁定只读）
        List<SalaryBillSaveDTO.ItemDTO> finalItems = recomputeCompanySocial(dto.getItems());

        if (bill == null) {
            bill = new SalaryBillEntity();
            bill.setPeriod(dto.getPeriod());
            bill.setStatus("DRAFT");
            bill.setCreatedBy(userId);
            bill.setCreatedAt(LocalDateTime.now());
            bill.setRemark(dto.getRemark());
            bill.setSocialPaid(Boolean.FALSE);
            billMapper.insert(bill);
        } else {
            bill.setRemark(dto.getRemark());
            bill.setUpdatedBy(userId);
            billMapper.updateById(bill);
        }

        // 整单替换明细（@TableLogic 软删 + uq 部分索引允许重插）
        itemMapper.delete(new LambdaQueryWrapper<SalaryBillItemEntity>()
                .eq(SalaryBillItemEntity::getBillId, bill.getId()));
        for (SalaryBillSaveDTO.ItemDTO item : finalItems) {
            SalaryBillItemEntity row = new SalaryBillItemEntity();
            row.setBillId(bill.getId());
            row.setEmployeeId(item.getEmployeeId());
            row.setItemId(item.getItemId());
            row.setAmount(item.getAmount());
            row.setCreatedBy(userId);
            itemMapper.insert(row);
        }
        log.info("工资单保存: billId={}, period={}, items={}, userId={}",
                bill.getId(), dto.getPeriod(), finalItems.size(), userId);
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
    public List<SalaryBillVO> listMyBills(Long userId) {
        // SPEC-P157 R4：员工自助——按绑定关系反查本员工的 PAID 单；
        // 未绑定账号 = 从未参与自助，返回空表（不越权看他人）。
        EmployeeEntity me = employeeMapper.selectOne(new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (me == null) {
            return List.of();
        }
        List<Long> billIds = itemMapper.selectList(new LambdaQueryWrapper<SalaryBillItemEntity>()
                .eq(SalaryBillItemEntity::getEmployeeId, me.getId()))
                .stream().map(SalaryBillItemEntity::getBillId).distinct().collect(Collectors.toList());
        if (billIds.isEmpty()) {
            return List.of();
        }
        return billMapper.selectList(new LambdaQueryWrapper<SalaryBillEntity>()
                .in(SalaryBillEntity::getId, billIds)
                .eq(SalaryBillEntity::getStatus, "PAID")
                .orderByDesc(SalaryBillEntity::getPeriod))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalaryBillItemVO> myItems(Long userId, Long billId) {
        // 自助明细：绑定员工 + 该单必须 PAID（未发工资不给看明细），且单据属本人
        EmployeeEntity me = employeeMapper.selectOne(new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (me == null) {
            throw new BusinessException("当前账号未绑定员工，请联系管理员在员工管理中绑定");
        }
        SalaryBillEntity bill = billMapper.selectById(billId);
        if (bill == null || !"PAID".equals(bill.getStatus())) {
            throw new BusinessException("工资单不存在或未发放");
        }
        boolean mine = itemMapper.selectCount(new LambdaQueryWrapper<SalaryBillItemEntity>()
                .eq(SalaryBillItemEntity::getBillId, billId)
                .eq(SalaryBillItemEntity::getEmployeeId, me.getId())) > 0;
        if (!mine) {
            throw new BusinessException("无权查看他人工资单");
        }
        return items(billId);
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
                    r.setCompanySocial(e.getCompanySocial());
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
        // SPEC-P155 R2：部门→费用科目映射（一次性加载，fail-loud 校验科目存在）
        Map<Long, Long> subjectByDept = loadDeptSubjectMap();

        String yearPrefix = period.substring(0, 4) + "%";

        // 逐员工聚合 + 累计预扣个税
        Map<Long, List<SalaryBillItemEntity>> byEmployee = items.stream()
                .collect(Collectors.groupingBy(SalaryBillItemEntity::getEmployeeId));
        List<SalaryBillEmployeeEntity> employeeRows = new ArrayList<>();
        for (Map.Entry<Long, List<SalaryBillItemEntity>> en : byEmployee.entrySet()) {
            BigDecimal gross = BigDecimal.ZERO, taxableGross = BigDecimal.ZERO,
                    social = BigDecimal.ZERO, specialAdd = BigDecimal.ZERO, otherDeduct = BigDecimal.ZERO,
                    companySocial = BigDecimal.ZERO;
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
                    case CATEGORY_COMPANY_SOCIAL -> companySocial = companySocial.add(amt);
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
            row.setCompanySocial(companySocial);
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
        bill.setTotalCompanySocial(sum(employeeRows, SalaryBillEmployeeEntity::getCompanySocial));
        bill.setTotalTax(sum(employeeRows, SalaryBillEmployeeEntity::getTax));
        bill.setTotalNet(sum(employeeRows, SalaryBillEmployeeEntity::getNet));
        bill.setStatus("CONFIRMED");
        bill.setUpdatedBy(userId);
        billMapper.updateById(bill);

        // 计提凭证（SPEC-P155 R2：借方按部门科目分摊分组；R1：应发+单位社保合并）
        BigDecimal totalDebit = bill.getTotalGross().add(bill.getTotalCompanySocial());
        VoucherEntity voucher = newVoucher(PREFIX_CONFIRM + billId, period,
                "工资计提 " + period, totalDebit, userId);
        Subject payable = requireSubject(SUBJECT_PAYABLE);
        Map<Long, BigDecimal> debitBySubject = new java.util.LinkedHashMap<>();
        Map<Long, EmployeeEntity> employees = employeeMapper.selectList(
                        new LambdaQueryWrapper<EmployeeEntity>().in(EmployeeEntity::getId, employeeIds))
                .stream().collect(Collectors.toMap(EmployeeEntity::getId, e -> e));
        for (SalaryBillEmployeeEntity row : employeeRows) {
            Long subjectId = resolveExpenseSubject(row.getEmployeeId(), employees, subjectByDept);
            debitBySubject.merge(subjectId,
                    row.getGross().add(row.getCompanySocial() == null ? BigDecimal.ZERO : row.getCompanySocial()),
                    BigDecimal::add);
        }
        int sort = 1;
        for (Map.Entry<Long, BigDecimal> e : debitBySubject.entrySet()) {
            voucherEntryMapper.insert(newEntry(voucher.getId(), e.getKey(),
                    e.getValue(), BigDecimal.ZERO, sort++, voucher.getSummary()));
        }
        voucherEntryMapper.insert(newEntry(voucher.getId(), payable.getId(),
                BigDecimal.ZERO, totalDebit, sort, voucher.getSummary()));

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySocial(Long billId, Long userId) {
        SalaryBillEntity bill = mustGet(billId);
        if (!"CONFIRMED".equals(bill.getStatus()) && !"PAID".equals(bill.getStatus())) {
            throw BusinessException.badRequest("仅已确认(计提)的工资单可缴纳社保，当前：" + statusCn(bill.getStatus()));
        }
        if (Boolean.TRUE.equals(bill.getSocialPaid())) {
            throw BusinessException.badRequest("社保已缴纳，不可重复缴纳（凭证 "
                    + bill.getSocialVoucherNo() + "）");
        }
        assertPeriodOpen(bill.getPeriod());

        BigDecimal company = bill.getTotalCompanySocial() == null ? BigDecimal.ZERO : bill.getTotalCompanySocial();
        BigDecimal personal = bill.getTotalSocial() == null ? BigDecimal.ZERO : bill.getTotalSocial();
        BigDecimal total = company.add(personal);
        if (total.signum() == 0) {
            throw BusinessException.badRequest("本工资单无社保金额（单位与个人均为 0），无可缴纳项");
        }

        VoucherEntity voucher = newVoucher(PREFIX_SOCIAL_PAY + billId, bill.getPeriod(),
                "社保公积金缴纳 " + bill.getPeriod(), total, userId);
        Subject payable = requireSubject(SUBJECT_PAYABLE);
        Subject otherPayable = requireSubject(SUBJECT_OTHER_PAYABLE);
        Subject bank = requireSubject(SUBJECT_BANK);
        int sort = 1;
        if (company.signum() > 0) {
            voucherEntryMapper.insert(newEntry(voucher.getId(), payable.getId(),
                    company, BigDecimal.ZERO, sort++, voucher.getSummary()));
        }
        if (personal.signum() > 0) {
            voucherEntryMapper.insert(newEntry(voucher.getId(), otherPayable.getId(),
                    personal, BigDecimal.ZERO, sort++, voucher.getSummary()));
        }
        voucherEntryMapper.insert(newEntry(voucher.getId(), bank.getId(),
                BigDecimal.ZERO, total, sort, voucher.getSummary()));

        bill.setSocialPaid(Boolean.TRUE);
        bill.setSocialVoucherId(voucher.getId());
        bill.setSocialVoucherNo(voucher.getVoucherNo());
        bill.setUpdatedBy(userId);
        billMapper.updateById(bill);
        log.info("社保缴纳: billId={}, period={}, company={}, personal={}, voucherNo={}, userId={}",
                billId, bill.getPeriod(), company, personal, voucher.getVoucherNo(), userId);
    }

    // ==================== 内部 ====================

    /**
     * SPEC-P155 R1：单位社保自动算——剔除前端传入的 COMPANY_SOCIAL 明细（自动算语义下
     * 前端只读），按 t_social_insurance_rate 配置逐员工重算：
     * 基数 = 该员工 EARN 项合计 clamp [baseFloor, baseCap] × companyRate（ROUND 2）。
     * 项目未配比例 ⇒ 该项目不算（金额缺省 0），不挡保存（宽容，日志留痕）。
     */
    private List<SalaryBillSaveDTO.ItemDTO> recomputeCompanySocial(List<SalaryBillSaveDTO.ItemDTO> items) {
        List<SalaryItemEntity> csItems = salaryItemMapper.selectList(
                new LambdaQueryWrapper<SalaryItemEntity>()
                        .eq(SalaryItemEntity::getCategory, CATEGORY_COMPANY_SOCIAL));
        List<SalaryBillSaveDTO.ItemDTO> kept = items.stream()
                .filter(i -> csItems.stream().noneMatch(c -> c.getId().equals(i.getItemId())))
                .collect(Collectors.toCollection(ArrayList::new));
        if (csItems.isEmpty()) {
            return kept;
        }
        List<Long> csItemIds = csItems.stream().map(SalaryItemEntity::getId).toList();
        Map<Long, SocialInsuranceRateEntity> rates = socialRateMapper.selectList(
                        new LambdaQueryWrapper<SocialInsuranceRateEntity>()
                                .in(SocialInsuranceRateEntity::getItemId, csItemIds))
                .stream().collect(Collectors.toMap(SocialInsuranceRateEntity::getItemId, r -> r));
        boolean anyRate = rates.containsKey(csItems.stream().map(SalaryItemEntity::getId).findFirst().orElse(null))
                || !rates.isEmpty();
        if (!anyRate) {
            log.warn("已配置 COMPANY_SOCIAL 项目但无比例配置，单位社保按 0 处理: itemIds={}", csItemIds);
            return kept;
        }

        // 每员工 EARN 合计 = GROSS 模式下的缴费基数
        Set<Long> earnItemIds = salaryItemMapper.selectList(
                        new LambdaQueryWrapper<SalaryItemEntity>()
                                .eq(SalaryItemEntity::getCategory, "EARN"))
                .stream().map(SalaryItemEntity::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> grossByEmployee = new java.util.LinkedHashMap<>();
        for (SalaryBillSaveDTO.ItemDTO item : items) {
            if (earnItemIds.contains(item.getItemId()) && item.getAmount() != null) {
                grossByEmployee.merge(item.getEmployeeId(), item.getAmount(), BigDecimal::add);
            }
        }

        // SPEC-P159（V203 双模式）：任一项目配 EMP_DECL ⇒ 涉及员工基数取其社保核定值；
        // 员工未核定 → fail-loud 400 点名员工（静默回落会把核定基数错算成应发）。
        Set<Long> declaredItemIds = csItems.stream().filter(c -> {
            SocialInsuranceRateEntity r0 = rates.get(c.getId());
            return "EMP_DECL".equals(r0 == null ? null : r0.getBaseMode());
        }).map(SalaryItemEntity::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> declaredBaseByEmployee = Map.of();
        if (!declaredItemIds.isEmpty()) {
            Set<Long> involved = grossByEmployee.keySet();
            Map<Long, BigDecimal> declared = employeeMapper.selectList(
                            new LambdaQueryWrapper<EmployeeEntity>()
                                    .in(EmployeeEntity::getId, involved)
                                    .isNotNull(EmployeeEntity::getSocialBaseDeclared))
                    .stream().collect(Collectors.toMap(
                            EmployeeEntity::getId, EmployeeEntity::getSocialBaseDeclared));
            for (Long empId : involved) {
                if (!declared.containsKey(empId)) {
                    throw BusinessException.badRequest("员工 " + empId
                            + " 未配置社保核定基数（单位社保基数模式=社保局核定），请先在员工档案补录");
                }
            }
            declaredBaseByEmployee = declared;
        }

        for (Map.Entry<Long, BigDecimal> en : grossByEmployee.entrySet()) {
            for (SalaryItemEntity cs : csItems) {
                SocialInsuranceRateEntity rate = rates.get(cs.getId());
                if (rate == null) {
                    continue;
                }
                // SPEC-P159：基数按项目模式取值——GROSS=当期应发（原有口径）；
                // EMP_DECL=该员工社保局核定基数（无核定值已在上方 fail-loud 拦截）。上下限 clamp 双模式通用。
                BigDecimal base = declaredItemIds.contains(cs.getId())
                        ? declaredBaseByEmployee.get(en.getKey())
                        : en.getValue();
                if (rate.getBaseFloor() != null && base.compareTo(rate.getBaseFloor()) < 0) {
                    base = rate.getBaseFloor();
                }
                if (rate.getBaseCap() != null && base.compareTo(rate.getBaseCap()) > 0) {
                    base = rate.getBaseCap();
                }
                BigDecimal amt = base.multiply(rate.getCompanyRate()).setScale(2, java.math.RoundingMode.HALF_UP);
                if (amt.signum() > 0) {
                    SalaryBillSaveDTO.ItemDTO row = new SalaryBillSaveDTO.ItemDTO();
                    row.setEmployeeId(en.getKey());
                    row.setItemId(cs.getId());
                    row.setAmount(amt);
                    kept.add(row);
                }
            }
        }
        return kept;
    }

    /**
     * SPEC-P155 R2：部门→费用科目映射加载（fail-loud：映射的科目必须真实存在，
     * 缺失 400 点名部门——静默回落会把分摊错到 6602）。
     */
    private Map<Long, Long> loadDeptSubjectMap() {
        List<DeptSubjectMapEntity> maps = deptSubjectMapMapper.selectList(null);
        if (maps.isEmpty()) {
            return Map.of();
        }
        List<Long> subjectIds = maps.stream().map(DeptSubjectMapEntity::getSubjectId).distinct().toList();
        Set<Long> validSubjectIds = subjectMapper.selectList(
                        new LambdaQueryWrapper<Subject>().in(Subject::getId, subjectIds)
                                .eq(Subject::getDeleted, 0))
                .stream().map(Subject::getId).collect(Collectors.toSet());
        Map<Long, Long> out = new java.util.LinkedHashMap<>();
        for (DeptSubjectMapEntity m : maps) {
            if (!validSubjectIds.contains(m.getSubjectId())) {
                throw BusinessException.badRequest("部门费用科目映射指向不存在的科目（deptId="
                        + m.getDeptId() + ", subjectId=" + m.getSubjectId() + "），请先修正映射配置");
            }
            out.put(m.getDeptId(), m.getSubjectId());
        }
        return out;
    }

    /** 分摊解析：员工部门 → 映射科目；无部门/无映射回落 6602。 */
    private Long resolveExpenseSubject(Long employeeId, Map<Long, EmployeeEntity> employees,
                                       Map<Long, Long> subjectByDept) {
        EmployeeEntity emp = employees.get(employeeId);
        if (emp != null && emp.getDeptId() != null) {
            Long mapped = subjectByDept.get(emp.getDeptId());
            if (mapped != null) {
                return mapped;
            }
        }
        return requireSubject(SUBJECT_EXPENSE).getId();
    }

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
        vo.setTotalCompanySocial(b.getTotalCompanySocial());
        vo.setSocialPaid(b.getSocialPaid());
        vo.setSocialVoucherNo(b.getSocialVoucherNo());
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

    // ==================== SPEC-P155 R3 导出 ====================

    @Override
    @Transactional(readOnly = true)
    public void exportPayroll(Long billId, jakarta.servlet.http.HttpServletResponse response) {
        SalaryBillEntity bill = mustGet(billId);
        List<SalaryBillItemVO> items = items(billId);

        cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = "工资条_" + bill.getPeriod();
        response.setHeader("Content-Disposition", "attachment;filename="
                + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8) + ".xlsx");
        try {
            writer.writeCellValue(0, 0, "工资条（" + bill.getPeriod() + "）");
            int row = 2;
            Long currentEmployee = null;
            for (SalaryBillItemVO it : items) {
                if (!it.getEmployeeId().equals(currentEmployee)) {
                    currentEmployee = it.getEmployeeId();
                    row++; // 员工间空行分隔
                    writer.writeCellValue(0, row, "员工：" + it.getEmployeeName()
                            + "（工号 " + it.getEmployeeId() + "）");
                    writer.writeCellValue(2, row, "薪资项目");
                    writer.writeCellValue(3, row, "金额");
                    row++;
                }
                writer.writeCellValue(2, row, it.getItemName());
                writer.writeCellValue(3, row, it.getAmount());
                row++;
            }
            writer.flush(response.getOutputStream());
        } catch (java.io.IOException e) {
            throw new BusinessException("工资条导出失败：" + e.getMessage());
        } finally {
            writer.close();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void exportTaxDeclaration(Long billId, jakarta.servlet.http.HttpServletResponse response) {
        SalaryBillEntity bill = mustGet(billId);
        List<SalaryBillEmployeeEntity> rows = billEmployeeMapper.selectList(
                new LambdaQueryWrapper<SalaryBillEmployeeEntity>()
                        .eq(SalaryBillEmployeeEntity::getBillId, billId)
                        .orderByAsc(SalaryBillEmployeeEntity::getEmployeeId));
        Map<Long, String> employeeNames = employeeMapper.selectList(
                        new LambdaQueryWrapper<EmployeeEntity>())
                .stream().collect(Collectors.toMap(EmployeeEntity::getId, EmployeeEntity::getName));

        cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = "个税申报表_" + bill.getPeriod();
        response.setHeader("Content-Disposition", "attachment;filename="
                + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8) + ".xlsx");
        try {
            writer.writeCellValue(0, 0, "个人所得税申报表（" + bill.getPeriod() + "）");
            String[] headers = {"员工", "应发", "计税应发", "个人社保", "专项附加", "本月预扣个税", "实发"};
            int headerRow = 2;
            for (int i = 0; i < headers.length; i++) {
                writer.writeCellValue(i, headerRow, headers[i]);
            }
            int r = headerRow + 1;
            for (SalaryBillEmployeeEntity e : rows) {
                writer.writeCellValue(0, r, employeeNames.getOrDefault(e.getEmployeeId(), "#" + e.getEmployeeId()));
                writer.writeCellValue(1, r, e.getGross());
                writer.writeCellValue(2, r, e.getTaxableGross());
                writer.writeCellValue(3, r, e.getSocial());
                writer.writeCellValue(4, r, e.getSpecialAdd());
                writer.writeCellValue(5, r, e.getTax());
                writer.writeCellValue(6, r, e.getNet());
                r++;
            }
            writer.flush(response.getOutputStream());
        } catch (java.io.IOException e) {
            throw new BusinessException("个税申报表导出失败：" + e.getMessage());
        } finally {
            writer.close();
        }
    }
}
