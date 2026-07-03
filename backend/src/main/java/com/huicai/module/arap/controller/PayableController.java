package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.dto.PayableVO;
import com.huicai.module.arap.service.PayableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.huicai.module.arap.entity.PayableEntity;

/**
 * 应付明细 — 历史视图（P34 后应付数据已迁移至 t_business_doc）。
 * <p>
 * 分页查询已改造为读取 t_business_doc（supplierId != null 的业务单据）。
 * 创建/确认/冲销等操作请使用「核销工作台」(/api/v1/reconciliation) 和「业务单据」(/api/v1/business-docs)。
 * <p>
 * @deprecated P34 架构后不再创建独立应付单，本 Controller 仅用于查看历史数据，
 *             后续前端将统一跳转到「核销工作台」。新增核销操作请走 ReconciliationController。
 */
@Deprecated
@Tag(name = "应付明细（历史视图）")
@RestController
@RequestMapping("/api/v1/payables")
@RequiredArgsConstructor
public class PayableController {

    private final PayableService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<PayableVO>> page(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String docNo,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String voucherNo,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(vendorId, period, docNo, invoiceNo, voucherNo, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<PayableVO> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<PayableEntity> create(@RequestBody PayableEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "确认应付单（草稿→已确认）")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        service.confirm(id, 0L);
        return R.ok();
    }

    @Operation(summary = "冲销应付单（CONFIRMED/SETTLED→REVERSED）")
    @PostMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id) {
        service.reverse(id, 0L);
        return R.ok();
    }

    @Operation(summary = "供应商账龄分析")
    @GetMapping("/aging")
    public R<Map<String, Object>> aging(@RequestParam(required = false) Long vendorId) {
        if (vendorId == null) {
            return R.ok(java.util.Map.of("message", "请指定供应商ID"));
        }
        return R.ok(service.agingAnalysis(vendorId));
    }
}
