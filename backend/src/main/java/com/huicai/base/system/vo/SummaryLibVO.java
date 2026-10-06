package com.huicai.base.system.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.system.entity.SummaryLibEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SummaryLibVO 出参 VO（P102 出参面 DTO 化 **批次 4/9**）
 *
 * 摘要库出参。对应前端 `summaryLib.ts` 的 `interface SummaryLibVO`。
 *
 * <p><b>字段集与前端接口逐字段一致</b>，由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）、
 * 以及审计列 {@code createdBy}/{@code updatedBy}。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class SummaryLibVO {

    /** DB 列 `id` */
    private Long id;
    /** DB 列 `summary_code` */
    private String summaryCode;
    /** DB 列 `summary_text` */
    private String summaryText;
    /** DB 列 `category` */
    private String category;
    /** DB 列 `sort_order` */
    private Integer sortOrder;
    /** DB 列 `is_active` */
    private Boolean isActive;
    /** DB 列 `created_at` */
    private LocalDateTime createdAt;

    public static SummaryLibVO from(SummaryLibEntity e) {
        if (e == null) {
            return null;
        }
        SummaryLibVO vo = new SummaryLibVO();
        vo.setId(e.getId());
        vo.setSummaryCode(e.getSummaryCode());
        vo.setSummaryText(e.getSummaryText());
        vo.setCategory(e.getCategory());
        vo.setSortOrder(e.getSortOrder());
        vo.setIsActive(e.getIsActive());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<SummaryLibVO> from(List<SummaryLibEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(SummaryLibVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<SummaryLibVO> from(IPage<SummaryLibEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SummaryLibVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}