package com.huicai.sme.asset.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AssetCategoryVO 出参 VO（P102 出参面 DTO 化 **批次 4/9**）
 *
 * 资产分类出参。对应前端 `asset.ts` 的 `interface AssetCategory`。
 *
 * <p><b>字段集与前端接口逐字段一致</b>，由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）、
 * 以及审计列 {@code createdBy}/{@code updatedBy}。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class AssetCategoryVO {

    /** DB 列 `id` */
    private Long id;
    /** DB 列 `code` */
    private String code;
    /** DB 列 `name` */
    private String name;
    /** DB 列 `parent_id` */
    private Long parentId;
    /** DB 列 `level` */
    private Integer level;
    /** DB 列 `depreciation_method` */
    private String depreciationMethod;
    /** DB 列 `useful_life` */
    private Integer usefulLife;
    /** DB 列 `residual_rate` */
    private BigDecimal residualRate;
    /** DB 列 `asset_subject_id` */
    private Long assetSubjectId;
    /** DB 列 `depreciation_subject_id` */
    private Long depreciationSubjectId;
    /** DB 列 `expense_subject_id` */
    private Long expenseSubjectId;
    /** DB 列 `remark` */
    private String remark;

    public static AssetCategoryVO from(AssetCategoryEntity e) {
        if (e == null) {
            return null;
        }
        AssetCategoryVO vo = new AssetCategoryVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setParentId(e.getParentId());
        vo.setLevel(e.getLevel());
        vo.setDepreciationMethod(e.getDepreciationMethod());
        vo.setUsefulLife(e.getUsefulLife());
        vo.setResidualRate(e.getResidualRate());
        vo.setAssetSubjectId(e.getAssetSubjectId());
        vo.setDepreciationSubjectId(e.getDepreciationSubjectId());
        vo.setExpenseSubjectId(e.getExpenseSubjectId());
        vo.setRemark(e.getRemark());
        return vo;
    }

    public static List<AssetCategoryVO> from(List<AssetCategoryEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(AssetCategoryVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AssetCategoryVO> from(IPage<AssetCategoryEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AssetCategoryVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}