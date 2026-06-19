package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.entity.PrepaymentEntity;
import com.huicai.module.arap.service.PrepaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "预收/预付管理")
@RestController
@RequestMapping("/api/v1/prepayment")
@RequiredArgsConstructor
public class PrepaymentController {

    private final PrepaymentService prepaymentService;

    @Operation(summary = "分页查询预付款/预收款")
    @GetMapping("/page")
    public R<IPage<PrepaymentEntity>> pageQuery(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(prepaymentService.pageQuery(vendorId, customerId, status, current, size));
    }

    @Operation(summary = "查询预付款详情")
    @GetMapping("/{id}")
    public R<PrepaymentEntity> getById(@PathVariable Long id) {
        return R.ok(prepaymentService.getById(id));
    }

    @Operation(summary = "新增预付款 (DRAFT)")
    @PostMapping
    public R<PrepaymentEntity> create(@RequestBody PrepaymentEntity entity) {
        return R.ok(prepaymentService.create(entity));
    }

    @Operation(summary = "确认预付款 (DRAFT → CONFIRMED)")
    @PostMapping("/{id}/confirm")
    public R<PrepaymentEntity> confirm(@PathVariable Long id) {
        return R.ok(prepaymentService.confirm(id));
    }

    @Operation(summary = "预付冲应付 — 核销抵扣 (CONFIRMED → APPLIED)")
    @PostMapping("/{prepayId}/apply-to-payable/{payableId}")
    public R<PrepaymentEntity> applyToPayable(
            @PathVariable Long prepayId,
            @PathVariable Long payableId,
            @RequestParam(required = false) BigDecimal applyAmount,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") Long userId,
            @RequestParam(required = false) String summary) {
        return R.ok(prepaymentService.applyToPayable(
                prepayId, payableId, applyAmount, period, userId, summary));
    }

    @Operation(summary = "预收冲应收 — 核销抵扣 (CONFIRMED → APPLIED)")
    @PostMapping("/{prepayId}/apply-to-receivable/{receivableId}")
    public R<PrepaymentEntity> applyToReceivable(
            @PathVariable Long prepayId,
            @PathVariable Long receivableId,
            @RequestParam(required = false) BigDecimal applyAmount,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") Long userId,
            @RequestParam(required = false) String summary) {
        return R.ok(prepaymentService.applyToReceivable(
                prepayId, receivableId, applyAmount, period, userId, summary));
    }

    @Operation(summary = "反冲预付款 (CONFIRMED/APPLIED → REVERSED)")
    @PostMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id,
                           @RequestParam(defaultValue = "0") Long userId,
                           @RequestParam String reason) {
        prepaymentService.reverse(id, userId, reason);
        return R.ok();
    }

    @Operation(summary = "查询供应商未结清预付款列表")
    @GetMapping("/open/{vendorId}")
    public R<List<PrepaymentEntity>> getOpenPrepayments(@PathVariable Long vendorId) {
        return R.ok(prepaymentService.getOpenPrepayments(vendorId));
    }

    @Operation(summary = "查询客户未结清预收款列表")
    @GetMapping("/open-customer/{customerId}")
    public R<List<PrepaymentEntity>> getOpenPrepaymentsForCustomer(@PathVariable Long customerId) {
        return R.ok(prepaymentService.getOpenPrepaymentsForCustomer(customerId));
    }
}
