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
import java.time.LocalDate;
import java.util.List;

/**
 * 预付款/预收款管理接口 — P12 核销业务闭环
 */
@Tag(name = "预付款/预收款")
@RestController
@RequestMapping("/api/v1/prepayment")
@RequiredArgsConstructor
public class PrepaymentController {

    private final PrepaymentService prepaymentService;

    @Operation(summary = "创建预付款 (供应商预付款)")
    @PostMapping("/payment")
    public R<PrepaymentEntity> createPaymentPrepay(
            @RequestParam Long vendorId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate txDate,
            @RequestParam(required = false) String summary,
            @RequestParam(defaultValue = "1") String createdBy) {
        if (period == null) {
            LocalDate now = LocalDate.now();
            period = String.format("%04d%02d", now.getYear(), now.getMonthValue());
        }
        return R.ok(prepaymentService.createPaymentPrepay(
                vendorId, amount, period, txDate, summary, "MANUAL", null, null, null, createdBy));
    }

    @Operation(summary = "创建预收款 (客户预收款)")
    @PostMapping("/receipt")
    public R<PrepaymentEntity> createReceiptPrepay(
            @RequestParam Long customerId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate txDate,
            @RequestParam(required = false) String summary,
            @RequestParam(defaultValue = "1") String createdBy) {
        if (period == null) {
            LocalDate now = LocalDate.now();
            period = String.format("%04d%02d", now.getYear(), now.getMonthValue());
        }
        return R.ok(prepaymentService.createReceiptPrepay(
                customerId, amount, period, txDate, summary, "MANUAL", null, null, null, createdBy));
    }

    @Operation(summary = "查询供应商预付款")
    @GetMapping("/payment/list")
    public R<List<PrepaymentEntity>> listPaymentPrepay(@RequestParam(required = false) Long vendorId) {
        return R.ok(prepaymentService.listPaymentPrepay(vendorId));
    }

    @Operation(summary = "查询客户预收款")
    @GetMapping("/receipt/list")
    public R<List<PrepaymentEntity>> listReceiptPrepay(@RequestParam(required = false) Long customerId) {
        return R.ok(prepaymentService.listReceiptPrepay(customerId));
    }

    @Operation(summary = "分页查询供应商预付款")
    @GetMapping("/payment/page")
    public R<IPage<PrepaymentEntity>> pagePaymentPrepay(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(prepaymentService.pagePaymentPrepay(vendorId, current, size));
    }

    @Operation(summary = "分页查询客户预收款")
    @GetMapping("/receipt/page")
    public R<IPage<PrepaymentEntity>> pageReceiptPrepay(
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(prepaymentService.pageReceiptPrepay(customerId, current, size));
    }

    @Operation(summary = "获取预付/预收详情")
    @GetMapping("/{id}")
    public R<PrepaymentEntity> getById(@PathVariable Long id) {
        return R.ok(prepaymentService.getById(id));
    }

    @Operation(summary = "用预付款冲应付账款")
    @PostMapping("/settle/payable")
    public R<BigDecimal> settlePayable(
            @RequestParam Long prepaymentId,
            @RequestParam Long payableId,
            @RequestParam BigDecimal settleAmount,
            @RequestParam(required = false) String remark) {
        return R.ok(prepaymentService.settlePayable(prepaymentId, payableId, settleAmount, remark));
    }

    @Operation(summary = "用预收款冲应收账款")
    @PostMapping("/settle/receivable")
    public R<BigDecimal> settleReceivable(
            @RequestParam Long prepaymentId,
            @RequestParam Long receivableId,
            @RequestParam BigDecimal settleAmount,
            @RequestParam(required = false) String remark) {
        return R.ok(prepaymentService.settleReceivable(prepaymentId, receivableId, settleAmount, remark));
    }

    @Operation(summary = "反冲销")
    @PostMapping("/{id}/reverse")
    public R<Void> reverseSettle(@PathVariable Long id) {
        prepaymentService.reverseSettle(id);
        return R.ok();
    }

    @Operation(summary = "确认 (DRAFT -> CONFIRMED)")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        prepaymentService.confirm(id);
        return R.ok();
    }

    @Operation(summary = "取消 (CONFIRMED -> CANCELLED)")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        prepaymentService.cancel(id);
        return R.ok();
    }
}
