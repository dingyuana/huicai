package com.huicai.sme.periodclose.controller;

import com.huicai.base.voucher.controller.PeriodCloseController;
import com.huicai.base.voucher.dto.BatchReviewPostRequest;
import com.huicai.base.voucher.dto.CarryoverStepResult;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.response.R;
import com.huicai.sme.periodclose.service.CarryoverSequenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * P84 结账工作台新端点（生成序列编排 + 批量审核记账）。
 *
 * <p>本控制器与 {@link PeriodCloseController} 共享基础路径，但归属
 * {@code sme.periodclose} 包：生成序列编排需同时依赖 {@code sme.asset}
 * （折旧）与 {@code base.voucher}（结转/分配），而 base 层须保持零依赖 sme
 * （全项目硬约束，实测无任何违反先例）。编排因此放在 sme 侧，Controller
 * 作为入口聚合两者；其余纯 base 端点仍留在 {@link PeriodCloseController}。
 *
 * @author Hermes
 */
@Tag(name = "期末结账工作台")
@RestController
@RequestMapping("/api/base/voucher/v1/period-close")
@RequiredArgsConstructor
public class CloseWorkbenchController {

    private final CarryoverSequenceService carryoverSequenceService;
    private final PeriodCloseService periodCloseService;

    @Operation(summary = "生成结转凭证序列（DEPR→CLOSE→DISTRIB，各自 DRAFT）")
    @GetMapping("/generate-sequence")
    public R<List<CarryoverStepResult>> generateSequence(@RequestParam String period) {
        return R.ok(carryoverSequenceService.generateSequence(period, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "一键人工审核记账（DRAFT→AUDITED→POSTED，同事务）")
    @PostMapping("/batch-review-post")
    public R<Void> batchReviewPost(@Valid @RequestBody BatchReviewPostRequest request) {
        periodCloseService.batchReviewPost(request.voucherIds(), SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
