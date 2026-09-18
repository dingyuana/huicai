package com.huicai.agency.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 代理服务进度跟踪 + 工作量统计服务（P79）
 * <p>
 * 设计边界（铁律#1）：进度与统计是只读聚合视图，节点推进由业务动作触发写入，
 * 本服务不新增任何审核/状态变更入口；事件监听器只写 t_service_progress，不碰业务表。
 * <p>
 * 节点化流程：INTAKE(取票) → BOOKING(记账) → REVIEW(审核结账) → FILING(报税) → DONE。
 * 事件驱动、只进不退（乱序/已 DONE 的事件忽略）；经理可 force-done（留审计）。
 * <p>
 * 数据隔离：agency_id 为租户维度，手动过滤（t_service_progress 列入 SHARED_TABLES，
 * 拦截器跳过自动注入 enterprise_id，避免破坏跨客户查询）。
 */
public interface ServiceProgressService {

    /**
     * 事件推进节点（幂等，只进不退）。
     * <p>
     * 由 4 个业务事件源调用：发票导入完成→INTAKE、凭证全部过账→BOOKING、
     * 结账完成→REVIEW、申报 APPROVED→FILING。
     * <ul>
     *   <li>目标节点当前 status=PENDING → 置 IN_PROGRESS（started_at=now）</li>
     *   <li>目标节点当前 status=IN_PROGRESS → 置 DONE（finished_at=now）</li>
     *   <li>目标节点已 DONE → 忽略（幂等）</li>
     *   <li>乱序（前一节点未 DONE 而当前被推进）→ 忽略 + WARN，不回退</li>
     * </ul>
     *
     * @param agencyId     代账公司（租户）
     * @param enterpriseId 被服务客户企业
     * @param period       YYYYMM
     * @param stage        目标节点 INTAKE/BOOKING/REVIEW/FILING
     * @return 是否发生了状态变更（true=推进/新建，false=已 DONE 幂等忽略）
     */
    boolean advanceStage(Long agencyId, Long enterpriseId, String period, String stage);

    /** 经理强制标记某节点 DONE（留审计） */
    void forceDone(Long progressId, Long operatorId, String operatorName, String operatorRole, String remark);

    /** 进度查询（按期间/客户/节点/状态过滤，agency_id 隔离） */
    ServiceProgressVO listProgress(Long agencyId, String period, Long enterpriseId,
                                   String stage, String status);

    /** 超期预警：due_date < today 且 status != DONE 的行 */
    List<ServiceProgressRowVO> listOvertime(Long agencyId, LocalDate today);

    /** 工作量统计（按代理用户/客户分组） */
    WorkloadVO getWorkload(Long agencyId, String periodFrom, String periodTo, String groupBy);

    // ── VO ──

    record ServiceProgressRowVO(
            Long progressId,
            Long enterpriseId,
            String enterpriseName,
            String period,
            String stage,
            String status,
            Long assignedTo,
            String assignedToName,
            LocalDate dueDate,
            boolean overtime
    ) {}

    record ServiceProgressSummaryVO(
            int total, int done, int inProgress, int pending, int overtime
    ) {}

    record ServiceProgressVO(
            Long agencyId,
            String period,
            List<ServiceProgressRowVO> rows,
            ServiceProgressSummaryVO summary
    ) {}

    record WorkloadRowVO(
            Long userId,
            String userName,
            int assignedCustomers,
            BigDecimal completionRate,
            int inProgress,
            int overtime
    ) {}

    record WorkloadVO(
            String periodFrom, String periodTo, String groupBy,
            List<WorkloadRowVO> rows
    ) {}

    /** 阶段顺序（只进不退校验；接口常量，impl/测试可引用） */
    List<String> STAGE_ORDER = List.of("INTAKE", "BOOKING", "REVIEW", "FILING", "DONE");
}
