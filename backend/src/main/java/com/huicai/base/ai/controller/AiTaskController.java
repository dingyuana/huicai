package com.huicai.base.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.ai.entity.AiAnomalyTagEntity;
import com.huicai.base.ai.entity.AiTaskEntity;
import com.huicai.base.ai.service.AiTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
    public R<AiTaskEntity> createTask(@RequestBody CreateTaskRequest request) {
        return R.ok(service.createAndDispatch(
                request.taskType,
                request.bizType,
                request.bizId,
                request.inputData
        ));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/tasks/page")
    public R<IPage<AiTaskEntity>> page(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(taskType, status, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/tasks/{id}")
    public R<AiTaskEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "审核并应用")
    @PostMapping("/tasks/{id}/review")
    public R<AiTaskEntity> review(@PathVariable Long id, @RequestParam Long reviewerId,
                                    @RequestParam(defaultValue = "true") boolean approved) {
        return R.ok(service.review(id, reviewerId, approved));
    }

    @Operation(summary = "标记异常")
    @PostMapping("/anomalies")
    public R<AiAnomalyTagEntity> tagAnomaly(@RequestBody AnomalyRequest request) {
        return R.ok(service.tagAnomaly(
                request.bizType, request.bizId, request.anomalyType,
                request.severity, request.description, request.taskId
        ));
    }

    @Operation(summary = "异常列表")
    @GetMapping("/anomalies")
    public R<List<AiAnomalyTagEntity>> listAnomalies(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Boolean resolved) {
        return R.ok(service.listAnomalies(bizType, resolved));
    }

    @Operation(summary = "解决异常")
    @PostMapping("/anomalies/{id}/resolve")
    public R<AiAnomalyTagEntity> resolve(@PathVariable Long id, @RequestParam Long resolverId) {
        return R.ok(service.resolveAnomaly(id, resolverId));
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
