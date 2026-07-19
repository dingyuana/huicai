package com.huicai.base.voucher.controller;

import com.huicai.common.response.R;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.base.system.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "期末结账")
@RestController
@RequestMapping("/api/base/voucher/v1/period-close")
@RequiredArgsConstructor
public class PeriodCloseController {

    private final PeriodCloseService periodCloseService;

    @Operation(summary = "结账前检查")
    @GetMapping("/check")
    public R<Map<String, Object>> check(@RequestParam String period) {
        return R.ok(periodCloseService.checkBeforeClose(period));
    }

    @Operation(summary = "生成损益结转凭证")
    @PostMapping("/profit-carryover")
    public R<Long> profitCarryover(@RequestParam String period) {
        return R.ok(periodCloseService.generateProfitCarryOver(period, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "执行结账")
    @PostMapping("/close")
    public R<Void> close(@RequestParam String period) {
        periodCloseService.closePeriod(period, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "反结账")
    @PostMapping("/reopen")
    public R<Void> reopen(@RequestParam String period) {
        periodCloseService.reopenPeriod(period, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "结账日志")
    @GetMapping("/log")
    public R<List<Map<String, Object>>> log(@RequestParam String period) {
        return R.ok(periodCloseService.listCloseLog(period));
    }
}
