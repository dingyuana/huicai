package com.huicai.sme.asset.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.asset.entity.AssetCardEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * AssetCardVO —— 由 t_asset_card 的真实列生成（批次 5）。
 *
 * <p>字段集 = 前端 asset.ts#AssetCard 已声明的 18 个字段，逐个核对：
 * ① 前端要而 DB 无列 → 直接停（本批已确保无）；② 前端要而 Entity 无字段 → 直接停。
 * 故本 VO 既不会漏前端字段，也不会凭空暴露前端不用的列。
 *
 * <p>刻意不外露：created_at, last_depreciation_period, updated_by, voucher_id
 */
@Data
public class AssetCardVO {

    private Long id;
    private String assetCode;
    private String assetName;
    private Long categoryId;
    private String spec;
    private Long deptId;
    private Long custodianId;
    private LocalDate acquisitionDate;
    private BigDecimal originalValue;
    private BigDecimal residualValue;
    private Integer usefulLife;
    private String depreciationMethod;
    private String status;
    private String location;
    private String serialNo;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private String remark;

    public static AssetCardVO from(AssetCardEntity e) {
        if (e == null) {
            return null;
        }
        AssetCardVO vo = new AssetCardVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getAssetCode() != null) vo.setAssetCode(e.getAssetCode());
        if (e.getAssetName() != null) vo.setAssetName(e.getAssetName());
        if (e.getCategoryId() != null) vo.setCategoryId(e.getCategoryId());
        if (e.getSpec() != null) vo.setSpec(e.getSpec());
        if (e.getDeptId() != null) vo.setDeptId(e.getDeptId());
        if (e.getCustodianId() != null) vo.setCustodianId(e.getCustodianId());
        if (e.getAcquisitionDate() != null) vo.setAcquisitionDate(e.getAcquisitionDate());
        if (e.getOriginalValue() != null) vo.setOriginalValue(e.getOriginalValue());
        if (e.getResidualValue() != null) vo.setResidualValue(e.getResidualValue());
        if (e.getUsefulLife() != null) vo.setUsefulLife(e.getUsefulLife());
        if (e.getDepreciationMethod() != null) vo.setDepreciationMethod(e.getDepreciationMethod());
        if (e.getStatus() != null) vo.setStatus(e.getStatus());
        if (e.getLocation() != null) vo.setLocation(e.getLocation());
        if (e.getSerialNo() != null) vo.setSerialNo(e.getSerialNo());
        if (e.getAccumulatedDepreciation() != null) vo.setAccumulatedDepreciation(e.getAccumulatedDepreciation());
        if (e.getNetValue() != null) vo.setNetValue(e.getNetValue());
        if (e.getRemark() != null) vo.setRemark(e.getRemark());
        return vo;
    }

    public static List<AssetCardVO> from(List<AssetCardEntity> list) {
        return list == null ? null : list.stream().map(AssetCardVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AssetCardVO> from(IPage<AssetCardEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AssetCardVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
