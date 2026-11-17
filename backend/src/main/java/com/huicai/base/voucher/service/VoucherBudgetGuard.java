package com.huicai.base.voucher.service;

import com.huicai.base.voucher.dto.BudgetWarningDTO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;

import java.util.List;

/**
 * P142（REQ-2026-156，D-1~D-7 全 A 拍板）：凭证过账时的预算强制检查钩子。
 *
 * <h2>接口放在 base、实现在 sme 的架构裁定</h2>
 * 凭证过账点在 {@code base.voucher.VoucherServiceImpl}，而预算执行域在
 * {@code sme.budget}。代码依赖方向实测：{@code sme → base} 56 处先例、
 * {@code base → sme} 0 处 —— 基座不依赖业务层是既定架构纪律，不能为钩子破例。
 * 故：接口（含 DTO）定义在 {@code base.voucher}，Spring 实现类
 * {@code BudgetEnforcementServiceImpl} 位于 {@code sme.budget.service}，
 * 由 {@code @RequiredArgsConstructor} 字段注入在运行期完成装配。
 *
 * <h2>调用契约（VoucherServiceImpl 责任）</h2>
 * 两个入口都必须在凭证 {@code status → POSTED} 更新**之前**调用，方法须运行在
 * Spring 事务内（post/unpost/batchPost 均为 {@code @Transactional}）——行锁与
 * {@code used_amount} 回写依赖同一事务原子性（D-5A：行锁方案）。
 *
 * <ul>
 *   <li><b>enforcePost</b>：正常凭证借方coder聚合做预算三态检查；超 BLOCK 抛
 *       {@code BusinessException}（fail-loud，凭证不落 POSTED）；通过则回写
 *       {@code used_amount}；WARN/APPROVE↦REQUIRE_APPROVE 以 warnings 返回（D-7A）。</li>
 *   <li><b>releasePost</b>：反过账（POSTED → AUDITED）的对称回减——不回减会使
 *       post → unpost → post 双重计数（铁律 #1 定義一致性，SPEC 实施补遗）。</li>
 * </ul>
 *
 * <p><b>红冲凭证语义（D-4A，铁律 #3 对冲）</b>：红冲凭证是红字对冲而非新增支出，
 * 其 {@code reversedFrom} 非空 ⇒ {@code enforcePost} 实现内走「回减原凭证对应
 * 科目使用额」分支，不对红冲凭证自身借贷做预算检查。
 */
public interface VoucherBudgetGuard {

    /**
     * 过账前预算检查 + 使用额回写。
     *
     * @param voucher 即将过账的凭证（含 period / reversedFrom）
     * @param entries 该凭证全部分录（调用方已查出，避免二次 SQL）
     * @return 非阻断提示（WARN / REQUIRE_APPROVE）；BLOCK 抛 BusinessException
     */
    List<BudgetWarningDTO> enforcePost(VoucherEntity voucher, List<VoucherEntryEntity> entries);

    /**
     * 反过账的预算使用额对称回减（{@code used_amount -= 借方聚合}）。
     * 无预算配置的科目静默跳过（与 NONE 语义一致）。
     */
    void releasePost(VoucherEntity voucher, List<VoucherEntryEntity> entries);
}
