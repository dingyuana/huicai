package com.huicai.sme.arap.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.sme.arap.entity.CustomerStatementEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户对账单出参 VO（批次 15）
 *
 * <p><b>⚠️ 前端 CustomerStatementList.vue 读了 5 个后端不存在的字段</b>（AGENTS §4.5 第 39 条形态）：
 * {@code customerName / originalAmount / outstandingAmount / writtenOffAmount}
 * — 其中 {@code customerName} 在 Entity 里是 {@code @TableField(exist = false)} 幽灵字段，
 * {@code originalAmount / outstandingAmount / writtenOffAmount} 在 DB 和 Entity 里<b>都不存在</b>。
 *
 * <p>页面展示结果：「客户名称/原金额/未结金额/已核销金额」列<b>永远空白</b>。
 * 这是<b>功能未实现</b>（页面已搭好但后端未提供）而非契约陈旧。
 *
 * <p>本 VO <b>只暴露真实 DB 列</b>，并把 4 个未实现字段显式注释在 VO 头部。
 *
 * <p><b>字段集</b>：id / statementNo / period / openingBalance / closingBalance /
 * status / remark / sentAt / createdAt（BaseEntity 自带 id/enterpriseId/createdBy/
 * createdAt/updatedAt/deleted/version）。
 *
 * <p><b>刻意不外露</b>：{@code deleted}/{@code enterpriseId}（内部状态与租户维度）、
 * {@code updatedBy}/{@code version}（乐观锁）、{@code createdBy}（审计列，前端不渲染）、
 * {@code customerName}/{@code statementDate}/{@code totalOriginal}/{@code totalSettled}/
 * {@code totalUnsettled}/{@code confirmedAt}（幽灵字段或计算字段）。
 */
@Data
public class CustomerStatementVO {

    private Long id;
    private String statementNo;
    private String period;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    /** DRAFT / SENT / CONFIRMED / CANCELLED */
    private String status;
    private String remark;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    // ── 下面 4 个是前端已读但后端尚未实现的字段（AGENTS §4.5 第 39 条）──
    // ⚠️ customerName     — Entity 里是 @TableField(exist = false)，DB 无此列；
    //                      需 JOIN t_customer 才能在 VO 里带出
    // ⚠️ originalAmount   — DB 无此列，应为 openingBalance 的别名？待产品确认
    // ⚠️ outstandingAmount— DB 无此列，应为 closingBalance 的别名？待产品确认
    // ⚠️ writtenOffAmount — DB 无此列，需从 t_arap_settlement 聚合计算
    //
    // 完整实现需要：① 联表查询或新增聚合列 ② 前端字段名对齐。本轮只做 VO 化。

    public static CustomerStatementVO from(CustomerStatementEntity e) {
        if (e == null) {
            return null;
        }
        CustomerStatementVO vo = new CustomerStatementVO();
        vo.setId(e.getId());
        vo.setStatementNo(e.getStatementNo());
        vo.setPeriod(e.getPeriod());
        vo.setOpeningBalance(e.getOpeningBalance());
        vo.setClosingBalance(e.getClosingBalance());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        vo.setSentAt(e.getSentAt());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<CustomerStatementVO> from(List<CustomerStatementEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(CustomerStatementVO::from).collect(java.util.stream.Collectors.toList());
    }

    public static IPage<CustomerStatementVO> from(IPage<CustomerStatementEntity> page) {
        if (page == null) {
            return null;
        }
        Page<CustomerStatementVO> out = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}