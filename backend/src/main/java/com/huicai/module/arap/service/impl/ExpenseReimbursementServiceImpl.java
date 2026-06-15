package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.ExpenseReimbursementEntity;
import com.huicai.module.arap.mapper.ExpenseReimbursementMapper;
import com.huicai.module.arap.service.ExpenseReimbursementService;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseReimbursementServiceImpl implements ExpenseReimbursementService {

    private static final String PREFIX = "REIMB-";

    private final ExpenseReimbursementMapper mapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectMapper subjectMapper;

    @Override
    public IPage<ExpenseReimbursementEntity> pageQuery(Long employeeId, String status, Integer current, Integer size) {
        Page<ExpenseReimbursementEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<ExpenseReimbursementEntity> wrapper = new LambdaQueryWrapper<>();
        if (employeeId != null) wrapper.eq(ExpenseReimbursementEntity::getEmployeeId, employeeId);
        if (StrUtil.isNotBlank(status)) wrapper.eq(ExpenseReimbursementEntity::getStatus, status);
        wrapper.orderByDesc(ExpenseReimbursementEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public List<ExpenseReimbursementEntity> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<ExpenseReimbursementEntity>()
                .orderByDesc(ExpenseReimbursementEntity::getCreatedAt));
    }

    @Override
    public ExpenseReimbursementEntity getById(Long id) {
        ExpenseReimbursementEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException("报销单不存在: " + id);
        return e;
    }

    @Override
    @Transactional
    public ExpenseReimbursementEntity createDraft(ExpenseReimbursementEntity entity) {
        if (entity.getEmployeeId() == null) throw new BusinessException("员工ID不能为空");
        if (StrUtil.isBlank(entity.getExpenseType())) throw new BusinessException("费用类型不能为空");
        if (entity.getAmount() == null || entity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("报销金额必须大于0");
        }
        entity.setStatus("DRAFT");
        entity.setReimbNo(generateNo());
        mapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public ExpenseReimbursementEntity updateDraft(ExpenseReimbursementEntity entity) {
        ExpenseReimbursementEntity existing = getById(entity.getId());
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅 DRAFT 状态可修改");
        }
        existing.setExpenseType(entity.getExpenseType());
        existing.setAmount(entity.getAmount());
        existing.setSummary(entity.getSummary());
        existing.setAttachmentIds(entity.getAttachmentIds());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public ExpenseReimbursementEntity submit(Long id) {
        ExpenseReimbursementEntity e = getById(id);
        if (!"DRAFT".equals(e.getStatus())) {
            throw new BusinessException("仅 DRAFT 可提交: 当前=" + e.getStatus());
        }
        e.setStatus("SUBMITTED");
        e.setSubmittedAt(LocalDateTime.now());
        mapper.updateById(e);
        return e;
    }

    @Override
    @Transactional
    public ExpenseReimbursementEntity approve(Long id, String approver) {
        ExpenseReimbursementEntity e = getById(id);
        if (!"SUBMITTED".equals(e.getStatus())) {
            throw new BusinessException("仅 SUBMITTED 可审批: 当前=" + e.getStatus());
        }
        e.setStatus("APPROVED");
        e.setApprovedAt(LocalDateTime.now());
        e.setApprovedBy(approver);
        mapper.updateById(e);
        return e;
    }

    @Override
    @Transactional
    public ExpenseReimbursementEntity reject(Long id, String approver, String reason) {
        ExpenseReimbursementEntity e = getById(id);
        if (!"SUBMITTED".equals(e.getStatus())) {
            throw new BusinessException("仅 SUBMITTED 可驳回: 当前=" + e.getStatus());
        }
        if (StrUtil.isBlank(reason)) throw new BusinessException("驳回必须填理由");
        e.setStatus("REJECTED");
        e.setApprovedAt(LocalDateTime.now());
        e.setApprovedBy(approver);
        e.setRejectReason(reason);
        mapper.updateById(e);
        return e;
    }

    @Override
    @Transactional
    public ExpenseReimbursementEntity generateVoucher(Long id, Long voucherId) {
        ExpenseReimbursementEntity e = getById(id);
        if (!"APPROVED".equals(e.getStatus())) {
            throw new BusinessException("仅 APPROVED 可生成凭证: 当前=" + e.getStatus());
        }
        e.setStatus("VOUCHERED");
        e.setVoucherId(voucherId);
        mapper.updateById(e);
        return e;
    }

    /**
     * P11-4: 报销单审批通过后自动生成凭证 (停 DRAFT 状态, 不自动过账).
     *
     * <p>按 expenseType 匹配硬编码科目模板:
     * <ul>
     *   <li>TRAVEL → 借 5602.03 差旅费 / 贷 1002 银行存款</li>
     *   <li>OFFICE → 借 5602.04 办公费 / 贷 1002</li>
     *   <li>ENTERTAIN → 借 5602.05 业务招待费 / 贷 1002</li>
     *   <li>TRANSPORT → 借 5602.06 交通费 / 贷 1002</li>
     *   <li>COMMUNICATION → 借 5602.07 通讯费 / 贷 1002</li>
     *   <li>OTHER → 借 5602.99 其他费用 / 贷 1002</li>
     * </ul>
     *
     * <p>返回 ExpenseReimbursementEntity (status=VOUCHERED, voucherId=新凭证ID)
     */
    @Transactional
    public ExpenseReimbursementEntity generateVoucherForApproved(Long id) {
        ExpenseReimbursementEntity e = getById(id);
        if (!"APPROVED".equals(e.getStatus())) {
            throw new BusinessException("仅 APPROVED 可生成凭证: 当前=" + e.getStatus());
        }

        // 1. 创建凭证 (DRAFT 状态, 不自动过账 — 走老丁"人是唯一审核主体"硬约束)
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(generateVoucherNo());
        voucher.setPeriod(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(e.getAmount());
        voucher.setTotalCredit(e.getAmount());
        voucher.setSummary("报销单 " + e.getReimbNo() + ": " + e.getSummary());
        voucher.setSource("GENERATED");
        voucher.setCreatedBy(1L); // 默认制单人
        voucherMapper.insert(voucher);

        // 2. 写入分录 (按费用类型选科目)
        String expenseCode = mapExpenseTypeToCode(e.getExpenseType());
        String[] entryInfo = lookupSubject(expenseCode);
        if (entryInfo == null) {
            throw new BusinessException("费用科目 " + expenseCode + " 不存在, 请先配置");
        }
        Long debitSubjectId = entryInfo[0] == null ? null : Long.parseLong(entryInfo[0]);
        String debitSubjectName = entryInfo[1];

        String[] cashInfo = lookupSubject("1002");
        if (cashInfo == null) {
            throw new BusinessException("银行存款科目 1002 不存在, 请先配置");
        }
        Long creditSubjectId = Long.parseLong(cashInfo[0]);
        String creditSubjectName = cashInfo[1];

        insertEntry(voucher.getId(), debitSubjectId, debitSubjectName, e.getAmount(), BigDecimal.ZERO, 1);
        insertEntry(voucher.getId(), creditSubjectId, creditSubjectName, BigDecimal.ZERO, e.getAmount(), 2);

        // 3. 更新报销单 → VOUCHERED
        e.setStatus("VOUCHERED");
        e.setVoucherId(voucher.getId());
        mapper.updateById(e);

        log.info("P11-4 报销单凭证生成: reimbId={}, voucherId={}, expenseType={}, amount={}",
                e.getId(), voucher.getId(), e.getExpenseType(), e.getAmount());
        return e;
    }

    private String mapExpenseTypeToCode(String expenseType) {
        return switch (expenseType) {
            case "TRAVEL" -> "5602.03";
            case "OFFICE" -> "5602.04";
            case "ENTERTAIN" -> "5602.05";
            case "TRANSPORT" -> "5602.06";
            case "COMMUNICATION" -> "5602.07";
            default -> "5602.99";
        };
    }

    private String[] lookupSubject(String code) {
        if (subjectMapper == null) return new String[]{"1", "占位科目(" + code + ")"};
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        if (list.isEmpty()) return null;
        Subject s = list.get(0);
        return new String[]{s.getId().toString(), s.getName()};
    }

    private void insertEntry(Long voucherId, Long subjectId, String subjectName,
                              BigDecimal debit, BigDecimal credit, int sort) {
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(voucherId);
        entry.setSubjectId(subjectId);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setSummary(subjectName);
        entry.setSortOrder(sort);
        voucherEntryMapper.insert(entry);
    }

    private String generateVoucherNo() {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return "REIMB-" + period + "-" + (System.currentTimeMillis() % 10000);
    }

    @Override
    public ExpenseReimbursementEntity findByBankStmtId(Long bankStmtId) {
        if (bankStmtId == null) return null;
        List<ExpenseReimbursementEntity> list = mapper.selectList(
                new LambdaQueryWrapper<ExpenseReimbursementEntity>()
                        .eq(ExpenseReimbursementEntity::getBankStmtId, bankStmtId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional
    public ExpenseReimbursementEntity autoCreateForBankStmt(Long bankStmtId, Long employeeId, BigDecimal amount, String summary) {
        // 防止重复创建
        ExpenseReimbursementEntity existing = findByBankStmtId(bankStmtId);
        if (existing != null) return existing;

        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setBankStmtId(bankStmtId);
        e.setEmployeeId(employeeId);
        e.setAmount(amount);
        e.setExpenseType("OTHER");
        e.setSummary(summary);
        e.setStatus("DRAFT");
        e.setReimbNo(generateNo());
        mapper.insert(e);
        return e;
    }

    private String generateNo() {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 简化：用时间戳末4位作为序号
        String serial = String.valueOf(System.currentTimeMillis() % 10000);
        return PREFIX + period + "-" + serial;
    }
}
