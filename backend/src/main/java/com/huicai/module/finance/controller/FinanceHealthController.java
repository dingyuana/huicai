package com.huicai.module.finance.controller;

import com.huicai.common.response.R;
import com.huicai.module.finance.dto.IntegrityCheckResult;
import com.huicai.module.finance.service.impl.FinanceIntegrityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 财务健康检查控制器
 * P32: 财务数据完整性与并发控制增强
 */
@Tag(name = "财务健康检查")
@RestController
@RequestMapping("/api/v1/finance/health")
@RequiredArgsConstructor
public class FinanceHealthController {

    private final FinanceIntegrityService integrityService;

    @Operation(summary = "数据完整性检查")
    @PostMapping("/integrity")
    public R<IntegrityCheckResult> checkIntegrity(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "ALL") String checks) {
        return R.ok(integrityService.checkAll(period));
    }
}
