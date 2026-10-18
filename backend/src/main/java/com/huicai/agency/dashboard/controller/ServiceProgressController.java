package com.huicai.agency.dashboard.controller;

import com.huicai.agency.dashboard.service.ServiceProgressService;
import com.huicai.agency.dashboard.service.ServiceProgressService.ServiceProgressVO;
import com.huicai.agency.dashboard.service.ServiceProgressService.WorkloadVO;
import com.huicai.agency.security.AgencyDataScopeResolver;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 代理服务进度跟踪 + 工作量统计（P79）
 * <pre>
 * GET  /api/v1/agency/service-progress?period=&enterpriseId=&stage=&status=
 * GET  /api/v1/agency/service-progress/overtime
 * POST /api/v1/agency/service-progress/{id}/force-done   （仅经理；body: {remark}）
 * GET  /api/v1/agency/workload?periodFrom=&periodTo=&groupBy=USER|ENTERPRISE
 * GET  /api/v1/agency/workload/export（同上参数）
 * </pre>
 *
 * <p>数据隔离：agency_id 取自 SecurityContext；<b>客户企业范围由
 * {@link AgencyDataScopeResolver} 在服务端判定</b>。
 *
 * <p>🔴 <b>SPEC-P116 修正</b>：本注释原写「会计/助理仅见自己分配的企业
 * （由前端筛选 + 查询端点支持 enterpriseId 过滤，经理不带 enterpriseId 看全租户）」——
 * **那是把隔离责任交给前端，等于没有隔离**：任何会计只要不带 {@code enterpriseId}
 * 调本端点，就能拿到整个代理公司全部客户的进度数据。
 * 现已改为服务端强制：受限角色必须显式指定企业，且该企业必须在其可见集合内。
 *
 * <p>⚠️ <b>已知局限（已登记 SPEC-P116 §7）</b>：受限角色目前是「逐个指定企业」，
 * 尚不支持「一次列出我负责的全部客户」。要支持需把 Service 的
 * {@code enterpriseId} 单参改为集合入参 —— 属接口变更，另立一轮，不混入本次修复。
 *
 * <p>铁律#1：本 Controller 只读聚合 + 经理 force-done（留审计），不触发任何业务单据状态变更。
 */
@Tag(name = "代理服务进度与工作量")
@RestController
@RequestMapping("/api/v1/agency")
@RequiredArgsConstructor
public class ServiceProgressController {

    private final ServiceProgressService service;
    private final AgencyDataScopeResolver scopeResolver;

    @Operation(summary = "进度查询（agency_id 隔离；服务端按代理角色收窄可见客户）")
    @GetMapping("/service-progress")
    public R<ServiceProgressVO> progress(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long enterpriseId,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status) {
        Long agencyId = SecurityUtils.getCurrentAgencyId();
        Long allowed = scopeResolver.enforceEnterpriseAccess(enterpriseId);
        return R.ok(service.listProgress(agencyId, period, allowed, stage, status));
    }

    @Operation(summary = "超期预警（due_date < 今天 且 非 DONE 且未提醒）")
    @GetMapping("/service-progress/overtime")
    public R<List<ServiceProgressService.ServiceProgressRowVO>> overtime() {
        Long agencyId = SecurityUtils.getCurrentAgencyId();
        // 代理级跨客户聚合视图 ⇒ 仅监督类角色
        scopeResolver.requireOversight("超期预警");
        return R.ok(service.listOvertime(agencyId, LocalDate.now()));
    }

    @Operation(summary = "经理强制完成节点（401/403 由角色校验；写审计日志）")
    @PostMapping("/service-progress/{id}/force-done")
    public R<Void> forceDone(@PathVariable Long id, @RequestBody ForceDoneReq req) {
        if (req == null || req.getRemark() == null || req.getRemark().isBlank()) {
            throw BusinessException.badRequest("P79_001 force-done 必须填写 remark");
        }
        Long operatorId = SecurityUtils.getCurrentUserId();
        String operatorName = SecurityUtils.getCurrentUsername();
        String operatorRole = SecurityUtils.getCurrentAgencyRole();
        service.forceDone(id, operatorId, operatorName, operatorRole, req.getRemark());
        return R.ok();
    }

    @Operation(summary = "工作量统计（USER/ENTERPRISE 维度）")
    @GetMapping("/workload")
    public R<WorkloadVO> workload(
            @RequestParam String periodFrom,
            @RequestParam String periodTo,
            @RequestParam(defaultValue = "USER") String groupBy) {
        Long agencyId = SecurityUtils.getCurrentAgencyId();
        // 工作量统计是「各会计/各客户的横向排名」，属跨客户聚合 ⇒ 仅监督类角色
        scopeResolver.requireOversight("工作量统计");
        return R.ok(service.getWorkload(agencyId, periodFrom, periodTo, groupBy));
    }

    @Data
    public static class ForceDoneReq {
        private String remark;
    }
}
