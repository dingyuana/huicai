package com.huicai.sme.arap.controller;

import com.huicai.common.response.R;
import com.huicai.sme.arap.service.ArapBalanceReportService;
import com.huicai.sme.arap.service.ArapBalanceReportService.ArapBalanceSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应收应付余额汇总")
@RestController
@RequestMapping("/api/sme/arap/v1/report/balances")
@RequiredArgsConstructor
public class ArapBalanceReportController {

    private final ArapBalanceReportService service;

    @Operation(summary = "应收应付余额汇总（按往来单位）")
    @GetMapping
    public R<ArapBalanceSummaryVO> getBalanceSummary(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long vendorId) {
        return R.ok(service.getBalanceSummary(period, customerId, vendorId));
    }
}