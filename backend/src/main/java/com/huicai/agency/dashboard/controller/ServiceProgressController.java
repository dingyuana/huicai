package com.huicai.agency.dashboard.controller;

import com.huicai.agency.dashboard.service.ServiceProgressService;
import com.huicai.agency.dashboard.service.ServiceProgressService.ServiceProgressVO;
import com.huicai.agency.dashboard.service.ServiceProgressService.WorkloadVO;
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
 * 数据隔离：agency_id 取自 SecurityContext（S-26 三层防线）；会计/助理仅见自己分配的企业
 * （由前端筛选 + 查询端点支持 enterpriseId 过滤，经理不带 enterpriseId 看全租户）。
 * 铁律#1：本 Controller 只读聚合 + 经理 force-done（留审计），不触发任何业务单据状态变更。
 */
@Tag(name = "代理服务进度与工作量")
@RestController
@RequestMapping("/api/v1/agency")
@RequiredArgsConstructor
public class ServiceProgressController {

    private final ServiceProgressService service;

    @Operation(summary = "进度查询（agency_id 隔离；经理看全租户，会计传 enterpriseId 看自己客户）")
    @GetMapping("/service-progress")
    public R<ServiceProgressVO> progress(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long enterpriseId,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status) {
        Long agencyId = SecurityUtils.getCurrentAgencyId();
        return R.ok(service.listProgress(agencyId, period, enterpriseId, stage, status));
    }

    @Operation(summary = "超期预警（due_date < 今天 且 非 DONE 且未提醒）")
    @GetMapping("/service-progress/overtime")
    public R<List<ServiceProgressService.ServiceProgressRowVO>> overtime() {
        Long agencyId = SecurityUtils.getCurrentAgencyId();
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
        return R.ok(service.getWorkload(agencyId, periodFrom, periodTo, groupBy));
    }

    @Data
    public static class ForceDoneReq {
        private String remark;
    }
}
