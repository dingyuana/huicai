package com.huicai.agency.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.agency.dashboard.entity.ServiceProgressEntity;
import com.huicai.agency.dashboard.mapper.ServiceProgressMapper;
import com.huicai.agency.dashboard.service.ServiceProgressService;
import com.huicai.agency.dashboard.service.ServiceProgressService.ServiceProgressRowVO;
import com.huicai.agency.dashboard.service.ServiceProgressService.ServiceProgressSummaryVO;
import com.huicai.agency.dashboard.service.ServiceProgressService.ServiceProgressVO;
import com.huicai.agency.dashboard.service.ServiceProgressService.WorkloadRowVO;
import com.huicai.agency.dashboard.service.ServiceProgressService.WorkloadVO;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.service.AuditLogService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 代理服务进度 + 工作量统计实现（P79）。
 * <p>
 * 设计边界（铁律#1）：事件监听器只写 t_service_progress，绝不改任何业务单据状态。
 * 数据隔离：t_service_progress 列入 SHARED_TABLES（拦截器跳过 enterprise_id 注入），
 * 本服务所有查询手动 eq(agency_id)，代理用户/客户名走 SHARED 表 t_agency_user / t_enterprise / t_user。
 * 双防线：advanceStage 拿到目标行后按状态前置条件再判断一次（mock 单测下 wrapper 可绕过）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceProgressServiceImpl implements ServiceProgressService {

    /** 会计/助理不可 force-done（SPEC P79_003）；经理/管理员放行 */
    private static final Set<String> FORCE_DONE_DENIED_ROLES = Set.of("ACCOUNTANT", "ASSISTANT");

    /** 工作量"应做节点"基数：每客户 4 个服务节点（INTAKE/BOOKING/REVIEW/FILING，不含 DONE 汇总） */
    private static final int STAGES_PER_CUSTOMER = 4;

    private final ServiceProgressMapper progressMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    // ─────────────── 事件推进（幂等 + 只进不退） ───────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean advanceStage(Long agencyId, Long enterpriseId, String period, String stage) {
        requirePeriod(period);
        int idx = STAGE_ORDER.indexOf(stage);
        if (idx < 0 || idx == STAGE_ORDER.size() - 1) {
            // DONE 是汇总终态，不单独推进；非法 stage 忽略
            return false;
        }
        // 只进不退：前序节点未 DONE 时，本节点事件乱序 → 忽略 + WARN
        if (idx > 0 && !isStageDone(agencyId, enterpriseId, period, STAGE_ORDER.get(idx - 1))) {
            log.warn("P79 乱序事件忽略：agency={} enterprise={} period={} stage={} 的前序 {} 未 DONE",
                    agencyId, enterpriseId, period, stage, STAGE_ORDER.get(idx - 1));
            return false;
        }

        ServiceProgressEntity row = findProgress(agencyId, enterpriseId, period, stage);
        if (row == null) {
            // 惰性创建：事件直接代表该节点业务完成 → 一步到 DONE（SPEC 场景1）
            row = new ServiceProgressEntity();
            row.setAgencyId(agencyId);
            row.setEnterpriseId(enterpriseId);
            row.setPeriod(period);
            row.setStage(stage);
            row.setStatus("DONE");
            row.setStartedAt(LocalDateTime.now());
            row.setFinishedAt(LocalDateTime.now());
            progressMapper.insert(row);
            return true;
        }
        // 双防线：状态前置
        if ("DONE".equals(row.getStatus())) {
            return false; // 幂等
        }
        row.setStatus("DONE");
        row.setStartedAt(row.getStartedAt() == null ? LocalDateTime.now() : row.getStartedAt());
        row.setFinishedAt(LocalDateTime.now());
        progressMapper.updateById(row);
        return true;
    }

    private boolean isStageDone(Long agencyId, Long enterpriseId, String period, String stage) {
        ServiceProgressEntity r = findProgress(agencyId, enterpriseId, period, stage);
        return r != null && "DONE".equals(r.getStatus());
    }

    private ServiceProgressEntity findProgress(Long agencyId, Long enterpriseId, String period, String stage) {
        LambdaQueryWrapper<ServiceProgressEntity> w = new LambdaQueryWrapper<ServiceProgressEntity>()
                .eq(ServiceProgressEntity::getAgencyId, agencyId)
                .eq(ServiceProgressEntity::getEnterpriseId, enterpriseId)
                .eq(ServiceProgressEntity::getPeriod, period)
                .eq(ServiceProgressEntity::getStage, stage)
                .eq(ServiceProgressEntity::getDeleted, 0);
        return progressMapper.selectOne(w);
    }

    // ─────────────── 经理 force-done（审计） ───────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceDone(Long progressId, Long operatorId, String operatorName, String operatorRole, String remark) {
        if (remark == null || remark.isBlank()) {
            throw BusinessException.badRequest("P79_001 force-done 必须填写 remark（审计说明）");
        }
        // 仅经理/管理员：会计/助理拒绝（SPEC P79_003）；operatorRole 由 controller 从 SecurityContext 提供
        if (operatorRole != null && FORCE_DONE_DENIED_ROLES.contains(operatorRole)) {
            throw BusinessException.forbidden("P79_003 会计/助理不可强制完成节点，仅经理可操作");
        }
        ServiceProgressEntity row = progressMapper.selectById(progressId);
        if (row == null || (row.getDeleted() != null && row.getDeleted() == 1)) {
            throw BusinessException.notFound("P79 进度节点不存在: " + progressId);
        }
        if ("DONE".equals(row.getStatus())) {
            throw BusinessException.conflict("P79_002 该节点已 DONE，无需 force（幂等拒绝）");
        }
        String oldStatus = row.getStatus();
        row.setStatus("DONE");
        row.setFinishedAt(LocalDateTime.now());
        row.setRemark(remark);
        row.setUpdatedBy(operatorId);
        progressMapper.updateById(row);
        // 审计留痕（铁律#5）：同步写 t_audit_log，与业务同事务
        auditLogService.recordStatusChange("SERVICE_PROGRESS", progressId,
                "status", oldStatus, "DONE");
        log.info("P79 force-done: progressId={} {}→DONE by {} ({}): {}",
                progressId, oldStatus, operatorName, operatorRole, remark);
    }

    // ─────────────── 进度查询 ───────────────

    @Override
    @Transactional(readOnly = true)
    public ServiceProgressVO listProgress(Long agencyId, String period, Long enterpriseId,
                                         String stage, String status) {
        LambdaQueryWrapper<ServiceProgressEntity> w = new LambdaQueryWrapper<ServiceProgressEntity>()
                .eq(ServiceProgressEntity::getAgencyId, agencyId)
                .eq(ServiceProgressEntity::getDeleted, 0);
        if (period != null && !period.isBlank()) {
            requirePeriod(period);
            w.eq(ServiceProgressEntity::getPeriod, period);
        }
        if (enterpriseId != null) {
            w.eq(ServiceProgressEntity::getEnterpriseId, enterpriseId);
        }
        if (stage != null && !stage.isBlank()) {
            w.eq(ServiceProgressEntity::getStage, stage);
        }
        if (status != null && !status.isBlank()) {
            w.eq(ServiceProgressEntity::getStatus, status);
        }
        List<ServiceProgressEntity> rows = progressMapper.selectList(w);

        Map<Long, String> entName = enterpriseNameMap(agencyId);
        Map<Long, String> userFullName = proxyUserFullNameMap(agencyId);
        LocalDate today = LocalDate.now();

        List<ServiceProgressRowVO> out = new ArrayList<>();
        int done = 0, inProgress = 0, pending = 0, overtime = 0;
        for (ServiceProgressEntity r : rows) {
            boolean isOvertime = "DONE".equals(r.getStatus()) == false
                    && r.getDueDate() != null && r.getDueDate().isBefore(today)
                    && r.getOvertimeNotifiedAt() == null;
            if ("DONE".equals(r.getStatus())) done++;
            else if ("IN_PROGRESS".equals(r.getStatus())) inProgress++;
            else pending++;
            if (isOvertime) overtime++;
            out.add(new ServiceProgressRowVO(r.getId(), r.getEnterpriseId(),
                    entName.getOrDefault(r.getEnterpriseId(), "-"), r.getPeriod(),
                    r.getStage(), r.getStatus(), r.getAssignedTo(),
                    r.getAssignedTo() == null ? "-" : userFullName.getOrDefault(r.getAssignedTo(), "-"),
                    r.getDueDate(), isOvertime));
        }
        out.sort((a, b) -> {
            int c = a.period().compareTo(b.period());
            if (c != 0) return c;
            c = a.enterpriseId().compareTo(b.enterpriseId());
            if (c != 0) return c;
            return STAGE_ORDER.indexOf(a.stage()) - STAGE_ORDER.indexOf(b.stage());
        });
        return new ServiceProgressVO(agencyId, period, out,
                new ServiceProgressSummaryVO(out.size(), done, inProgress, pending, overtime));
    }

    // ─────────────── 超期预警 ───────────────

    @Override
    @Transactional(readOnly = true)
    public List<ServiceProgressRowVO> listOvertime(Long agencyId, LocalDate today) {
        LocalDate t = today == null ? LocalDate.now() : today;
        LambdaQueryWrapper<ServiceProgressEntity> w = new LambdaQueryWrapper<ServiceProgressEntity>()
                .eq(ServiceProgressEntity::getAgencyId, agencyId)
                .eq(ServiceProgressEntity::getDeleted, 0)
                .ne(ServiceProgressEntity::getStatus, "DONE")
                .isNotNull(ServiceProgressEntity::getDueDate)
                .lt(ServiceProgressEntity::getDueDate, t)
                .isNull(ServiceProgressEntity::getOvertimeNotifiedAt);
        List<ServiceProgressEntity> rows = progressMapper.selectList(w);
        Map<Long, String> entName = enterpriseNameMap(agencyId);
        Map<Long, String> userFullName = proxyUserFullNameMap(agencyId);
        return rows.stream().map(r -> new ServiceProgressRowVO(
                r.getId(), r.getEnterpriseId(), entName.getOrDefault(r.getEnterpriseId(), "-"),
                r.getPeriod(), r.getStage(), r.getStatus(), r.getAssignedTo(),
                r.getAssignedTo() == null ? "-" : userFullName.getOrDefault(r.getAssignedTo(), "-"),
                r.getDueDate(), true)).collect(Collectors.toList());
    }

    // ─────────────── 工作量统计 ───────────────

    @Override
    @Transactional(readOnly = true)
    public WorkloadVO getWorkload(Long agencyId, String periodFrom, String periodTo, String groupBy) {
        requirePeriodRange(periodFrom, periodTo);
        String g = normalizeGroupBy(groupBy);
        LocalDate today = LocalDate.now();

        // 区间内该 agency 的进度行
        LambdaQueryWrapper<ServiceProgressEntity> w = new LambdaQueryWrapper<ServiceProgressEntity>()
                .eq(ServiceProgressEntity::getAgencyId, agencyId)
                .eq(ServiceProgressEntity::getDeleted, 0)
                .between(ServiceProgressEntity::getPeriod, periodFrom, periodTo);
        List<ServiceProgressEntity> inRange = progressMapper.selectList(w);

        Map<Long, String> entName = enterpriseNameMap(agencyId);
        Map<Long, String> userFullName = proxyUserFullNameMap(agencyId);

        if ("USER".equals(g)) {
            // 按经办代理用户分组：assignedCustomers × 4 为应做节点基数（SPEC 场景5）
            Map<Long, List<ServiceProgressEntity>> byUser = new TreeMap<>();
            for (ServiceProgressEntity r : inRange) {
                if (r.getAssignedTo() != null) {
                    byUser.computeIfAbsent(r.getAssignedTo(), k -> new ArrayList<>()).add(r);
                }
            }
            List<WorkloadRowVO> rows = new ArrayList<>();
            for (Map.Entry<Long, List<ServiceProgressEntity>> e : byUser.entrySet()) {
                Long agencyUserId = e.getKey();
                List<ServiceProgressEntity> group = e.getValue();
                int assignedCustomers = agencyUserEnterpriseMapper.countByUserId(agencyUserId);
                rows.add(buildWorkloadRow(agencyUserId,
                        userFullName.getOrDefault(agencyUserId, "-"),
                        assignedCustomers, group, today));
            }
            return new WorkloadVO(periodFrom, periodTo, g, rows);
        }

        // ENTERPRISE：按客户企业分组；完成率 = 区间内该客户已 DONE 节点 / 实际进度行总数
        Map<Long, List<ServiceProgressEntity>> byEnt = new TreeMap<>();
        for (ServiceProgressEntity r : inRange) {
            byEnt.computeIfAbsent(r.getEnterpriseId(), k -> new ArrayList<>()).add(r);
        }
        List<WorkloadRowVO> rows = new ArrayList<>();
        for (Map.Entry<Long, List<ServiceProgressEntity>> e : byEnt.entrySet()) {
            Long entId = e.getKey();
            List<ServiceProgressEntity> group = e.getValue();
            int assignees = countAssignees(entId);
            rows.add(new WorkloadRowVO(entId, entName.getOrDefault(entId, "-"),
                    assignees, completionRate(group),
                    inProgressCount(group), overtimeCount(group, today)));
        }
        return new WorkloadVO(periodFrom, periodTo, g, rows);
    }

    /** 工作量单行（USER 维度）：assignedCustomers × 4 为应做节点基数 */
    private WorkloadRowVO buildWorkloadRow(Long userId, String userName, int assignedCustomers,
                                           List<ServiceProgressEntity> group, LocalDate today) {
        int expected = assignedCustomers * STAGES_PER_CUSTOMER;
        return new WorkloadRowVO(userId, userName, assignedCustomers,
                completionRate(group, expected), inProgressCount(group), overtimeCount(group, today));
    }

    /** USER 维度完成率：已 DONE 节点 / (分配客户数 × 4)；基数为 0 时 done=0→0 否则→1 */
    private BigDecimal completionRate(List<ServiceProgressEntity> group, int expected) {
        long done = group.stream().filter(r -> "DONE".equals(r.getStatus())).count();
        if (expected <= 0) {
            return done == 0 ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        return BigDecimal.valueOf(done).divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
    }

    /** ENTERPRISE 维度完成率：区间内已 DONE 节点 / 实际进度行总数（无进度行→0） */
    private BigDecimal completionRate(List<ServiceProgressEntity> group) {
        if (group.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long done = group.stream().filter(r -> "DONE".equals(r.getStatus())).count();
        return BigDecimal.valueOf(done).divide(BigDecimal.valueOf(group.size()), 4, RoundingMode.HALF_UP);
    }

    private int inProgressCount(List<ServiceProgressEntity> group) {
        return (int) group.stream().filter(r -> "IN_PROGRESS".equals(r.getStatus())).count();
    }

    private int overtimeCount(List<ServiceProgressEntity> group, LocalDate today) {
        return (int) group.stream()
                .filter(r -> !"DONE".equals(r.getStatus())
                        && r.getDueDate() != null && r.getDueDate().isBefore(today)
                        && r.getOvertimeNotifiedAt() == null)
                .count();
    }

    /** 客户当前有效分配的代理用户数（t_agency_user_enterprise，deleted=0 且未解除） */
    private int countAssignees(Long enterpriseId) {
        LambdaQueryWrapper<com.huicai.agency.user.entity.AgencyUserEnterpriseEntity> w =
                new LambdaQueryWrapper<>();
        w.eq(com.huicai.agency.user.entity.AgencyUserEnterpriseEntity::getEnterpriseId, enterpriseId)
         .eq(com.huicai.agency.user.entity.AgencyUserEnterpriseEntity::getDeleted, 0);
        return agencyUserEnterpriseMapper.selectCount(w).intValue();
    }

    // ─────────────── 辅助 ───────────────

    private List<AgencyUserEntity> agencyUsers(Long agencyId) {
        LambdaQueryWrapper<AgencyUserEntity> w = new LambdaQueryWrapper<AgencyUserEntity>()
                .eq(AgencyUserEntity::getAgencyId, agencyId)
                .eq(AgencyUserEntity::getDeleted, 0);
        return agencyUserMapper.selectList(w);
    }

    private Map<Long, String> enterpriseNameMap(Long agencyId) {
        LambdaQueryWrapper<EnterpriseEntity> w = new LambdaQueryWrapper<EnterpriseEntity>()
                .eq(EnterpriseEntity::getAgencyId, agencyId)
                .eq(EnterpriseEntity::getDeleted, 0);
        List<EnterpriseEntity> ents = enterpriseMapper.selectList(w);
        return ents.stream().collect(Collectors.toMap(EnterpriseEntity::getId,
                e -> e.getEnterpriseName() == null ? "-" : e.getEnterpriseName(), (a, b) -> a));
    }

    /** agency_user_id → 代理用户真实姓名（经 t_user.realName 解析；无映射回退 agency_user_id 字符串） */
    private Map<Long, String> proxyUserFullNameMap(Long agencyId) {
        Map<Long, Long> mapping = new HashMap<>();
        for (AgencyUserEntity u : agencyUsers(agencyId)) {
            if (u.getUserId() != null) {
                mapping.put(u.getId(), u.getUserId());
            }
        }
        Map<Long, String> realName = realNameMap(mapping.values());
        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<Long, Long> e : mapping.entrySet()) {
            result.put(e.getKey(), realName.getOrDefault(e.getValue(), String.valueOf(e.getValue())));
        }
        return result;
    }

    private Map<Long, String> realNameMap(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<UserEntity> w = new LambdaQueryWrapper<UserEntity>()
                .in(UserEntity::getId, userIds);
        List<UserEntity> users = userMapper.selectList(w);
        return users.stream().collect(Collectors.toMap(UserEntity::getId,
                u -> u.getRealName() == null ? u.getUsername() : u.getRealName(), (a, b) -> a));
    }

    private String normalizeGroupBy(String groupBy) {
        String g = groupBy == null ? "USER" : groupBy.trim().toUpperCase();
        if (!Set.of("USER", "ENTERPRISE").contains(g)) {
            throw BusinessException.badRequest("P79 groupBy 非法: " + groupBy + "（须 USER/ENTERPRISE）");
        }
        return g;
    }

    private void requirePeriod(String period) {
        if (period == null || !period.matches("\\d{6}")) {
            throw BusinessException.badRequest("P79 period 须为 6 位数字 YYYYMM: " + period);
        }
    }

    private void requirePeriodRange(String from, String to) {
        requirePeriod(from);
        requirePeriod(to);
        if (from.compareTo(to) > 0) {
            throw BusinessException.badRequest("P79 periodFrom 不能大于 periodTo: " + from + " > " + to);
        }
    }
}
