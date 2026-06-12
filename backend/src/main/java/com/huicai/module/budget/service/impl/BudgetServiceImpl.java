package com.huicai.module.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.budget.entity.BudgetAdjustmentEntity;
import com.huicai.module.budget.entity.BudgetEntity;
import com.huicai.module.budget.entity.BudgetEntryEntity;
import com.huicai.module.budget.mapper.BudgetAdjustmentMapper;
import com.huicai.module.budget.mapper.BudgetEntryMapper;
import com.huicai.module.budget.mapper.BudgetMapper;
import com.huicai.module.budget.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private final BudgetMapper budgetMapper;
    private final BudgetEntryMapper entryMapper;
    private final BudgetAdjustmentMapper adjustmentMapper;

    @Override
    public IPage<BudgetEntity> pageQuery(String period, String status, Integer current, Integer size) {
        Page<BudgetEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<BudgetEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(period)) wrapper.eq(BudgetEntity::getPeriod, period);
        if (StrUtil.isNotBlank(status)) wrapper.eq(BudgetEntity::getStatus, status);
        wrapper.orderByDesc(BudgetEntity::getCreatedAt);
        return budgetMapper.selectPage(page, wrapper);
    }

    @Override
    public BudgetEntity getById(Long id) {
        BudgetEntity entity = budgetMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("预算单不存在");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetEntity create(BudgetEntity entity, List<BudgetEntryEntity> entries) {
        if (entity.getStatus() == null) entity.setStatus("DRAFT");
        BigDecimal total = BigDecimal.ZERO;
        if (entries != null) {
            for (BudgetEntryEntity e : entries) {
                total = total.add(e.getAmount());
                if (e.getUsedAmount() == null) e.setUsedAmount(BigDecimal.ZERO);
                if (e.getControlType() == null) e.setControlType("WARN");
            }
        }
        entity.setTotalAmount(total);
        budgetMapper.insert(entity);
        if (entries != null) {
            for (BudgetEntryEntity e : entries) {
                e.setBudgetId(entity.getId());
                entryMapper.insert(e);
            }
        }
        return entity;
    }

    @Override
    public BudgetEntity approve(Long id) {
        BudgetEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可审批");
        }
        entity.setStatus("APPROVED");
        entity.setApprovedAt(LocalDateTime.now());
        budgetMapper.updateById(entity);
        return entity;
    }

    @Override
    public BudgetEntity activate(Long id) {
        BudgetEntity entity = getById(id);
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new BusinessException("仅已审批可激活");
        }
        entity.setStatus("ACTIVE");
        budgetMapper.updateById(entity);
        return entity;
    }

    @Override
    public Map<String, Object> checkBudget(Long subjectId, String period, BigDecimal amount) {
        List<Map<String, Object>> entries = entryMapper.findBySubjectAndPeriod(subjectId, period);
        Map<String, Object> result = new LinkedHashMap<>();
        if (entries.isEmpty()) {
            result.put("pass", true);
            result.put("controlType", "NONE");
            result.put("message", "无预算配置");
            return result;
        }
        Map<String, Object> entry = entries.get(0);
        BigDecimal budget = toBigDecimal(entry.get("amount"));
        BigDecimal used = toBigDecimal(entry.get("usedAmount"));
        BigDecimal newUsed = used.add(amount);
        BigDecimal ratio = budget.compareTo(BigDecimal.ZERO) > 0
                ? newUsed.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String controlType = (String) entry.get("controlType");
        boolean pass = true;
        String action = "WARN";
        switch (controlType) {
            case "BLOCK":
                if (newUsed.compareTo(budget) > 0) {
                    pass = false;
                    action = "BLOCK";
                }
                break;
            case "APPROVE":
                if (newUsed.compareTo(budget) > 0) {
                    pass = true;
                    action = "REQUIRE_APPROVE";
                }
                break;
            case "WARN":
            default:
                if (ratio.compareTo(BigDecimal.valueOf(80)) > 0) {
                    action = "WARN";
                }
                break;
        }
        result.put("pass", pass);
        result.put("action", action);
        result.put("controlType", controlType);
        result.put("budget", budget);
        result.put("used", used);
        result.put("newUsed", newUsed);
        result.put("remaining", budget.subtract(newUsed));
        result.put("usageRatio", ratio);
        return result;
    }

    @Override
    public Map<String, Object> executionAnalysis(String period) {
        List<BudgetEntity> budgets = budgetMapper.selectList(
                new LambdaQueryWrapper<BudgetEntity>()
                        .eq(BudgetEntity::getPeriod, period)
                        .in(BudgetEntity::getStatus, "APPROVED", "ACTIVE")
        );
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalUsed = BigDecimal.ZERO;
        int count = 0;
        for (BudgetEntity b : budgets) {
            totalBudget = totalBudget.add(b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount());
            List<BudgetEntryEntity> entries = entryMapper.selectList(
                    new LambdaQueryWrapper<BudgetEntryEntity>()
                            .eq(BudgetEntryEntity::getBudgetId, b.getId())
            );
            for (BudgetEntryEntity e : entries) {
                totalUsed = totalUsed.add(e.getUsedAmount() == null ? BigDecimal.ZERO : e.getUsedAmount());
                count++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("totalBudget", totalBudget);
        result.put("totalUsed", totalUsed);
        result.put("remaining", totalBudget.subtract(totalUsed));
        result.put("executionRatio", totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? totalUsed.multiply(BigDecimal.valueOf(100))
                .divide(totalBudget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("entryCount", count);
        return result;
    }

    @Override
    public IPage<BudgetAdjustmentEntity> pageQueryAdjustment(String status, Integer current, Integer size) {
        Page<BudgetAdjustmentEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<BudgetAdjustmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) wrapper.eq(BudgetAdjustmentEntity::getStatus, status);
        wrapper.orderByDesc(BudgetAdjustmentEntity::getCreatedAt);
        return adjustmentMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetAdjustmentEntity createAdjustment(BudgetAdjustmentEntity entity) {
        if (entity.getStatus() == null) entity.setStatus("DRAFT");
        if (entity.getAdjustmentDate() == null) entity.setAdjustmentDate(LocalDate.now());
        adjustmentMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetAdjustmentEntity approveAdjustment(Long id) {
        BudgetAdjustmentEntity entity = adjustmentMapper.selectById(id);
        if (entity == null) throw new BusinessException("调整单不存在");
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可审批");
        }
        BudgetEntity budget = getById(entity.getBudgetId());
        if ("INCREASE".equals(entity.getAdjustmentType())) {
            budget.setTotalAmount(budget.getTotalAmount().add(entity.getAdjustmentAmount()));
        } else if ("DECREASE".equals(entity.getAdjustmentType())) {
            budget.setTotalAmount(budget.getTotalAmount().subtract(entity.getAdjustmentAmount()));
        }
        budgetMapper.updateById(budget);
        entity.setStatus("APPROVED");
        entity.setApprovedAt(LocalDateTime.now());
        adjustmentMapper.updateById(entity);
        return entity;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString());
    }
}
