package com.huicai.module.arap.controller;

import com.huicai.common.response.R;
import com.huicai.module.arap.service.PaymentPlanService;
import com.huicai.module.arap.service.PaymentPlanService.PaymentPlanGroupVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "付款计划")
@RestController
@RequestMapping("/api/v1/payment-plans")
@RequiredArgsConstructor
public class PaymentPlanController {

    private final PaymentPlanService service;

    @Operation(summary = "生成付款计划")
    @GetMapping
    public R<List<PaymentPlanGroupVO>> generatePaymentPlan(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long vendorId) {
        return R.ok(service.generatePaymentPlan(period, vendorId));
    }
}