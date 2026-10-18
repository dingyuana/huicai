package com.huicai.base.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.ai.entity.AiAnomalyTagEntity;
import com.huicai.base.ai.entity.AiTaskEntity;
import com.huicai.base.ai.service.AiTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.huicai.base.ai.vo.AiAnomalyTagVO;
import com.huicai.base.ai.vo.AiTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AI 任务管理")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService service;

    @Operation(summary = "下发 AI 任务")
    @PostMapping("/tasks")
    public R<AiTaskVO> createTask(@RequestBody CreateTaskRequest request) {
        return R.ok(AiTaskVO.from(service.createAndDispatch(
                request.taskType,
                request.bizType,
                request.bizId,
                request.inputData
        )));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/tasks/page")
    // 🔴 事务必须挂在 Controller 上（SPEC-P128 §7.1 同型缺陷，2026-10-18 服务器实测）：
    //   t_ai_task 是 RLS 租户表，而 TenantGucSetter#apply 在无事务时直接 return
    //   ⇒ GUC 恒 NULL ⇒ 谓词取 NULL ⇒ 整表被过滤。
    //   🔴 **仅给 Service 加类级 @Transactional 无效**：AiTaskService 已挂类级注解，
    //   实测日志仍是 "SqlSession was not registered for synchronization because
    //   synchronization is not active"，症状不变（DB 有数据但 total=0）。
    //   故与 VoucherTypeController 同法：把事务上提到 Controller。
    @Transactional(rollbackFor = Exception.class)
    public R<IPage<AiTaskVO>> page(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(AiTaskVO.from(service.pageQuery(taskType, status, current, size)));
    }

    @Operation(summary = "详情")
    @GetMapping("/tasks/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<AiTaskVO> getById(@PathVariable Long id) {
        return R.ok(AiTaskVO.from(service.getById(id)));
    }

    @Operation(summary = "审核并应用")
    @PostMapping("/tasks/{id}/review")
    public R<AiTaskVO> review(@PathVariable Long id, @RequestParam Long reviewerId,
                                    @RequestParam(defaultValue = "true") boolean approved) {
        return R.ok(AiTaskVO.from(service.review(id, reviewerId, approved)));
    }

    @Operation(summary = "标记异常")
    @PostMapping("/anomalies")
    public R<AiAnomalyTagVO> tagAnomaly(@RequestBody AnomalyRequest request) {
        return R.ok(AiAnomalyTagVO.from(service.tagAnomaly(
                request.bizType, request.bizId, request.anomalyType,
                request.severity, request.description, request.taskId
        )));
    }

    @Operation(summary = "异常列表")
    @GetMapping("/anomalies")
    public R<List<AiAnomalyTagVO>> listAnomalies(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Boolean resolved) {
        return R.ok(AiAnomalyTagVO.from(service.listAnomalies(bizType, resolved)));
    }

    @Operation(summary = "解决异常")
    @PostMapping("/anomalies/{id}/resolve")
    public R<AiAnomalyTagVO> resolve(@PathVariable Long id, @RequestParam Long resolverId) {
        return R.ok(AiAnomalyTagVO.from(service.resolveAnomaly(id, resolverId)));
    }

    public static class CreateTaskRequest {
        public String taskType;
        public String bizType;
        public Long bizId;
        public Map<String, Object> inputData;
    }

    public static class AnomalyRequest {
        public String bizType;
        public Long bizId;
        public String anomalyType;
        public String severity;
        public String description;
        public Long taskId;
    }
}
