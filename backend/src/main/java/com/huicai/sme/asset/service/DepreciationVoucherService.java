package com.huicai.sme.asset.service;

import com.huicai.sme.asset.dto.DepreciationVoucherResult;

/**
 * 折旧自动制证服务（P85-C）.
 *
 * <p>按期间把<b>已计提</b>的折旧记录（t_asset_depreciation）制为一张 DEPR DRAFT 凭证。
 * 与"计提"严格分离——本服务只读 t_asset_depreciation 的既有结果，
 * 绝不自行计算折旧金额（计提走 AssetCardService.depreciatePeriod）。
 *
 * <p>分录口径（现行科目表实测存在，无资产分类→费用科目映射，P85 先固定）：
 * <pre>
 *   借 6602 管理费用    <sum> 本期折旧合计
 *   贷 1602 累计折旧    <sum> 本期折旧合计
 * </pre>
 *
 * <p>幂等键 {@code DEPR-{period}}：凭证号由本方法统一赋值为 {@code DEPR-{period}}，
 * 重复调用直接报错（守铁律#1——生成后由人工审核，不自动重试）。
 *
 * <p>依赖方向：sme.asset → base.voucher 是项目既有单向方向（sme/cash、sme/arap、
 * sme/tax 均已如此）；反向（base.voucher 依赖 sme.asset）项目内不存在，不可新增。
 * 因此 P84 的 generate-sequence 编排落在 {@code sme.periodclose}（编排层需同时依赖
 * sme.asset 与 base.voucher），详见 docs/specs/P84-close-workbench.md §1.2。
 */
public interface DepreciationVoucherService {

    /**
     * 按期间生成 DEPR 折旧凭证（DRAFT，需人工审核）。
     *
     * @param period 期间 YYYYMM（必填，须在 t_period 中且状态 open）
     * @param userId 操作者ID
     * @return 制证结果（凭证ID/凭证号/借贷金额/明细行数）
     * @throws com.huicai.common.exception.BusinessException
     *         期间未配置/已结账、该期间已有 DEPR 凭证、当期无折旧数据、
     *         科目 6602/1602 未配置、折旧数据异常（金额<=0）
     */
    DepreciationVoucherResult generate(String period, Long userId);
}
