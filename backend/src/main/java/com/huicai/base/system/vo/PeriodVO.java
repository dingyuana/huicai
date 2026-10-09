package com.huicai.base.system.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.system.entity.PeriodEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PeriodVO 出参 VO（P102 出参面 DTO 化 **批次 4/9**）
 *
 * 期间出参。对应前端 `period.ts` 的 `interface PeriodVO`。
 *
 * <p><b>字段集与前端接口逐字段一致</b>，由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）、
 * 以及审计列 {@code createdBy}/{@code updatedBy}。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class PeriodVO {

    /** DB 列 `id` */
    private Long id;
    /** DB 列 `year` */
    private Integer year;
    /** DB 列 `month` */
    private Integer month;
    /** DB 列 `period_code` */
    private String periodCode;
    /** DB 列 `start_date` */
    private LocalDate startDate;
    /** DB 列 `end_date` */
    private LocalDate endDate;
    /** DB 列 `status` */
    private String status;
    /** DB 列 `opening_status` */
    private String openingStatus;
    /** DB 列 `created_at` */
    private LocalDateTime createdAt;
    /** DB 列 `opened_at` */
    private LocalDateTime openedAt;
    /** DB 列 `opened_by_name` */
    private String openedByName;

    public static PeriodVO from(PeriodEntity e) {
        if (e == null) {
            return null;
        }
        PeriodVO vo = new PeriodVO();
        vo.setId(e.getId());
        vo.setYear(e.getYear());
        vo.setMonth(e.getMonth());
        vo.setPeriodCode(e.getPeriodCode());
        vo.setStartDate(e.getStartDate());
        vo.setEndDate(e.getEndDate());
        vo.setStatus(e.getStatus());
        vo.setOpeningStatus(e.getOpeningStatus());
        vo.setCreatedAt(e.getCreatedAt());
        vo.setOpenedAt(e.getOpenedAt());
        vo.setOpenedByName(e.getOpenedByName());
        return vo;
    }

    public static List<PeriodVO> from(List<PeriodEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(PeriodVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<PeriodVO> from(IPage<PeriodEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PeriodVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}