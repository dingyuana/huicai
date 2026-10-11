package com.huicai.sme.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetAdjustmentEntity;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.entity.BudgetEntryEntity;
import com.huicai.sme.budget.mapper.BudgetAdjustmentMapper;
import com.huicai.sme.budget.mapper.BudgetEntryMapper;
import com.huicai.sme.budget.mapper.BudgetMapper;
import com.huicai.sme.budget.service.BudgetService;
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
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class BudgetServiceImpl implements BudgetService {

    /**
     * 取当前制单人 —— 有登录态用真实用户。
     *
     * <p><b>SPEC-P119</b>：P117 把 created_by 修成真实落库列后，本类多条创建
     * 路径此前无人写入该列，导致制审分离校验恒不触发（恒绿门禁形态）。
     *
     * <p><b>Q2 裁定：无登录态返回 null，不写兜底常量。</b>
     * Excel 导入 / 定时任务 / 系统初始化本就没有「录入人」概念，
     * 填 DEFAULT_USER_ID 等于凭空造一个假用户（与 P118 清理的问题同型）。
     * 代价是这些单据永远无法做制审分离校验 —— 属已登记的已知局限，
     * 校验对 created_by IS NULL 放行，与凭证层/业务单据层口径一致。
     */
    private Long currentOperatorId() {
        try {
            return com.huicai.base.system.util.SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 制审分离：制单人不能审批自己提交的单据。
     *
     * <p><b>SPEC-P119</b>：本校验此前完全不存在。与 P116 已补的凭证层、
     * 业务单据层口径逐字对齐 —— 含 {@code created_by} 为 NULL 时放行。
     *
     * <p>NULL 放行是刻意为之：Excel 导入 / 定时任务 / 系统初始化无登录态，
     * 本就没有「录入人」概念（Q2 裁定），此时若反过来说「他自审」是错的。
     * 代价是这些单据永远无法做制审分离校验，属已登记的已知局限。
     */
    private void assertNotSelfApprove(Long userId, Long createdBy, String docName) {
        if (userId != null && createdBy != null && userId.equals(createdBy)) {
            throw BusinessException.badRequest(
                    "制单人不能审批自己提交的" + docName + "（制审分离）");
        }
    }

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
        // P0-fix: 原为 `if (entity.getStatus() == null) entity.setStatus(BUDGET_DRAFT)`。
        // BudgetController#create(@RequestBody CreateRequest) 内层仍是 BudgetEntity ——
        // 只包一层 request **不构成隔离**（与 AssetInventory 同型），其 status 可被客户端绑定。
        // chk_budget_status 允许 DRAFT/SUBMITTED/APPROVED/ACTIVE/CLOSED/REJECTED/FROZEN，
        // 故 POST status=APPROVED / ACTIVE 可**一步跳过**提交与审批（铁律 #1）。
        // 门禁 check_entity_status_massassignment.py 存量项，改为无条件强制 DRAFT。
        entity.setStatus(BudgetStatus.BUDGET_DRAFT);
        BigDecimal total = BigDecimal.ZERO;
        if (entries != null) {
            for (BudgetEntryEntity e : entries) {
                total = total.add(e.getAmount());
                if (e.getUsedAmount() == null) e.setUsedAmount(BigDecimal.ZERO);
                if (e.getControlType() == null) e.setControlType("WARN");
            }
        }
        entity.setTotalAmount(total);
        entity.setCreatedBy(currentOperatorId());
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
    public BudgetEntity submit(Long id) {
        BudgetEntity entity = getById(id);
        if (!BudgetStatus.BUDGET_DRAFT.equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        entity.setStatus(BudgetStatus.BUDGET_SUBMITTED);
        budgetMapper.updateById(entity);
        return entity;
    }

    @Override
    public BudgetEntity approve(Long id) {
        BudgetEntity entity = getById(id);
        if (!BudgetStatus.BUDGET_SUBMITTED.equals(entity.getStatus())) {
            throw new BusinessException("仅已提交状态可审批");
        }
        assertNotSelfApprove(currentOperatorId(), entity.getCreatedBy(), "预算");
        entity.setStatus(BudgetStatus.BUDGET_APPROVED);
        entity.setApprovedAt(LocalDateTime.now());
        budgetMapper.updateById(entity);
        return entity;
    }

    @Override
    public BudgetEntity activate(Long id) {
        BudgetEntity entity = getById(id);
        if (!BudgetStatus.BUDGET_APPROVED.equals(entity.getStatus())) {
            throw new BusinessException("仅已审批可激活");
        }
        entity.setStatus(BudgetStatus.BUDGET_ACTIVE);
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
        // findBySubjectAndPeriod 用 be.* 返回 Map，真实 DB 的 key 是 snake_case
        // (used_amount/control_type)，而 Mock 夹具写的是 camelCase —— 两种都要读，
        // 否则真实 DB 上 controlType 读出 null 走 default 分支、usedAmount 恒 0。
        BigDecimal used = toBigDecimal(pick(entry, "usedAmount", "used_amount"));
        BigDecimal newUsed = used.add(amount);
        BigDecimal ratio = budget.compareTo(BigDecimal.ZERO) > 0
                ? newUsed.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        // 空 controlType 兜底为 WARN：历史数据/手工 SQL 可能为 null，
        // switch(null) 会直接 NPE，把整张单据校验打成 500。DB 侧 chk_ 默认 WARN。
        String controlType = pick(entry, "controlType", "control_type");
        if (StrUtil.isBlank(controlType)) {
            controlType = "WARN";
        }
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
                        .in(BudgetEntity::getStatus, BudgetStatus.BUDGET_APPROVED, BudgetStatus.BUDGET_ACTIVE)
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
        // P0-fix: 原为条件兜底，BudgetController#createAdjustment 直收 @RequestBody
        // BudgetAdjustmentEntity（违反铁律 #13）。t_budget_adjustment **没有任何 status CHECK**
        // ⇒ DB 层零兜底，客户端传 "__GARBAGE__" 也会原样入库。改为无条件强制。
        entity.setStatus(BudgetStatus.ADJUSTMENT_PENDING);
        if (entity.getAdjustmentDate() == null) entity.setAdjustmentDate(LocalDate.now());
        adjustmentMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetAdjustmentEntity approveAdjustment(Long id) {
        BudgetAdjustmentEntity entity = adjustmentMapper.selectById(id);
        if (entity == null) throw new BusinessException("调整单不存在");
        if (!BudgetStatus.ADJUSTMENT_PENDING.equals(entity.getStatus())) {
            throw new BusinessException("仅待审批状态可批准");
        }
        BudgetEntity budget = getById(entity.getBudgetId());
        if ("INCREASE".equals(entity.getAdjustmentType())) {
            budget.setTotalAmount(budget.getTotalAmount().add(entity.getAdjustmentAmount()));
        } else if ("DECREASE".equals(entity.getAdjustmentType())) {
            budget.setTotalAmount(budget.getTotalAmount().subtract(entity.getAdjustmentAmount()));
        }
        budgetMapper.updateById(budget);
        entity.setStatus(BudgetStatus.ADJUSTMENT_APPROVED);
        entity.setApprovedAt(LocalDateTime.now());
        adjustmentMapper.updateById(entity);
        return entity;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString());
    }

    /**
     * 从 Mapper 返回的 Map 里取第一个非 null 的 key 值.
     *
     * <p>背景：{@code BudgetEntryMapper.findBySubjectAndPeriod} 的 SELECT 用
     * {@code be.*}，PostgreSQL 返回的列名是 snake_case（{@code used_amount} /
     * {@code control_type}），而本方法原先按 camelCase（{@code usedAmount} /
     * {@code controlType}）读取 —— 只有 Mock 夹具恰好写 camelCase 才让单测变绿，
     * 真实 DB 上必然读出 null（AGENTS §4.3.7 Mock 盲区）。这里按
     * {@code camelCase 优先、snake_case 兜底} 的顺序取值，两种来源都能用。
     */
    private String pick(Map<String, Object> row, String camelKey, String snakeKey) {
        Object v = row.get(camelKey);
        if (v == null) {
            v = row.get(snakeKey);
        }
        return v == null ? null : v.toString();
    }
}
