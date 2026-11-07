package com.huicai.sme.asset.service;

import com.huicai.sme.asset.dto.AssetChangeCreateDTO;
import com.huicai.sme.asset.vo.AssetChangeVO;

import java.util.List;

/**
 * SPEC-P144（REQ-2026-161 / roadmap P0-2 D-4）：资产变动（调整）服务。
 *
 * <p>roadmap 裁定：资产变动生成调整凭证，<b>不修改原折旧记录</b> ——
 * 本服务只动卡片主数据（originalValue/deptId）与变动流水表，
 * {@code t_asset_depreciation} 历史计提记录原样保留。</p>
 */
public interface AssetChangeService {

    /**
     * 登记资产变动；VALUE_ADJUST 同步生成调整凭证并回填 voucherId。
     *
     * @return 变动记录 ID
     */
    Long create(AssetChangeCreateDTO dto, Long userId);

    /** 某资产的变动流水（含凭证号回显），按变动日期倒序。 */
    List<AssetChangeVO> listByAsset(Long assetId);
}
