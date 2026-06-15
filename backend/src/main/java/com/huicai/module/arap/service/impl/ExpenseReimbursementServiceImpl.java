package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.ExpenseReimbursementEntity;
import com.huicai.module.arap.mapper.ExpenseReimbursementMapper;
import com.huicai.module.arap.service.ExpenseReimbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseReimbursementServiceImpl implements ExpenseReimbursementService {

    private static final String PREFIX = "REIMB-";

    private final ExpenseReimbursementMapper mapper;

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
