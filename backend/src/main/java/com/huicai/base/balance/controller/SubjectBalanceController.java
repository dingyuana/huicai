package com.huicai.base.balance.controller;

import com.huicai.common.response.R;
import com.huicai.base.balance.dto.SubjectBalanceVO;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Tag(name = "科目余额")
@RestController
@RequestMapping("/api/base/balance/v1/subject-balances")
@RequiredArgsConstructor
public class SubjectBalanceController {

    private final SubjectBalanceService subjectBalanceService;

    @Operation(summary = "期初建账（支持指定建账日期）")
    @PostMapping("/init")
    public R<Void> initOpening(@RequestParam String period,
                               @RequestParam(required = false) String openedAt,
                               @RequestBody Map<Long, BigDecimal> balances) {
        subjectBalanceService.initOpeningBalances(period, parseOpenedAt(openedAt), balances);
        return R.ok();
    }

    private LocalDateTime parseOpenedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignoredDateTime) {
                try {
                    return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException ignoredIsoDateTime) {
                    throw BusinessException.badRequest("建账日期格式错误, 应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
                }
            }
        }
    }

    @Operation(summary = "查询科目余额表")
    @GetMapping
    public R<List<SubjectBalanceVO>> listByPeriod(@RequestParam String period) {
        return R.ok(subjectBalanceService.queryByPeriodWithSubject(period));
    }

    @Operation(summary = "试算平衡检查")
    @GetMapping("/trial-balance")
    public R<Map<String, Object>> trialBalance(@RequestParam String period) {
        return R.ok(subjectBalanceService.checkTrialBalance(period));
    }

    @Operation(summary = "锁定期初（写入 t_period.opening_status=locked）")
    @PostMapping("/lock")
    public R<Void> lockOpening(@RequestParam String period) {
        subjectBalanceService.lockOpeningBalances(period);
        return R.ok();
    }

    @Operation(summary = "解锁期初（仅无已过账凭证时允许）")
    @PostMapping("/unlock")
    public R<Void> unlockOpening(@RequestParam String period) {
        subjectBalanceService.unlockOpeningBalances(period);
        return R.ok();
    }

    @Operation(summary = "清空期初余额（仅未锁定且无已过账凭证时允许）")
    @PostMapping("/clear")
    public R<Void> clearOpening(@RequestParam String period) {
        subjectBalanceService.clearOpeningBalances(period);
        return R.ok();
    }
}
