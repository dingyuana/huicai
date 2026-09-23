package com.huicai.base.voucher.dto;

import java.util.List;

/**
 * 结账序列生成步骤结果。
 *
 * <p>P84 结账工作台第 1 步"生成结转凭证"的编排结果单元：单步可成功生成、
 * 可无数据跳过、也可失败。编排器<b>不因单步失败中断</b>，而是把每步结果
 * 汇总返回，让操作员看到完整序列状态（有折旧凭证却因无损益而整体中断的
 * 体验不可接受）。
 *
 * @param step      步骤标识：DEPR / PROFIT_CARRY_OVER / PROFIT_DISTRIBUTION
 * @param stepName  步骤中文名
 * @param voucherId 成功时新建凭证 id；跳过/失败为 null
 * @param status    GENERATED（已生成）/ SKIPPED（无数据跳过）/ FAILED（异常）
 * @param reason    跳过或失败原因（成功为 null）
 * @param vouchers  本步生成的凭证清单
 * @author Hermes
 */
public record CarryoverStepResult(
        String step,
        String stepName,
        Long voucherId,
        String status,
        String reason,
        List<SequenceVoucherVO> vouchers
) {

    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";

    public static CarryoverStepResult generated(String step, String stepName,
                                                Long voucherId, List<SequenceVoucherVO> vouchers) {
        return new CarryoverStepResult(step, stepName, voucherId, STATUS_GENERATED, null, vouchers);
    }

    public static CarryoverStepResult skipped(String step, String stepName, String reason) {
        return new CarryoverStepResult(step, stepName, null, STATUS_SKIPPED, reason, List.of());
    }

    public static CarryoverStepResult failed(String step, String stepName, String reason) {
        return new CarryoverStepResult(step, stepName, null, STATUS_FAILED, reason, List.of());
    }
}
