package com.huicai.base.balance.controller;

import com.huicai.common.response.R;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.service.SubjectBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "科目余额")
@RestController
@RequestMapping("/api/v1/subject-balances")
@RequiredArgsConstructor
public class SubjectBalanceController {

    private final SubjectBalanceService subjectBalanceService;

    @Operation(summary = "期初建账")
    @PostMapping("/init")
    public R<Void> initOpening(@RequestParam String period, @RequestBody Map<Long, BigDecimal> balances) {
        subjectBalanceService.initOpeningBalances(period, balances);
        return R.ok();
    }

    @Operation(summary = "查询科目余额表")
    @GetMapping
    public R<List<SubjectBalanceEntity>> listByPeriod(@RequestParam String period) {
        return R.ok(subjectBalanceService.queryByPeriod(period));
    }

    @Operation(summary = "试算平衡检查")
    @GetMapping("/trial-balance")
    public R<Map<String, Object>> trialBalance(@RequestParam String period) {
        return R.ok(subjectBalanceService.checkTrialBalance(period));
    }
}
