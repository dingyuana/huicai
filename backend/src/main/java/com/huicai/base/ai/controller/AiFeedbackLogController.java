package com.huicai.base.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.base.ai.entity.AiFeedbackLogEntity;
import com.huicai.base.ai.service.AiFeedbackLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AI 分类反馈日志")
@RestController
@RequestMapping("/api/v1/ai/feedback-logs")
@RequiredArgsConstructor
public class AiFeedbackLogController {

    private final AiFeedbackLogService service;

    @Operation(summary = "反馈日志分页")
    @GetMapping
    public R<IPage<AiFeedbackLogEntity>> page(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long bankTxnId,
            @RequestParam(required = false) String humanAction,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.page(tenantId, bankTxnId, humanAction, current, size));
    }

    @Operation(summary = "反馈日志详情")
    @GetMapping("/{id}")
    public R<AiFeedbackLogEntity> getById(@PathVariable Long id) {
        AiFeedbackLogEntity entity = service.getById(id);
        if (entity == null) {
            return R.badRequest("反馈日志不存在");
        }
        return R.ok(entity);
    }

    @Operation(summary = "记录反馈")
    @PostMapping
    public R<AiFeedbackLogEntity> create(@RequestBody AiFeedbackLogEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "按租户统计")
    @GetMapping("/summary")
    public R<List<Map<String, Object>>> summaryByTenant(@RequestParam Long tenantId) {
        return R.ok(service.summaryByTenant(tenantId));
    }

    @Operation(summary = "查询某流水的所有反馈")
    @GetMapping("/recent")
    public R<List<Map<String, Object>>> recentByBankTxn(@RequestParam Long bankTxnId) {
        return R.ok(service.recentByBankTxn(bankTxnId));
    }

    @Operation(summary = "删除某流水的所有反馈")
    @DeleteMapping("/by-bank-txn")
    public R<Void> deleteByBankTxn(@RequestParam Long bankTxnId) {
        service.deleteByBankTxn(bankTxnId);
        return R.ok();
    }
}
