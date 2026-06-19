package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.dto.ReceivableVO;
import com.huicai.module.arap.service.ReceivableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.huicai.module.arap.entity.ReceivableEntity;
import java.util.List;
import java.util.Map;

@Tag(name = "应收明细")
@RestController
@RequestMapping("/api/v1/receivables")
@RequiredArgsConstructor
public class ReceivableController {

    private final ReceivableService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<ReceivableVO>> page(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(customerId, period, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<ReceivableVO> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<ReceivableEntity> create(@RequestBody ReceivableEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "确认应收单（草稿→已确认）")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        service.confirm(id, 0L);
        return R.ok();
    }

    @Operation(summary = "冲销应收单（CONFIRMED/SETTLED→REVERSED）")
    @PostMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id) {
        service.reverse(id, 0L);
        return R.ok();
    }

    @Operation(summary = "逾期应收列表")
    @GetMapping("/overdue")
    public R<List<Map<String, Object>>> overdue() {
        return R.ok(service.overdueList());
    }

    @Operation(summary = "客户账龄分析")
    @GetMapping("/aging")
    public R<Map<String, Object>> aging(@RequestParam(required = false) Long customerId) {
        if (customerId == null) {
            return R.ok(service.overallAging());
        }
        return R.ok(service.agingAnalysis(customerId));
    }
}
