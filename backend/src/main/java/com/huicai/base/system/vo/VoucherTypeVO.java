package com.huicai.base.system.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.system.entity.VoucherTypeEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * VoucherTypeVO 出参 VO（P102 出参面 DTO 化 **批次 4/9**）
 *
 * 凭证类型出参。对应前端 `voucherType.ts` 的 `interface VoucherTypeVO`。
 *
 * <p><b>字段集与前端接口逐字段一致</b>，由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）、
 * 以及审计列 {@code createdBy}/{@code updatedBy}。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class VoucherTypeVO {

    /** DB 列 `id` */
    private Long id;
    /** DB 列 `code` */
    private String code;
    /** DB 列 `name` */
    private String name;
    /** DB 列 `sort_order` */
    private Integer sortOrder;
    /** DB 列 `numbering_rule` */
    private String numberingRule;
    /** DB 列 `is_active` */
    private Boolean isActive;
    /** DB 列 `remark` */
    private String remark;
    /** DB 列 `created_at` */
    private LocalDateTime createdAt;

    public static VoucherTypeVO from(VoucherTypeEntity e) {
        if (e == null) {
            return null;
        }
        VoucherTypeVO vo = new VoucherTypeVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setSortOrder(e.getSortOrder());
        vo.setNumberingRule(e.getNumberingRule());
        vo.setIsActive(e.getIsActive());
        vo.setRemark(e.getRemark());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<VoucherTypeVO> from(List<VoucherTypeEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(VoucherTypeVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<VoucherTypeVO> from(IPage<VoucherTypeEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VoucherTypeVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}