package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销异常记录 — 存储自动核销失败/需要人工介入的异常.
 *
 * <p><b>P109 / REQ-2026-138 —— 本 Entity 曾与表完全脱节，2026-10-06 修复</b>
 *
 * <p><b>原缺陷（真库实测，取证载体 {@code ReconciliationExceptionEntityDbProbeTest}）</b>：
 * <ul>
 *   <li>{@code createException()} <b>写入必挂</b>：
 *       {@code ERROR: null value in column "account_id" ... violates not-null constraint}
 *       —— {@code account_id} 是 NOT NULL + FK，而 Entity 连该字段都没有；</li>
 *   <li>{@code retryException()} <b>对任何记录都抛</b>「异常记录缺少目标单据信息」
 *       —— 它读的 {@code targetDocType}/{@code targetDocId} 当时标着
 *       {@code @TableField(exist=false)}，读回恒 null；</li>
 *   <li>表 {@code count(*) = 0} ⇒ 缺陷从未被真实写入触发过。</li>
 * </ul>
 *
 * <p><b>根因（AGENTS §4.5 第 5 条同型）</b>：本 Entity 被整体重写成「往来单位-centric」，
 * 但<b>配套的 migration 从未写过</b> —— V1 baseline 建的是「银行账户-centric」
 * （{@code account_id} / {@code description} / {@code period}），两者从未对齐。
 * ⇒ 那 15 处 {@code exist = false} 是<b>症状不是病因</b>；只删注解会把报错从
 * 「撞 NOT NULL」变成「Unknown column」，问题原地不动。
 *
 * <p><b>修法</b>（老丁裁定 D-109-1 = 表迁就 Entity）：{@code V169} 补齐 13 列，
 * 本类删掉 12 处失效标注；{@code account_id} / {@code period} 降为可空（非破坏性，
 * 保留列以便回溯银行账户视角）。
 *
 * <p><b>顺带修掉一处「字段遮蔽」造成的反向缺口</b>：本类原先<b>重复声明</b>
 * {@code createdAt} / {@code updatedAt} / {@code createdBy}，遮蔽了 {@link BaseEntity}
 * 里的同名字段。其中遮蔽掉的是 {@code updatedAt}（BaseEntity 侧是
 * {@code fill = INSERT_UPDATE}，而 {@code V127:34} 已给本表加了真实列
 * {@code updated_at}）⇒ 表现为<b>真实列永不写入、读回恒 null</b>
 * （AGENTS §4.2 第 16 条的反向缺口）。现<b>删掉本类的重复声明</b>，
 * 改由 BaseEntity 提供可写入的 {@code updatedAt}。
 * ⚠️ {@code createdBy} 是例外：{@code V169} 补了真实列，故本类<b>保留</b>声明但
 * <b>去掉 {@code exist=false}</b>，让 {@code Service} 里既有的
 * {@code ex.setCreatedBy(...)} 真正落库（否则那行赋值是误导性死代码）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reconciliation_exception")
public class ReconciliationExceptionEntity extends BaseEntity {

    /**
     * 🔴 <b>真幽灵字段，不要删也不要赋值</b>：{@code t_reconciliation_exception}
     * <b>没有 {@code tenant_id} 列</b>（真实库 {@code information_schema} 已核对）。
     * P106 批次 1a-2 已删除 {@code ReconciliationServiceImpl} 里对它的赋值 ——
     * 修法是<b>删代码而不是改列</b>：改成 {@code setEnterpriseId} 会给一个不存在的
     * 租户列加值语义，并掩盖 Entity 层缺陷。
     */
    @TableField(exist = false)
    private Long tenantId;

    /** 来源单据类型: receipt / payment / bank_txn / BUSINESS_DOC / INVOICE */
    private String sourceDocType;

    /** 来源单据 ID */
    private Long sourceDocId;

    /** 目标单据类型: INVOICE_OUT / INVOICE_IN（可能为空）；retryException 依赖它，非空才可重试 */
    private String targetDocType;

    /** 目标单据 ID（可能为空）；retryException 依赖它 */
    private Long targetDocId;

    /** 客户/供应商 ID */
    private Long partyId;

    /** 客户/供应商类型: CUSTOMER / VENDOR */
    private String partyType;

    /** 来源金额 */
    private BigDecimal amount;

    /** 目标未结金额 */
    private BigDecimal unsettledAmount;

    /**
     * 异常类型 —— <b>合法值以 DB CHECK {@code chk_exception_type} 为准</b>，
     * 即 {@code AMOUNT_DIFF} / {@code DATE_DIFF} / {@code UNMATCHED} / {@code DUPLICATE}。
     *
     * <p>⚠️ <b>P109 / D-109-2 裁定</b>：前端原先用的是另一套词表
     * （{@code PARTY_MISMATCH} / {@code AMOUNT_MISMATCH} / {@code INVOICE_NOT_FOUND} /
     * {@code MATCH_FAILED} / {@code APPROVAL_REQUIRED}），与 CHECK <b>完全不交集</b>。
     * 裁定为「<b>改前端对齐 DB</b>」，故本字段的文档以 DB 为准。
     * 铁律：任何常量都要 {@code pg_get_constraintdef} 查证后再用，禁止照抄别处的字符串
     * （AGENTS §4.2 第 9/14 条）。
     */
    private String exceptionType;

    /** 异常原因描述（替代遗留的 description 列） */
    private String exceptionReason;

    /** AI/规则推荐的匹配方案 (JSON) */
    private String matchSuggestion;

    /** 状态: OPEN / RESOLVED / IGNORED */
    @StatusChangeable(entity = "RECONCILIATION_EXCEPTION", fieldName = "status")
    private String status;

    /** 已重试次数；前端异常池页面 {{ row.retryCount ?? 0 }} 展示用 */
    private Integer retryCount;

    /** 指派处理人 */
    private Long assignedTo;

    /** 处理人 */
    private Long resolvedBy;

    /** 处理时间 */
    private LocalDateTime resolvedAt;

    /** 处理备注 */
    private String remark;

    /**
     * 创建人 —— 显式重声明以<b>遮蔽</b> {@link BaseEntity#createdBy} 的
     * {@code exist = false}：{@code V169} 已补真实列 {@code created_by}，
     * 故此处去掉 {@code exist=false}，让 Service 的赋值真正落库。
     */
    private Long createdBy;

    // createdAt / updatedAt / enterpriseId / deleted / updatedBy / version 一律
    // 继承自 BaseEntity —— 不要再在本类重复声明（重复声明会遮蔽基类可写入的字段，
    // 见本类 javadoc「字段遮蔽」段）。
}