package com.huicai.sme.periodclose.service;

import com.huicai.base.voucher.dto.CarryoverStepResult;
import com.huicai.base.voucher.dto.SequenceVoucherVO;

import java.util.List;

/**
 * 结账结转序列编排（P84 结账工作台第 1 步）。
 *
 * <p>编排三步结转：<b>DEPR 折旧</b>（sme.asset）→ <b>CLOSE 损益结转</b>
 * （base.voucher）→ <b>DISTRIB 利润分配</b>（base.voucher），返回每步结果供
 * Drawer 凭证卡片展示。
 *
 * <h3>为什么落在 {@code sme.periodclose} 而非 {@code base.voucher}</h3>
 * 项目硬约束：<b>base 层零依赖 sme.*</b>（grep 实测 0 命中，无任何违反先例）。
 * 而本编排需同时调用 {@code sme.asset.DepreciationVoucherService} 与
 * {@code base.voucher.PeriodCloseService}——放 base 的任何层都会产生反向依赖。
 * {@code sme} 依赖 {@code base.voucher} 有既有先例（sme/tax、sme/cash 的
 * voucher entry 生成），故编排归属 {@code sme.periodclose}，依赖方向为
 * sme → base + sme，单向合法。P85 SPEC 的"Controller 层编排"为最初设想，
 * 实际实现下移到 Service 层（Controller 仅做参数提取，可单测，符合项目约定）。
 *
 * <h3>部分成功语义</h3>
 * 单步抛异常<b>不中断</b>整个序列，而是记录为 SKIPPED/FAILED 后继续——结账序列
 * 要"尽力而为"，让操作员看到完整状态（有折旧凭证却因无损益而整体中断的体验
 * 不可接受）。无幂等保护的失败步骤建议由用户重试，已生成步骤因幂等键拦截不会重复建单。
 *
 * <h3>人工审核铁律</h3>
 * 本方法只生成 {@code DRAFT} 凭证，<b>绝不自动审核/过账</b>；审核记账由
 * 用户主动点击触发 {@code PeriodCloseService#batchReviewPost}。
 *
 * @author Hermes
 */
public interface CarryoverSequenceService {

    /**
     * 生成指定期间的三步结转凭证序列（各自 DRAFT，不自动审核）。
     *
     * @param period 期间标识，如 {@code 202609}
     * @param userId 操作人 id
     * @return 三步结果列表（顺序固定 DEPR → CLOSE → DISTRIB，每步必有一条）
     */
    List<CarryoverStepResult> generateSequence(String period, Long userId);

    /**
     * 按凭证 id 查询序列凭证的展示信息（含分录行数与类型名）。
     *
     * @param voucherId 凭证 id
     * @return 卡片视图；凭证不存在时返回 {@code null}
     */
    SequenceVoucherVO getVoucherView(Long voucherId);
}
