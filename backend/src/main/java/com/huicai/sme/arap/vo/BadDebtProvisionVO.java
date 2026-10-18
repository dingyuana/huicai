package com.huicai.sme.arap.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 坏账准备出参 VO（批次 15）
 *
 * <p><b>⚠️ 前端 BadDebtList.vue 读了 5 个后端不存在的字段</b>（AGENTS §4.5 第 39 条形态）：
 * {@code expectedBalance / existingBalance / adjustmentAmount / adjustmentType / voucherNo}
 * 这 5 个在 {@link BadDebtProvisionEntity} 里都标了 {@code @TableField(exist = false)}，
 * DB 侧 {@code t_bad_debt_provision} 也<b>没有这些列</b>（已通过
 * {@code information_schema.columns} 核对）。
 *
 * <p>页面读它们的结果是：坏账准备列表的「应有余额/已有余额/调整金额/调整类型/凭证号」
 * 列<b>永远显示空白</b>（实际是 null）。这不是契约陈旧，而是<b>功能未实现</b>——
 * 页面已经把 UI 搭好了，但后端计算逻辑和落库列都还没做。
 *
 * <p>本 VO <b>只暴露真实 DB 列</b>（与 Entity 对齐，去掉幽灵字段），
 * 并把 5 个未实现的字段显式注释在 VO 头部，避免后人误以为「已经返回了」。
 * 如果要让那 5 列真正有数据，需要另外立项补 DDL + 计算逻辑（预计是 P1 工作）。
 *
 * <p><b>字段集</b>：id / period / method / provisionDate / totalAmount / status / remark / createdAt
 * （ BaseEntity 自带 id/enterpriseId/createdBy/createdAt/updatedAt/deleted/version）。
 *
 * <p><b>刻意不外露</b>：{@code deleted}/{@code enterpriseId}（内部状态与租户维度）、
 * {@code updatedBy}/{@code version}（乐观锁）、{@code createdBy}（审计列，前端不渲染）。
 */
@Data
public class BadDebtProvisionVO {

    private Long id;
    private String period;
    private String method;
    private LocalDate provisionDate;
    private BigDecimal totalAmount;
    /** 状态：DRAFT / CONFIRMED / VOUCHERED */
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    // ── 下面 5 个是前端已读但后端尚未实现的字段（AGENTS §4.5 第 39 条）──
    // ⚠️ expectedBalance  — 应有余额（按账龄计算的总坏账准备应有金额），待 DDL + 计算逻辑
    // ⚠️ existingBalance  — 科目已有余额（科目 1231 当前余额），待 DDL + 计算逻辑
    // ⚠️ adjustmentAmount — 补提/冲回金额 = expectedBalance - existingBalance，待实现
    // ⚠️ adjustmentType   — 调整类型：PROVISION-补提 / REVERSAL-冲回，待实现
    // ⚠️ voucherNo        — 凭证编号，DB 无此列；凭证号需通过 voucher_id JOIN 查询
    //
    // 上述 5 字段的完整实现需要：① t_bad_debt_provision 加列或单独关联表 ② 计算逻辑
    // ③ 前端改为从后端接口读取。本轮只做 VO 化，不填补功能缺口。

    public static BadDebtProvisionVO from(BadDebtProvisionEntity e) {
        if (e == null) {
            return null;
        }
        BadDebtProvisionVO vo = new BadDebtProvisionVO();
        vo.setId(e.getId());
        vo.setPeriod(e.getPeriod());
        vo.setMethod(e.getMethod());
        vo.setProvisionDate(e.getProvisionDate());
        vo.setTotalAmount(e.getTotalAmount());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<BadDebtProvisionVO> from(List<BadDebtProvisionEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(BadDebtProvisionVO::from).collect(java.util.stream.Collectors.toList());
    }

    public static IPage<BadDebtProvisionVO> from(IPage<BadDebtProvisionEntity> page) {
        if (page == null) {
            return null;
        }
        Page<BadDebtProvisionVO> out = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}