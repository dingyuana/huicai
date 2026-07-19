package com.huicai.sme.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.dto.ExpenseReimbursementVO;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.sme.arap.mapper.ExpenseReimbursementMapper;
import com.huicai.sme.arap.service.ExpenseReimbursementService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.auth.entity.DeptEntity;
import com.huicai.base.subject.entity.Subject;
import com.huicai.base.auth.entity.UserEntity;
import com.huicai.base.auth.mapper.DeptMapper;
import com.huicai.base.subject.mapper.SubjectMapper;
import com.huicai.base.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseReimbursementServiceImpl implements ExpenseReimbursementService {

    private static final String PREFIX = "REIMB-";

    private final ExpenseReimbursementMapper mapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectMapper subjectMapper;
    private final EmployeeMapper employeeMapper;
    private final UserMapper userMapper;
    private final DeptMapper deptMapper;

    @Override
    public IPage<ExpenseReimbursementVO> pageQuery(Long employeeId, String status, Integer current, Integer size) {
        Page<ExpenseReimbursementEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<ExpenseReimbursementEntity> wrapper = new LambdaQueryWrapper<>();
        if (employeeId != null) wrapper.eq(ExpenseReimbursementEntity::getEmployeeId, employeeId);
        if (StrUtil.isNotBlank(status)) wrapper.eq(ExpenseReimbursementEntity::getStatus, status);
        wrapper.orderByDesc(ExpenseReimbursementEntity::getCreatedAt);
        IPage<ExpenseReimbursementEntity> entityPage = mapper.selectPage(page, wrapper);

        IPage<ExpenseReimbursementVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<ExpenseReimbursementVO> vos = entityPage.getRecords().stream()
                .map(ExpenseReimbursementVO::fromEntity).collect(Collectors.toList());
        populateEmployeeNames(vos);
        populateDeptNames(vos);
        populateUserNames(vos);
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public List<ExpenseReimbursementVO> listAll() {
        List<ExpenseReimbursementEntity> list = mapper.selectList(new LambdaQueryWrapper<ExpenseReimbursementEntity>()
                .orderByDesc(ExpenseReimbursementEntity::getCreatedAt));
        List<ExpenseReimbursementVO> vos = list.stream()
                .map(ExpenseReimbursementVO::fromEntity).collect(Collectors.toList());
        populateEmployeeNames(vos);
        populateDeptNames(vos);
        populateUserNames(vos);
        return vos;
    }

    @Override
    public ExpenseReimbursementVO getById(Long id) {
        ExpenseReimbursementEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException("报销单不存在: " + id);
        ExpenseReimbursementVO vo = ExpenseReimbursementVO.fromEntity(e);
        populateEmployeeNames(List.of(vo));
        populateDeptNames(List.of(vo));
        populateUserNames(List.of(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO createDraft(ExpenseReimbursementEntity entity) {
        if (entity.getEmployeeId() == null) throw new BusinessException("员工ID不能为空");
        if (StrUtil.isBlank(entity.getExpenseType())) throw new BusinessException("费用类型不能为空");
        if (entity.getAmount() == null || entity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("报销金额必须大于0");
        }
        entity.setStatus("DRAFT");
        entity.setReimbNo(generateNo());
        mapper.insert(entity);
        return getById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO updateDraft(ExpenseReimbursementEntity entity) {
        ExpenseReimbursementEntity existing = mapper.selectById(entity.getId());
        if (existing == null) throw new BusinessException("报销单不存在: " + entity.getId());
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅 DRAFT 状态可修改");
        }
        existing.setExpenseType(entity.getExpenseType());
        existing.setAmount(entity.getAmount());
        existing.setSummary(entity.getSummary());
        existing.setAttachmentIds(entity.getAttachmentIds());
        mapper.updateById(existing);
        return getById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO submit(Long id) {
        ExpenseReimbursementEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException("报销单不存在: " + id);
        if (!"DRAFT".equals(e.getStatus())) {
            throw new BusinessException("仅 DRAFT 可提交: 当前=" + e.getStatus());
        }
        e.setStatus("SUBMITTED");
        e.setSubmittedAt(LocalDateTime.now());
        mapper.updateById(e);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO approve(Long id, String approver) {
        ExpenseReimbursementEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException("报销单不存在: " + id);
        if (!"SUBMITTED".equals(e.getStatus())) {
            throw new BusinessException("仅 SUBMITTED 可审批: 当前=" + e.getStatus());
        }
        e.setStatus("APPROVED");
        e.setApprovedAt(LocalDateTime.now());
        e.setApprovedBy(approver);
        mapper.updateById(e);

        // P11-4: 审批通过后自动生成凭证(APPROVED → VOUCHERED)
        try {
            generateVoucherForApproved(id);
            log.info("P11-4 报销单审批通过自动生成凭证: reimbId={}", id);
        } catch (Exception ex) {
            log.error("P11-4 报销单自动生成凭证失败, 可手工调用 generateVoucherForApproved: reimbId={}, error={}", id, ex.getMessage());
        }
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO reject(Long id, String approver, String reason) {
        ExpenseReimbursementEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException("报销单不存在: " + id);
        if (!"SUBMITTED".equals(e.getStatus())) {
            throw new BusinessException("仅 SUBMITTED 可驳回: 当前=" + e.getStatus());
        }
        if (StrUtil.isBlank(reason)) throw new BusinessException("驳回必须填理由");
        e.setStatus("REJECTED");
        e.setApprovedAt(LocalDateTime.now());
        e.setApprovedBy(approver);
        e.setRejectReason(reason);
        mapper.updateById(e);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO generateVoucher(Long id, Long voucherId) {
        ExpenseReimbursementEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException("报销单不存在: " + id);
        if (!"APPROVED".equals(e.getStatus())) {
            throw new BusinessException("仅 APPROVED 可生成凭证: 当前=" + e.getStatus());
        }
        e.setStatus("VOUCHERED");
        e.setVoucherId(voucherId);
        mapper.updateById(e);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO generateVoucherForApproved(Long id) {
        ExpenseReimbursementEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException("报销单不存在: " + id);
        if (!"APPROVED".equals(e.getStatus())) {
            throw new BusinessException("仅 APPROVED 可生成凭证: 当前=" + e.getStatus());
        }

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(generateVoucherNo());
        voucher.setPeriod(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(e.getAmount());
        voucher.setTotalCredit(e.getAmount());
        voucher.setSummary("报销单 " + e.getReimbNo() + ": " + e.getSummary());
        voucher.setSource("GENERATED");
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        String expenseCode = mapExpenseTypeToCode(e.getExpenseType());
        String[] entryInfo = lookupSubject(expenseCode);
        if (entryInfo == null) {
            throw new BusinessException("费用科目 " + expenseCode + " 不存在, 请先配置");
        }
        Long debitSubjectId = entryInfo[0] == null ? null : Long.parseLong(entryInfo[0]);

        String[] cashInfo = lookupSubject("1002");
        if (cashInfo == null) {
            throw new BusinessException("银行存款科目 1002 不存在, 请先配置");
        }
        Long creditSubjectId = Long.parseLong(cashInfo[0]);

        insertEntry(voucher.getId(), debitSubjectId, e.getAmount(), BigDecimal.ZERO, 1);
        insertEntry(voucher.getId(), creditSubjectId, BigDecimal.ZERO, e.getAmount(), 2);

        e.setStatus("VOUCHERED");
        e.setVoucherId(voucher.getId());
        mapper.updateById(e);

        log.info("P11-4 报销单凭证生成: reimbId={}, voucherId={}, expenseType={}, amount={}",
                e.getId(), voucher.getId(), e.getExpenseType(), e.getAmount());
        return getById(id);
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
    @Transactional(rollbackFor = Exception.class)
    public ExpenseReimbursementVO autoCreateForBankStmt(Long bankStmtId, Long employeeId, BigDecimal amount, String summary) {
        ExpenseReimbursementEntity existing = findByBankStmtId(bankStmtId);
        if (existing != null) return getById(existing.getId());

        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setBankStmtId(bankStmtId);
        e.setEmployeeId(employeeId);
        e.setAmount(amount);
        e.setExpenseType("OTHER");
        e.setSummary(summary);
        e.setStatus("DRAFT");
        e.setReimbNo(generateNo());
        mapper.insert(e);
        return getById(e.getId());
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

    private void insertEntry(Long voucherId, Long subjectId, BigDecimal debit, BigDecimal credit, int sort) {
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(voucherId);
        entry.setSubjectId(subjectId);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setSortOrder(sort);
        voucherEntryMapper.insert(entry);
    }

    private String generateVoucherNo() {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return "REIMB-" + period + "-" + (System.currentTimeMillis() % 10000);
    }

    private String generateNo() {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String serial = String.valueOf(System.currentTimeMillis() % 10000);
        return PREFIX + period + "-" + serial;
    }

    private void populateEmployeeNames(List<ExpenseReimbursementVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> empIds = vos.stream()
                .map(ExpenseReimbursementVO::getEmployeeId).filter(java.util.Objects::nonNull).distinct().toList();
        if (empIds.isEmpty()) return;
        Map<Long, String> nameMap = employeeMapper.selectBatchIds(empIds).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, EmployeeEntity::getName));
        for (ExpenseReimbursementVO vo : vos) {
            if (vo.getEmployeeId() != null) vo.setEmployeeName(nameMap.get(vo.getEmployeeId()));
        }
    }

    private void populateDeptNames(List<ExpenseReimbursementVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> deptIds = vos.stream()
                .map(ExpenseReimbursementVO::getDeptId).filter(java.util.Objects::nonNull).distinct().toList();
        if (deptIds.isEmpty()) return;
        Map<Long, String> nameMap = deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(DeptEntity::getId, DeptEntity::getName));
        for (ExpenseReimbursementVO vo : vos) {
            if (vo.getDeptId() != null) vo.setDeptName(nameMap.get(vo.getDeptId()));
        }
    }

    private void populateUserNames(List<ExpenseReimbursementVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> userIds = vos.stream()
                .map(ExpenseReimbursementVO::getCreatedBy).filter(java.util.Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) return;
        Map<Long, String> nameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, this::resolveUserDisplayName));
        for (ExpenseReimbursementVO vo : vos) {
            if (vo.getCreatedBy() != null) vo.setCreatedByName(nameMap.get(vo.getCreatedBy()));
        }
    }

    private String resolveUserDisplayName(UserEntity user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) return user.getRealName();
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        return user.getUsername();
    }
}
