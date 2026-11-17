package com.huicai.sme.periodclose.service.impl;

import com.huicai.base.voucher.dto.CarryoverStepResult;
import com.huicai.base.voucher.dto.SequenceVoucherVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.transaction.RequiresNewRunner;
import com.huicai.sme.asset.dto.DepreciationVoucherResult;
import com.huicai.sme.asset.service.DepreciationVoucherService;
import com.huicai.sme.periodclose.service.CarryoverSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 结账结转序列编排实现（P84 结账工作台第 1 步）。
 *
 * <p>三步顺序固定：DEPR 折旧 → CLOSE 损益结转 → DISTRIB 利润分配。
 * <b>单步失败不中断</b>——catch 后记为 SKIPPED/FAILED 并继续，保证返回结果
 * 始终包含全部三步，让操作员看到完整序列状态而非中断在半路。
 *
 * <p><b>事务形态（SPEC-P137，2026-10-20）</b>：类级事务仅满足 RLS 守卫
 * （本类读 {@code t_voucher}/{@code t_voucher_entry} 租户表，无事务 ⇒ GUC 恒 NULL），
 * 而<b>三个步各自经 {@link RequiresNewRunner} 在 REQUIRES_NEW 独立事务执行</b>——
 * 单步抛 {@code BusinessException} 只回滚该步自己的事务，经 {@code runStep} 捕获转
 * SKIPPED 后<b>不污染外层事务</b>。若三步改为直接在内层调用（REQUIRED 加入外层），
 * 内层异常会把共享事务标记 rollback-only，外层提交必抛
 * {@code UnexpectedRollbackException}（2026-10-20 generate-sequence 500 实证，
 * 回归锁 {@code CarryoverSequenceTxIsolationRealDBTest}）。
 *
 * <p>依赖方向：sme.periodclose → base.voucher + sme.asset + base.system，
 * 均为 sme→base 单向合法方向，base 层保持零依赖 sme。
 *
 * @author Hermes
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 🔴 类级事务（RLS 守卫 RlsTransactionStructureTest）：本类读 t_voucher / t_voucher_entry，
//   均为 RLS 租户表。无事务 ⇒ TenantGucSetter#apply 提前 return ⇒ GUC 恒 NULL
//   ⇒ 谓词取 NULL ⇒ 结转查询与凭证视图恒返 0 行。
@Transactional(rollbackFor = Exception.class)
public class CarryoverSequenceServiceImpl implements CarryoverSequenceService {

    private final DepreciationVoucherService depreciationVoucherService;
    private final PeriodCloseService periodCloseService;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherTypeService voucherTypeService;
    /** SPEC-P137：单步独立事务执行器（REQUIRES_NEW）。 */
    private final RequiresNewRunner stepTxRunner;

    private static final String STEP_DEPR = "DEPR";
    private static final String STEP_CLOSE = "CLOSE";
    private static final String STEP_DISTRIB = "DISTRIB";

    @Override
    public List<CarryoverStepResult> generateSequence(String period, Long userId) {
        List<CarryoverStepResult> results = new ArrayList<>(3);

        results.add(runStep(STEP_DEPR, "折旧凭证",
                () -> stepDepreciation(period, userId), "当期无折旧计提数据"));

        results.add(runStep(STEP_CLOSE, "损益结转凭证",
                () -> stepCarryOver(period, userId), "当期无可结转的损益数据"));

        results.add(runStep(STEP_DISTRIB, "利润分配凭证",
                () -> stepDistribution(period, userId), "当期无待分配的利润"));

        return results;
    }

    /** DEPR 步：调折旧制证服务，返回生成结果。 */
    private CarryoverStepResult stepDepreciation(String period, Long userId) {
        DepreciationVoucherResult r = depreciationVoucherService.generate(period, userId);
        return generated(STEP_DEPR, "折旧凭证", r.voucherId());
    }

    /** CLOSE 步：调损益结转服务，返回生成结果。 */
    private CarryoverStepResult stepCarryOver(String period, Long userId) {
        Long id = periodCloseService.generateProfitCarryOver(period, userId);
        return generated(STEP_CLOSE, "损益结转凭证", id);
    }

    /** DISTRIB 步：调利润分配服务，返回生成结果。 */
    private CarryoverStepResult stepDistribution(String period, Long userId) {
        Long id = periodCloseService.generateProfitDistribution(period, userId);
        return generated(STEP_DISTRIB, "利润分配凭证", id);
    }

    /** 组装单步 GENERATED 结果（含卡片视图）。 */
    private CarryoverStepResult generated(String step, String stepName, Long voucherId) {
        return CarryoverStepResult.generated(step, stepName, voucherId, List.of(toVO(voucherId)));
    }

    /**
     * 执行单步并兜底异常：成功→GENERATED，业务异常（无数据/幂等拦截）→SKIPPED，
     * 其他异常→FAILED。保证序列不会因单步中断。
     *
     * @param defaultSkipReason 业务异常消息为空时使用的兜底跳过原因
     */
    private CarryoverStepResult runStep(String step, String stepName,
                                        StepExecutor executor, String defaultSkipReason) {
        try {
            // SPEC-P137：REQUIRES_NEW 独立事务执行。步内异常只回滚该步自己的事务，
            // 不把外层事务标记 rollback-only（否则 runStep 吞异常后外层提交必炸）。
            return stepTxRunner.run(executor::execute);
        } catch (BusinessException e) {
            // 无数据 / 幂等拦截（已存在该期间凭证）：属正常业务结果，记 SKIPPED
            String reason = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage() : defaultSkipReason;
            log.info("结转序列单步跳过: step={} reason={}", step, reason);
            return CarryoverStepResult.skipped(step, stepName, reason);
        } catch (Exception e) {
            log.error("结转序列单步失败: step={}", step, e);
            return CarryoverStepResult.failed(step, stepName, e.getMessage());
        }
    }

    /** 单步执行器：成功时返回该步 GENERATED 结果。 */
    @FunctionalInterface
    private interface StepExecutor {
        CarryoverStepResult execute();
    }

    /**
     * 组装序列凭证卡片视图。
     *
     * <p>分录行数用 {@code voucherEntryMapper} 查询计数而非 {@code getDetail()}，
     * 避免为一张卡片做完整详情装配。凭证类型名容错：类型不存在时留空不中断。
     */
    private SequenceVoucherVO toVO(Long voucherId) {
        VoucherEntity e = voucherMapper.selectById(voucherId);
        if (e == null) {
            return null;
        }
        String typeName = null;
        try {
            VoucherTypeEntity type = e.getVoucherTypeId() != null
                    ? voucherTypeService.getById(e.getVoucherTypeId()) : null;
            if (type != null) {
                typeName = type.getName();
            }
        } catch (IllegalArgumentException ex) {
            log.debug("凭证类型不存在, 类型名留空: voucherId={} typeId={}",
                    voucherId, e.getVoucherTypeId());
        }
        int entryCount = voucherEntryMapper.selectByVoucherId(voucherId).size();
        return new SequenceVoucherVO(e.getId(), e.getVoucherNo(), typeName,
                e.getTotalDebit(), e.getTotalCredit(), entryCount, e.getStatus(), e.getCreatedAt());
    }

    @Override
    public SequenceVoucherVO getVoucherView(Long voucherId) {
        return toVO(voucherId);
    }
}
